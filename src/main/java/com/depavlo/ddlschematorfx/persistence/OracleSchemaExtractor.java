package com.depavlo.ddlschematorfx.persistence;

import com.depavlo.ddlschematorfx.model.ConnectionDetails;
import com.depavlo.ddlschematorfx.model.ObjectType;
import com.depavlo.ddlschematorfx.model.Schema;
import org.apache.commons.collections4.map.MultiKeyMap; // Імпорт MultiKeyMap

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Клас для витягнення структури схеми з бази даних Oracle
public class OracleSchemaExtractor {

    // SQL запит для отримання списку об'єктів схеми
    private static final String GET_SCHEMA_OBJECTS_SQL_TEMPLATE =
            "SELECT object_name, object_type FROM all_objects WHERE owner = ? AND object_type IN (%s) AND status = 'VALID' AND NOT (object_type = 'SEQUENCE' AND object_name LIKE 'ISEQ$$_%%')";

    // Виклик функції DBMS_METADATA.GET_DDL
    private static final String GET_DDL_SQL =
            "{ ? = call DBMS_METADATA.GET_DDL(?, ?, ?) }";


    /**
     * Витягує структуру схеми з бази даних Oracle.
     * @param connectionDetails Деталі підключення до БД.
     * @param schemaOwnerName Назва схеми (власник), яку потрібно витягти.
     * @return Об'єкт Schema з витягнутими DDL об'єктів.
     * @throws SQLException Якщо виникає помилка при роботі з БД.
     */
    public Schema extractSchema(ConnectionDetails connectionDetails, String schemaOwnerName) throws SQLException {
        if (connectionDetails == null || schemaOwnerName == null || schemaOwnerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Connection details and schema owner name must be provided.");
        }

        // Ініціалізуємо MultiKeyMap для зберігання DDL
        MultiKeyMap<Object, String> objectDdlsMap = new MultiKeyMap<>();

        try (Connection connection = DriverManager.getConnection(
                connectionDetails.getUrl(),
                connectionDetails.getUser(),
                connectionDetails.getPassword())) {

            // TODO: Налаштування NLS параметрів та EBR, якщо необхідно

            ObjectType[] objectTypesToExtract = {
                    ObjectType.TABLE, ObjectType.VIEW, ObjectType.SEQUENCE,
                    ObjectType.PROCEDURE, ObjectType.FUNCTION, ObjectType.PACKAGE,
                    ObjectType.TRIGGER, ObjectType.INDEX, // CONSTRAINT обробляється через TABLE DDL або окремо
                    ObjectType.MATERIALIZED_VIEW, ObjectType.SYNONYM, ObjectType.DATABASE_LINK,
                    ObjectType.DIRECTORY_OBJECT, ObjectType.JOB, ObjectType.QUEUE,
                    ObjectType.TYPE, ObjectType.JAVA_SOURCE, ObjectType.LIBRARY,
                    ObjectType.SCHEDULER, ObjectType.XML_SCHEMA
                    // CONSTRAINT часто є частиною DDL таблиці. Якщо потрібно окремо, додайте.
            };

            String objectTypesSqlString = buildObjectTypesSqlString(objectTypesToExtract);

            try (PreparedStatement pstmt = connection.prepareStatement(String.format(GET_SCHEMA_OBJECTS_SQL_TEMPLATE, objectTypesSqlString))) {
                pstmt.setString(1, schemaOwnerName.toUpperCase());

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        String objectName = rs.getString("object_name");
                        String objectTypeString = rs.getString("object_type"); // Тип об'єкта як рядок з ALL_OBJECTS

                        ObjectType currentObjectType = getObjectTypeFromString(objectTypeString);
                        if (currentObjectType == ObjectType.OTHER && !"CONSTRAINT".equals(objectTypeString)) { // Дозволяємо CONSTRAINT проходити, якщо він є в запиті
                            System.out.println("Пропущено невідомий тип об'єкта: " + objectTypeString + " для " + objectName);
                            continue;
                        }


                        String ddl = getObjectDdl(connection, objectTypeString, objectName, schemaOwnerName);

                        // Додаємо DDL до MultiKeyMap
                        // Ключі: ObjectType enum та ім'я об'єкта (String)
                        // Ім'я схеми-власника (schemaOwnerName) зберігається в самому об'єкті Schema
                        if (ddl != null) { // Переконуємося, що DDL не null
                            objectDdlsMap.put(currentObjectType, objectName, ddl);
                            System.out.println("Витягнуто DDL для: " + currentObjectType + "/" + objectName + " (Власник: " + schemaOwnerName + ")");
                        } else {
                            System.out.println("Порожній DDL для: " + currentObjectType + "/" + objectName + " (Власник: " + schemaOwnerName + "). Пропущено.");
                        }
                    }
                }
            }

            String schemaId = UUID.randomUUID().toString();
            LocalDateTime extractionTimestamp = LocalDateTime.now();

            // Створюємо та повертаємо об'єкт Schema
            // schemaOwnerName - це ім'я схеми, яке ми витягуємо
            return new Schema(schemaId, schemaOwnerName, objectDdlsMap, extractionTimestamp, connectionDetails);

        } catch (SQLException e) {
            System.err.println("Помилка витягнення схеми '" + schemaOwnerName + "': " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.err.println("Невідома помилка при витягненні схеми '" + schemaOwnerName + "': " + e.getMessage());
            e.printStackTrace();
            throw new SQLException("Невідома помилка при витягненні схеми", e);
        }
    }

    private String buildObjectTypesSqlString(ObjectType[] objectTypes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < objectTypes.length; i++) {
            // Використовуємо назву enum константи, замінюючи підкреслення на пробіл, якщо потрібно для ALL_OBJECTS
            // Наприклад, MATERIALIZED_VIEW -> "MATERIALIZED VIEW"
            String typeForSql = objectTypes[i].name().replace("_", " ");
            sb.append("'").append(typeForSql).append("'");
            if (i < objectTypes.length - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    private String getObjectDdl(Connection connection, String objectTypeForDbmsMetadata, String objectName, String schemaName) throws SQLException {
        String ddl = null;
        // Мапа для відображення типів ALL_OBJECTS на типи, які очікує DBMS_METADATA.GET_DDL
        // Деякі типи можуть вимагати специфічного перетворення або обробки.
        Map<String, String> dbmsMetadataTypeMap = new HashMap<>();
        dbmsMetadataTypeMap.put("JOB", "PROCOBJ"); // Для Oracle Scheduler Jobs
        // Додайте інші відображення, якщо потрібно, наприклад:
        // dbmsMetadataTypeMap.put("PACKAGE BODY", "PACKAGE_BODY");
        // dbmsMetadataTypeMap.put("TYPE BODY", "TYPE_BODY");

        String effectiveObjectType = dbmsMetadataTypeMap.getOrDefault(objectTypeForDbmsMetadata, objectTypeForDbmsMetadata);

        // Спеціальна обробка для CONSTRAINT, якщо ми вирішимо їх витягувати окремо
        // Зазвичай вони є частиною DDL таблиці.
        // if ("CONSTRAINT".equals(objectTypeForDbmsMetadata)) {
        //     // Для CONSTRAINT потрібно знати тип обмеження та ім'я таблиці.
        //     // DBMS_METADATA.GET_DDL('CONSTRAINT', constraint_name, schema_name)
        //     // DBMS_METADATA.GET_DDL('REF_CONSTRAINT', constraint_name, schema_name) для FK
        //     // Це може бути складно, тому часто їх отримують разом з таблицею.
        //     // Якщо ви вирішили їх витягувати, тут потрібна буде додаткова логіка.
        //     System.out.println("Окреме витягнення DDL для CONSTRAINT " + objectName + " поки не реалізовано повністю.");
        //     // effectiveObjectType = "CONSTRAINT"; // або "REF_CONSTRAINT"
        // }


        try (CallableStatement cs = connection.prepareCall(GET_DDL_SQL)) {
            cs.registerOutParameter(1, java.sql.Types.CLOB);
            cs.setString(2, effectiveObjectType); // Тип об'єкта для DBMS_METADATA
            cs.setString(3, objectName);         // Ім'я об'єкта
            cs.setString(4, schemaName);         // Власник схеми

            // Налаштування параметрів трансформації для DBMS_METADATA (опціонально)
            // Наприклад, щоб виключити атрибути зберігання:
            // try (Statement transformStmt = connection.createStatement()) {
            //     transformStmt.execute("BEGIN DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM,'STORAGE',false); END;");
            //     transformStmt.execute("BEGIN DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM,'TABLESPACE',false); END;");
            //     transformStmt.execute("BEGIN DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM,'SEGMENT_ATTRIBUTES',false); END;");
            //     // Для констрейнтів як ALTER TABLE statements:
            //     // transformStmt.execute("BEGIN DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM,'CONSTRAINTS_AS_ALTER',true); END;");
            // } catch (SQLException e) {
            //     System.err.println("Попередження: не вдалося налаштувати параметри трансформації DBMS_METADATA: " + e.getMessage());
            // }

            cs.execute();

            java.sql.Clob clob = cs.getClob(1);
            if (clob != null) {
                if (clob.length() > 0) {
                    ddl = clob.getSubString(1, (int) clob.length());
                } else {
                    ddl = ""; // Порожній DDL
                }
                clob.free(); // Звільнення ресурсів CLOB
            }
        } catch (SQLException e) {
            System.err.println("Помилка отримання DDL для " + effectiveObjectType + " " + schemaName + "." + objectName + ": " + e.getMessage());
            // Не кидаємо виняток далі, щоб продовжити витягнення інших об'єктів,
            // але повертаємо null, щоб сигналізувати про проблему з цим конкретним об'єктом.
            return null;
        }
        return ddl;
    }

    /**
     * Допоміжний метод для конвертації рядка типу об'єкта в enum ObjectType.
     * @param objectTypeString Рядок типу об'єкта (наприклад, "TABLE", "VIEW", "MATERIALIZED VIEW").
     * @return Відповідний ObjectType або ObjectType.OTHER, якщо тип не знайдено.
     */
    private ObjectType getObjectTypeFromString(String objectTypeString) {
        if (objectTypeString == null || objectTypeString.trim().isEmpty()) {
            return ObjectType.OTHER;
        }
        String normalizedType = objectTypeString.toUpperCase().replace(" ", "_");
        try {
            return ObjectType.valueOf(normalizedType);
        } catch (IllegalArgumentException e) {
            // Спеціальні випадки, які не мапляться напряму
            // (хоча ObjectType.java вже має багато з них, це для прикладу)
            if ("MATERIALIZED_VIEW".equals(normalizedType)) { // Вже є в enum
                return ObjectType.MATERIALIZED_VIEW;
            }
            if ("DATABASE_LINK".equals(normalizedType)) { // Вже є в enum
                return ObjectType.DATABASE_LINK;
            }
            // Додайте інші мапінги, якщо enum не покриває всі варіанти з ALL_OBJECTS
            System.err.println("Попередження: Невідомий тип об'єкта '" + objectTypeString + "' (нормалізовано як '" + normalizedType + "'). Повертається OTHER.");
            return ObjectType.OTHER;
        }
    }
}

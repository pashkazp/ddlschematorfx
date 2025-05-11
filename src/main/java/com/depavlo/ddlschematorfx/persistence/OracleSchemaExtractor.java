package com.depavlo.ddlschematorfx.persistence;

import com.depavlo.ddlschematorfx.model.ConnectionDetails;
import com.depavlo.ddlschematorfx.model.Schema;
import com.depavlo.ddlschematorfx.model.ObjectType;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement; // Імпорт PreparedStatement
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
    // Фільтруємо за власником (owner) та типом об'єкта (object_type)
    // Включаємо тільки дійсні об'єкти ('VALID')
    // Виключаємо послідовності, пов'язані з identity columns (імена починаються на 'ISEQ$$_')
    // %s буде замінено на список типів об'єктів у форматі 'TYPE1', 'TYPE2', ...
    private static final String GET_SCHEMA_OBJECTS_SQL_TEMPLATE =
            "SELECT object_name, object_type FROM all_objects WHERE owner = ? AND object_type IN (%s) AND status = 'VALID' AND NOT (object_type = 'SEQUENCE' AND object_name LIKE 'ISEQ$$_%')"; // Додано виключення для ISEQ$$_

    // Виклик функції DBMS_METADATA.GET_DDL
    // Параметри: object_type, name, schema, version, model, transform
    // TODO: Налаштувати параметри DBMS_METADATA для отримання повного DDL, включаючи специфічні для 21c можливості
    private static final String GET_DDL_SQL =
            "{ ? = call DBMS_METADATA.GET_DDL(?, ?, ?) }"; // Повертає CLOB

    // TODO: Додати SQL для налаштування NLS параметрів та Edition-Based Redefinition (EBR)
    // private static final String SET_NLS_PARAMS_SQL = "ALTER SESSION SET NLS_DATE_FORMAT = 'YYYY-MM-DD HH24:MI:SS'";
    // private static final String SET_EDITION_SQL = "ALTER SESSION SET EDITION = ?";


    /**
     * Витягує структуру схеми з бази даних Oracle.
     * @param connectionDetails Деталі підключення до БД.
     * @param schemaName Назва схеми (власник), яку потрібно витягти.
     * @return Об'єкт Schema з витягнутими DDL об'єктів.
     * @throws SQLException Якщо виникає помилка при роботі з БД.
     */
    public Schema extractSchema(ConnectionDetails connectionDetails, String schemaName) throws SQLException {
        // Перевірка вхідних параметрів
        if (connectionDetails == null || schemaName == null || schemaName.trim().isEmpty()) {
            throw new IllegalArgumentException("Connection details and schema name must be provided.");
        }

        // Використовуємо try-with-resources для автоматичного закриття Connection
        try (Connection connection = DriverManager.getConnection(
                connectionDetails.getUrl(),
                connectionDetails.getUser(),
                connectionDetails.getPassword())) {

            // TODO: Налаштування NLS параметрів та EBR, якщо необхідно
            // try (Statement stmt = connection.createStatement()) {
            //     stmt.execute(SET_NLS_PARAMS_SQL);
            //     // Якщо використовується EBR, встановити відповідну Edition
            //     // if (connectionDetails.getEdition() != null) { ... }
            // } catch (SQLException e) {
            //     System.err.println("Помилка налаштування сесії: " + e.getMessage());
            //     // Продовжуємо, але логуємо попередження
            // }

            Map<String, String> objectDdls = new HashMap<>();

            // Отримуємо список типів об'єктів, які ми хочемо витягти
            // TODO: Винести список типів об'єктів у конфігурацію або параметр методу
            // Використовуємо ObjectType.values() як приклад, але потрібно фільтрувати
            // тільки ті типи, які підтримує DBMS_METADATA.GET_DDL та які ми хочемо порівнювати.
            ObjectType[] objectTypesToExtract = {
                    ObjectType.TABLE, ObjectType.VIEW, ObjectType.SEQUENCE,
                    ObjectType.PROCEDURE, ObjectType.FUNCTION, ObjectType.PACKAGE,
                    ObjectType.TRIGGER, ObjectType.INDEX, ObjectType.CONSTRAINT,
                    ObjectType.MATERIALIZED_VIEW, ObjectType.SYNONYM, ObjectType.DATABASE_LINK,
                    ObjectType.DIRECTORY_OBJECT, ObjectType.JOB, ObjectType.QUEUE,
                    ObjectType.TYPE, ObjectType.JAVA_SOURCE, ObjectType.LIBRARY,
                    ObjectType.SCHEDULER, ObjectType.XML_SCHEMA
                    // TODO: Додати інші типи з ObjectType, якщо потрібно
            };

            // Формуємо рядок з типами об'єктів для SQL запиту
            String objectTypesSqlString = buildObjectTypesSqlString(objectTypesToExtract);

            // Отримуємо список об'єктів схеми за типами
            // Використовуємо PreparedStatement для передачі параметра owner
            try (PreparedStatement pstmt = connection.prepareStatement(String.format(GET_SCHEMA_OBJECTS_SQL_TEMPLATE, objectTypesSqlString))) {
                pstmt.setString(1, schemaName.toUpperCase()); // Встановлюємо параметр owner

                try (ResultSet rs = pstmt.executeQuery()) {
                    // Для кожного об'єкта отримуємо його DDL
                    while (rs.next()) {
                        String objectName = rs.getString("object_name");
                        String objectType = rs.getString("object_type"); // Тип об'єкта як рядок з ALL_OBJECTS

                        // Отримуємо DDL для поточного об'єкта
                        String ddl = getObjectDdl(connection, objectType, objectName, schemaName);

                        // Зберігаємо DDL у мапу. Ключ: ТИП/ВЛАСНИК/ІМ'Я
                        String objectKey = objectType + "/" + schemaName + "/" + objectName;
                        objectDdls.put(objectKey, ddl);

                        System.out.println("Витягнуто DDL для: " + objectKey); // Логування прогресу
                    }
                }
            }


            // Генеруємо унікальний ID для витягнутої схеми
            String schemaId = UUID.randomUUID().toString();
            // Фіксуємо час витягнення
            LocalDateTime extractionTimestamp = LocalDateTime.now();

            // Створюємо та повертаємо об'єкт Schema
            return new Schema(schemaId, schemaName, objectDdls, extractionTimestamp);

        } catch (SQLException e) {
            System.err.println("Помилка витягнення схеми: " + e.getMessage());
            throw e; // Перекидаємо виняток для обробки на вищому рівні
        } catch (Exception e) {
             System.err.println("Невідома помилка при витягненні схеми: " + e.getMessage());
             throw new SQLException("Невідома помилка при витягненні схеми", e); // Обгортаємо інші винятки
        }
    }

    /**
     * Допоміжний метод для формування рядка з типами об'єктів для SQL запиту IN (..., ...).
     * @param objectTypes Масив типів об'єктів.
     * @return Рядок у форматі "'TYPE1', 'TYPE2', ...".
     */
    private String buildObjectTypesSqlString(ObjectType[] objectTypes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < objectTypes.length; i++) {
            // Використовуємо назву перерахування як рядок типу об'єкта
            sb.append("'").append(objectTypes[i].name().replace("_", " ")).append("'"); // Замінюємо '_' на пробіл, як в ALL_OBJECTS
            if (i < objectTypes.length - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }


    /**
     * Викликає DBMS_METADATA.GET_DDL для отримання DDL конкретного об'єкта.
     * @param connection Активне JDBC підключення.
     * @param objectType Тип об'єкта (як рядок з ALL_OBJECTS, наприклад, 'TABLE', 'JOB').
     * @param objectName Ім'я об'єкта.
     * @param schemaName Власник об'єкта.
     * @return Рядок з DDL об'єкта.
     * @throws SQLException Якщо виникає помилка при виклику DBMS_METADATA.
     */
    private String getObjectDdl(Connection connection, String objectType, String objectName, String schemaName) throws SQLException {
        String ddl = null;
        // Визначаємо тип об'єкта для виклику DBMS_METADATA.GET_DDL
        // Для деяких типів (наприклад, JOB) потрібно використовувати іншу назву
        String dbmsMetadataObjectType = objectType;
        // Мапа для відображення типів ALL_OBJECTS на типи DBMS_METADATA
        Map<String, String> dbmsMetadataTypeMap = new HashMap<>();
        dbmsMetadataTypeMap.put("JOB", "PROCOBJ"); // Oracle Scheduler Job
        dbmsMetadataTypeMap.put("PROGRAM", "PROCOBJ"); // Oracle Scheduler Program
        dbmsMetadataTypeMap.put("SCHEDULE", "PROCOBJ"); // Oracle Scheduler Schedule
        dbmsMetadataTypeMap.put("CHAIN", "PROCOBJ"); // Oracle Scheduler Chain
        dbmsMetadataTypeMap.put("RULE SET", "RULE_SET"); // Rule Set
        // TODO: Додати інші відображення, якщо потрібно


        if (dbmsMetadataTypeMap.containsKey(objectType)) {
            dbmsMetadataObjectType = dbmsMetadataTypeMap.get(objectType);
        }
        // Для більшості типів назва в ALL_OBJECTS співпадає з назвою для DBMS_METADATA.GET_DDL


        try (CallableStatement cs = connection.prepareCall(GET_DDL_SQL)) {
            // Реєструємо вихідний параметр (повертається CLOB)
            cs.registerOutParameter(1, java.sql.Types.CLOB);
            // Встановлюємо вхідні параметри для DBMS_METADATA.GET_DDL
            cs.setString(2, dbmsMetadataObjectType); // Використовуємо відображений тип об'єкта
            cs.setString(3, objectName); // name
            cs.setString(4, schemaName); // schema
            // TODO: Встановити параметри version, model, transform, якщо потрібно
            // Наприклад, для отримання повного DDL без сегментних атрибутів:
            // try (Statement transformStmt = connection.createStatement()) {
            //     transformStmt.execute("BEGIN DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM,'SEGMENT_ATTRIBUTES',false); END;");
            // }
            // Наприклад, для отримання DDL без власницьких схем:
            // try (Statement transformStmt = connection.createStatement()) {
            //     transformStmt.execute("BEGIN DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM,'STORAGE',false); END;");
            //     transformStmt.execute("BEGIN DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM,'TABLESPACE',false); END;");
            //     transformStmt.execute("BEGIN DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM,'SEGMENT_ATTRIBUTES',false); END;");
            //     transformStmt.execute("BEGIN DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM,'CONSTRAINTS_AS_ALTER',false); END;"); // Constraints as part of table DDL
            //     transformStmt.execute("BEGIN DBMS_METADATA.SET_TRANSFORM_PARAM(DBMS_METADATA.SESSION_TRANSFORM,'SIZE_BYTE_KEYWORD',false); END;"); // Remove BYTE keyword for VARCHAR2/CHAR
            // }


            cs.execute(); // Виконуємо виклик

            // Отримуємо результат (CLOB) та конвертуємо його в String
            java.sql.Clob clob = cs.getClob(1);
            if (clob != null) {
                // Використовуємо getSubString для отримання вмісту CLOB
                // Перевіряємо довжину, щоб уникнути помилок для порожніх CLOB
                if (clob.length() > 0) {
                    ddl = clob.getSubString(1, (int) clob.length());
                } else {
                    ddl = ""; // Порожній DDL для порожнього CLOB
                }
            }

        } catch (SQLException e) {
            System.err.println("Помилка отримання DDL для " + objectType + " " + schemaName + "." + objectName + ": " + e.getMessage());
            // TODO: Логування детальної помилки. Можливо, варто продовжити витягнення інших об'єктів.
            // Кидаємо виняток далі, щоб сигналізувати про проблему.
            throw e;
        }
        return ddl;
    }

    // TODO: Додати інші допоміжні методи, якщо потрібно.
}

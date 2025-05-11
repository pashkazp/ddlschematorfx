package com.depavlo.ddlschematorfx.persistence;

import com.depavlo.ddlschematorfx.model.ConnectionDetails;
import com.depavlo.ddlschematorfx.model.Schema;
import com.depavlo.ddlschematorfx.model.ObjectType; // Імпорт перерахування ObjectType

import java.sql.CallableStatement; // Для виклику процедур/функцій Oracle (DBMS_METADATA)
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet; // Для отримання результатів запитів
import java.sql.SQLException;
import java.sql.Statement; // Для виконання простих запитів
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID; // Для генерації ID схеми

// Клас для витягнення структури схеми з бази даних Oracle
public class OracleSchemaExtractor {

    // SQL запит для отримання списку об'єктів схеми
    // Фільтруємо за власником (owner) та типом об'єкта (object_type)
    // Включаємо тільки дійсні об'єкти ('VALID')
    // TODO: Розширити список типів об'єктів для Oracle 21c
    private static final String GET_SCHEMA_OBJECTS_SQL =
            "SELECT object_name, object_type FROM all_objects WHERE owner = ? AND object_type IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) AND status = 'VALID'";

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

            // Отримуємо список об'єктів схеми за типами
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(buildGetSchemaObjectsSql(schemaName, objectTypesToExtract))) { // Викликаємо допоміжний метод для побудови SQL

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
     * Допоміжний метод для побудови SQL запиту для отримання списку об'єктів.
     * @param owner Власник схеми.
     * @param objectTypes Масив типів об'єктів для включення.
     * @return Рядок SQL запиту.
     */
    private String buildGetSchemaObjectsSql(String owner, ObjectType[] objectTypes) {
        StringBuilder sql = new StringBuilder("SELECT object_name, object_type FROM all_objects WHERE owner = '")
                .append(owner.toUpperCase()).append("' AND object_type IN (");

        for (int i = 0; i < objectTypes.length; i++) {
            // Використовуємо назву перерахування як рядок типу об'єкта
            sql.append("'").append(objectTypes[i].name().replace("_", " ")).append("'"); // Замінюємо '_' на пробіл, як в ALL_OBJECTS
            if (i < objectTypes.length - 1) {
                sql.append(", ");
            }
        }
        sql.append(") AND status = 'VALID'");
        // TODO: Додати фільтрацію за певними схемами або об'єктами, якщо потрібно
        // TODO: Додати ORDER BY для детермінованого порядку (наприклад, ORDER BY object_type, object_name)
        return sql.toString();
    }


    /**
     * Викликає DBMS_METADATA.GET_DDL для отримання DDL конкретного об'єкта.
     * @param connection Активне JDBC підключення.
     * @param objectType Тип об'єкта (як рядок, наприклад, 'TABLE').
     * @param objectName Ім'я об'єкта.
     * @param schemaName Власник об'єкта.
     * @return Рядок з DDL об'єкта.
     * @throws SQLException Якщо виникає помилка при виклику DBMS_METADATA.
     */
    private String getObjectDdl(Connection connection, String objectType, String objectName, String schemaName) throws SQLException {
        String ddl = null;
        try (CallableStatement cs = connection.prepareCall(GET_DDL_SQL)) {
            // Реєструємо вихідний параметр (повертається CLOB)
            cs.registerOutParameter(1, java.sql.Types.CLOB);
            // Встановлюємо вхідні параметри для DBMS_METADATA.GET_DDL
            cs.setString(2, objectType); // object_type
            cs.setString(3, objectName); // name
            cs.setString(4, schemaName); // schema
            // TODO: Встановити параметри version, model, transform, якщо потрібно

            cs.execute(); // Виконуємо виклик

            // Отримуємо результат (CLOB) та конвертуємо його в String
            java.sql.Clob clob = cs.getClob(1);
            if (clob != null) {
                ddl = clob.getSubString(1, (int) clob.length());
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

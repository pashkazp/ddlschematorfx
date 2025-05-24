package com.depavlo.ddlschematorfx.service;

import com.depavlo.ddlschematorfx.model.Difference;
import com.depavlo.ddlschematorfx.model.DifferenceType;
import com.depavlo.ddlschematorfx.model.MigrationScript;
import com.depavlo.ddlschematorfx.model.ObjectType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ScriptGenerationService {

    // Порядок виконання для різних типів операцій
    private static final int ORDER_DROP_MODIFIED_RECREATABLE = 10; // Наприклад, для індексів, які будуть перестворені
    private static final int ORDER_DROP_REMOVED = 20;
    private static final int ORDER_CREATE_ADDED = 30;
    private static final int ORDER_ALTER_OR_CREATE_REPLACE_MODIFIED = 40; // Для CREATE OR REPLACE
    private static final int ORDER_MANUAL_REVIEW_MODIFIED = 50; // Для таблиць, послідовностей, що потребують уваги

    public List<MigrationScript> generateScripts(List<Difference> differences) {
        List<MigrationScript> migrationScripts = new ArrayList<>();
        if (differences == null) {
            return migrationScripts;
        }

        for (Difference diff : differences) {
            switch (diff.getType()) {
                case ADDED:
                    migrationScripts.add(generateCreateScript(diff));
                    break;
                case REMOVED:
                    migrationScripts.add(generateDropScript(diff));
                    break;
                case MODIFIED:
                    migrationScripts.addAll(generateModifyScripts(diff));
                    break;
            }
        }
        // Тут можна додати сортування migrationScripts за полем executionOrder, якщо потрібно
        // migrationScripts.sort(Comparator.comparingInt(MigrationScript::getExecutionOrder));
        return migrationScripts;
    }

    private MigrationScript generateCreateScript(Difference diff) {
        String fileName = String.format("CREATE_%s_%s.sql", diff.getObjectType(), sanitizeFileName(diff.getObjectName()));
        // targetDdl має містити повний CREATE statement
        String content = (diff.getTargetDdl() != null) ? diff.getTargetDdl().trim() : "-- ERROR: Target DDL is missing for ADDED object " + diff.getObjectName();
        if (!content.endsWith(";")) {
            content += "\n/"; // Додаємо слеш для виконання в Oracle, якщо його немає (або краще ;)
        }
        return new MigrationScript(diff.getObjectType(), fileName, content, ORDER_CREATE_ADDED);
    }

    private MigrationScript generateDropScript(Difference diff) {
        String objectTypeForDrop = diff.getObjectType().name().replace("_", " ");
        // Особливі випадки для DROP
        if (diff.getObjectType() == ObjectType.PACKAGE && diff.getObjectName().toUpperCase().endsWith("_BODY")) {
             // Це може бути не зовсім коректно, якщо тіло пакета видаляється окремо.
             // Зазвичай DROP PACKAGE видаляє і спеку, і тіло.
             // Якщо це саме видалення ТІЛА, то команда інша.
             // Поки що припускаємо, що видаляється весь пакет, якщо тип PACKAGE.
             // Якщо ж це саме PACKAGE_BODY, то треба мати такий ObjectType.
        } else if (diff.getObjectType() == ObjectType.TYPE && diff.getObjectName().toUpperCase().endsWith("_BODY")) {
            // Аналогічно для TYPE BODY
        }


        String fileName = String.format("DROP_%s_%s.sql", diff.getObjectType(), sanitizeFileName(diff.getObjectName()));
        String content = String.format("DROP %s %s.%s;\n/",
                objectTypeForDrop,
                sanitizeIdentifier(diff.getObjectOwner()),
                sanitizeIdentifier(diff.getObjectName()));
        
        // Деякі об'єкти не мають власника в DROP команді (наприклад, DIRECTORY, PUBLIC SYNONYM)
        // Це потребує більш детальної логіки або перевірки типів
        if (diff.getObjectType() == ObjectType.DIRECTORY_OBJECT || 
            (diff.getObjectType() == ObjectType.SYNONYM && "PUBLIC".equalsIgnoreCase(diff.getObjectOwner()))) {
             content = String.format("DROP %s %s;\n/",
                objectTypeForDrop,
                sanitizeIdentifier(diff.getObjectName()));
        }


        return new MigrationScript(diff.getObjectType(), fileName, content, ORDER_DROP_REMOVED);
    }

    private List<MigrationScript> generateModifyScripts(Difference diff) {
        List<MigrationScript> scripts = new ArrayList<>();
        String objectOwner = sanitizeIdentifier(diff.getObjectOwner());
        String objectName = sanitizeIdentifier(diff.getObjectName());
        String cleanObjectNameForFile = sanitizeFileName(diff.getObjectName());

        switch (diff.getObjectType()) {
            case VIEW:
            case PROCEDURE:
            case FUNCTION:
            case PACKAGE: // Припускаємо, що targetDdl містить CREATE OR REPLACE PACKAGE (для специфікації)
            // case PACKAGE_BODY: // Потрібен окремий ObjectType для PACKAGE_BODY
            case TRIGGER:
            case SYNONYM: // CREATE OR REPLACE SYNONYM
                String createOrReplaceContent = (diff.getTargetDdl() != null) ? diff.getTargetDdl().trim() : String.format("-- ERROR: Target DDL is missing for MODIFIED %s %s.%s", diff.getObjectType(), objectOwner, objectName);
                if (!createOrReplaceContent.endsWith(";") && !createOrReplaceContent.endsWith("/")) {
                     createOrReplaceContent += "\n/";
                } else if (createOrReplaceContent.endsWith(";") && !createOrReplaceContent.trim().endsWith("\n/")){
                    // Якщо закінчується на ';', але не на '/', додаємо '/' на новому рядку для деяких випадків
                    createOrReplaceContent = createOrReplaceContent.substring(0, createOrReplaceContent.length()-1) + "\n/";
                }


                scripts.add(new MigrationScript(
                        diff.getObjectType(),
                        String.format("MODIFY_OR_REPLACE_%s_%s.sql", diff.getObjectType(), cleanObjectNameForFile),
                        createOrReplaceContent,
                        ORDER_ALTER_OR_CREATE_REPLACE_MODIFIED
                ));
                break;
            
            case INDEX:
                // Для індексів безпечніше перестворити: DROP + CREATE
                String dropIndexFileName = String.format("DROP_MODIFIED_%s_%s.sql", diff.getObjectType(), cleanObjectNameForFile);
                String dropIndexContent = String.format("DROP INDEX %s.%s;\n/", objectOwner, objectName);
                 if ("PUBLIC".equalsIgnoreCase(diff.getObjectOwner()) && diff.getObjectType() == ObjectType.SYNONYM) { // Малоймовірно для індексів, але для загальності
                    dropIndexContent = String.format("DROP PUBLIC %s %s;\n/", diff.getObjectType().name().replace("_"," "), objectName);
                }

                scripts.add(new MigrationScript(diff.getObjectType(), dropIndexFileName, dropIndexContent, ORDER_DROP_MODIFIED_RECREATABLE));

                String createIndexFileName = String.format("CREATE_MODIFIED_%s_%s.sql", diff.getObjectType(), cleanObjectNameForFile);
                String createIndexContent = (diff.getTargetDdl() != null) ? diff.getTargetDdl().trim() : String.format("-- ERROR: Target DDL is missing for recreating MODIFIED INDEX %s.%s", objectOwner, objectName);
                if (!createIndexContent.endsWith(";") && !createIndexContent.endsWith("/")) {
                     createIndexContent += "\n/";
                } else if (createIndexContent.endsWith(";") && !createIndexContent.trim().endsWith("\n/")){
                    createIndexContent = createIndexContent.substring(0, createIndexContent.length()-1) + "\n/";
                }
                scripts.add(new MigrationScript(diff.getObjectType(), createIndexFileName, createIndexContent, ORDER_ALTER_OR_CREATE_REPLACE_MODIFIED));
                break;

            case TABLE:
                String tableComment = String.format(
                        "-- MODIFIED TABLE: %s.%s\n" +
                        "-- УВАГА: Потрібен ручний аналіз та генерація ALTER TABLE скриптів.\n" +
                        "-- Автоматична генерація DROP+CREATE не виконується через ризик втрати даних.\n" +
                        "-- --- Source DDL (стара версія) ---\n%s\n" +
                        "-- --- Target DDL (нова версія) ---\n%s\n",
                        objectOwner, objectName,
                        commentOutDdl(diff.getSourceDdl()),
                        commentOutDdl(diff.getTargetDdl())
                );
                scripts.add(new MigrationScript(
                        diff.getObjectType(),
                        String.format("REVIEW_TABLE_%s_%s.sql", diff.getObjectType(), cleanObjectNameForFile),
                        tableComment,
                        ORDER_MANUAL_REVIEW_MODIFIED
                ));
                break;
            
            case SEQUENCE:
                 String sequenceComment = String.format(
                        "-- MODIFIED SEQUENCE: %s.%s\n" +
                        "-- УВАГА: Зміна послідовності через DROP+CREATE скине її поточне значення.\n" +
                        "-- Розгляньте можливість використання ALTER SEQUENCE для зміни параметрів.\n" +
                        "-- --- Source DDL (стара версія) ---\n%s\n" +
                        "-- --- Target DDL (нова версія, може бути використана для перестворення) ---\n%s\n",
                        objectOwner, objectName,
                        commentOutDdl(diff.getSourceDdl()),
                        (diff.getTargetDdl() != null ? diff.getTargetDdl().trim() + "\n/" : "-- Target DDL for SEQUENCE is missing")
                );
                scripts.add(new MigrationScript(
                        diff.getObjectType(),
                        String.format("REVIEW_SEQUENCE_%s_%s.sql", diff.getObjectType(), cleanObjectNameForFile),
                        sequenceComment,
                        ORDER_MANUAL_REVIEW_MODIFIED
                ));
                break;

            // Додайте обробку інших типів за потреби
            default:
                String defaultComment = String.format(
                        "-- MODIFIED OBJECT (TYPE: %s): %s.%s\n" +
                        "-- УВАГА: Автоматична генерація скрипту для цього типу об'єкта не реалізована повністю.\n" +
                        "-- Розгляньте можливість використання CREATE OR REPLACE, якщо це доречно, або ручне оновлення.\n" +
                        "-- --- Source DDL (стара версія) ---\n%s\n" +
                        "-- --- Target DDL (нова версія) ---\n%s\n",
                        diff.getObjectType(), objectOwner, objectName,
                        commentOutDdl(diff.getSourceDdl()),
                        commentOutDdl(diff.getTargetDdl())
                );
                 if (diff.getTargetDdl() != null && 
                    (diff.getTargetDdl().toUpperCase().contains("CREATE OR REPLACE") || 
                     diff.getObjectType() == ObjectType.MATERIALIZED_VIEW)) { // MVIEW часто перестворюють
                    String createOrReplaceDefault = diff.getTargetDdl().trim();
                     if (!createOrReplaceDefault.endsWith(";") && !createOrReplaceDefault.endsWith("/")) {
                         createOrReplaceDefault += "\n/";
                     } else if (createOrReplaceDefault.endsWith(";") && !createOrReplaceDefault.trim().endsWith("\n/")){
                        createOrReplaceDefault = createOrReplaceDefault.substring(0, createOrReplaceDefault.length()-1) + "\n/";
                    }
                    defaultComment = createOrReplaceDefault; // Якщо є CREATE OR REPLACE, використовуємо його
                     scripts.add(new MigrationScript(
                        diff.getObjectType(),
                        String.format("MODIFY_OR_REPLACE_%s_%s.sql", diff.getObjectType(), cleanObjectNameForFile),
                        defaultComment,
                        ORDER_ALTER_OR_CREATE_REPLACE_MODIFIED
                     ));
                } else {
                    scripts.add(new MigrationScript(
                        diff.getObjectType(),
                        String.format("REVIEW_OBJECT_%s_%s.sql", diff.getObjectType(), cleanObjectNameForFile),
                        defaultComment,
                        ORDER_MANUAL_REVIEW_MODIFIED
                    ));
                }
                break;
        }
        return scripts;
    }

    private String sanitizeFileName(String name) {
        if (name == null) return "UNKNOWN_OBJECT";
        return name.replaceAll("[^a-zA-Z0-9_.-]", "_").toUpperCase();
    }
    
    private String sanitizeIdentifier(String identifier) {
        if (identifier == null) return "\"UNKNOWN_IDENTIFIER\"";
        // Якщо ідентифікатор не взятий в лапки і містить символи, що вимагають лапок, або є ключовим словом,
        // його слід взяти в лапки. Для простоти, якщо він не в лапках і містить щось крім букв, цифр, _, $, # - беремо в лапки.
        // Або якщо він є регістро-залежним (містить малі літери).
        // Oracle зберігає нецитовані ідентифікатори у верхньому регістрі.
        if (identifier.matches("^[A-Z][A-Z0-9_\\$#]*$") && !isOracleKeyword(identifier.toUpperCase())) {
            return identifier.toUpperCase(); // Нецитовані, стандартні імена
        }
        return "\"" + identifier.replace("\"", "\"\"") + "\""; // Цитуємо, екрануючи внутрішні лапки
    }

    // Дуже спрощений список ключових слів Oracle, потребує розширення
    private static final List<String> ORACLE_KEYWORDS = List.of("TABLE", "VIEW", "INDEX", "USER", "ORDER", "GROUP", "SESSION"); 
    private boolean isOracleKeyword(String word) {
        return ORACLE_KEYWORDS.contains(word.toUpperCase());
    }


    private String commentOutDdl(String ddl) {
        if (ddl == null || ddl.trim().isEmpty()) {
            return "-- (No DDL provided)";
        }
        return ddl.lines().map(line -> "-- " + line).collect(Collectors.joining("\n"));
    }
}

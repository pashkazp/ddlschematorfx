package com.depavlo.ddlschematorfx.service;

import com.depavlo.ddlschematorfx.model.Difference;
import com.depavlo.ddlschematorfx.model.DifferenceType;
import com.depavlo.ddlschematorfx.model.MigrationScript;
import com.depavlo.ddlschematorfx.model.ObjectType;
import com.depavlo.ddlschematorfx.utils.DdlUtils; // Імпорт утиліт

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ScriptGenerationService {

    private static final int ORDER_DROP_MODIFIED_RECREATABLE = 10;
    private static final int ORDER_DROP_REMOVED = 20;
    private static final int ORDER_CREATE_ADDED = 30;
    private static final int ORDER_ALTER_OR_CREATE_REPLACE_MODIFIED = 40;
    private static final int ORDER_MANUAL_REVIEW_MODIFIED = 50;

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
        return migrationScripts;
    }

    private MigrationScript generateCreateScript(Difference diff) {
        String fileName = String.format("CREATE_%s_%s.sql", diff.getObjectType(), sanitizeFileName(diff.getObjectName()));
        String originalTargetDdl = (diff.getTargetDdl() != null) ? diff.getTargetDdl().trim() : "-- ERROR: Target DDL is missing for ADDED object " + diff.getObjectName();

        // Намагаємося видалити префікс схеми з основної CREATE заяви
        String content = DdlUtils.stripSchemaFromCreateStatement(originalTargetDdl, diff.getObjectType(), diff.getObjectName());

        if (!content.endsWith(";") && !content.endsWith("/")) {
            content += "\n/";
        } else if (content.endsWith(";") && !content.trim().endsWith("\n/")){
            content = content.substring(0, content.length()-1) + "\n/";
        }
        return new MigrationScript(diff.getObjectType(), fileName, content, ORDER_CREATE_ADDED);
    }

    private MigrationScript generateDropScript(Difference diff) {
        String objectTypeForDrop = diff.getObjectType().name().replace("_", " ");
        String objectNameToDrop = sanitizeIdentifier(diff.getObjectName()); // Використовуємо некваліфіковане ім'я
        String fileName = String.format("DROP_%s_%s.sql", diff.getObjectType(), sanitizeFileName(diff.getObjectName()));

        String content;
        // Для деяких типів об'єктів (наприклад, PUBLIC SYNONYM, DIRECTORY) не потрібен власник
        if (diff.getObjectType() == ObjectType.SYNONYM && "PUBLIC".equalsIgnoreCase(diff.getObjectOwner())) {
            content = String.format("DROP PUBLIC %s %s;\n/",
                    objectTypeForDrop, // SYNONYM
                    objectNameToDrop);
        } else if (diff.getObjectType() == ObjectType.DIRECTORY_OBJECT) {
            content = String.format("DROP %s %s;\n/",
                    objectTypeForDrop, // DIRECTORY
                    objectNameToDrop);
        }
        else {
            // За замовчуванням генеруємо DROP без вказівки схеми (для виконання у цільовій схемі)
            content = String.format("DROP %s %s;\n/",
                    objectTypeForDrop,
                    objectNameToDrop);
        }
        return new MigrationScript(diff.getObjectType(), fileName, content, ORDER_DROP_REMOVED);
    }

    private List<MigrationScript> generateModifyScripts(Difference diff) {
        List<MigrationScript> scripts = new ArrayList<>();
        // String objectOwner = sanitizeIdentifier(diff.getObjectOwner()); // Власник тепер менш важливий для генерації
        String objectNameForFile = sanitizeFileName(diff.getObjectName());
        String unqualifiedObjectName = diff.getObjectName(); // Має бути некваліфікованим

        switch (diff.getObjectType()) {
            case VIEW:
            case PROCEDURE:
            case FUNCTION:
            case PACKAGE:
            case TRIGGER:
            case SYNONYM: // Для не-public синонімів
                String originalTargetDdl = (diff.getTargetDdl() != null) ? diff.getTargetDdl().trim() :
                        String.format("-- ERROR: Target DDL is missing for MODIFIED %s %s", diff.getObjectType(), unqualifiedObjectName);

                String createOrReplaceContent = DdlUtils.stripSchemaFromCreateStatement(originalTargetDdl, diff.getObjectType(), unqualifiedObjectName);

                if (!createOrReplaceContent.endsWith(";") && !createOrReplaceContent.endsWith("/")) {
                    createOrReplaceContent += "\n/";
                } else if (createOrReplaceContent.endsWith(";") && !createOrReplaceContent.trim().endsWith("\n/")){
                    createOrReplaceContent = createOrReplaceContent.substring(0, createOrReplaceContent.length()-1) + "\n/";
                }

                scripts.add(new MigrationScript(
                        diff.getObjectType(),
                        String.format("MODIFY_OR_REPLACE_%s_%s.sql", diff.getObjectType(), objectNameForFile),
                        createOrReplaceContent,
                        ORDER_ALTER_OR_CREATE_REPLACE_MODIFIED
                ));
                break;

            case INDEX:
                String dropIndexFileName = String.format("DROP_MODIFIED_%s_%s.sql", diff.getObjectType(), objectNameForFile);
                // Індекси часто мають унікальні імена в межах схеми, тому власник не потрібен для DROP
                String dropIndexContent = String.format("DROP INDEX %s;\n/", sanitizeIdentifier(unqualifiedObjectName));
                scripts.add(new MigrationScript(diff.getObjectType(), dropIndexFileName, dropIndexContent, ORDER_DROP_MODIFIED_RECREATABLE));

                String createIndexFileName = String.format("CREATE_MODIFIED_%s_%s.sql", diff.getObjectType(), objectNameForFile);
                String originalCreateIndexDdl = (diff.getTargetDdl() != null) ? diff.getTargetDdl().trim() :
                        String.format("-- ERROR: Target DDL is missing for recreating MODIFIED INDEX %s", unqualifiedObjectName);

                // Для CREATE INDEX, якщо він містить ім'я таблиці з префіксом схеми, це може бути проблемою.
                // DdlUtils.stripSchemaFromCreateStatement може не спрацювати ідеально для CREATE INDEX.
                // Поки що використовуємо "як є", але з попередженням, що може знадобитися ручне редагування.
                String createIndexContent = originalCreateIndexDdl; // Або спробувати DdlUtils.stripSchemaPrefixesForComparison(originalCreateIndexDdl, diff.getObjectOwner());
                if (!createIndexContent.endsWith(";") && !createIndexContent.endsWith("/")) {
                    createIndexContent += "\n/";
                } else if (createIndexContent.endsWith(";") && !createIndexContent.trim().endsWith("\n/")){
                    createIndexContent = createIndexContent.substring(0, createIndexContent.length()-1) + "\n/";
                }
                scripts.add(new MigrationScript(diff.getObjectType(), createIndexFileName, createIndexContent, ORDER_ALTER_OR_CREATE_REPLACE_MODIFIED));
                break;

            case TABLE:
                String tableComment = String.format(
                        "-- MODIFIED TABLE: %s (Owner: %s -> %s)\n" + // Додаємо інформацію про власників для контексту
                                "-- УВАГА: Потрібен ручний аналіз та генерація ALTER TABLE скриптів.\n" +
                                "-- Автоматична генерація DROP+CREATE не виконується через ризик втрати даних.\n" +
                                "-- --- Source DDL (стара версія, схема: %s) ---\n%s\n" +
                                "-- --- Target DDL (нова версія, схема: %s) ---\n%s\n",
                        unqualifiedObjectName, diff.getObjectOwner(), diff.getObjectOwner(), // Припускаємо, що власник той самий для логічного об'єкта
                        diff.getObjectOwner(), commentOutDdl(diff.getSourceDdl()),
                        diff.getObjectOwner(), commentOutDdl(diff.getTargetDdl())
                );
                scripts.add(new MigrationScript(
                        diff.getObjectType(),
                        String.format("REVIEW_TABLE_%s_%s.sql", diff.getObjectType(), objectNameForFile),
                        tableComment,
                        ORDER_MANUAL_REVIEW_MODIFIED
                ));
                break;

            case SEQUENCE:
                String sequenceComment = String.format(
                        "-- MODIFIED SEQUENCE: %s (Owner: %s -> %s)\n" +
                                "-- УВАГА: Зміна послідовності через DROP+CREATE скине її поточне значення.\n" +
                                "-- Розгляньте можливість використання ALTER SEQUENCE для зміни параметрів.\n" +
                                "-- --- Source DDL (стара версія, схема: %s) ---\n%s\n" +
                                "-- --- Target DDL (нова версія, схема: %s, може бути використана для перестворення) ---\n%s\n",
                        unqualifiedObjectName, diff.getObjectOwner(), diff.getObjectOwner(),
                        diff.getObjectOwner(), commentOutDdl(diff.getSourceDdl()),
                        diff.getObjectOwner(), (diff.getTargetDdl() != null ? DdlUtils.stripSchemaFromCreateStatement(diff.getTargetDdl().trim(), ObjectType.SEQUENCE, unqualifiedObjectName) + "\n/" : "-- Target DDL for SEQUENCE is missing")
                );
                scripts.add(new MigrationScript(
                        diff.getObjectType(),
                        String.format("REVIEW_SEQUENCE_%s_%s.sql", diff.getObjectType(), objectNameForFile),
                        sequenceComment,
                        ORDER_MANUAL_REVIEW_MODIFIED
                ));
                break;

            default:
                String defaultTargetDdl = (diff.getTargetDdl() != null) ? diff.getTargetDdl().trim() : "";
                String modifiedDefaultDdl = DdlUtils.stripSchemaFromCreateStatement(defaultTargetDdl, diff.getObjectType(), unqualifiedObjectName);
                if (!modifiedDefaultDdl.endsWith(";") && !modifiedDefaultDdl.endsWith("/")) {
                    modifiedDefaultDdl += "\n/";
                } else if (modifiedDefaultDdl.endsWith(";") && !modifiedDefaultDdl.trim().endsWith("\n/")){
                    modifiedDefaultDdl = modifiedDefaultDdl.substring(0, modifiedDefaultDdl.length()-1) + "\n/";
                }

                if (diff.getTargetDdl() != null && diff.getTargetDdl().toUpperCase().contains("CREATE OR REPLACE")) {
                    scripts.add(new MigrationScript(
                            diff.getObjectType(),
                            String.format("MODIFY_OR_REPLACE_%s_%s.sql", diff.getObjectType(), objectNameForFile),
                            modifiedDefaultDdl,
                            ORDER_ALTER_OR_CREATE_REPLACE_MODIFIED
                    ));
                } else {
                    String defaultComment = String.format(
                            "-- MODIFIED OBJECT (TYPE: %s): %s (Owner: %s -> %s)\n" +
                                    "-- УВАГА: Автоматична генерація скрипту для цього типу об'єкта може бути неповною.\n" +
                                    "-- Розгляньте можливість використання CREATE OR REPLACE або ручне оновлення.\n" +
                                    "-- --- Source DDL (стара версія, схема: %s) ---\n%s\n" +
                                    "-- --- Target DDL (нова версія, схема: %s, спроба зробити schema-agnostic) ---\n%s\n",
                            diff.getObjectType(), unqualifiedObjectName, diff.getObjectOwner(), diff.getObjectOwner(),
                            diff.getObjectOwner(), commentOutDdl(diff.getSourceDdl()),
                            diff.getObjectOwner(), commentOutDdl(modifiedDefaultDdl) // Показуємо модифікований DDL у коментарі
                    );
                    scripts.add(new MigrationScript(
                            diff.getObjectType(),
                            String.format("REVIEW_OBJECT_%s_%s.sql", diff.getObjectType(), objectNameForFile),
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
        // Проста логіка: якщо містить щось крім букв, цифр, підкреслення, долара, решітки, або якщо є малі літери - беремо в лапки.
        // Це не враховує ключові слова Oracle.
        if (!identifier.matches("^[A-Z][A-Z0-9_\\$#]*$") || !identifier.equals(identifier.toUpperCase())) {
            return "\"" + identifier.replace("\"", "\"\"") + "\""; // Цитуємо, екрануючи внутрішні лапки
        }
        // Якщо ідентифікатор вже в лапках або є стандартним, повертаємо як є (або у верхньому регістрі)
        if (identifier.startsWith("\"") && identifier.endsWith("\"")) {
            return identifier;
        }
        return identifier.toUpperCase();
    }

    private String commentOutDdl(String ddl) {
        if (ddl == null || ddl.trim().isEmpty()) {
            return "-- (No DDL provided)";
        }
        return ddl.lines().map(line -> "-- " + line).collect(Collectors.joining("\n"));
    }
}

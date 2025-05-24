package com.depavlo.ddlschematorfx.utils;

import com.depavlo.ddlschematorfx.model.ObjectType; // Потрібно для деяких специфічних правил

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DdlUtils {

    /**
     * Намагається видалити префікси схеми з DDL рядка для цілей порівняння.
     * Це спрощений підхід, який може не покривати всі випадки.
     * Приклад: "SCHEMA_A"."TABLE_B" -> "TABLE_B"
     * SCHEMA_A.TABLE_C -> TABLE_C
     * @param ddl DDL рядок.
     * @param schemaName Ім'я схеми, префікси якої потрібно видалити.
     * @return DDL рядок з видаленими (або заміненими) префіксами схеми.
     */
    public static String stripSchemaPrefixesForComparison(String ddl, String schemaName) {
        if (ddl == null || schemaName == null || schemaName.trim().isEmpty()) {
            return ddl;
        }

        // Екрануємо ім'я схеми для використання в регулярному виразі
        String quotedSchemaName = Pattern.quote(schemaName.toUpperCase()); // Oracle часто зберігає імена у верхньому регістрі

        // Регулярний вираз для пошуку "SCHEMA_NAME"."OBJECT_NAME" або SCHEMA_NAME.OBJECT_NAME
        // Перша група захоплює лапки навколо імені об'єкта, якщо вони є.
        // Друга група захоплює саме ім'я об'єкта.
        // (?i) - ігнорування регістру для SCHEMA_NAME
        // Pattern pattern = Pattern.compile("(?i)" + quotedSchemaName + "\\s*\\.\\s*(\"?)([a-zA-Z0-9_\\$#]+)(\"?)");
        // Більш обережний варіант: шукаємо "SCHEMA"."OBJ" або SCHEMA.OBJ
        // Важливо не замінити SCHEMA. в коментарях або рядкових літералах. Це складно з простим regex.
        
        String regex1 = "(?i)(?:\\b" + quotedSchemaName + "\\b\\s*\\.\\s*)(\"?[A-Za-z0-9_$#]+\"?)"; // SCHEMA.OBJECT or SCHEMA."OBJECT"
        // Замінюємо SCHEMA.OBJECT на OBJECT (або "OBJECT")
        String result = ddl.replaceAll(regex1, "$1");

        // Додатково для випадків, коли ім'я схеми може бути в лапках: \"" + schemaName + "\"."
        String quotedSchemaInPattern = Pattern.quote("\"" + schemaName.toUpperCase() + "\"");
        String regex2 = "(?i)(?:" + quotedSchemaInPattern + "\\s*\\.\\s*)(\"?[A-Za-z0-9_$#]+\"?)"; // "SCHEMA"."OBJECT"
        result = result.replaceAll(regex2, "$1");
        
        // Дуже спрощено, може потребувати доопрацювання для різних стилів цитування та пробілів.
        // Цей підхід не ідеальний і може мати хибні спрацювання або пропуски.
        return result;
    }

    /**
     * Намагається видалити префікс схеми з основної команди CREATE / CREATE OR REPLACE.
     * Наприклад, CREATE TABLE "SCHEMA_A"."TABLE_B" -> CREATE TABLE "TABLE_B"
     * @param ddl Оригінальний DDL.
     * @param objectType Тип об'єкта.
     * @param unqualifiedObjectName Ім'я об'єкта без схеми.
     * @return Модифікований DDL або оригінальний, якщо модифікація не вдалася.
     */
    public static String stripSchemaFromCreateStatement(String ddl, ObjectType objectType, String unqualifiedObjectName) {
        if (ddl == null || objectType == null || unqualifiedObjectName == null) {
            return ddl;
        }

        // Спрощений шаблон для пошуку CREATE [OR REPLACE] TYPE/TABLE/VIEW/etc. "SCHEMA"."OBJECT"
        // або CREATE [OR REPLACE] TYPE/TABLE/VIEW/etc. SCHEMA.OBJECT
        // (?i) - ігнорування регістру для CREATE OR REPLACE ...
        // \\s+ - один або більше пробільних символів
        // (?:\"[^\"]+\"\\.)? - опціональна схема в лапках з крапкою
        // (?:[A-Za-z0-9_]+\\.)? - опціональна схема без лапок з крапкою
        // (\"?)%s(\"?) - ім'я об'єкта, можливо в лапках (замість %s буде Pattern.quote(unqualifiedObjectName))

        String objectTypePattern = objectType.name().replace("_", "\\s+"); // Наприклад, MATERIALIZED_VIEW -> MATERIALIZED\s+VIEW
        String quotedObjectName = Pattern.quote(unqualifiedObjectName); // Екрануємо ім'я об'єкта

        // Регулярний вираз, що шукає "CREATE [OR REPLACE] <TYPE> schema.object" або "CREATE [OR REPLACE] <TYPE> "schema"."object""
        // і замінює на "CREATE [OR REPLACE] <TYPE> object"
        // Перша група (createPart): "CREATE OR REPLACE TYPE ", "CREATE TABLE ", etc.
        // Друга група (schemaPart): "SCHEMA_NAME." або "\"SCHEMA_NAME\"." - її ми хочемо видалити
        // Третя група (objectNamePart): "\"OBJECT_NAME\"" або "OBJECT_NAME" - її ми залишаємо
        Pattern pattern = Pattern.compile(
                "(?i)(CREATE(?:\\s+OR\\s+REPLACE)?\\s+" + objectTypePattern + "\\s+)" + // $1: CREATE [OR REPLACE] TYPE
                "(?:(?:\"[A-Za-z0-9_$#]+\"|[A-Za-z0-9_$#]+)\\s*\\.\\s*)" +             // schema part (non-capturing)
                "(\"?" + quotedObjectName + "\"?)"                                 // $2: object name (з можливими лапками)
                , Pattern.CASE_INSENSITIVE);

        Matcher matcher = pattern.matcher(ddl);
        if (matcher.find()) {
            // Замінюємо "CREATE TYPE SCHEMA.OBJ" на "CREATE TYPE OBJ"
            // Або "CREATE TYPE "SCHEMA"."OBJ"" на "CREATE TYPE "OBJ""
            try {
                return matcher.replaceAll("$1$2");
            } catch (Exception e) {
                System.err.println("Помилка при спробі видалити префікс схеми з CREATE заяви для " + objectType + " " + unqualifiedObjectName + ": " + e.getMessage());
                return ddl; // Повертаємо оригінал у разі помилки
            }
        }
        return ddl; // Повертаємо оригінал, якщо шаблон не знайдено
    }
}

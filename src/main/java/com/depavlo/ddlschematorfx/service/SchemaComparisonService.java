package com.depavlo.ddlschematorfx.service;

import com.depavlo.ddlschematorfx.model.Difference;
import com.depavlo.ddlschematorfx.model.DifferenceType;
import com.depavlo.ddlschematorfx.model.ObjectType; // Потрібно буде створити цей enum, якщо його ще немає
import com.depavlo.ddlschematorfx.model.Schema;
import com.manticore.jsqlformatter.JSQLFormatter; // Імпорт форматера

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SchemaComparisonService {

    /**
     * Порівнює дві схеми та повертає список відмінностей.
     *
     * @param sourceSchema Схема-джерело (наприклад, стара версія або схема з БД).
     * @param targetSchema Цільова схема (наприклад, нова версія або схема з файлів).
     * @return Список об'єктів Difference.
     */
    public List<Difference> compareSchemas(Schema sourceSchema, Schema targetSchema) {
        if (sourceSchema == null || targetSchema == null) {
            throw new IllegalArgumentException("Source and target schemas cannot be null.");
        }

        List<Difference> differences = new ArrayList<>();
        Map<String, String> sourceObjects = sourceSchema.getObjectDdls();
        Map<String, String> targetObjects = targetSchema.getObjectDdls();

        // Використовуємо JSQLFormatter для нормалізації DDL перед порівнянням
        // Налаштування форматера за замовчуванням
        JSQLFormatter formatter = new JSQLFormatter();

        // Знаходимо об'єкти, які були видалені (є в source, але немає в target)
        for (String sourceKey : sourceObjects.keySet()) {
            if (!targetObjects.containsKey(sourceKey)) {
                String[] keyParts = parseObjectKey(sourceKey);
                if (keyParts != null) {
                    differences.add(new Difference(
                            DifferenceType.REMOVED,
                            getObjectTypeFromString(keyParts[0]),
                            keyParts[2], // objectName
                            keyParts[1], // objectOwner (schema name)
                            sourceObjects.get(sourceKey),
                            null,
                            "Object removed from target schema."
                    ));
                }
            }
        }

        // Знаходимо об'єкти, які були додані або змінені
        for (String targetKey : targetObjects.keySet()) {
            String[] keyParts = parseObjectKey(targetKey);
            if (keyParts == null) continue; // Пропускаємо, якщо ключ некоректний

            String targetDdl = targetObjects.get(targetKey);
            String formattedTargetDdl = "";
            if (targetDdl != null && !targetDdl.trim().isEmpty()) {
                 try {
                    formattedTargetDdl = formatter.format(targetDdl);
                } catch (Exception e) {
                    System.err.println("Warning: Could not format target DDL for object " + targetKey + ". Using raw DDL. Error: " + e.getMessage());
                    formattedTargetDdl = targetDdl; // Використовуємо "сирий" DDL у разі помилки форматування
                }
            }


            if (!sourceObjects.containsKey(targetKey)) {
                // Об'єкт додано (є в target, але немає в source)
                differences.add(new Difference(
                        DifferenceType.ADDED,
                        getObjectTypeFromString(keyParts[0]),
                        keyParts[2], // objectName
                        keyParts[1], // objectOwner (schema name)
                        null,
                        targetDdl, // Зберігаємо оригінальний DDL
                        "Object added to target schema."
                ));
            } else {
                // Об'єкт існує в обох схемах, перевіряємо на зміни
                String sourceDdl = sourceObjects.get(targetKey);
                String formattedSourceDdl = "";

                if (sourceDdl != null && !sourceDdl.trim().isEmpty()) {
                    try {
                        formattedSourceDdl = formatter.format(sourceDdl);
                    } catch (Exception e) {
                        System.err.println("Warning: Could not format source DDL for object " + targetKey + ". Using raw DDL. Error: " + e.getMessage());
                        formattedSourceDdl = sourceDdl; // Використовуємо "сирий" DDL у разі помилки форматування
                    }
                }

                // Порівнюємо відформатовані DDL
                if (!formattedSourceDdl.equals(formattedTargetDdl)) {
                    differences.add(new Difference(
                            DifferenceType.MODIFIED,
                            getObjectTypeFromString(keyParts[0]),
                            keyParts[2], // objectName
                            keyParts[1], // objectOwner (schema name)
                            sourceDdl, // Зберігаємо оригінальний DDL
                            targetDdl, // Зберігаємо оригінальний DDL
                            "Object DDL has been modified."
                            // TODO: В майбутньому можна додати більш детальний diff
                    ));
                }
            }
        }
        return differences;
    }

    /**
     * Допоміжний метод для парсингу ключа об'єкта.
     * Ключ має формат: ТИП_ОБЄКТУ/ВЛАСНИК_СХЕМИ/ІМЯ_ОБЄКТУ
     * @param objectKey Ключ об'єкта.
     * @return Масив рядків [тип, власник, ім'я] або null, якщо формат некоректний.
     */
    private String[] parseObjectKey(String objectKey) {
        if (objectKey == null) return null;
        String[] parts = objectKey.split("/", 3); // Ліміт 3, щоб ім'я об'єкта могло містити "/"
        if (parts.length == 3) {
            return parts;
        }
        System.err.println("Warning: Could not parse object key: " + objectKey + ". Expected format: TYPE/OWNER/NAME");
        return null; // Або повернути масив з одним елементом, щоб уникнути NPE далі, але це менш чисто
    }

    /**
     * Допоміжний метод для конвертації рядка типу об'єкта в enum ObjectType.
     * @param objectTypeString Рядок типу об'єкта (наприклад, "TABLE", "VIEW").
     * @return Відповідний ObjectType або ObjectType.OTHER, якщо тип не знайдено.
     */
    private ObjectType getObjectTypeFromString(String objectTypeString) {
        if (objectTypeString == null) return ObjectType.OTHER;
        try {
            // Спроба прямого перетворення (якщо назви співпадають з enum константами)
            // Можливо, потрібно буде замінити пробіли на підкреслення, якщо типи з БД містять пробіли
            return ObjectType.valueOf(objectTypeString.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            System.err.println("Warning: Unknown object type string: " + objectTypeString + ". Defaulting to OTHER.");
            // Тут можна додати більш складну логіку мапінгу, якщо потрібно
            // наприклад, "MATERIALIZED VIEW" -> ObjectType.MATERIALIZED_VIEW
            if ("MATERIALIZED VIEW".equalsIgnoreCase(objectTypeString)) {
                return ObjectType.MATERIALIZED_VIEW;
            }
            if ("DATABASE LINK".equalsIgnoreCase(objectTypeString)) {
                return ObjectType.DATABASE_LINK;
            }
            // ... додати інші специфічні мапінги
            return ObjectType.OTHER;
        }
    }
}

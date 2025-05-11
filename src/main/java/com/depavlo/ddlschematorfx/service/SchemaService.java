package com.depavlo.ddlschematorfx.service;

import com.depavlo.ddlschematorfx.model.Schema;
import com.depavlo.ddlschematorfx.model.ConnectionDetails; // Імпорт ConnectionDetails

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files; // Імпорт для роботи з файловою системою
import java.nio.file.Path; // Імпорт для роботи зі шляхами
import java.nio.file.Paths; // Імпорт для роботи зі шляхами
import java.time.format.DateTimeFormatter; // Імпорт для форматування дати/часу
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Простий сервіс для зберігання витягнутих схем у пам'яті та збереження їх у файли
// В реальному додатку може бути більш складним (наприклад, з кешуванням, збереженням на диск тощо)
public class SchemaService {

    // Мапа для зберігання схем за їх унікальним ID
    private final Map<String, Schema> loadedSchemas = new HashMap<>();

    // Форматер для створення унікальних імен директорій на основі дати та часу
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");


    /**
     * Додає витягнуту схему до сховища.
     * @param schema Об'єкт Schema для додавання.
     */
    public void addSchema(Schema schema) {
        if (schema != null && schema.getId() != null) {
            loadedSchemas.put(schema.getId(), schema);
            System.out.println("Схему '" + schema.getName() + "' з ID '" + schema.getId() + "' додано до сховища.");
        }
    }

    /**
     * Отримує схему за її унікальним ID.
     * @param schemaId Унікальний ID схеми.
     * @return Об'єкт Schema, або null, якщо схему не знайдено.
     */
    public Schema getSchema(String schemaId) {
        return loadedSchemas.get(schemaId);
    }

    /**
     * Видаляє схему за її унікальним ID.
     * @param schemaId Унікальний ID схеми.
     * @return Видалений об'єкт Schema, або null, якщо схему не знайдено.
     */
    public Schema removeSchema(String schemaId) {
        return loadedSchemas.remove(schemaId);
    }

    /**
     * Отримує список усіх завантажених схем.
     * @return Список об'єктів Schema.
     */
    public List<Schema> getAllSchemas() {
        return new java.util.ArrayList<>(loadedSchemas.values());
    }

    /**
     * Очищає сховище схем.
     */
    public void clearSchemas() {
        loadedSchemas.clear();
        System.out.println("Сховище схем очищено.");
    }

    /**
     * Зберігає витягнуту схему у файлову систему.
     * Створює директорію для схеми та піддиректорії для типів об'єктів.
     * Назва директорії включає назву підключення, назву схеми та час витягнення.
     * @param schema Об'єкт Schema для збереження (має містити sourceConnection).
     * @param baseDirectoryPath Шлях до базової директорії, де буде створено директорію схеми.
     * @throws IOException Якщо виникає помилка при роботі з файловою системою.
     */
    public void saveSchemaToFile(Schema schema, String baseDirectoryPath) throws IOException { // Прибрано connectionName
        if (schema == null || schema.getSourceConnection() == null || baseDirectoryPath == null || baseDirectoryPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Schema (with source connection) and base directory path must be provided.");
        }

        // Отримуємо назву підключення з об'єкта Schema
        String connectionName = schema.getSourceConnection().getName();

        // Формуємо назву директорії для схеми: назва_підключення_назва_схеми_YYYYMMDD_HHmmss
        // Замінюємо потенційно проблемні символи в назві підключення та назві схеми
        String cleanConnectionName = connectionName.replaceAll("[^a-zA-Z0-9_.-]", "_");
        String cleanSchemaName = schema.getName().replaceAll("[^a-zA-Z0-9_.-]", "_"); // Очищаємо назву схеми
        String schemaDirectoryName = cleanConnectionName + "_" + cleanSchemaName + "_" + schema.getExtractionTimestamp().format(TIMESTAMP_FORMATTER);


        Path schemaDirectory = Paths.get(baseDirectoryPath, schemaDirectoryName);

        // Створюємо директорію для схеми, якщо вона не існує
        Files.createDirectories(schemaDirectory);
        System.out.println("Створено директорію для схеми: " + schemaDirectory.toAbsolutePath());

        // Зберігаємо DDL кожного об'єкта
        if (schema.getObjectDdls() != null) {
            for (Map.Entry<String, String> entry : schema.getObjectDdls().entrySet()) {
                String objectKey = entry.getKey(); // Формат: ТИП/ВЛАСНИК/ІМ'Я
                String ddl = entry.getValue();

                if (ddl == null || ddl.trim().isEmpty()) {
                    System.out.println("Пропущено збереження порожнього DDL для: " + objectKey);
                    continue; // Пропускаємо порожні DDL
                }

                // Парсимо ключ, щоб отримати тип об'єкта, власника та ім'я
                String[] keyParts = objectKey.split("/");
                if (keyParts.length != 3) {
                    System.err.println("Некоректний формат ключа об'єкта: " + objectKey + ". Пропущено.");
                    continue;
                }
                String objectType = keyParts[0];
                String objectOwner = keyParts[1]; // Власник схеми
                String objectName = keyParts[2];

                // Формуємо шлях до піддиректорії для типу об'єкта
                Path objectTypeDirectory = schemaDirectory.resolve(objectType);

                // Створюємо піддиректорію для типу об'єкта, якщо вона не існує
                Files.createDirectories(objectTypeDirectory);

                // Формуємо назву файлу: ім'я_об'єкта.sql
                // Замінюємо потенційно проблемні символи в імені файлу
                String cleanObjectName = objectName.replaceAll("[^a-zA-Z0-9_.-]", "_");
                Path ddlFilePath = objectTypeDirectory.resolve(cleanObjectName + ".sql");

                // Записуємо DDL у файл
                try (BufferedWriter writer = Files.newBufferedWriter(ddlFilePath, StandardCharsets.UTF_8)) {
                    writer.write(ddl);
                    System.out.println("Збережено DDL для " + objectKey + " у файл: " + ddlFilePath.toAbsolutePath());
                } catch (IOException e) {
                    System.err.println("Помилка при збереженні DDL для " + objectKey + " у файл " + ddlFilePath.toAbsolutePath() + ": " + e.getMessage());
                    // TODO: Логування детальної помилки. Можливо, варто продовжити збереження інших об'єктів.
                }
            }
        }
        System.out.println("Збереження схеми '" + schema.getName() + "' завершено.");
    }


    // TODO: Додати методи для збереження/завантаження схем на диск, якщо потрібно.
}

package com.depavlo.ddlschematorfx.service;

import com.depavlo.ddlschematorfx.model.ConnectionDetails;
import com.depavlo.ddlschematorfx.model.Schema;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

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
    public void saveSchemaToFile(Schema schema, String baseDirectoryPath) throws IOException {
        if (schema == null || schema.getSourceConnection() == null || baseDirectoryPath == null || baseDirectoryPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Schema (with source connection) and base directory path must be provided.");
        }

        String connectionName = schema.getSourceConnection().getName();
        String cleanConnectionName = connectionName.replaceAll("[^a-zA-Z0-9_.-]", "_");
        String cleanSchemaName = schema.getName().replaceAll("[^a-zA-Z0-9_.-]", "_");
        String schemaDirectoryName = cleanConnectionName + "_" + cleanSchemaName + "_" + schema.getExtractionTimestamp().format(TIMESTAMP_FORMATTER);

        Path schemaDirectory = Paths.get(baseDirectoryPath, schemaDirectoryName);
        Files.createDirectories(schemaDirectory);
        System.out.println("Створено директорію для схеми: " + schemaDirectory.toAbsolutePath());

        if (schema.getObjectDdls() != null) {
            for (Map.Entry<String, String> entry : schema.getObjectDdls().entrySet()) {
                String objectKey = entry.getKey();
                String ddl = entry.getValue();

                if (ddl == null || ddl.trim().isEmpty()) {
                    System.out.println("Пропущено збереження порожнього DDL для: " + objectKey);
                    continue;
                }

                String[] keyParts = objectKey.split("/");
                if (keyParts.length != 3) {
                    System.err.println("Некоректний формат ключа об'єкта: " + objectKey + ". Пропущено.");
                    continue;
                }
                String objectType = keyParts[0];
                // String objectOwner = keyParts[1]; // Власник схеми - вже є в назві директорії
                String objectName = keyParts[2];

                Path objectTypeDirectory = schemaDirectory.resolve(objectType);
                Files.createDirectories(objectTypeDirectory);

                String cleanObjectName = objectName.replaceAll("[^a-zA-Z0-9_.-]", "_");
                Path ddlFilePath = objectTypeDirectory.resolve(cleanObjectName + ".sql");

                try (BufferedWriter writer = Files.newBufferedWriter(ddlFilePath, StandardCharsets.UTF_8)) {
                    writer.write(ddl);
                    System.out.println("Збережено DDL для " + objectKey + " у файл: " + ddlFilePath.toAbsolutePath());
                } catch (IOException e) {
                    System.err.println("Помилка при збереженні DDL для " + objectKey + " у файл " + ddlFilePath.toAbsolutePath() + ": " + e.getMessage());
                }
            }
        }
        System.out.println("Збереження схеми '" + schema.getName() + "' завершено.");
    }

    /**
     * Завантажує схему зі структурованого DDL-сховища (директорії з окремими файлами).
     * @param schemaDirectoryPath Шлях до директорії схеми.
     * @return Об'єкт Schema, завантажений з файлів.
     * @throws IOException Якщо виникає помилка при читанні файлів або структура директорії некоректна.
     */
    public Schema loadSchemaFromDirectory(Path schemaDirectoryPath) throws IOException {
        if (schemaDirectoryPath == null || !Files.isDirectory(schemaDirectoryPath)) {
            throw new IllegalArgumentException("Необхідно вказати дійсну директорію схеми.");
        }

        String directoryName = schemaDirectoryPath.getFileName().toString();
        // Парсинг назви директорії для отримання назви схеми та часу "витягнення"
        // Формат: cleanConnectionName_cleanSchemaName_yyyyMMdd_HHmmss
        // Ми зосередимося на cleanSchemaName та formatter timestamp
        String schemaNameFromFile;
        LocalDateTime extractionTimestampFromFile;

        String[] dirNameParts = directoryName.split("_");
        if (dirNameParts.length < 2) { // Потрібно щонайменше назва_схеми_ЧАС
            throw new IOException("Некоректний формат назви директорії схеми: " + directoryName + ". Очікується формат 'connectionName_schemaName_timestamp'.");
        }

        // Спроба витягти час з кінця
        try {
            extractionTimestampFromFile = LocalDateTime.parse(dirNameParts[dirNameParts.length - 1], TIMESTAMP_FORMATTER);
            // Якщо успішно, то назва схеми - це частина перед останнім елементом (часом)
            // і перед першим елементом (назвою підключення, яку ми можемо ігнорувати для схем з файлів)
            if (dirNameParts.length > 2) { // connectionName_schemaName_timestamp
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < dirNameParts.length - 1; i++) {
                    sb.append(dirNameParts[i]);
                    if (i < dirNameParts.length - 2) {
                        sb.append("_");
                    }
                }
                schemaNameFromFile = sb.toString();
            } else if (dirNameParts.length == 2) { // schemaName_timestamp (якщо connectionName не було або було порожнім)
                schemaNameFromFile = dirNameParts[0];
            }
            else {
                throw new IOException("Не вдалося визначити назву схеми з назви директорії: " + directoryName);
            }
        } catch (DateTimeParseException e) {
            System.err.println("Помилка парсингу часу з назви директорії: " + directoryName + ". Буде використано поточний час.");
            extractionTimestampFromFile = LocalDateTime.now(); // Запасний варіант
            // Якщо час не розпарсився, припускаємо, що остання частина не час,
            // а вся назва директорії - це назва схеми (менш надійний варіант)
            // Або можна викинути помилку, якщо потрібен точний час.
            // Для простоти, якщо час не парситься, спробуємо взяти назву схеми з передостанньої частини, якщо є 3 частини.
            if (dirNameParts.length >= 2) { // connectionName_schemaName (або просто schemaName, якщо немає "_")
                StringBuilder sb = new StringBuilder();
                // Якщо час не розпарсився, то schemaName - це все до останнього _ (якщо він був) або вся назва
                // Спробуємо взяти всі частини крім першої (connectionName) як назву схеми
                if(dirNameParts.length > 1){ // Якщо є хоча б connectionName_schemaName
                    for (int i = 1; i < dirNameParts.length; i++) {
                        sb.append(dirNameParts[i]);
                        if (i < dirNameParts.length - 1) {
                            sb.append("_");
                        }
                    }
                    schemaNameFromFile = sb.toString();
                } else {
                    schemaNameFromFile = directoryName; // Якщо назва директорії не містить "_"
                }

            } else {
                schemaNameFromFile = directoryName; // Якщо назва директорії не містить "_"
            }
            System.out.println("Увага: не вдалося розпарсити час з назви директорії '" + directoryName + "'. Назва схеми встановлена як '" + schemaNameFromFile + "', час встановлено як поточний.");
        }
        if (schemaNameFromFile == null || schemaNameFromFile.trim().isEmpty()){
            schemaNameFromFile = "UNKNOWN_SCHEMA_FROM_" + directoryName; // Запасний варіант, якщо назва схеми не визначена
            System.out.println("Увага: назва схеми не була чітко визначена з '" + directoryName + "'. Встановлено: " + schemaNameFromFile);
        }


        Map<String, String> objectDdls = new HashMap<>();
        System.out.println("Завантаження схеми '" + schemaNameFromFile + "' з директорії: " + schemaDirectoryPath);

        // Створюємо final змінну для використання в лямбді
        final String finalSchemaNameFromFile = schemaNameFromFile;

        // Проходимо по піддиректоріях (типах об'єктів)
        try (Stream<Path> objectTypeDirs = Files.list(schemaDirectoryPath)) {
            objectTypeDirs.filter(Files::isDirectory).forEach(objectTypeDir -> {
                String objectType = objectTypeDir.getFileName().toString();
                // Проходимо по файлах .sql всередині кожної директорії типу об'єкта
                try (Stream<Path> ddlFiles = Files.list(objectTypeDir)) {
                    ddlFiles.filter(file -> Files.isRegularFile(file) && file.toString().endsWith(".sql"))
                            .forEach(ddlFile -> {
                                String fileName = ddlFile.getFileName().toString();
                                // Видаляємо розширення .sql для отримання "чистого" імені об'єкта
                                String objectName = fileName.substring(0, fileName.length() - 4);
                                // Тут ми припускаємо, що "чисте" ім'я об'єкта не потребує додаткової очистки,
                                // оскільки воно вже було очищене при збереженні.

                                try {
                                    String ddlContent = Files.readString(ddlFile, StandardCharsets.UTF_8);
                                    // Ключ: ТИП/ВЛАСНИК/ІМ'Я
                                    // Використовуємо final змінну в лямбді
                                    String objectKey = objectType + "/" + finalSchemaNameFromFile + "/" + objectName;
                                    objectDdls.put(objectKey, ddlContent);
                                    System.out.println("Завантажено DDL для: " + objectKey);
                                } catch (IOException e) {
                                    System.err.println("Помилка читання файлу DDL " + ddlFile.toAbsolutePath() + ": " + e.getMessage());
                                    // Можна продовжити завантаження інших файлів
                                }
                            });
                } catch (IOException e) {
                    System.err.println("Помилка доступу до директорії типу об'єкта " + objectTypeDir.toAbsolutePath() + ": " + e.getMessage());
                }
            });
        }

        // Генеруємо унікальний ID для завантаженої схеми
        String schemaId = UUID.randomUUID().toString();
        // ConnectionDetails для схеми з файлів буде null
        return new Schema(schemaId, finalSchemaNameFromFile, objectDdls, extractionTimestampFromFile, null); // Використовуємо final змінну і тут
    }
}

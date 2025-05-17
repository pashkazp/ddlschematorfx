package com.depavlo.ddlschematorfx.service;

import com.depavlo.ddlschematorfx.model.ConnectionDetails; // Потрібен для meta.properties
import com.depavlo.ddlschematorfx.model.ObjectType;
import com.depavlo.ddlschematorfx.model.Schema;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.apache.commons.collections4.keyvalue.MultiKey;


import java.io.BufferedReader; // Для читання properties
import java.io.BufferedWriter;
import java.io.FileInputStream; // Для читання properties
import java.io.FileOutputStream; // Для запису properties
import java.io.IOException;
import java.io.InputStreamReader; // Для читання properties
import java.io.OutputStreamWriter; // Для запису properties
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties; // Для роботи з .properties файлом
import java.util.UUID;
import java.util.stream.Stream;

public class SchemaService {

    private final Map<String, Schema> loadedSchemas = new HashMap<>(); // Ключ - це Schema.id (UUID)
    // DateTimeFormatter для TIMESTAMP_FORMATTER не використовується для назв директорій, якщо є meta.properties
    // private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String META_PROPERTIES_FILE = "meta.properties";
    private static final String KEY_SCHEMA_NAME = "schemaName";
    private static final String KEY_EXTRACTION_TIMESTAMP = "extractionTimestamp";
    private static final String KEY_ORIGINAL_CONN_NAME = "originalConnectionName"; // Може бути корисним
    private static final String KEY_CURRENT_SOURCE_ID = "currentSourceIdentifier"; // Зберігаємо currentSourceIdentifier
    private static final String KEY_ORIGINAL_SOURCE_ID = "originalSourceIdentifier";


    /**
     * Додає або оновлює схему у сховищі.
     * Якщо схема з таким самим currentSourceIdentifier вже існує, вона замінюється.
     * @param newSchema Нова схема для додавання або оновлення.
     */
    public void addSchema(Schema newSchema) {
        if (newSchema == null || newSchema.getId() == null) {
            System.err.println("Помилка: Спроба додати null схему або схему без ID.");
            return;
        }

        String currentSourceId = newSchema.getCurrentSourceIdentifier();

        if (currentSourceId != null && !currentSourceId.trim().isEmpty()) {
            // Шукаємо існуючу схему з таким самим currentSourceIdentifier
            Optional<Schema> existingSchemaOpt = loadedSchemas.values().stream()
                    .filter(s -> currentSourceId.equals(s.getCurrentSourceIdentifier()))
                    .findFirst();

            if (existingSchemaOpt.isPresent()) {
                Schema oldSchema = existingSchemaOpt.get();
                loadedSchemas.remove(oldSchema.getId()); // Видаляємо стару схему за її унікальним ID екземпляра
                System.out.println("Оновлено схему для джерела: " + currentSourceId + ". Старий ID: " + oldSchema.getId() + ", Новий ID: " + newSchema.getId());
            }
        } else {
            System.out.println("Увага: Схема '" + newSchema.getName() + "' (ID: " + newSchema.getId() + ") додається без currentSourceIdentifier. Неможливо відстежити дублікати за джерелом.");
        }

        loadedSchemas.put(newSchema.getId(), newSchema); // Додаємо нову (або оновлену) схему
        System.out.println("Схему '" + newSchema.getName() + "' (ID: " + newSchema.getId() + ", CurrentSourceID: " + (currentSourceId != null ? currentSourceId : "N/A") + ") додано/оновлено у сховищі.");
    }


    public Schema getSchema(String schemaId) {
        return loadedSchemas.get(schemaId);
    }

    public Schema removeSchema(String schemaId) {
        return loadedSchemas.remove(schemaId);
    }

    public List<Schema> getAllSchemas() {
        return new ArrayList<>(loadedSchemas.values());
    }

    public void clearSchemas() {
        loadedSchemas.clear();
        System.out.println("Сховище схем очищено.");
    }

    /**
     * Зберігає схему у файлову систему, включаючи meta.properties.
     * @param schema Об'єкт Schema для збереження.
     * @param baseDirectoryPath Шлях до базової директорії, де буде створено директорію схеми.
     * @param schemaDirectoryName Назва директорії для схеми (може бути запропонована або введена користувачем).
     * @throws IOException Якщо виникає помилка при роботі з файловою системою.
     */
    public void saveSchemaToFile(Schema schema, Path baseDirectoryPath, String schemaDirectoryName) throws IOException {
        if (schema == null || baseDirectoryPath == null || schemaDirectoryName == null || schemaDirectoryName.trim().isEmpty()) {
            throw new IllegalArgumentException("Schema, base directory path, and schema directory name must be provided and not empty.");
        }

        Path schemaDirectory = baseDirectoryPath.resolve(schemaDirectoryName);
        Files.createDirectories(schemaDirectory);
        System.out.println("Створено/використано директорію для схеми: " + schemaDirectory.toAbsolutePath());

        // Збереження meta.properties
        Properties metaProps = new Properties();
        metaProps.setProperty(KEY_SCHEMA_NAME, schema.getName());
        metaProps.setProperty(KEY_EXTRACTION_TIMESTAMP, schema.getExtractionTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        if (schema.getSourceConnection() != null && schema.getSourceConnection().getName() != null) {
            metaProps.setProperty(KEY_ORIGINAL_CONN_NAME, schema.getSourceConnection().getName());
        } else {
            metaProps.setProperty(KEY_ORIGINAL_CONN_NAME, "N/A_OR_FROM_FILE");
        }
        // Зберігаємо currentSourceIdentifier та originalSourceIdentifier
        if (schema.getCurrentSourceIdentifier() != null) {
            metaProps.setProperty(KEY_CURRENT_SOURCE_ID, schema.getCurrentSourceIdentifier());
        }
        if (schema.getOriginalSourceIdentifier() != null) {
            metaProps.setProperty(KEY_ORIGINAL_SOURCE_ID, schema.getOriginalSourceIdentifier());
        }


        Path metaFilePath = schemaDirectory.resolve(META_PROPERTIES_FILE);
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(metaFilePath.toFile()), StandardCharsets.UTF_8)) {
            metaProps.store(writer, "Schema Metadata");
            System.out.println("Збережено " + META_PROPERTIES_FILE + " у " + metaFilePath.toAbsolutePath());
        }

        // Збереження DDL об'єктів
        if (schema.getObjectDdls() != null) {
            for (Map.Entry<MultiKey<?>, String> entry : schema.getObjectDdls().entrySet()) {
                MultiKey<?> multiKey = entry.getKey();
                String ddl = entry.getValue();

                if (ddl == null || ddl.trim().isEmpty()) continue;

                if (multiKey.size() == 2 && multiKey.getKey(0) instanceof ObjectType && multiKey.getKey(1) instanceof String) {
                    ObjectType objectType = (ObjectType) multiKey.getKey(0);
                    String objectName = (String) multiKey.getKey(1);

                    Path objectTypeDirectory = schemaDirectory.resolve(objectType.name());
                    Files.createDirectories(objectTypeDirectory);
                    String cleanObjectName = objectName.replaceAll("[^a-zA-Z0-9_.-]", "_");
                    Path ddlFilePath = objectTypeDirectory.resolve(cleanObjectName + ".sql");

                    try (BufferedWriter writer = Files.newBufferedWriter(ddlFilePath, StandardCharsets.UTF_8)) {
                        writer.write(ddl);
                    } catch (IOException e) {
                        System.err.println("Помилка при збереженні DDL для " + objectType.name() + "/" + objectName + ": " + e.getMessage());
                    }
                } else {
                    System.err.println("Некоректний формат MultiKey при збереженні: " + multiKey);
                }
            }
        }
        System.out.println("Збереження схеми '" + schema.getName() + "' у директорію '" + schemaDirectoryName + "' завершено.");
    }

    /**
     * Завантажує схему з директорії, читаючи meta.properties.
     * @param schemaDirectoryPath Шлях до директорії схеми.
     * @return Об'єкт Schema.
     * @throws IOException Якщо помилка читання або meta.properties відсутній/некоректний.
     */
    public Schema loadSchemaFromDirectory(Path schemaDirectoryPath) throws IOException {
        if (schemaDirectoryPath == null || !Files.isDirectory(schemaDirectoryPath)) {
            throw new IllegalArgumentException("Необхідно вказати дійсну директорію схеми.");
        }

        Path metaFilePath = schemaDirectoryPath.resolve(META_PROPERTIES_FILE);
        if (!Files.exists(metaFilePath)) {
            throw new IOException(META_PROPERTIES_FILE + " не знайдено у директорії: " + schemaDirectoryPath);
        }

        Properties metaProps = new Properties();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(metaFilePath.toFile()), StandardCharsets.UTF_8)) {
            metaProps.load(reader);
        }

        String schemaNameFromFile = metaProps.getProperty(KEY_SCHEMA_NAME);
        String timestampString = metaProps.getProperty(KEY_EXTRACTION_TIMESTAMP);
        // String originalConnectionName = metaProps.getProperty(KEY_ORIGINAL_CONN_NAME); // Можемо використовувати пізніше
        String originalSourceIdFromFile = metaProps.getProperty(KEY_ORIGINAL_SOURCE_ID); // Читаємо originalSourceIdentifier
        // currentSourceIdentifier з meta.properties не використовується для ідентифікації цього завантаження,
        // оскільки currentSourceIdentifier для завантаженої з директорії схеми - це завжди "DIR::шлях"

        if (schemaNameFromFile == null || schemaNameFromFile.trim().isEmpty()) {
            throw new IOException("Властивість '" + KEY_SCHEMA_NAME + "' не знайдена або порожня у " + META_PROPERTIES_FILE);
        }
        if (timestampString == null || timestampString.trim().isEmpty()) {
            throw new IOException("Властивість '" + KEY_EXTRACTION_TIMESTAMP + "' не знайдена або порожня у " + META_PROPERTIES_FILE);
        }

        LocalDateTime extractionTimestampFromFile;
        try {
            extractionTimestampFromFile = LocalDateTime.parse(timestampString, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new IOException("Некоректний формат часу у " + META_PROPERTIES_FILE + " для властивості '" + KEY_EXTRACTION_TIMESTAMP + "'. Очікується ISO_LOCAL_DATE_TIME.", e);
        }

        MultiKeyMap<Object, String> objectDdls = new MultiKeyMap<>();
        System.out.println("Завантаження схеми '" + schemaNameFromFile + "' з директорії: " + schemaDirectoryPath);

        try (Stream<Path> objectTypeDirs = Files.list(schemaDirectoryPath)) {
            objectTypeDirs.filter(Files::isDirectory).forEach(objectTypeDir -> {
                String objectTypeString = objectTypeDir.getFileName().toString();
                ObjectType currentObjectType;
                try {
                    // Спроба перетворити назву директорії на ObjectType
                    // Якщо директорія не є типом об'єкта (наприклад, .git), вона буде проігнорована
                    currentObjectType = ObjectType.valueOf(objectTypeString.toUpperCase());
                } catch (IllegalArgumentException e) {
                    // System.out.println("Директорія '" + objectTypeString + "' не є відомим типом об'єкта, пропускається.");
                    return; // Пропускаємо цю директорію
                }

                try (Stream<Path> ddlFiles = Files.list(objectTypeDir)) {
                    ddlFiles.filter(file -> Files.isRegularFile(file) && file.toString().endsWith(".sql"))
                            .forEach(ddlFile -> {
                                String fileName = ddlFile.getFileName().toString();
                                String objectName = fileName.substring(0, fileName.length() - 4);
                                try {
                                    String ddlContent = Files.readString(ddlFile, StandardCharsets.UTF_8);
                                    objectDdls.put(currentObjectType, objectName, ddlContent);
                                } catch (IOException ex) {
                                    System.err.println("Помилка читання файлу DDL " + ddlFile.toAbsolutePath() + ": " + ex.getMessage());
                                }
                            });
                } catch (IOException ex) {
                    System.err.println("Помилка доступу до директорії типу об'єкта " + objectTypeDir.toAbsolutePath() + ": " + ex.getMessage());
                }
            });
        }

        String schemaId = UUID.randomUUID().toString(); // Новий унікальний ID для цього екземпляра
        String currentSourceIdentifierForThisLoad = "DIR::" + schemaDirectoryPath.toAbsolutePath().toString();

        // Створюємо об'єкт Schema, передаючи originalSourceIdFromFile
        // sourceConnection буде null, оскільки завантажено з файлів
        return new Schema(schemaId, schemaNameFromFile, objectDdls, extractionTimestampFromFile, null, currentSourceIdentifierForThisLoad, originalSourceIdFromFile);
    }
}

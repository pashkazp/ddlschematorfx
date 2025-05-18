package com.depavlo.ddlschematorfx.service;

import com.depavlo.ddlschematorfx.model.ConnectionDetails;
import com.depavlo.ddlschematorfx.model.ObjectType;
import com.depavlo.ddlschematorfx.model.Schema;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.apache.commons.collections4.keyvalue.MultiKey;

import java.io.BufferedWriter;
import java.io.File; // Для роботи з файлами при очищенні директорії
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream; // Для ітерації по вмісту директорії
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator; // Для сортування файлів перед видаленням (зворотний порядок)
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;

public class SchemaService {

    private final Map<String, Schema> loadedSchemas = new HashMap<>();
    private static final String META_PROPERTIES_FILE = "meta.properties";
    private static final String KEY_SCHEMA_NAME = "schemaName";
    private static final String KEY_EXTRACTION_TIMESTAMP = "extractionTimestamp";
    private static final String KEY_ORIGINAL_CONN_NAME = "originalConnectionName";
    private static final String KEY_CURRENT_SOURCE_ID = "currentSourceIdentifier";
    private static final String KEY_ORIGINAL_SOURCE_ID = "originalSourceIdentifier";


    public void addSchema(Schema newSchema) {
        if (newSchema == null || newSchema.getId() == null) {
            System.err.println("Помилка: Спроба додати null схему або схему без ID.");
            return;
        }

        String currentSourceId = newSchema.getCurrentSourceIdentifier();

        if (currentSourceId != null && !currentSourceId.trim().isEmpty()) {
            Optional<Schema> existingSchemaOpt = loadedSchemas.values().stream()
                    .filter(s -> currentSourceId.equals(s.getCurrentSourceIdentifier()))
                    .findFirst();

            if (existingSchemaOpt.isPresent()) {
                Schema oldSchema = existingSchemaOpt.get();
                loadedSchemas.remove(oldSchema.getId());
                System.out.println("Оновлено схему для джерела: " + currentSourceId + ". Старий ID: " + oldSchema.getId() + ", Новий ID: " + newSchema.getId());
            }
        } else {
            System.out.println("Увага: Схема '" + newSchema.getName() + "' (ID: " + newSchema.getId() + ") додається без currentSourceIdentifier. Неможливо відстежити дублікати за джерелом.");
        }

        loadedSchemas.put(newSchema.getId(), newSchema);
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
     * Рекурсивно видаляє весь вміст директорії (файли та піддиректорії).
     * Сама директорія не видаляється.
     * @param directoryPath Шлях до директорії для очищення.
     * @throws IOException Якщо виникає помилка при видаленні.
     */
    public void clearDirectory(Path directoryPath) throws IOException {
        if (Files.exists(directoryPath) && Files.isDirectory(directoryPath)) {
            System.out.println("Очищення директорії: " + directoryPath);
            // Використовуємо Files.walk для рекурсивного обходу, сортуємо у зворотному порядку, щоб спочатку видалялися файли, потім директорії
            try (Stream<Path> walk = Files.walk(directoryPath)) {
                walk.sorted(Comparator.reverseOrder())
                        .filter(path -> !path.equals(directoryPath)) // Не видаляємо саму кореневу директорію
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                                System.out.println("Видалено: " + path);
                            } catch (IOException e) {
                                System.err.println("Не вдалося видалити " + path + ": " + e.getMessage());
                                // Можна кинути RuntimeException, якщо критично, або продовжити
                            }
                        });
            }
            System.out.println("Директорію " + directoryPath + " очищено (або спроба очищення завершена).");
        } else if (Files.exists(directoryPath) && !Files.isDirectory(directoryPath)) {
            throw new IOException("Вказаний шлях не є директорією: " + directoryPath);
        }
        // Якщо директорія не існує, нічого не робимо
    }


    public void saveSchemaToFile(Schema schema, Path baseDirectoryPath, String schemaDirectoryName) throws IOException {
        if (schema == null || baseDirectoryPath == null || schemaDirectoryName == null || schemaDirectoryName.trim().isEmpty()) {
            throw new IllegalArgumentException("Schema, base directory path, and schema directory name must be provided and not empty.");
        }

        Path schemaDirectory = baseDirectoryPath.resolve(schemaDirectoryName);
        // Директорія тепер створюється (або перевіряється на існування) перед викликом цього методу,
        // і очищується також перед викликом, якщо це операція "Зберегти" на існуючий шлях.
        // Тут ми просто гарантуємо, що вона існує.
        Files.createDirectories(schemaDirectory);
        System.out.println("Використання/створення директорії для схеми: " + schemaDirectory.toAbsolutePath());

        Properties metaProps = new Properties();
        metaProps.setProperty(KEY_SCHEMA_NAME, schema.getName());
        metaProps.setProperty(KEY_EXTRACTION_TIMESTAMP, schema.getExtractionTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        if (schema.getSourceConnection() != null && schema.getSourceConnection().getName() != null) {
            metaProps.setProperty(KEY_ORIGINAL_CONN_NAME, schema.getSourceConnection().getName());
        } else {
            metaProps.setProperty(KEY_ORIGINAL_CONN_NAME, "N/A_OR_FROM_FILE");
        }
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
        String originalSourceIdFromFile = metaProps.getProperty(KEY_ORIGINAL_SOURCE_ID);

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
                    currentObjectType = ObjectType.valueOf(objectTypeString.toUpperCase());
                } catch (IllegalArgumentException e) {
                    return;
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

        String schemaId = UUID.randomUUID().toString();
        String currentSourceIdentifierForThisLoad = "DIR::" + schemaDirectoryPath.toAbsolutePath().toString();
        // При завантаженні з директорії, sourceConnection встановлюємо в null
        return new Schema(schemaId, schemaNameFromFile, objectDdls, extractionTimestampFromFile, null, currentSourceIdentifierForThisLoad, originalSourceIdFromFile);
    }
}

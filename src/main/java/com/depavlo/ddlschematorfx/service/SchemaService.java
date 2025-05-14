package com.depavlo.ddlschematorfx.service;

import com.depavlo.ddlschematorfx.model.ConnectionDetails;
import com.depavlo.ddlschematorfx.model.ObjectType;
import com.depavlo.ddlschematorfx.model.Schema;
import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.MultiKeyMap;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap; // ВИПРАВЛЕНО: Використовуємо HashMap тут
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public class SchemaService {

    // ВИПРАВЛЕНО: loadedSchemas має бути звичайним HashMap<String, Schema>
    private final Map<String, Schema> loadedSchemas = new HashMap<>();
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public void addSchema(Schema schema) {
        if (schema != null && schema.getId() != null) {
            loadedSchemas.put(schema.getId(), schema);
            System.out.println("Схему '" + schema.getName() + "' з ID '" + schema.getId() + "' додано до сховища.");
        }
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

    public void saveSchemaToFile(Schema schema, String baseDirectoryPath) throws IOException {
        if (schema == null || baseDirectoryPath == null || baseDirectoryPath.trim().isEmpty()) {
            throw new IllegalArgumentException("Schema and base directory path must be provided.");
        }
        String connectionName = "UNKNOWN_CONNECTION";
        if (schema.getSourceConnection() != null && schema.getSourceConnection().getName() != null && !schema.getSourceConnection().getName().trim().isEmpty()) {
            connectionName = schema.getSourceConnection().getName();
        } else {
            connectionName = "FILE_LOADED";
            System.out.println("Увага: ConnectionDetails для схеми '" + schema.getName() + "' не знайдено. Використовується префікс '" + connectionName + "' для назви директорії.");
        }

        String cleanConnectionName = connectionName.replaceAll("[^a-zA-Z0-9_.-]", "_");
        String cleanSchemaName = schema.getName().replaceAll("[^a-zA-Z0-9_.-]", "_");
        String schemaDirectoryName = cleanConnectionName + "_" + cleanSchemaName + "_" + schema.getExtractionTimestamp().format(TIMESTAMP_FORMATTER);

        Path schemaDirectory = Paths.get(baseDirectoryPath, schemaDirectoryName);
        Files.createDirectories(schemaDirectory);
        System.out.println("Створено директорію для схеми: " + schemaDirectory.toAbsolutePath());

        if (schema.getObjectDdls() != null) {
            for (Map.Entry<MultiKey<?>, String> entry : schema.getObjectDdls().entrySet()) {
                MultiKey<?> multiKey = entry.getKey();
                String ddl = entry.getValue();

                if (ddl == null || ddl.trim().isEmpty()) {
                    System.out.println("Пропущено збереження порожнього DDL для ключа: " + multiKey);
                    continue;
                }

                if (multiKey.size() == 2 && multiKey.getKey(0) instanceof ObjectType && multiKey.getKey(1) instanceof String) {
                    ObjectType objectType = (ObjectType) multiKey.getKey(0);
                    String objectName = (String) multiKey.getKey(1);

                    Path objectTypeDirectory = schemaDirectory.resolve(objectType.name());
                    Files.createDirectories(objectTypeDirectory);

                    String cleanObjectName = objectName.replaceAll("[^a-zA-Z0-9_.-]", "_");
                    Path ddlFilePath = objectTypeDirectory.resolve(cleanObjectName + ".sql");

                    try (BufferedWriter writer = Files.newBufferedWriter(ddlFilePath, StandardCharsets.UTF_8)) {
                        writer.write(ddl);
                        System.out.println("Збережено DDL для " + objectType.name() + "/" + objectName + " у файл: " + ddlFilePath.toAbsolutePath());
                    } catch (IOException e) {
                        System.err.println("Помилка при збереженні DDL для " + objectType.name() + "/" + objectName + " у файл " + ddlFilePath.toAbsolutePath() + ": " + e.getMessage());
                    }
                } else {
                    System.err.println("Некоректний формат MultiKey: " + multiKey + ". Пропущено.");
                }
            }
        }
        System.out.println("Збереження схеми '" + schema.getName() + "' завершено.");
    }

    public Schema loadSchemaFromDirectory(Path schemaDirectoryPath) throws IOException {
        if (schemaDirectoryPath == null || !Files.isDirectory(schemaDirectoryPath)) {
            throw new IllegalArgumentException("Необхідно вказати дійсну директорію схеми.");
        }

        String directoryName = schemaDirectoryPath.getFileName().toString();
        String schemaNameFromFile;
        LocalDateTime extractionTimestampFromFile;

        String[] dirNameParts = directoryName.split("_");
        if (dirNameParts.length < 2) {
            throw new IOException("Некоректний формат назви директорії схеми: " + directoryName + ". Очікується формат 'connectionName_schemaName_timestamp' або 'schemaName_timestamp'.");
        }

        try {
            extractionTimestampFromFile = LocalDateTime.parse(dirNameParts[dirNameParts.length - 1], TIMESTAMP_FORMATTER);
            if (dirNameParts.length > 2) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < dirNameParts.length - 1; i++) {
                    sb.append(dirNameParts[i]);
                    if (i < dirNameParts.length - 2) sb.append("_");
                }
                schemaNameFromFile = sb.toString();
            } else {
                schemaNameFromFile = dirNameParts[0];
            }
        } catch (DateTimeParseException e) {
            extractionTimestampFromFile = LocalDateTime.now();
            if(dirNameParts.length > 1){
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < dirNameParts.length; i++) {
                    sb.append(dirNameParts[i]);
                    if (i < dirNameParts.length - 1) {
                        sb.append("_");
                    }
                }
                schemaNameFromFile = sb.toString();
                if(schemaNameFromFile.endsWith("_")) schemaNameFromFile = schemaNameFromFile.substring(0, schemaNameFromFile.length()-1);

            } else {
                schemaNameFromFile = directoryName;
            }
            System.out.println("Увага: не вдалося розпарсити час з назви директорії '" + directoryName + "'. Назва схеми встановлена як '" + schemaNameFromFile + "', час встановлено як поточний.");
        }

        if (schemaNameFromFile == null || schemaNameFromFile.trim().isEmpty()) {
            schemaNameFromFile = "UNKNOWN_SCHEMA_FROM_" + directoryName;
            System.out.println("Увага: назва схеми не була чітко визначена з '" + directoryName + "'. Встановлено: " + schemaNameFromFile);
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
                    System.err.println("Невідомий тип об'єкта в назві директорії: " + objectTypeString + ". Пропускається.");
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
                                    System.out.println("Завантажено DDL для: " + currentObjectType.name() + "/" + objectName);
                                } catch (IOException e) {
                                    System.err.println("Помилка читання файлу DDL " + ddlFile.toAbsolutePath() + ": " + e.getMessage());
                                }
                            });
                } catch (IOException e) {
                    System.err.println("Помилка доступу до директорії типу об'єкта " + objectTypeDir.toAbsolutePath() + ": " + e.getMessage());
                }
            });
        }

        String schemaId = UUID.randomUUID().toString();
        return new Schema(schemaId, schemaNameFromFile, objectDdls, extractionTimestampFromFile, null);
    }
}

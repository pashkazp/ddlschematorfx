package com.depavlo.ddlschematorfx.model;

import org.apache.commons.collections4.map.MultiKeyMap;

import java.nio.file.Path; // Імпорт для Path
import java.time.LocalDateTime;
import java.util.Objects;

public class Schema {
    private String id;
    private String name;
    private MultiKeyMap<Object, String> objectDdls;
    private LocalDateTime extractionTimestamp;
    private ConnectionDetails sourceConnection;
    private String currentSourceIdentifier;
    private String originalSourceIdentifier;
    private Path lastSavedPath; // Шлях до директорії, куди схему було востаннє збережено

    // Конструктор для витягнення з БД
    public Schema(String id, String name, MultiKeyMap<Object, String> objectDdls,
                  LocalDateTime extractionTimestamp, ConnectionDetails sourceConnection,
                  String currentSourceIdentifier) {
        this(id, name, objectDdls, extractionTimestamp, sourceConnection, currentSourceIdentifier, currentSourceIdentifier, null); // lastSavedPath = null
    }

    // Конструктор для завантаження з файлів (може мати originalSourceIdentifier)
    public Schema(String id, String name, MultiKeyMap<Object, String> objectDdls,
                  LocalDateTime extractionTimestamp, ConnectionDetails sourceConnection, // Зазвичай null для файлів
                  String currentSourceIdentifier, String originalSourceIdentifier) {
        this(id, name, objectDdls, extractionTimestamp, sourceConnection, currentSourceIdentifier, originalSourceIdentifier, null); // lastSavedPath = null спочатку
    }


    // Повний конструктор
    public Schema(String id, String name, MultiKeyMap<Object, String> objectDdls,
                  LocalDateTime extractionTimestamp, ConnectionDetails sourceConnection,
                  String currentSourceIdentifier, String originalSourceIdentifier, Path lastSavedPath) {
        this.id = id;
        this.name = name;
        this.objectDdls = (objectDdls != null) ? objectDdls : new MultiKeyMap<>();
        this.extractionTimestamp = extractionTimestamp;
        this.sourceConnection = sourceConnection;
        this.currentSourceIdentifier = currentSourceIdentifier;
        this.originalSourceIdentifier = (originalSourceIdentifier != null) ? originalSourceIdentifier : currentSourceIdentifier;
        this.lastSavedPath = lastSavedPath;
    }

    // Гетери
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public MultiKeyMap<Object, String> getObjectDdls() {
        return objectDdls;
    }

    public LocalDateTime getExtractionTimestamp() {
        return extractionTimestamp;
    }

    public ConnectionDetails getSourceConnection() {
        return sourceConnection;
    }

    public String getCurrentSourceIdentifier() {
        return currentSourceIdentifier;
    }

    public String getOriginalSourceIdentifier() {
        return originalSourceIdentifier;
    }

    public Path getLastSavedPath() { // Гетер для lastSavedPath
        return lastSavedPath;
    }

    // Сеттери
    public void setOriginalSourceIdentifier(String originalSourceIdentifier) {
        this.originalSourceIdentifier = originalSourceIdentifier;
    }

    public void setLastSavedPath(Path lastSavedPath) { // Сеттер для lastSavedPath
        this.lastSavedPath = lastSavedPath;
    }


    public void addObjectDdl(ObjectType objectType, String objectName, String ddl) {
        if (this.objectDdls == null) {
            this.objectDdls = new MultiKeyMap<>();
        }
        this.objectDdls.put(objectType, objectName, ddl);
    }

    @Override
    public String toString() {
        return "Schema{" +
                "id='" + id.substring(0, Math.min(id.length(), 8)) + "...'" +
                ", name='" + name + '\'' +
                ", currentSourceId='" + (currentSourceIdentifier != null ? currentSourceIdentifier : "N/A") + '\'' +
                (originalSourceIdentifier != null && !originalSourceIdentifier.equals(currentSourceIdentifier) ? ", originalSourceId='" + originalSourceIdentifier + '\'' : "") +
                (lastSavedPath != null ? ", lastSavedPath='" + lastSavedPath.toString() + '\'' : "") + // Додано lastSavedPath
                ", objectCount=" + (objectDdls != null ? objectDdls.size() : 0) +
                ", extractionTimestamp=" + extractionTimestamp +
                ", sourceConnectionName='" + (sourceConnection != null ? sourceConnection.getName() : "N/A") + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Schema schema = (Schema) o;
        return Objects.equals(id, schema.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

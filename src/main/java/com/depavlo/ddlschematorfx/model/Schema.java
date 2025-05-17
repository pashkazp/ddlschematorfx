package com.depavlo.ddlschematorfx.model;

import org.apache.commons.collections4.map.MultiKeyMap;

import java.time.LocalDateTime;
import java.util.Objects;

public class Schema {
    private String id; // Унікальний UUID ідентифікатор екземпляра схеми в пам'яті
    private String name; // Назва схеми (власник)
    private MultiKeyMap<Object, String> objectDdls;
    private LocalDateTime extractionTimestamp; // Час витягнення/завантаження схеми
    private ConnectionDetails sourceConnection; // Деталі підключення-джерела (null для схем з файлів)
    private String currentSourceIdentifier; // Унікальний ідентифікатор поточного джерела (DB::connId::schemaName або DIR::path)
    private String originalSourceIdentifier; // Ідентифікатор первинного джерела, якщо завантажено з meta.properties

    // Конструктор для витягнення з БД
    public Schema(String id, String name, MultiKeyMap<Object, String> objectDdls,
                  LocalDateTime extractionTimestamp, ConnectionDetails sourceConnection,
                  String currentSourceIdentifier) {
        this(id, name, objectDdls, extractionTimestamp, sourceConnection, currentSourceIdentifier, currentSourceIdentifier); // originalSourceIdentifier = currentSourceIdentifier
    }

    // Повний конструктор
    public Schema(String id, String name, MultiKeyMap<Object, String> objectDdls,
                  LocalDateTime extractionTimestamp, ConnectionDetails sourceConnection,
                  String currentSourceIdentifier, String originalSourceIdentifier) {
        this.id = id;
        this.name = name;
        this.objectDdls = (objectDdls != null) ? objectDdls : new MultiKeyMap<>();
        this.extractionTimestamp = extractionTimestamp;
        this.sourceConnection = sourceConnection;
        this.currentSourceIdentifier = currentSourceIdentifier;
        this.originalSourceIdentifier = (originalSourceIdentifier != null) ? originalSourceIdentifier : currentSourceIdentifier;
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

    // Сеттер для originalSourceIdentifier, може знадобитися при завантаженні з meta.properties
    public void setOriginalSourceIdentifier(String originalSourceIdentifier) {
        this.originalSourceIdentifier = originalSourceIdentifier;
    }


    // Метод для додавання DDL об'єкта
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

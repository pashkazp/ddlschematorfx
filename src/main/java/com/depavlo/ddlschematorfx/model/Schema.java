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
    private String sourceIdentifier; // Унікальний ідентифікатор джерела схеми (наприклад, connectionId::schemaName або filePath)

    // Конструктор
    public Schema(String id, String name, MultiKeyMap<Object, String> objectDdls,
                  LocalDateTime extractionTimestamp, ConnectionDetails sourceConnection,
                  String sourceIdentifier) { // Додано sourceIdentifier
        this.id = id;
        this.name = name;
        this.objectDdls = (objectDdls != null) ? objectDdls : new MultiKeyMap<>();
        this.extractionTimestamp = extractionTimestamp;
        this.sourceConnection = sourceConnection;
        this.sourceIdentifier = sourceIdentifier; // Ініціалізація нового поля
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

    public String getSourceIdentifier() { // Гетер для sourceIdentifier
        return sourceIdentifier;
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
                "id='" + id.substring(0, Math.min(id.length(), 8)) + "...'" + // Показуємо лише частину ID
                ", name='" + name + '\'' +
                ", sourceIdentifier='" + (sourceIdentifier != null ? sourceIdentifier : "N/A") + '\'' +
                ", objectCount=" + (objectDdls != null ? objectDdls.size() : 0) +
                ", extractionTimestamp=" + extractionTimestamp +
                ", sourceConnectionName='" + (sourceConnection != null ? sourceConnection.getName() : "N/A") + '\'' +
                '}';
    }

    // equals та hashCode тепер базуються на ID екземпляра, що є правильним.
    // sourceIdentifier не повинен впливати на equals/hashCode самого об'єкта Schema,
    // він використовується для логіки заміни в SchemaService.
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

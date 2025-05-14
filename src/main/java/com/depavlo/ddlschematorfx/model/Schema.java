package com.depavlo.ddlschematorfx.model;

import org.apache.commons.collections4.map.MultiKeyMap; // Імпорт MultiKeyMap

import java.time.LocalDateTime;
import java.util.Map; // Залишаємо для сумісності, якщо десь використовується як тип
import java.util.Objects;

// Клас для представлення структури схеми
public class Schema {
    private String id; // Унікальний ідентифікатор схеми
    private String name; // Назва схеми (власник)
    // MultiKeyMap, де ключі - це ObjectType та String (objectName), а значення - DDL
    private MultiKeyMap<Object, String> objectDdls;
    private LocalDateTime extractionTimestamp; // Час витягнення схеми
    private ConnectionDetails sourceConnection; // Деталі підключення-джерела

    // Конструктор
    public Schema(String id, String name, MultiKeyMap<Object, String> objectDdls, LocalDateTime extractionTimestamp, ConnectionDetails sourceConnection) {
        this.id = id;
        this.name = name;
        // Ініціалізуємо objectDdls, навіть якщо передано null, щоб уникнути NPE
        this.objectDdls = (objectDdls != null) ? objectDdls : new MultiKeyMap<>();
        this.extractionTimestamp = extractionTimestamp;
        this.sourceConnection = sourceConnection;
    }

    // Гетери
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /**
     * Повертає MultiKeyMap, що містить DDL об'єктів.
     * Ключ складається з ObjectType та імені об'єкта (String).
     * @return MultiKeyMap з DDL об'єктів.
     */
    public MultiKeyMap<Object, String> getObjectDdls() {
        return objectDdls;
    }

    public LocalDateTime getExtractionTimestamp() {
        return extractionTimestamp;
    }

    public ConnectionDetails getSourceConnection() {
        return sourceConnection;
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
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
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

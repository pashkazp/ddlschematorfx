package com.depavlo.ddlschematorfx.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

// Клас для представлення структури схеми
public class Schema {
    private String id; // Унікальний ідентифікатор схеми (наприклад, згенерований UUID або комбінація назви та часу)
    private String name; // Назва схеми (власник)
    // Мапа, де ключ - унікальний ідентифікатор об'єкта (наприклад, ТИП/ВЛАСНИК/ІМ'Я), а значення - його DDL
    private Map<String, String> objectDdls;
    private LocalDateTime extractionTimestamp; // Час витягнення схеми

    // Конструктор
    public Schema(String id, String name, Map<String, String> objectDdls, LocalDateTime extractionTimestamp) {
        this.id = id;
        this.name = name;
        this.objectDdls = objectDdls;
        this.extractionTimestamp = extractionTimestamp;
    }

    // Гетери
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getObjectDdls() {
        return objectDdls;
    }

    public LocalDateTime getExtractionTimestamp() {
        return extractionTimestamp;
    }

    // Сетери (якщо потрібні, але для незмінних об'єктів краще їх уникати)
    // public void setId(String id) { this.id = id; }
    // public void setName(String name) { this.name = name; }
    // public void setObjectDdls(Map<String, String> objectDdls) { this.objectDdls = objectDdls; }
    // public void setExtractionTimestamp(LocalDateTime extractionTimestamp) { this.extractionTimestamp = extractionTimestamp; }


    @Override
    public String toString() {
        return "Schema{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", objectCount=" + (objectDdls != null ? objectDdls.size() : 0) +
               ", extractionTimestamp=" + extractionTimestamp +
               '}';
    }

    // Методи equals та hashCode для порівняння об'єктів Schema за їх ID
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
package com.depavlo.ddlschematorfx.model;

import java.util.Objects;

// Клас для представлення знайденої відмінності між схемами
public class Difference {
    private DifferenceType type; // Тип зміни (ADDED, REMOVED, MODIFIED)
    private ObjectType objectType; // Тип об'єкта (TABLE, PROCEDURE тощо)
    private String objectName; // Ім'я об'єкта
    private String objectOwner; // Власник об'єкта
    private String sourceDdl; // DDL об'єкта в першій схемі (для порівняння або видалення)
    private String targetDdl; // DDL об'єкта в другій схемі (для порівняння або створення)
    private String diffDetails; // Деталі відмінностей (наприклад, результат текстового diff)

    // Конструктор
    public Difference(DifferenceType type, ObjectType objectType, String objectName, String objectOwner, String sourceDdl, String targetDdl, String diffDetails) {
        this.type = type;
        this.objectType = objectType;
        this.objectName = objectName;
        this.objectOwner = objectOwner;
        this.sourceDdl = sourceDdl;
        this.targetDdl = targetDdl;
        this.diffDetails = diffDetails;
    }

    // Гетери
    public DifferenceType getType() {
        return type;
    }

    public ObjectType getObjectType() {
        return objectType;
    }

    public String getObjectName() {
        return objectName;
    }

    public String getObjectOwner() {
        return objectOwner;
    }

    public String getSourceDdl() {
        return sourceDdl;
    }

    public String getTargetDdl() {
        return targetDdl;
    }

    public String getDiffDetails() {
        return diffDetails;
    }

    // Сетери (якщо потрібні)
    // public void setDiffDetails(String diffDetails) { this.diffDetails = diffDetails; }


    @Override
    public String toString() {
        return "Difference{" +
               "type=" + type +
               ", objectType=" + objectType +
               ", objectName='" + objectName + '\'' +
               ", objectOwner='" + objectOwner + '\'' +
               '}';
    }

    // Методи equals та hashCode для порівняння об'єктів Difference
    // Порівняння відмінностей може бути складним, залежно від того, що вважається унікальною відмінністю.
    // Ця реалізація порівнює за типом зміни, типом об'єкта, ім'ям та власником.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Difference that = (Difference) o;
        return type == that.type && objectType == that.objectType && Objects.equals(objectName, that.objectName) && Objects.equals(objectOwner, that.objectOwner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, objectType, objectName, objectOwner);
    }
}
package com.depavlo.ddlschematorfx.model;

import java.util.Objects;

// Клас для представлення міграційного скрипта
public class MigrationScript {
    private ObjectType objectType; // Тип об'єкта, до якого відноситься скрипт
    private String fileName; // Назва файлу скрипта (наприклад, TABLE_MY_TABLE_ALTER.sql)
    private String scriptContent; // Текст SQL скрипта
    private int executionOrder; // Порядок виконання скрипта (для врахування залежностей)

    // Конструктор
    public MigrationScript(ObjectType objectType, String fileName, String scriptContent, int executionOrder) {
        this.objectType = objectType;
        this.fileName = fileName;
        this.scriptContent = scriptContent;
        this.executionOrder = executionOrder;
    }

    // Гетери
    public ObjectType getObjectType() {
        return objectType;
    }

    public String getFileName() {
        return fileName;
    }

    public String getScriptContent() {
        return scriptContent;
    }

    public int getExecutionOrder() {
        return executionOrder;
    }

    // Сетери (якщо потрібні)
    // public void setExecutionOrder(int executionOrder) { this.executionOrder = executionOrder; }


    @Override
    public String toString() {
        return "MigrationScript{" +
               "objectType=" + objectType +
               ", fileName='" + fileName + '\'' +
               ", executionOrder=" + executionOrder +
               '}';
    }

     // Методи equals та hashCode для порівняння об'єктів MigrationScript за назвою файлу
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MigrationScript that = (MigrationScript) o;
        return Objects.equals(fileName, that.fileName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName);
    }
}
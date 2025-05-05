package com.depavlo.ddlschematorfx.model;

// Перерахування для типів об'єктів схеми Oracle
public enum ObjectType {
    TABLE,
    COLUMN, // Хоча колонки є частиною таблиць, їх відмінності можуть відстежуватися окремо
    INDEX,
    CONSTRAINT,
    TRIGGER,
    PROCEDURE,
    FUNCTION,
    PACKAGE,
    SEQUENCE,
    VIEW,
    MATERIALIZED_VIEW,
    SYNONYM,
    DATABASE_LINK,
    DIRECTORY_OBJECT,
    JOB,
    QUEUE,
    TYPE,
    JAVA_SOURCE,
    LIBRARY,
    SCHEDULER,
    XML_SCHEMA,
    EDITION_BASED_OBJECT, // Для специфічних об'єктів EBR
    INVISIBLE_COLUMN, // Для невидимих колонок Oracle 12c+
    HYBRID_PARTITIONED_TABLE, // Для гібридних партиціонованих таблиць Oracle 12c+
    AUTO_LIST_PARTITIONING, // Для авто-партиціонування за списком Oracle 12c+
    // Додайте інші типи об'єктів Oracle 21c за необхідності
    OTHER // Для всіх інших типів
}
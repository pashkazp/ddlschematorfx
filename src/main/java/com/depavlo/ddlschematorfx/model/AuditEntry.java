package com.depavlo.ddlschematorfx.model;

import java.time.LocalDateTime;
import java.util.Objects;

// Клас для представлення запису аудиту
public class AuditEntry {
    private String id; // Унікальний ідентифікатор запису аудиту
    private LocalDateTime timestamp; // Час події
    private String user; // Користувач або ідентифікатор сесії (якщо автентифікація не реалізована)
    private String action; // Виконана дія (EXTRACT_SCHEMA, COMPARE_SCHEMAS, APPLY_SCRIPTS тощо)
    private String details; // Деталі дії
    private String status; // Статус (SUCCESS, FAILURE)
    private String errorMessage; // Повідомлення про помилку (у разі невдачі)

    // Конструктор
    public AuditEntry(String id, LocalDateTime timestamp, String user, String action, String details, String status, String errorMessage) {
        this.id = id;
        this.timestamp = timestamp;
        this.user = user;
        this.action = action;
        this.details = details;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    // Гетери
    public String getId() {
        return id;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getUser() {
        return user;
    }

    public String getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    // Сетери (якщо потрібні, наприклад, для оновлення статусу)
    // public void setStatus(String status) { this.status = status; }
    // public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }


    @Override
    public String toString() {
        return "AuditEntry{" +
               "id='" + id + '\'' +
               ", timestamp=" + timestamp +
               ", user='" + user + '\'' +
               ", action='" + action + '\'' +
               ", status='" + status + '\'' +
               '}';
    }

    // Методи equals та hashCode для порівняння об'єктів AuditEntry за їх ID
     @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditEntry that = (AuditEntry) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
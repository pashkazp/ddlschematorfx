package com.depavlo.ddlschematorfx.model;

import java.util.Objects;

// Клас для представлення деталей підключення до бази даних
public class ConnectionDetails {
    private String id; // Унікальний ідентифікатор підключення
    private String name; // Зручна назва для користувача (наприклад, "Тестова БД", "Продакшн")
    private String url; // URL підключення (JDBC рядок)
    private String user; // Користувач
    private String password; // Пароль (має зберігатися зашифрованим)
    private String schemaName; // Назва схеми за замовчуванням (власник)
    private String description; // Короткий опис підключення

    // Конструктор
    public ConnectionDetails(String id, String name, String url, String user, String password, String schemaName, String description) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.user = user;
        this.password = password;
        this.schemaName = schemaName;
        this.description = description;
    }

    // Гетери
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getDescription() {
        return description;
    }

    // Сетери (якщо потрібні, наприклад, для оновлення пароля)
    // public void setPassword(String password) { this.password = password; }
    // public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return "ConnectionDetails{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", url='" + url + '\'' +
               ", user='" + user + '\'' +
               ", schemaName='" + schemaName + '\'' +
               '}';
    }

    // Методи equals та hashCode для порівняння об'єктів ConnectionDetails за їх ID
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConnectionDetails that = (ConnectionDetails) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
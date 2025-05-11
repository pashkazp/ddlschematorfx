package com.depavlo.ddlschematorfx.service; // Рекомендовано створити окремий пакет для сервісів

import com.depavlo.ddlschematorfx.model.Schema;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Простий сервіс для зберігання витягнутих схем у пам'яті
// В реальному додатку може бути більш складним (наприклад, з кешуванням, збереженням на диск тощо)
public class SchemaService {

    // Мапа для зберігання схем за їх унікальним ID
    private final Map<String, Schema> loadedSchemas = new HashMap<>();

    /**
     * Додає витягнуту схему до сховища.
     * @param schema Об'єкт Schema для додавання.
     */
    public void addSchema(Schema schema) {
        if (schema != null && schema.getId() != null) {
            loadedSchemas.put(schema.getId(), schema);
            System.out.println("Схему '" + schema.getName() + "' з ID '" + schema.getId() + "' додано до сховища.");
        }
    }

    /**
     * Отримує схему за її унікальним ID.
     * @param schemaId Унікальний ID схеми.
     * @return Об'єкт Schema, або null, якщо схему не знайдено.
     */
    public Schema getSchema(String schemaId) {
        return loadedSchemas.get(schemaId);
    }

    /**
     * Видаляє схему за її унікальним ID.
     * @param schemaId Унікальний ID схеми.
     * @return Видалений об'єкт Schema, або null, якщо схему не знайдено.
     */
    public Schema removeSchema(String schemaId) {
        return loadedSchemas.remove(schemaId);
    }

    /**
     * Отримує список усіх завантажених схем.
     * @return Список об'єктів Schema.
     */
    public List<Schema> getAllSchemas() {
        return new java.util.ArrayList<>(loadedSchemas.values());
    }

    /**
     * Очищає сховище схем.
     */
    public void clearSchemas() {
        loadedSchemas.clear();
        System.out.println("Сховище схем очищено.");
    }

    // TODO: Додати методи для збереження/завантаження схем на диск, якщо потрібно.
}

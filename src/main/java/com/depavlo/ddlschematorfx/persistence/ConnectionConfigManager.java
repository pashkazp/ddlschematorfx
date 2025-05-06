package com.depavlo.ddlschematorfx.persistence; // Рекомендовано створити окремий пакет для шару Persistence

import com.depavlo.ddlschematorfx.model.ConnectionDetails;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.exceptions.EncryptionOperationNotPossibleException;

import java.io.*;
import java.nio.charset.StandardCharsets; // Для вказання кодування
import java.sql.Connection; // Імпорт для JDBC Connection
import java.sql.DriverManager; // Імпорт для DriverManager
import java.sql.SQLException; // Імпорт для SQLException
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

// Клас для управління збереженням та завантаженням конфігурацій підключень
public class ConnectionConfigManager {

    // Назва файлу конфігурації. Можна зробити її параметризованою.
    private static final String CONFIG_FILE_NAME = "connections.properties";
    // Ключ для шифрування Jasypt. В реальному додатку цей ключ має зберігатися безпечно
    // (наприклад, у змінних середовища, системних властивостях або захищеному сховищі).
    // Використання фіксованого ключа в коді НЕБЕЗПЕЧНО для продакшн-середовищ!
    private static final String ENCRYPTION_KEY = "your-super-secret-key"; // !!! Змініть це на реальний, безпечний ключ !!!

    private final StandardPBEStringEncryptor encryptor;

    public ConnectionConfigManager() {
        // Ініціалізація Jasypt encryptor
        encryptor = new StandardPBEStringEncryptor();
        encryptor.setPassword(ENCRYPTION_KEY); // Встановлюємо ключ шифрування
        // Можна налаштувати алгоритм шифрування, якщо потрібно
        // encryptor.setAlgorithm("PBEWithMD5AndDES");
    }

    /**
     * Шифрує рядок за допомогою Jasypt.
     * @param text Рядок для шифрування.
     * @return Зашифрований рядок.
     */
    private String encrypt(String text) {
        if (text == null || text.isEmpty()) {
            return text; // Не шифруємо порожні або null рядки
        }
        return encryptor.encrypt(text);
    }

    /**
     * Дешифрує рядок за допомогою Jasypt.
     * Обробляє можливі помилки дешифрування (наприклад, якщо ключ неправильний).
     * @param encryptedText Зашифрований рядок.
     * @return Дешифрований рядок або оригінальний рядок, якщо дешифрування неможливе.
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText; // Не дешифруємо порожні або null рядки
        }
        try {
            return encryptor.decrypt(encryptedText);
        } catch (EncryptionOperationNotPossibleException e) {
            // Обробка помилки дешифрування.
            // Можливо, варто логувати помилку та/або повернути спеціальне значення.
            System.err.println("Помилка дешифрування: " + e.getMessage());
            // Можна повернути порожній рядок, null або оригінальний зашифрований рядок
            return encryptedText; // Повертаємо оригінальний рядок у разі помилки
        }
    }

    /**
     * Зберігає список конфігурацій підключень у файл,
     * групуючи їх за ID, сортуючи параметри та додаючи порожні рядки.
     * @param connections Список конфігурацій для збереження.
     */
    public void saveConnections(List<ConnectionDetails> connections) {
        // Групуємо підключення за ID
        Map<String, ConnectionDetails> connectionsById = connections.stream()
                .collect(Collectors.toMap(ConnectionDetails::getId, conn -> conn));

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(CONFIG_FILE_NAME), StandardCharsets.UTF_8))) {

            // Записуємо заголовок файлу
            writer.write("# Database Connection Configurations");
            writer.newLine();
            writer.write("# Generated on " + new java.util.Date());
            writer.newLine();
            writer.newLine(); // Порожній рядок після заголовка

            boolean firstConnection = true;

            // Сортуємо ID підключень для послідовного запису
            List<String> sortedIds = new ArrayList<>(connectionsById.keySet());
            sortedIds.sort(Comparator.naturalOrder()); // Сортування за ID

            for (String id : sortedIds) {
                ConnectionDetails connection = connectionsById.get(id);

                if (!firstConnection) {
                    writer.newLine(); // Додаємо порожній рядок перед кожною групою, крім першої
                }
                firstConnection = false;

                // Використовуємо TreeMap для автоматичного сортування параметрів за ключами
                Map<String, String> connectionProperties = new TreeMap<>();
                String prefix = id + ".";
                connectionProperties.put(prefix + "name", connection.getName());
                connectionProperties.put(prefix + "url", connection.getUrl());
                connectionProperties.put(prefix + "user", connection.getUser());
                // Шифруємо пароль перед збереженням
                connectionProperties.put(prefix + "password", encrypt(connection.getPassword()));
                connectionProperties.put(prefix + "schemaName", connection.getSchemaName());
                connectionProperties.put(prefix + "description", connection.getDescription());

                // Записуємо параметри підключення (вони вже відсортовані завдяки TreeMap)
                for (Map.Entry<String, String> entry : connectionProperties.entrySet()) {
                    // Екрануємо спеціальні символи, якщо необхідно для .properties формату
                    String key = entry.getKey();
                    String value = entry.getValue();
                    // Проста екранізація для прикладу, можливо, потрібно більш повне екранування
                    if (value != null) { // Перевірка на null
                        value = value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
                    } else {
                        value = ""; // Замінюємо null на порожній рядок при збереженні
                    }
                    writer.write(key + "=" + value);
                    writer.newLine();
                }
            }

            System.out.println("Конфігурації підключень збережено у " + CONFIG_FILE_NAME);
        } catch (IOException e) {
            System.err.println("Помилка при збереженні конфігурацій: " + e.getMessage());
            // TODO: Логування та обробка помилки, можливо, показ повідомлення користувачеві
        }
    }

    /**
     * Завантажує конфігурації підключень з файлу.
     * Дешифрує паролі після завантаження.
     * @return Список завантажених конфігурацій підключень.
     */
    public List<ConnectionDetails> loadConnections() {
        List<ConnectionDetails> connections = new ArrayList<>();
        Properties properties = new Properties();

        File configFile = new File(CONFIG_FILE_NAME);
        if (!configFile.exists()) {
            System.out.println("Файл конфігурації " + CONFIG_FILE_NAME + " не знайдено. Повертаємо порожній список.");
            return connections; // Повертаємо порожній список, якщо файл не існує
        }

        // Використовуємо InputStreamReader з вказанням кодування для коректного читання
        try (InputStream input = new FileInputStream(CONFIG_FILE_NAME);
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {

            // Properties.load може читати файл, навіть якщо він містить порожні рядки та коментарі (#)
            properties.load(reader);

            // Отримуємо всі унікальні ID підключень з ключів властивостей
            List<String> connectionIds = properties.stringPropertyNames().stream()
                    .map(key -> key.split("\\.")[0]) // Виділяємо префікс (ID)
                    .distinct() // Залишаємо тільки унікальні ID
                    .toList();

            for (String id : connectionIds) {
                String name = properties.getProperty(id + ".name");
                String url = properties.getProperty(id + ".url");
                String user = properties.getProperty(id + ".user");
                String encryptedPassword = properties.getProperty(id + ".password");
                String schemaName = properties.getProperty(id + ".schemaName");
                String description = properties.getProperty(id + ".description");

                // Дешифруємо пароль після завантаження
                String password = decrypt(encryptedPassword);

                // Перевірка на null або порожні значення після завантаження та дешифрування
                // Враховуємо, що description може бути null/порожнім.
                // Пароль після дешифрування також може бути null/порожнім, якщо він не був збережений.
                if (id != null && name != null && url != null && user != null && schemaName != null) {
                    connections.add(new ConnectionDetails(id, name, url, user, password, schemaName, description));
                } else {
                    System.err.println("Помилка завантаження конфігурації з ID " + id + ": відсутні обов'язкові поля.");
                    // TODO: Логування або обробка неповних записів
                }
            }

            System.out.println("Конфігурації підключень завантажено з " + CONFIG_FILE_NAME);

        } catch (IOException e) {
            System.err.println("Помилка при завантаженні конфігурацій: " + e.getMessage());
            // TODO: Логування та обробка помилки, можливо, показ повідомлення користувачеві
        }

        return connections;
    }

    /**
     * Генерує унікальний ID для нового підключення.
     * Можна використовувати UUID або інший механізм.
     * @return Унікальний рядок ID.
     */
    public String generateUniqueId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Тестує підключення до бази даних Oracle за наданими параметрами.
     * @param url URL підключення до БД.
     * @param user Користувач БД.
     * @param password Пароль користувача БД.
     * @return true, якщо підключення успішне, false в іншому випадку.
     */
    public boolean testDatabaseConnection(String url, String user, String password) {
        // Перевірка на null або порожні параметри перед спробою підключення
        if (url == null || url.trim().isEmpty() || user == null || user.trim().isEmpty() || password == null) {
            System.err.println("Неповні дані для тесту підключення.");
            return false;
        }

        // Спроба встановлення підключення
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            // Якщо підключення успішне і не викинуло винятку, повертаємо true
            return true;
        } catch (SQLException e) {
            // Обробка помилок підключення (неправильні облікові дані, недоступна БД тощо)
            System.err.println("Помилка тесту підключення до БД: " + e.getMessage());
            // TODO: Логування детальної помилки
            return false;
        } catch (Exception e) {
            // Обробка інших можливих винятків
            System.err.println("Невідома помилка при тестуванні підключення: " + e.getMessage());
            // TODO: Логування детальної помилки
            return false;
        }
    }


    // TODO: Додати методи для видалення окремої конфігурації, оновлення тощо.
    // Ці методи мають завантажувати всі конфігурації, вносити зміни у список
    // та потім зберігати весь оновлений список назад у файл.
}

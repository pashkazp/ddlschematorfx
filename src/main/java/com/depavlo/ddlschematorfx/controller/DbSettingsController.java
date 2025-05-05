package com.depavlo.ddlschematorfx.controller; // Рекомендовано створити окремий пакет для контролерів

import com.depavlo.ddlschematorfx.model.ConnectionDetails; // Імпорт класу моделі
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

// Клас контролера для вікна налаштувань підключення до БД
public class DbSettingsController {

    // Елементи інтерфейсу, пов'язані через fx:id в FXML
    @FXML
    private ListView<ConnectionDetails> connectionListView; // Список збережених підключень
    @FXML
    private TextField nameTextField; // Поле для назви підключення
    @FXML
    private TextField urlTextField; // Поле для URL
    @FXML
    private TextField userTextField; // Поле для користувача
    @FXML
    private PasswordField passwordField; // Поле для пароля
    @FXML
    private TextField schemaNameTextField; // Поле для назви схеми

    private Stage dialogStage; // Посилання на Stage цього вікна/діалогу

    // Метод, який викликається автоматично після завантаження FXML
    @FXML
    private void initialize() {
        // Тут можна виконати початкову ініціалізацію, наприклад,
        // завантажити збережені підключення та відобразити їх у connectionListView.
        // Це потребуватиме взаємодії з шаром Persistence.

        // Приклад: завантаження та відображення фіктивних даних
        // List<ConnectionDetails> savedConnections = loadSavedConnections(); // TODO: Implement loading
        // connectionListView.getItems().addAll(savedConnections);

        // Додавання слухача для вибору елемента у списку
        connectionListView.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> showConnectionDetails(newValue));
    }

    // Метод для встановлення Stage цього вікна/діалогу
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    // Метод для відображення деталей вибраного підключення у полях вводу
    private void showConnectionDetails(ConnectionDetails connection) {
        if (connection != null) {
            // Заповнюємо поля даними з об'єкта ConnectionDetails
            nameTextField.setText(connection.getName());
            urlTextField.setText(connection.getUrl());
            userTextField.setText(connection.getUser());
            // Пароль не слід відображати безпосередньо,
            // або потрібно реалізувати механізм його дешифрування та тимчасового показу.
            // Наразі просто очистимо поле або залишимо його порожнім.
            passwordField.setText(""); // Або реалізувати дешифрування
            schemaNameTextField.setText(connection.getSchemaName());
        } else {
            // Якщо нічого не вибрано, очищаємо поля
            nameTextField.setText("");
            urlTextField.setText("");
            userTextField.setText("");
            passwordField.setText("");
            schemaNameTextField.setText("");
        }
    }

    /**
     * Обробник дії для кнопки "Нове".
     * Очищає поля для введення нового підключення.
     */
    @FXML
    private void handleNewConnection() {
        showConnectionDetails(null); // Очищаємо поля
        // Можливо, потрібно зняти виділення зі списку
        connectionListView.getSelectionModel().clearSelection();
    }

    /**
     * Обробник дії для кнопки "Редагувати".
     * Наразі simply вибирає елемент зі списку (якщо він є),
     * що викличе showConnectionDetails.
     * Повна реалізація може потребувати додаткової логіки.
     */
    @FXML
    private void handleEditConnection() {
        // Логіка редагування може бути реалізована тут.
        // Наприклад, можна зробити поля доступними для редагування,
        // якщо вони були заблоковані.
    }

    /**
     * Обробник дії для кнопки "Видалити".
     * Видаляє вибране підключення зі списку та зі сховища.
     */
    @FXML
    private void handleDeleteConnection() {
        ConnectionDetails selectedConnection = connectionListView.getSelectionModel().getSelectedItem();
        if (selectedConnection != null) {
            // TODO: Implement deletion from storage (Persistence Layer)
            // deleteConnectionFromStorage(selectedConnection);
            connectionListView.getItems().remove(selectedConnection); // Видаляємо зі списку в UI
            handleNewConnection(); // Очищаємо поля після видалення
        } else {
            // TODO: Show a warning to the user
            System.out.println("Будь ласка, виберіть підключення для видалення.");
        }
    }

    /**
     * Обробник дії для кнопки "Зберегти".
     * Зчитує дані з полів та зберігає підключення.
     */
    @FXML
    private void handleSaveConnection() {
        // Зчитуємо дані з полів
        String id = null; // TODO: Implement ID handling (generate for new, get for existing)
        String name = nameTextField.getText();
        String url = urlTextField.getText();
        String user = userTextField.getText();
        String password = passwordField.getText(); // TODO: Encrypt password before saving
        String schemaName = schemaNameTextField.getText();
        String description = ""; // TODO: Add a field for description if needed

        // TODO: Basic validation
        if (name.isEmpty() || url.isEmpty() || user.isEmpty() || password.isEmpty() || schemaName.isEmpty()) {
             // TODO: Show validation error to the user
             System.out.println("Будь ласка, заповніть всі обов'язкові поля.");
             return;
        }

        ConnectionDetails newConnection = new ConnectionDetails(id, name, url, user, password, schemaName, description);

        // TODO: Implement saving to storage (Persistence Layer)
        // saveConnectionToStorage(newConnection);

        // TODO: Update the list view after saving
        // For now, just print to console
        System.out.println("Підключення збережено (фіктивно): " + newConnection);

        // TODO: Refresh the list view or add the new/updated item
        // connectionListView.getItems().clear();
        // connectionListView.getItems().addAll(loadSavedConnections());
    }

    /**
     * Обробник дії для кнопки "Тест підключення".
     * Перевіряє можливість підключення до БД з введеними параметрами.
     */
    @FXML
    private void handleTestConnection() {
        // Зчитуємо дані з полів
        String url = urlTextField.getText();
        String user = userTextField.getText();
        String password = passwordField.getText(); // TODO: Use password for test

        // TODO: Implement connection test logic (Persistence Layer)
        System.out.println("Тестування підключення до: " + url + " від користувача " + user + "...");
        // boolean success = testDatabaseConnection(url, user, password);
        // TODO: Show test result to the user
    }

    /**
     * Обробник дії для кнопки "Закрити".
     * Закриває вікно налаштувань.
     */
    @FXML
    private void handleClose() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    // TODO: Implement methods for loading and saving connections from/to file (Persistence Layer)
    // private List<ConnectionDetails> loadSavedConnections() { ... }
    // private void saveConnectionToStorage(ConnectionDetails connection) { ... }
    // private void deleteConnectionFromStorage(ConnectionDetails connection) { ... }
    // private boolean testDatabaseConnection(String url, String user, String password) { ... }
}

package com.depavlo.ddlschematorfx.controller;

import com.depavlo.ddlschematorfx.model.ConnectionDetails;
import com.depavlo.ddlschematorfx.persistence.ConnectionConfigManager;

import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert; // Імпорт для діалогових вікон
import javafx.scene.control.Alert.AlertType;

import java.util.List;
import java.util.Optional; // Для роботи з Optional

// Клас контролера для вікна налаштувань підключення до БД
public class DbSettingsController {

    @FXML
    private ListView<ConnectionDetails> connectionListView;
    @FXML
    private TextField nameTextField;
    @FXML
    private TextField urlTextField;
    @FXML
    private TextField userTextField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField schemaNameTextField;

    private Stage dialogStage;
    private ConnectionConfigManager configManager;
    private ObservableList<ConnectionDetails> savedConnections;

    @FXML
    private void initialize() {
        configManager = new ConnectionConfigManager();

        // Завантажуємо збережені підключення при ініціалізації контролера
        savedConnections = FXCollections.observableArrayList(configManager.loadConnections());
        connectionListView.setItems(savedConnections); // Відображаємо їх у ListView

        // Додавання слухача для зміни виділеного елемента
        // Цей слухач буде викликатися, коли виділення змінюється (включаючи зняття виділення)
        connectionListView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> showConnectionDetails(newValue));

        // Вибираємо перший елемент у списку при завантаженні, якщо він є
        if (!savedConnections.isEmpty()) {
            connectionListView.getSelectionModel().selectFirst();
        }
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    /**
     * Відображає деталі вибраного підключення у полях вводу.
     * Якщо connection == null, поля очищаються.
     * @param connection Об'єкт ConnectionDetails для відображення, або null.
     */
    private void showConnectionDetails(ConnectionDetails connection) {
        if (connection != null) {
            nameTextField.setText(connection.getName());
            urlTextField.setText(connection.getUrl());
            userTextField.setText(connection.getUser());
            // Пароль не дешифруємо для відображення в полі PasswordField з міркувань безпеки.
            // Користувачеві доведеться ввести його знову при редагуванні, АБО МИ ВИКОРИСТАЄМО СТАРИЙ.
            passwordField.setText("");
            schemaNameTextField.setText(connection.getSchemaName());
        } else {
            // Якщо виділення знято або нічого не вибрано, очищаємо поля
            nameTextField.setText("");
            urlTextField.setText("");
            userTextField.setText("");
            passwordField.setText("");
            schemaNameTextField.setText("");
        }
    }

    /**
     * Обробник дії для кнопки "Нове".
     * Очищає поля для введення нового підключення та знімає виділення.
     */
    @FXML
    private void handleNewConnection() {
        showConnectionDetails(null); // Очищаємо поля
        connectionListView.getSelectionModel().clearSelection(); // Знімаємо виділення зі списку
    }

    /**
     * Обробник дії для кнопки "Редагувати".
     * Ця кнопка може бути корисною, якщо поля зазвичай заблоковані для редагування.
     * Наразі просто гарантує, що вибраний елемент відображається.
     */
    @FXML
    private void handleEditConnection() {
        ConnectionDetails selectedConnection = connectionListView.getSelectionModel().getSelectedItem();
        if (selectedConnection == null) {
            showAlert(AlertType.WARNING, "Редагування", "Не вибрано підключення для редагування.", "Будь ласка, виберіть підключення зі списку.");
        } else {
            // Поля вже мають бути заповнені завдяки слухачу selectedItemProperty
            // Якщо поля були заблоковані, тут можна їх розблокувати.
        }
    }

    /**
     * Обробник дії для кнопки "Видалити".
     * Видаляє вибране підключення зі списку та зі сховища.
     */
    @FXML
    private void handleDeleteConnection() {
        ConnectionDetails selectedConnection = connectionListView.getSelectionModel().getSelectedItem();
        if (selectedConnection != null) {
            // Можна додати діалог підтвердження видалення
            // Optional<ButtonType> result = showAlert(AlertType.CONFIRMATION, "Видалення підключення", "Підтвердження видалення", "Ви впевнені, що хочете видалити підключення '" + selectedConnection.getName() + "'?");
            // if (result.isPresent() && result.get() == ButtonType.OK) {
            savedConnections.remove(selectedConnection);
            configManager.saveConnections(savedConnections.stream().toList());
            handleNewConnection(); // Очищаємо поля після видалення та знімаємо виділення
            // TODO: Показати повідомлення про успішне видалення
            // }
        } else {
            showAlert(AlertType.WARNING, "Видалення", "Не вибрано підключення для видалення.", "Будь ласка, виберіть підключення зі списку.");
        }
    }

    /**
     * Обробник дії для кнопки "Зняти виділення".
     * Знімає виділення з усіх елементів у списку підключень.
     */
    @FXML
    private void handleUnselectAll() {
        connectionListView.getSelectionModel().clearSelection();
        showConnectionDetails(null); // Очищаємо поля, оскільки нічого не вибрано
    }


    /**
     * Обробник дії для кнопки "Зберегти".
     * Зчитує дані з полів та зберігає підключення.
     */
    @FXML
    private void handleSaveConnection() {
        // Отримуємо поточний виділений елемент (якщо є)
        ConnectionDetails selectedConnection = connectionListView.getSelectionModel().getSelectedItem();

        if (selectedConnection == null) {
            // *** Випадок: Жоден рядок не вибрано. Просто перезберігаємо поточний список. ***
            configManager.saveConnections(savedConnections.stream().toList());
            showAlert(AlertType.INFORMATION, "Збереження", "Успіх", "Всі налаштування підключень перезбережено.");
            // Очищаємо поля після перезбереження, оскільки вони не стосуються конкретного вибраного елемента
            showConnectionDetails(null);
            // Знімаємо виділення, якщо воно було (хоча при selectedConnection == null його і так немає)
            connectionListView.getSelectionModel().clearSelection();

        } else {
            // *** Випадок: Рядок вибрано. Зберігаємо або оновлюємо конкретне підключення. ***

            // Зчитуємо дані з полів
            String name = nameTextField.getText();
            String url = urlTextField.getText();
            String user = userTextField.getText();
            String password = passwordField.getText(); // Пароль з поля введення
            String schemaName = schemaNameTextField.getText();
            String description = ""; // TODO: Add a field for description if needed

            // *** Валідація ***
            // Перевірка обов'язкових полів (крім пароля, який обробляється окремо)
            if (name.isEmpty() || url.isEmpty() || user.isEmpty() || schemaName.isEmpty()) {
                showAlert(AlertType.WARNING, "Помилка валідації", "Неповні дані підключення.", "Будь ласка, заповніть всі обов'язкові поля (Назва, URL, Користувач, Схема).");
                return;
            }

            // Перевірка пароля: обов'язковий для нового підключення
            // Якщо selectedConnection != null, це може бути редагування, і пароль не обов'язковий,
            // якщо ми використовуємо старий.
            if (password.isEmpty() && connectionListView.getSelectionModel().getSelectedItem() == null) { // Перевірка на нове підключення
                showAlert(AlertType.WARNING, "Помилка валідації", "Не введено пароль.", "Будь ласка, введіть пароль для нового підключення.");
                return;
            }
            // *** Кінець валідації ***


            String id;
            String passwordToSave; // Пароль, який буде збережено (або новий, або старий зашифрований)

            // Визначаємо, чи це нове підключення, чи редагування існуючого
            // selectedConnection вже отримано на початку методу
            if (connectionListView.getSelectionModel().getSelectedItem() != null) { // Це редагування існуючого
                id = selectedConnection.getId();

                // Якщо поле пароля порожнє, використовуємо старий пароль з об'єкта
                if (password.isEmpty()) {
                    passwordToSave = selectedConnection.getPassword(); // Використовуємо вже зашифрований пароль
                } else {
                    passwordToSave = password; // Використовуємо новий введений пароль (буде зашифровано в configManager)
                }

                // Видаляємо старий запис зі списку в пам'яті перед додаванням оновленого
                savedConnections.remove(selectedConnection);
            } else { // Це нове підключення (хоча цей випадок має бути оброблений кнопкою "Нове" та валідацією)
                // Цей else блок, ймовірно, не буде досягнуто, якщо користувач коректно
                // використовує кнопку "Нове" перед заповненням полів для нового підключення.
                // Але для повноти логіки:
                id = configManager.generateUniqueId();
                passwordToSave = password; // Використовуємо введений пароль
            }

            // Створюємо новий об'єкт ConnectionDetails
            // Важливо: передаємо passwordToSave, який може бути або новим паролем (для шифрування),
            // або старим зашифрованим паролем. configManager.saveConnections обробляє шифрування.
            ConnectionDetails newConnection = new ConnectionDetails(id, name, url, user, passwordToSave, schemaName, description);

            // Додаємо нове/оновлене підключення до списку в UI
            savedConnections.add(newConnection);

            // Зберігаємо весь оновлений список у файл
            configManager.saveConnections(savedConnections.stream().toList());

            // TODO: Показати повідомлення про успішне збереження користувачеві (наприклад, у панелі статусу)
            showAlert(AlertType.INFORMATION, "Збереження", "Успіх", "Налаштування підключення збережено.");

            // Опціонально: виділити збережене підключення у списку
            connectionListView.getSelectionModel().select(newConnection);
        }
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
        String passwordFromField = passwordField.getText(); // Пароль з поля введення

        // Отримуємо поточний виділений елемент (якщо є)
        ConnectionDetails selectedConnection = connectionListView.getSelectionModel().getSelectedItem();

        // *** Валідація для тесту ***
        if (url.isEmpty() || user.isEmpty()) {
            showAlert(AlertType.WARNING, "Тест підключення", "Неповні дані.", "Будь ласка, заповніть поля URL та Користувач для тестування.");
            return;
        }

        String passwordToUseForTest;

        // Визначаємо, який пароль використовувати для тесту:
        // Якщо поле пароля не порожнє, використовуємо його.
        // Якщо поле пароля порожнє І вибрано існуюче підключення, використовуємо збережений пароль.
        // В іншому випадку (поле порожнє І немає вибраного підключення), пароля немає.
        if (!passwordFromField.isEmpty()) {
            passwordToUseForTest = passwordFromField;
        } else if (selectedConnection != null) {
            // Використовуємо збережений пароль з вибраного об'єкта.
            // Його потрібно дешифрувати перед використанням для підключення.
            // TODO: Додати метод дешифрування в ConnectionConfigManager, який приймає зашифрований рядок.
            // Наразі, якщо selectedConnection.getPassword() повертає зашифрований пароль,
            // його потрібно дешифрувати перед передачею в testDatabaseConnection.
            // Припустимо, що ConnectionDetails зберігає ЗАШИФРОВАНИЙ пароль.
            // Нам потрібен доступ до методу дешифрування з ConnectionConfigManager.
            // Можна передати ConnectionConfigManager в цей метод або зробити метод дешифрування публічним.
            // Або, краще, створити окремий сервіс для роботи з підключеннями, який має доступ до менеджера конфігурацій.

            // Тимчасове рішення (потребує доступу до дешифратора):
            // passwordToUseForTest = configManager.decrypt(selectedConnection.getPassword()); // Припустимо, що decrypt публічний або доступний

            // Більш правильне рішення: отримати дешифрований пароль з об'єкта,
            // якщо він був дешифрований при завантаженні. Або дешифрувати тут.
            // Якщо ConnectionDetails зберігає дешифрований пароль після loadConnections:
            passwordToUseForTest = configManager.decrypt(selectedConnection.getPassword()); // Якщо ConnectionDetails зберігає дешифрований пароль

            // Якщо ConnectionDetails зберігає зашифрований пароль і дешифрування відбувається тільки в loadConnections:
            // Потрібно або дешифрувати тут, або змінити логіку зберігання/завантаження.
            // Давайте припустимо, що ConnectionDetails зберігає дешифрований пароль після завантаження.
            if (passwordToUseForTest == null || passwordToUseForTest.isEmpty()) {
                showAlert(AlertType.WARNING, "Тест підключення", "Немає пароля.", "Пароль для вибраного підключення відсутній або недійсний.");
                return;
            }


        } else {
            // Поле пароля порожнє І немає вибраного підключення - пароля немає
            showAlert(AlertType.WARNING, "Тест підключення", "Не введено пароль.", "Будь ласка, введіть пароль або виберіть збережене підключення.");
            return;
        }

        // *** Кінець валідації для тесту ***


        // Викликаємо метод тестування підключення з ConnectionConfigManager
        // Передаємо URL, користувача та визначений пароль для тесту
        boolean success = configManager.testDatabaseConnection(url, user, passwordToUseForTest);

        // Відображаємо результат тесту користувачеві
        if (success) {
            showAlert(AlertType.INFORMATION, "Тест підключення", "Успіх", "Підключення успішне!");
        } else {
            showAlert(AlertType.ERROR, "Тест підключення", "Помилка", "Не вдалося підключитися. Перевірте параметри.");
        }
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

    /**
     * Допоміжний метод для показу діалогових вікон.
     */
    private void showAlert(AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }


    // TODO: Додати інші допоміжні методи, якщо потрібно.
}

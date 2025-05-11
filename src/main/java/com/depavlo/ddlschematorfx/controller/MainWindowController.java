package com.depavlo.ddlschematorfx.controller;

import com.depavlo.ddlschematorfx.model.ConnectionDetails;
import com.depavlo.ddlschematorfx.model.Schema;
import com.depavlo.ddlschematorfx.persistence.ConnectionConfigManager;
import com.depavlo.ddlschematorfx.persistence.OracleSchemaExtractor; // Імпорт екстрактора
import com.depavlo.ddlschematorfx.service.SchemaService;

import javafx.application.Platform;
import javafx.concurrent.Task; // Імпорт для роботи з потоками
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button; // Імпорт Button
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell; // Імпорт ListCell
import javafx.scene.control.ProgressIndicator; // Імпорт ProgressIndicator (якщо додасте в FXML)
import javafx.scene.layout.AnchorPane;
import javafx.stage.DirectoryChooser; // Імпорт для вибору директорії
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.collections.FXCollections;

import java.io.File; // Імпорт File
import java.io.IOException;
import java.net.URL; // Імпорт URL
import java.sql.SQLException; // Імпорт SQLException
import java.util.List;

// Контролер головного вікна додатку
public class MainWindowController {

    @FXML
    private Label statusBarLabel; // Панель статусу
    @FXML
    private ComboBox<ConnectionDetails> connectionComboBox; // ComboBox для вибору підключення
    @FXML
    private Button extractSchemaButton; // Кнопка "Витягти схему" (додайте fx:id="extractSchemaButton" в FXML)
    // @FXML private ProgressIndicator progressIndicator; // Якщо додасте в FXML

    private Stage primaryStage; // Головний Stage
    private ConnectionConfigManager connectionConfigManager; // Менеджер конфігурацій
    private SchemaService schemaService; // Сервіс схем
    // TODO: Додати інші залежності (GitLabManager, AuditService тощо)

    // Метод, який викликається автоматично після завантаження FXML
    @FXML
    private void initialize() {
        // Ініціалізація елементів інтерфейсу
        // Налаштування відображення елементів у ComboBox
        connectionComboBox.setCellFactory(lv -> new ConnectionDetailsListCell());
        connectionComboBox.setButtonCell(new ConnectionDetailsListCell()); // Для відображення вибраного елемента

        // TODO: Додати слухач для ComboBox, якщо потрібно реагувати на вибір одразу
    }

    // Сетери для передачі залежностей з MainApp
    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void setConnectionConfigManager(ConnectionConfigManager connectionConfigManager) {
        this.connectionConfigManager = connectionConfigManager;
        // Після встановлення менеджера можна завантажити підключення
        loadConnectionsIntoComboBox();
    }

    public void setSchemaService(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    // TODO: Додати сетери для інших залежностей


    // Допоміжний метод для завантаження підключень у ComboBox
    private void loadConnectionsIntoComboBox() {
        if (connectionConfigManager != null) {
            List<ConnectionDetails> connections = connectionConfigManager.loadConnections();
            connectionComboBox.setItems(FXCollections.observableArrayList(connections));
        }
    }

    /**
     * Обробник дії для пункту меню "Вихід".
     */
    @FXML
    private void handleExit() {
        Platform.exit();
    }

    /**
     * Обробник дії для пункту меню "Налаштування підключень...".
     * Відкриває вікно налаштувань підключень до БД.
     */
    @FXML
    private void handleDbSettings() {
        try {
            // Завантажуємо FXML файл вікна налаштувань
            FXMLLoader loader = new FXMLLoader();
            // Шлях до FXML файлу. Переконайтеся, що цей шлях правильний
            String fxmlPath = "/com/depavlo/ddlschematorfx/view/DbSettings.fxml";
            URL fxmlLocation = getClass().getResource(fxmlPath);

            if (fxmlLocation == null) {
                // Якщо ресурс не знайдено, показуємо помилку
                showAlert(AlertType.ERROR, "Помилка завантаження FXML", "Ресурс не знайдено", "Не вдалося знайти FXML файл: " + fxmlPath + "\nПеревірте шлях та наявність файлу у директорії ресурсів.");
                return; // Зупиняємо виконання методу
            }

            loader.setLocation(fxmlLocation);

            AnchorPane page = loader.load();

            // Створюємо нове вікно (Stage) для діалогу
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Налаштування підключень до БД");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(primaryStage);
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);

            // Передаємо залежності до контролера вікна налаштувань
            DbSettingsController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setConnectionConfigManager(connectionConfigManager);

            // Показуємо вікно та чекаємо, доки користувач його закриє
            dialogStage.showAndWait();

            // Після закриття вікна налаштувань, оновлюємо список підключень у ComboBox
            loadConnectionsIntoComboBox();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert(AlertType.ERROR, "Помилка", "Помилка завантаження вікна", "Виникла помилка при завантаженні вікна налаштувань підключень: " + e.getMessage());
        }
    }

    /**
     * Обробник дії для пункту меню "Витягти схему з БД...".
     * Запускає процес витягнення схеми з вибраної БД в окремому потоці.
     */
    @FXML
    private void handleExtractSchema() {
        final ConnectionDetails selectedConnection = connectionComboBox.getSelectionModel().getSelectedItem(); // Оголошено як final

        if (selectedConnection == null) {
            showAlert(AlertType.WARNING, "Витягнення схеми", "Не вибрано підключення.", "Будь ласка, виберіть підключення до бази даних зі списку.");
            return;
        }

        // Блокуємо кнопку витягнення та, можливо, інші елементи UI під час операції
        extractSchemaButton.setDisable(true);
        // if (progressIndicator != null) progressIndicator.setVisible(true);
        statusBarLabel.setText("Витягнення схеми...");

        // Створюємо Task для виконання операції в окремому потоці
        Task<Schema> extractionTask = new Task<>() {
            @Override
            protected Schema call() throws Exception {
                // Цей код виконується в фоновому потоці

                // TODO: Реалізувати оновлення прогресу Task, якщо OracleSchemaExtractor надає таку можливість
                // updateProgress(currentWork, totalWork);
                // updateMessage("Витягнення таблиць...");

                OracleSchemaExtractor extractor = new OracleSchemaExtractor();
                // Викликаємо метод витягнення схеми
                return extractor.extractSchema(selectedConnection, selectedConnection.getSchemaName());
            }
        };

        // Налаштовуємо обробники подій для Task
        extractionTask.setOnSucceeded(event -> {
            final Schema extractedSchema = extractionTask.getValue(); // Оголошено як final
            schemaService.addSchema(extractedSchema); // Зберігаємо витягнуту схему в сервісі

            statusBarLabel.setText("Витягнення схеми завершено.");
            // if (progressIndicator != null) progressIndicator.setVisible(false);
            extractSchemaButton.setDisable(false); // Розблоковуємо кнопку

            showAlert(AlertType.INFORMATION, "Витягнення схеми", "Усіх", "Схему витягнуто успішно!");

            // TODO: Можливо, автоматично перейти до вікна порівняння або відобразити деталі схеми
        });

        extractionTask.setOnFailed(event -> {
            Throwable exception = extractionTask.getException();
            System.err.println("Помилка витягнення схеми: " + exception.getMessage());
            // TODO: Логування детальної помилки

            statusBarLabel.setText("Помилка витягнення схеми.");
            // if (progressIndicator != null) progressIndicator.setVisible(false);
            extractSchemaButton.setDisable(false); // Розблоковуємо кнопку

            showAlert(AlertType.ERROR, "Витягнення схеми", "Помилка", "Не вдалося витягти схему: " + exception.getMessage());
        });

        extractionTask.setOnCancelled(event -> {
            statusBarLabel.setText("Витягнення схеми скасовано.");
            // if (progressIndicator != null) progressIndicator.setVisible(false);
            extractSchemaButton.setDisable(false);
            showAlert(AlertType.WARNING, "Витягнення схеми", "Скасовано", "Операцію витягнення схеми скасовано.");
        });

        // Запускаємо Task в новому потоці
        new Thread(extractionTask).start();

        System.out.println("Запущено витягнення схеми для підключення: " + selectedConnection.getName());
    }

    /**
     * Обробник дії для пункту меню "Зберегти активну схему як...".
     * Відкриває діалог вибору директорії та зберігає активну схему.
     */
    @FXML
    private void handleSaveActiveSchema() {
        // TODO: Визначити, яка схема є "активною".
        // Наразі припустимо, що активна схема - це остання витягнута схема,
        // яка зберігається в SchemaService. Можливо, потрібно додати логіку вибору схеми для збереження.

        final List<Schema> loadedSchemas = schemaService.getAllSchemas();
        if (loadedSchemas.isEmpty()) {
            showAlert(AlertType.WARNING, "Збереження схеми", "Немає схеми для збереження.", "Спочатку витягніть схему з бази даних.");
            return;
        }

        // Припустимо, що ми зберігаємо останню витягнуту схему для простоти
        final Schema schemaToSave = loadedSchemas.get(loadedSchemas.size() - 1);

        // Отримуємо ConnectionDetails безпосередньо з об'єкта Schema
        final ConnectionDetails sourceConnection = schemaToSave.getSourceConnection();

        if (sourceConnection == null) {
            // Це може статися, якщо схема була завантажена не з БД, а з DDL файлу,
            // або якщо логіка витягнення не зберегла sourceConnection в Schema.
            // У такому випадку, можливо, варто запитати користувача назву директорії,
            // або використовувати інший спосіб ідентифікації схеми.
            showAlert(AlertType.WARNING, "Збереження схеми", "Немає інформації про джерело схеми.", "Не вдалося визначити підключення, з якого була витягнута схема. Збереження може використовувати лише назву схеми та час.");
            // TODO: Вирішити, як формувати назву директорії, якщо sourceConnection відсутній
            // Наразі, ми кинемо виняток у saveSchemaToFile, якщо sourceConnection == null
            // Або можна передати null замість connectionName і обробити це в saveSchemaToFile.
            // Давайте передамо null і обробимо в saveSchemaToFile.
        }


        // Відкриваємо діалог вибору директорії
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Виберіть директорію для збереження схеми");

        // Встановлюємо початкову директорію (опціонально)
        // directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));

        final File selectedDirectory = directoryChooser.showDialog(primaryStage);

        if (selectedDirectory != null) {
            // Користувач вибрав директорію
            final String baseDirectoryPath = selectedDirectory.getAbsolutePath();

            // Запускаємо збереження схеми в окремому потоці
            Task<Void> saveTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    // Викликаємо saveSchemaToFile, передаючи об'єкт Schema
                    schemaService.saveSchemaToFile(schemaToSave, baseDirectoryPath);
                    return null;
                }
            };

            saveTask.setOnSucceeded(event -> {
                // Цей код виконується в потоці UI після успішного завершення Task
                statusBarLabel.setText("Збереження схеми завершено.");
                showAlert(AlertType.INFORMATION, "Збереження схеми", "Успіх", "Схему успішно збережено!");
            });

            saveTask.setOnFailed(event -> {
                // Цей код виконується в потоці UI після невдалого завершення Task
                Throwable exception = saveTask.getException();
                System.err.println("Помилка збереження схеми: " + exception.getMessage());
                // TODO: Логування детальної помилки
                statusBarLabel.setText("Помилка збереження схеми.");
                showAlert(AlertType.ERROR, "Збереження схеми", "Помилка", "Не вдалося зберегти схему: " + exception.getMessage());
            });

            saveTask.setOnCancelled(event -> {
                // Цей код виконується в потоці UI, якщо Task було скасовано
                statusBarLabel.setText("Збереження схеми скасовано.");
                showAlert(AlertType.WARNING, "Збереження схеми", "Скасовано", "Операцію збереження схеми скасовано.");
            });


            new Thread(saveTask).start();

            System.out.println("Запущено збереження схеми '" + schemaToSave.getName() + "' у директорію: " + baseDirectoryPath);

        } else {
            // Користувач скасував діалог вибору директорії
            System.out.println("Збереження схеми скасовано користувачем.");
            statusBarLabel.setText("Збереження скасовано.");
        }
    }


    // TODO: Додати обробники для інших пунктів меню
    // TODO: Додати допоміжні методи для показу діалогових вікон (як у DbSettingsController)
    private void showAlert(AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }


    // Користувацька фабрика клітинок для відображення ConnectionDetails у ComboBox та ListView
    // Можна використовувати той самий клас для обох
    private static class ConnectionDetailsListCell extends javafx.scene.control.ListCell<ConnectionDetails> {
        @Override
        protected void updateItem(ConnectionDetails connection, boolean empty) {
            super.updateItem(connection, empty);
            if (empty || connection == null) {
                setText(null);
            } else {
                // Відображаємо зручну назву підключення
                setText(connection.getName());
            }
        }
    }
}

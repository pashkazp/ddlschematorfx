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
// import javafx.scene.control.ProgressIndicator; // Імпорт ProgressIndicator (якщо додасте в FXML)
import javafx.scene.layout.AnchorPane;
import javafx.stage.DirectoryChooser; // Імпорт для вибору директорії
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.collections.FXCollections;

import java.io.File; // Імпорт File
import java.io.IOException;
import java.net.URL; // Імпорт URL
import java.nio.file.Path; // Для роботи зі шляхами
import java.sql.SQLException; // Імпорт SQLException
import java.util.List;

// Контролер головного вікна додатку
public class MainWindowController {

    @FXML
    private Label statusBarLabel; // Панель статусу
    @FXML
    private ComboBox<ConnectionDetails> connectionComboBox; // ComboBox для вибору підключення
    @FXML
    private Button extractSchemaButton; // Кнопка "Витягти схему"
    // @FXML private ProgressIndicator progressIndicator; // Якщо додасте в FXML

    private Stage primaryStage; // Головний Stage
    private ConnectionConfigManager connectionConfigManager; // Менеджер конфігурацій
    private SchemaService schemaService; // Сервіс схем

    @FXML
    private void initialize() {
        connectionComboBox.setCellFactory(lv -> new ConnectionDetailsListCell());
        connectionComboBox.setButtonCell(new ConnectionDetailsListCell());
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void setConnectionConfigManager(ConnectionConfigManager connectionConfigManager) {
        this.connectionConfigManager = connectionConfigManager;
        loadConnectionsIntoComboBox();
    }

    public void setSchemaService(SchemaService schemaService) {
        this.schemaService = schemaService;
    }

    private void loadConnectionsIntoComboBox() {
        if (connectionConfigManager != null) {
            List<ConnectionDetails> connections = connectionConfigManager.loadConnections();
            connectionComboBox.setItems(FXCollections.observableArrayList(connections));
        }
    }

    @FXML
    private void handleExit() {
        Platform.exit();
    }

    @FXML
    private void handleDbSettings() {
        try {
            FXMLLoader loader = new FXMLLoader();
            String fxmlPath = "/com/depavlo/ddlschematorfx/view/DbSettings.fxml";
            URL fxmlLocation = getClass().getResource(fxmlPath);

            if (fxmlLocation == null) {
                showAlert(AlertType.ERROR, "Помилка завантаження FXML", "Ресурс не знайдено", "Не вдалося знайти FXML файл: " + fxmlPath + "\nПеревірте шлях та наявність файлу у директорії ресурсів.");
                return;
            }
            loader.setLocation(fxmlLocation);
            AnchorPane page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Налаштування підключень до БД");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(primaryStage);
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);

            DbSettingsController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setConnectionConfigManager(connectionConfigManager);

            dialogStage.showAndWait();
            loadConnectionsIntoComboBox();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert(AlertType.ERROR, "Помилка", "Помилка завантаження вікна", "Виникла помилка при завантаженні вікна налаштувань підключень: " + e.getMessage());
        }
    }

    @FXML
    private void handleExtractSchema() {
        final ConnectionDetails selectedConnection = connectionComboBox.getSelectionModel().getSelectedItem();

        if (selectedConnection == null) {
            showAlert(AlertType.WARNING, "Витягнення схеми", "Не вибрано підключення.", "Будь ласка, виберіть підключення до бази даних зі списку.");
            return;
        }

        extractSchemaButton.setDisable(true);
        statusBarLabel.setText("Витягнення схеми...");

        Task<Schema> extractionTask = new Task<>() {
            @Override
            protected Schema call() throws Exception {
                OracleSchemaExtractor extractor = new OracleSchemaExtractor();
                return extractor.extractSchema(selectedConnection, selectedConnection.getSchemaName());
            }
        };

        extractionTask.setOnSucceeded(event -> {
            final Schema extractedSchema = extractionTask.getValue();
            schemaService.addSchema(extractedSchema);
            statusBarLabel.setText("Витягнення схеми завершено для: " + extractedSchema.getName());
            extractSchemaButton.setDisable(false);
            showAlert(AlertType.INFORMATION, "Витягнення схеми", "Успіх", "Схему '" + extractedSchema.getName() + "' витягнуто успішно!");
        });

        extractionTask.setOnFailed(event -> {
            Throwable exception = extractionTask.getException();
            System.err.println("Помилка витягнення схеми: " + exception.getMessage());
            statusBarLabel.setText("Помилка витягнення схеми.");
            extractSchemaButton.setDisable(false);
            showAlert(AlertType.ERROR, "Витягнення схеми", "Помилка", "Не вдалося витягти схему: " + exception.getMessage());
        });

        extractionTask.setOnCancelled(event -> {
            statusBarLabel.setText("Витягнення схеми скасовано.");
            extractSchemaButton.setDisable(false);
            showAlert(AlertType.WARNING, "Витягнення схеми", "Скасовано", "Операцію витягнення схеми скасовано.");
        });

        new Thread(extractionTask).start();
        System.out.println("Запущено витягнення схеми для підключення: " + selectedConnection.getName());
    }


    /**
     * Обробник дії для пункту меню "Завантажити схему з DDL...".
     * Відкриває діалог вибору директорії та завантажує схему з файлів.
     */
    @FXML
    private void handleLoadSchemaFromDirectory() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Виберіть директорію зі збереженою схемою");

        File selectedDirectory = directoryChooser.showDialog(primaryStage);

        if (selectedDirectory != null) {
            final Path schemaDirectoryPath = selectedDirectory.toPath();
            statusBarLabel.setText("Завантаження схеми з директорії...");
            // Можна заблокувати кнопку/пункт меню на час завантаження
            // loadSchemaFromDirMenuItem.setDisable(true); // Якщо є fx:id для MenuItem

            Task<Schema> loadTask = new Task<>() {
                @Override
                protected Schema call() throws Exception {
                    // Викликаємо метод сервісу для завантаження схеми з директорії
                    return schemaService.loadSchemaFromDirectory(schemaDirectoryPath);
                }
            };

            loadTask.setOnSucceeded(event -> {
                final Schema loadedSchema = loadTask.getValue();
                schemaService.addSchema(loadedSchema); // Додаємо завантажену схему до сервісу
                statusBarLabel.setText("Схему '" + loadedSchema.getName() + "' успішно завантажено з директорії.");
                // loadSchemaFromDirMenuItem.setDisable(false);
                showAlert(AlertType.INFORMATION, "Завантаження схеми", "Успіх", "Схему '" + loadedSchema.getName() + "' успішно завантажено з: " + schemaDirectoryPath.toString());
                // TODO: Оновити UI, якщо потрібно (наприклад, списки доступних схем для порівняння)
            });

            loadTask.setOnFailed(event -> {
                Throwable exception = loadTask.getException();
                System.err.println("Помилка завантаження схеми з директорії: " + exception.getMessage());
                exception.printStackTrace(); // Друк стеку викликів для діагностики
                statusBarLabel.setText("Помилка завантаження схеми з директорії.");
                // loadSchemaFromDirMenuItem.setDisable(false);
                showAlert(AlertType.ERROR, "Завантаження схеми", "Помилка", "Не вдалося завантажити схему з директорії: " + schemaDirectoryPath.toString() + "\nПричина: " + exception.getMessage());
            });

            loadTask.setOnCancelled(event -> {
                statusBarLabel.setText("Завантаження схеми скасовано.");
                // loadSchemaFromDirMenuItem.setDisable(false);
                showAlert(AlertType.WARNING, "Завантаження схеми", "Скасовано", "Операцію завантаження схеми з директорії скасовано.");
            });

            new Thread(loadTask).start();
            System.out.println("Запущено завантаження схеми з директорії: " + schemaDirectoryPath);
        } else {
            System.out.println("Вибір директорії для завантаження схеми скасовано користувачем.");
            statusBarLabel.setText("Завантаження схеми скасовано.");
        }
    }


    @FXML
    private void handleSaveActiveSchema() {
        final List<Schema> loadedSchemas = schemaService.getAllSchemas();
        if (loadedSchemas.isEmpty()) {
            showAlert(AlertType.WARNING, "Збереження схеми", "Немає схеми для збереження.", "Спочатку витягніть або завантажте схему.");
            return;
        }

        // TODO: Додати можливість вибору, яку саме схему зберігати, якщо їх декілька.
        // Наразі зберігаємо останню додану.
        final Schema schemaToSave = loadedSchemas.get(loadedSchemas.size() - 1);

        // Для схем, завантажених з файлів, sourceConnection буде null.
        // Метод saveSchemaToFile очікує non-null sourceConnection.
        // Потрібно або змінити saveSchemaToFile, або не дозволяти зберігати схеми, завантажені з файлів, цим методом,
        // або створити фіктивний ConnectionDetails для них.
        if (schemaToSave.getSourceConnection() == null) {
            showAlert(AlertType.WARNING, "Збереження схеми", "Неможливо зберегти схему", "Схеми, завантажені з файлової системи, не можуть бути збережені цим методом, оскільки відсутні деталі вихідного підключення.");
            // Або можна створити діалог для введення "назви підключення" для збереження
            return;
        }


        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Виберіть директорію для збереження схеми");
        final File selectedDirectory = directoryChooser.showDialog(primaryStage);

        if (selectedDirectory != null) {
            final String baseDirectoryPath = selectedDirectory.getAbsolutePath();
            statusBarLabel.setText("Збереження схеми...");

            Task<Void> saveTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    schemaService.saveSchemaToFile(schemaToSave, baseDirectoryPath);
                    return null;
                }
            };

            saveTask.setOnSucceeded(event -> {
                statusBarLabel.setText("Збереження схеми '" + schemaToSave.getName() + "' завершено.");
                showAlert(AlertType.INFORMATION, "Збереження схеми", "Успіх", "Схему '" + schemaToSave.getName() + "' успішно збережено!");
            });

            saveTask.setOnFailed(event -> {
                Throwable exception = saveTask.getException();
                System.err.println("Помилка збереження схеми: " + exception.getMessage());
                statusBarLabel.setText("Помилка збереження схеми.");
                showAlert(AlertType.ERROR, "Збереження схеми", "Помилка", "Не вдалося зберегти схему: " + exception.getMessage());
            });

            saveTask.setOnCancelled(event -> {
                statusBarLabel.setText("Збереження схеми скасовано.");
                showAlert(AlertType.WARNING, "Збереження схеми", "Скасовано", "Операцію збереження схеми скасовано.");
            });

            new Thread(saveTask).start();
        } else {
            statusBarLabel.setText("Збереження скасовано.");
        }
    }

    private void showAlert(AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private static class ConnectionDetailsListCell extends javafx.scene.control.ListCell<ConnectionDetails> {
        @Override
        protected void updateItem(ConnectionDetails connection, boolean empty) {
            super.updateItem(connection, empty);
            if (empty || connection == null) {
                setText(null);
            } else {
                setText(connection.getName());
            }
        }
    }
}

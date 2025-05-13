package com.depavlo.ddlschematorfx.controller;

import com.depavlo.ddlschematorfx.model.ConnectionDetails;
import com.depavlo.ddlschematorfx.model.Difference;
import com.depavlo.ddlschematorfx.model.Schema;
import com.depavlo.ddlschematorfx.persistence.ConnectionConfigManager;
import com.depavlo.ddlschematorfx.persistence.OracleSchemaExtractor;
import com.depavlo.ddlschematorfx.service.SchemaComparisonService; // Імпорт сервісу порівняння
import com.depavlo.ddlschematorfx.service.SchemaService;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog; // Для вибору схем
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem; // Для доступу до пункту меню
import javafx.scene.layout.AnchorPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class MainWindowController {

    @FXML
    private Label statusBarLabel;
    @FXML
    private ComboBox<ConnectionDetails> connectionComboBox;
    @FXML
    private Button extractSchemaButton;
    @FXML
    private MenuItem compareSchemasMenuItem; // fx:id для пункту меню "Порівняти схеми..."

    private Stage primaryStage;
    private ConnectionConfigManager connectionConfigManager;
    private SchemaService schemaService;
    private SchemaComparisonService schemaComparisonService; // Поле для сервісу порівняння

    @FXML
    private void initialize() {
        connectionComboBox.setCellFactory(lv -> new ConnectionDetailsListCell());
        connectionComboBox.setButtonCell(new ConnectionDetailsListCell());
        // Початково деактивуємо пункт меню порівняння, активуємо, коли є хоча б 2 схеми
        if (compareSchemasMenuItem != null) {
            compareSchemasMenuItem.setDisable(true);
        }
        updateCompareMenuItemState();
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

    // Сеттер для SchemaComparisonService
    public void setSchemaComparisonService(SchemaComparisonService schemaComparisonService) {
        this.schemaComparisonService = schemaComparisonService;
    }

    private void loadConnectionsIntoComboBox() {
        if (connectionConfigManager != null) {
            List<ConnectionDetails> connections = connectionConfigManager.loadConnections();
            connectionComboBox.setItems(FXCollections.observableArrayList(connections));
        }
    }

    private void updateCompareMenuItemState() {
        if (compareSchemasMenuItem != null && schemaService != null) {
            compareSchemasMenuItem.setDisable(schemaService.getAllSchemas().size() < 2);
        }
    }


    @FXML
    private void handleExit() {
        Platform.exit();
    }

    @FXML
    private void handleDbSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/depavlo/ddlschematorfx/view/DbSettings.fxml"));
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
            showAlert(AlertType.ERROR, "Помилка", "Помилка завантаження вікна", "Виникла помилка: " + e.getMessage());
        }
    }

    @FXML
    private void handleExtractSchema() {
        final ConnectionDetails selectedConnection = connectionComboBox.getSelectionModel().getSelectedItem();
        if (selectedConnection == null) {
            showAlert(AlertType.WARNING, "Витягнення схеми", "Не вибрано підключення.", "Будь ласка, виберіть підключення.");
            return;
        }
        extractSchemaButton.setDisable(true);
        statusBarLabel.setText("Витягнення схеми для " + selectedConnection.getSchemaName() + "...");
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
            statusBarLabel.setText("Схему '" + extractedSchema.getName() + "' успішно витягнуто.");
            extractSchemaButton.setDisable(false);
            showAlert(AlertType.INFORMATION, "Витягнення схеми", "Успіх", "Схему '" + extractedSchema.getName() + "' витягнуто!");
            updateCompareMenuItemState();
        });
        extractionTask.setOnFailed(event -> handleTaskFailure(extractionTask, "витягнення схеми"));
        extractionTask.setOnCancelled(event -> handleTaskCancellation("витягнення схеми"));
        new Thread(extractionTask).start();
    }

    @FXML
    private void handleLoadSchemaFromDirectory() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Виберіть директорію зі збереженою схемою");
        File selectedDirectory = directoryChooser.showDialog(primaryStage);
        if (selectedDirectory != null) {
            final Path schemaDirectoryPath = selectedDirectory.toPath();
            statusBarLabel.setText("Завантаження схеми з " + schemaDirectoryPath.getFileName() + "...");
            Task<Schema> loadTask = new Task<>() {
                @Override
                protected Schema call() throws Exception {
                    return schemaService.loadSchemaFromDirectory(schemaDirectoryPath);
                }
            };
            loadTask.setOnSucceeded(event -> {
                final Schema loadedSchema = loadTask.getValue();
                schemaService.addSchema(loadedSchema);
                statusBarLabel.setText("Схему '" + loadedSchema.getName() + "' успішно завантажено.");
                showAlert(AlertType.INFORMATION, "Завантаження схеми", "Успіх", "Схему '" + loadedSchema.getName() + "' завантажено з: " + schemaDirectoryPath.toString());
                updateCompareMenuItemState();
            });
            loadTask.setOnFailed(event -> handleTaskFailure(loadTask, "завантаження схеми з директорії"));
            loadTask.setOnCancelled(event -> handleTaskCancellation("завантаження схеми з директорії"));
            new Thread(loadTask).start();
        } else {
            statusBarLabel.setText("Завантаження схеми скасовано.");
        }
    }

    /**
     * Обробник для пункту меню "Порівняти схеми...".
     */
    @FXML
    private void handleCompareSchemas() {
        List<Schema> availableSchemas = schemaService.getAllSchemas();
        if (availableSchemas.size() < 2) {
            showAlert(AlertType.WARNING, "Порівняння схем", "Недостатньо схем", "Для порівняння потрібно щонайменше дві завантажені схеми.");
            return;
        }

        // Створюємо список назв схем для діалогових вікон
        List<String> schemaNames = availableSchemas.stream()
                .map(s -> s.getName() + " (ID: " + s.getId().substring(0, 8) + "...)") // Короткий ID для унікальності
                .collect(Collectors.toList());

        // Діалог вибору першої схеми (джерела)
        ChoiceDialog<String> sourceDialog = new ChoiceDialog<>(null, schemaNames);
        sourceDialog.setTitle("Вибір схеми-джерела");
        sourceDialog.setHeaderText("Виберіть першу схему (джерело) для порівняння:");
        sourceDialog.setContentText("Схема-джерело:");
        Optional<String> sourceResult = sourceDialog.showAndWait();

        if (sourceResult.isEmpty()) {
            statusBarLabel.setText("Порівняння скасовано.");
            return;
        }
        Schema sourceSchema = findSchemaByDisplayName(sourceResult.get(), availableSchemas);

        // Діалог вибору другої схеми (цілі)
        ChoiceDialog<String> targetDialog = new ChoiceDialog<>(null, schemaNames);
        targetDialog.setTitle("Вибір цільової схеми");
        targetDialog.setHeaderText("Виберіть другу схему (цільову) для порівняння:");
        targetDialog.setContentText("Цільова схема:");
        Optional<String> targetResult = targetDialog.showAndWait();

        if (targetResult.isEmpty()) {
            statusBarLabel.setText("Порівняння скасовано.");
            return;
        }
        Schema targetSchema = findSchemaByDisplayName(targetResult.get(), availableSchemas);

        if (sourceSchema == null || targetSchema == null) {
            showAlert(AlertType.ERROR, "Порівняння схем", "Помилка вибору схем", "Не вдалося знайти вибрані схеми. Спробуйте ще раз.");
            return;
        }

        if (sourceSchema.getId().equals(targetSchema.getId())) {
            showAlert(AlertType.WARNING, "Порівняння схем", "Однакові схеми", "Вибрано одну й ту ж схему для джерела та цілі. Порівняння не має сенсу.");
            return;
        }


        statusBarLabel.setText("Порівняння схем: " + sourceSchema.getName() + " та " + targetSchema.getName() + "...");
        // Блокуємо пункт меню на час порівняння
        if (compareSchemasMenuItem != null) compareSchemasMenuItem.setDisable(true);


        Task<List<Difference>> comparisonTask = new Task<>() {
            @Override
            protected List<Difference> call() throws Exception {
                return schemaComparisonService.compareSchemas(sourceSchema, targetSchema);
            }
        };

        comparisonTask.setOnSucceeded(event -> {
            List<Difference> differences = comparisonTask.getValue();
            statusBarLabel.setText("Порівняння завершено. Знайдено відмінностей: " + differences.size());
            if (compareSchemasMenuItem != null) compareSchemasMenuItem.setDisable(false); // Розблоковуємо
            updateCompareMenuItemState(); // Оновлюємо стан, якщо кількість схем змінилась

            // На цьому етапі просто виводимо результат
            System.out.println("Результати порівняння схем '" + sourceSchema.getName() + "' (Source) та '" + targetSchema.getName() + "' (Target):");
            if (differences.isEmpty()) {
                System.out.println("Відмінностей не знайдено.");
                showAlert(AlertType.INFORMATION, "Результат порівняння", "Відмінностей не знайдено",
                        "Схеми '" + sourceSchema.getName() + "' та '" + targetSchema.getName() + "' ідентичні (з урахуванням форматування).");
            } else {
                differences.forEach(System.out::println);
                // TODO: Показати результати у спеціальному вікні/таблиці
                showAlert(AlertType.INFORMATION, "Результат порівняння", "Знайдено відмінностей: " + differences.size(),
                        "Деталі виведено в консоль. Перегляньте консоль для списку відмінностей між '" +
                                sourceSchema.getName() + "' та '" + targetSchema.getName() + "'.");
            }
        });

        comparisonTask.setOnFailed(event -> {
            handleTaskFailure(comparisonTask, "порівняння схем");
            if (compareSchemasMenuItem != null) compareSchemasMenuItem.setDisable(false); // Розблоковуємо
            updateCompareMenuItemState();
        });
        comparisonTask.setOnCancelled(event -> {
            handleTaskCancellation("порівняння схем");
            if (compareSchemasMenuItem != null) compareSchemasMenuItem.setDisable(false); // Розблоковуємо
            updateCompareMenuItemState();
        });

        new Thread(comparisonTask).start();
    }

    // Допоміжний метод для пошуку схеми за відображуваним ім'ям
    private Schema findSchemaByDisplayName(String displayName, List<Schema> schemas) {
        for (Schema schema : schemas) {
            String currentDisplayName = schema.getName() + " (ID: " + schema.getId().substring(0, 8) + "...)";
            if (currentDisplayName.equals(displayName)) {
                return schema;
            }
        }
        return null;
    }


    @FXML
    private void handleSaveActiveSchema() {
        final List<Schema> loadedSchemas = schemaService.getAllSchemas();
        if (loadedSchemas.isEmpty()) {
            showAlert(AlertType.WARNING, "Збереження схеми", "Немає схеми для збереження.", "Спочатку витягніть або завантажте схему.");
            return;
        }
        final Schema schemaToSave = loadedSchemas.get(loadedSchemas.size() - 1);
        if (schemaToSave.getSourceConnection() == null) {
            showAlert(AlertType.WARNING, "Збереження схеми", "Неможливо зберегти схему", "Схеми, завантажені з файлів, не можуть бути збережені цим методом.");
            return;
        }
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Виберіть директорію для збереження схеми");
        final File selectedDirectory = directoryChooser.showDialog(primaryStage);
        if (selectedDirectory != null) {
            final String baseDirectoryPath = selectedDirectory.getAbsolutePath();
            statusBarLabel.setText("Збереження схеми '" + schemaToSave.getName() + "'...");
            Task<Void> saveTask = new Task<>() {
                @Override
                protected Void call() throws Exception {
                    schemaService.saveSchemaToFile(schemaToSave, baseDirectoryPath);
                    return null;
                }
            };
            saveTask.setOnSucceeded(event -> {
                statusBarLabel.setText("Схему '" + schemaToSave.getName() + "' успішно збережено.");
                showAlert(AlertType.INFORMATION, "Збереження схеми", "Успіх", "Схему '" + schemaToSave.getName() + "' збережено!");
            });
            saveTask.setOnFailed(event -> handleTaskFailure(saveTask, "збереження схеми"));
            saveTask.setOnCancelled(event -> handleTaskCancellation("збереження схеми"));
            new Thread(saveTask).start();
        } else {
            statusBarLabel.setText("Збереження скасовано.");
        }
    }

    private void handleTaskFailure(Task<?> task, String operationName) {
        Throwable exception = task.getException();
        System.err.println("Помилка операції '" + operationName + "': " + exception.getMessage());
        exception.printStackTrace();
        statusBarLabel.setText("Помилка операції: " + operationName);
        showAlert(AlertType.ERROR, "Помилка операції", "Помилка: " + operationName, "Не вдалося виконати операцію: " + exception.getMessage());
    }

    private void handleTaskCancellation(String operationName) {
        statusBarLabel.setText("Операцію '" + operationName + "' скасовано.");
        showAlert(AlertType.WARNING, "Операцію скасовано", "Скасовано", "Операцію '" + operationName + "' було скасовано.");
    }


    private void showAlert(AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private static class ConnectionDetailsListCell extends ListCell<ConnectionDetails> {
        @Override
        protected void updateItem(ConnectionDetails connection, boolean empty) {
            super.updateItem(connection, empty);
            setText(empty || connection == null ? null : connection.getName());
        }
    }
}

package com.depavlo.ddlschematorfx.controller;

import com.depavlo.ddlschematorfx.model.ConnectionDetails;
import com.depavlo.ddlschematorfx.model.Difference;
import com.depavlo.ddlschematorfx.model.Schema;
import com.depavlo.ddlschematorfx.persistence.ConnectionConfigManager;
import com.depavlo.ddlschematorfx.persistence.OracleSchemaExtractor;
import com.depavlo.ddlschematorfx.service.SchemaComparisonService;
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
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
// TextInputDialog більше не потрібен для цього методу
// import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox; // Потрібен для завантаження SaveSchemaDialog.fxml, якщо його корінь VBox
import javafx.stage.DirectoryChooser; // DirectoryChooser все ще використовується в SaveSchemaDialogController
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences; // Для збереження останньої директорії
import java.util.stream.Collectors;

public class MainWindowController {

    @FXML
    private Label statusBarLabel;
    @FXML
    private ComboBox<ConnectionDetails> connectionComboBox;
    @FXML
    private Button extractSchemaButton;
    @FXML
    private MenuItem compareSchemasMenuItem;
    @FXML
    private MenuItem saveSchemaMenuItem;

    private Stage primaryStage;
    private ConnectionConfigManager connectionConfigManager;
    private SchemaService schemaService;
    private SchemaComparisonService schemaComparisonService;

    private static final DateTimeFormatter DIRECTORY_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    // Для збереження шляху останньої використаної базової директорії
    private static final String PREF_LAST_SAVE_BASE_DIR = "lastSaveBaseDir";


    @FXML
    private void initialize() {
        connectionComboBox.setCellFactory(lv -> new ConnectionDetailsListCell());
        connectionComboBox.setButtonCell(new ConnectionDetailsListCell());
        if (compareSchemasMenuItem != null) {
            compareSchemasMenuItem.setDisable(true);
        }
        if (saveSchemaMenuItem != null) {
            saveSchemaMenuItem.setDisable(true);
        }
        updateSchemaActionMenuItemsState();
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

    public void setSchemaComparisonService(SchemaComparisonService schemaComparisonService) {
        this.schemaComparisonService = schemaComparisonService;
    }

    private void loadConnectionsIntoComboBox() {
        if (connectionConfigManager != null) {
            List<ConnectionDetails> connections = connectionConfigManager.loadConnections();
            connectionComboBox.setItems(FXCollections.observableArrayList(connections));
        }
    }

    private void updateSchemaActionMenuItemsState() {
        boolean schemasAvailable = schemaService != null && !schemaService.getAllSchemas().isEmpty();
        boolean enoughSchemasForComparison = schemaService != null && schemaService.getAllSchemas().size() >= 2;

        if (compareSchemasMenuItem != null) {
            compareSchemasMenuItem.setDisable(!enoughSchemasForComparison);
        }
        if (saveSchemaMenuItem != null) {
            saveSchemaMenuItem.setDisable(!schemasAvailable);
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
            statusBarLabel.setText("Схему '" + extractedSchema.getName() + "' успішно витягнуто/оновлено.");
            extractSchemaButton.setDisable(false);
            showAlert(AlertType.INFORMATION, "Витягнення схеми", "Успіх", "Схему '" + extractedSchema.getName() + "' витягнуто/оновлено!");
            updateSchemaActionMenuItemsState();
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
                statusBarLabel.setText("Схему '" + loadedSchema.getName() + "' успішно завантажено/оновлено.");
                showAlert(AlertType.INFORMATION, "Завантаження схеми", "Успіх", "Схему '" + loadedSchema.getName() + "' завантажено/оновлено з: " + schemaDirectoryPath.toString());
                updateSchemaActionMenuItemsState();
            });
            loadTask.setOnFailed(event -> handleTaskFailure(loadTask, "завантаження схеми з директорії"));
            loadTask.setOnCancelled(event -> handleTaskCancellation("завантаження схеми з директорії"));
            new Thread(loadTask).start();
        } else {
            statusBarLabel.setText("Завантаження схеми скасовано.");
        }
    }

    @FXML
    private void handleCompareSchemas() {
        List<Schema> availableSchemas = schemaService.getAllSchemas();
        if (availableSchemas.size() < 2) {
            showAlert(AlertType.WARNING, "Порівняння схем", "Недостатньо схем", "Для порівняння потрібно щонайменше дві завантажені схеми.");
            return;
        }

        List<String> schemaDisplayNames = availableSchemas.stream()
                .map(this::getSchemaDisplayName)
                .collect(Collectors.toList());

        ChoiceDialog<String> sourceDialog = new ChoiceDialog<>(null, schemaDisplayNames);
        sourceDialog.setTitle("Вибір схеми-джерела");
        sourceDialog.setHeaderText("Виберіть першу схему (джерело) для порівняння:");
        sourceDialog.setContentText("Схема-джерело:");
        Optional<String> sourceResult = sourceDialog.showAndWait();

        if (sourceResult.isEmpty()) {
            statusBarLabel.setText("Порівняння скасовано.");
            return;
        }
        final Schema sourceSchema = findSchemaByDisplayName(sourceResult.get(), availableSchemas);

        ChoiceDialog<String> targetDialog = new ChoiceDialog<>(null, schemaDisplayNames);
        targetDialog.setTitle("Вибір цільової схеми");
        targetDialog.setHeaderText("Виберіть другу схему (цільову) для порівняння:");
        targetDialog.setContentText("Цільова схема:");
        Optional<String> targetResult = targetDialog.showAndWait();

        if (targetResult.isEmpty()) {
            statusBarLabel.setText("Порівняння скасовано.");
            return;
        }
        final Schema targetSchema = findSchemaByDisplayName(targetResult.get(), availableSchemas);

        if (sourceSchema == null || targetSchema == null) {
            showAlert(AlertType.ERROR, "Порівняння схем", "Помилка вибору схем", "Не вдалося знайти вибрані схеми.");
            return;
        }

        if (sourceSchema.getId().equals(targetSchema.getId())) {
            showAlert(AlertType.WARNING, "Порівняння схем", "Однакові схеми", "Вибрано одну й ту ж схему для джерела та цілі.");
            return;
        }

        statusBarLabel.setText("Порівняння схем: " + sourceSchema.getName() + " та " + targetSchema.getName() + "...");
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
            if (compareSchemasMenuItem != null) compareSchemasMenuItem.setDisable(false);
            updateSchemaActionMenuItemsState();

            if (differences.isEmpty()) {
                showAlert(AlertType.INFORMATION, "Результат порівняння", "Відмінностей не знайдено",
                        "Схеми '" + sourceSchema.getName() + "' та '" + targetSchema.getName() + "' ідентичні.");
            } else {
                showComparisonResultsWindow(differences, sourceSchema.getName(), targetSchema.getName());
            }
        });
        comparisonTask.setOnFailed(event -> {
            handleTaskFailure(comparisonTask, "порівняння схем");
            if (compareSchemasMenuItem != null) compareSchemasMenuItem.setDisable(false);
            updateSchemaActionMenuItemsState();
        });
        comparisonTask.setOnCancelled(event -> {
            handleTaskCancellation("порівняння схем");
            if (compareSchemasMenuItem != null) compareSchemasMenuItem.setDisable(false);
            updateSchemaActionMenuItemsState();
        });
        new Thread(comparisonTask).start();
    }

    private String getSchemaDisplayName(Schema schema) {
        if (schema == null) return "N/A";
        String sourceInfo = "N/A";
        if (schema.getCurrentSourceIdentifier() != null) {
            if (schema.getCurrentSourceIdentifier().startsWith("DB::")) {
                sourceInfo = "DB: " + schema.getName();
                if (schema.getSourceConnection() != null) {
                    sourceInfo = "DB: " + schema.getSourceConnection().getName() + "/" + schema.getName();
                }
            } else if (schema.getCurrentSourceIdentifier().startsWith("DIR::")) {
                Path dirPath = Paths.get(schema.getCurrentSourceIdentifier().substring(5));
                sourceInfo = "DIR: " + dirPath.getFileName().toString();
            }
        }
        return schema.getName() + " (" + sourceInfo + ", ID: " + schema.getId().substring(0, 8) + "...)";
    }

    private Schema findSchemaByDisplayName(String displayName, List<Schema> schemas) {
        for (Schema schema : schemas) {
            if (getSchemaDisplayName(schema).equals(displayName)) {
                return schema;
            }
        }
        return null;
    }

    @FXML
    private void handleSaveSchemaAction() {
        List<Schema> availableSchemas = schemaService.getAllSchemas();
        if (availableSchemas.isEmpty()) {
            showAlert(AlertType.WARNING, "Збереження схеми", "Немає доступних схем", "Спочатку витягніть або завантажте схему.");
            return;
        }

        List<String> schemaDisplayNames = availableSchemas.stream()
                .map(this::getSchemaDisplayName)
                .collect(Collectors.toList());

        ChoiceDialog<String> schemaChoiceDialog = new ChoiceDialog<>(null, schemaDisplayNames);
        schemaChoiceDialog.setTitle("Вибір схеми для збереження");
        schemaChoiceDialog.setHeaderText("Виберіть схему, яку потрібно зберегти:");
        schemaChoiceDialog.setContentText("Схема:");
        Optional<String> chosenSchemaResult = schemaChoiceDialog.showAndWait();

        if (chosenSchemaResult.isEmpty()) {
            statusBarLabel.setText("Збереження скасовано.");
            return;
        }

        final Schema schemaToSave = findSchemaByDisplayName(chosenSchemaResult.get(), availableSchemas);
        if (schemaToSave == null) {
            showAlert(AlertType.ERROR, "Збереження схеми", "Помилка вибору", "Не вдалося знайти вибрану схему.");
            return;
        }

        // Формуємо запропоновану назву директорії
        String proposedDirName = schemaToSave.getName().replaceAll("[^a-zA-Z0-9_.-]", "_") +
                "_" +
                schemaToSave.getExtractionTimestamp().format(DIRECTORY_TIMESTAMP_FORMATTER);
        if (schemaToSave.getSourceConnection() != null && schemaToSave.getSourceConnection().getName() != null) {
            proposedDirName = schemaToSave.getSourceConnection().getName().replaceAll("[^a-zA-Z0-9_.-]", "_") + "_" + proposedDirName;
        } else if (schemaToSave.getCurrentSourceIdentifier() != null && schemaToSave.getCurrentSourceIdentifier().startsWith("DIR::")) {
            Path dirPath = Paths.get(schemaToSave.getCurrentSourceIdentifier().substring(5));
            // Використовуємо назву директорії, з якої було завантажено, як частину запропонованої назви
            // Це може бути корисно, якщо користувач хоче "перезберегти" завантажену з файлів схему під новою міткою часу
            proposedDirName = dirPath.getFileName().toString().replaceAll("[^a-zA-Z0-9_.-]", "_") + "_SAVED_" +
                    schemaToSave.getExtractionTimestamp().format(DIRECTORY_TIMESTAMP_FORMATTER);
        }


        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/depavlo/ddlschematorfx/view/SaveSchemaDialog.fxml"));
            // Важливо: тип кореневого елемента SaveSchemaDialog.fxml (VBox)
            VBox page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Параметри збереження схеми");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(primaryStage);
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);

            SaveSchemaDialogController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setProposedSchemaDirectoryName(proposedDirName);

            // Встановлюємо початкову базову директорію (остання використана)
            Preferences prefs = Preferences.userNodeForPackage(MainWindowController.class);
            String lastUsedBaseDir = prefs.get(PREF_LAST_SAVE_BASE_DIR, null);
            if (lastUsedBaseDir != null) {
                File initialDir = new File(lastUsedBaseDir);
                if (initialDir.isDirectory()) {
                    controller.setInitialBaseDirectory(initialDir);
                }
            }

            dialogStage.showAndWait();

            if (controller.isSaveClicked()) {
                final String finalSchemaDirectoryName = controller.getSchemaDirectoryName();
                final Path baseDirectoryPath = controller.getSelectedBaseDirectoryPath();

                if (finalSchemaDirectoryName == null || finalSchemaDirectoryName.trim().isEmpty() || baseDirectoryPath == null) {
                    showAlert(AlertType.ERROR, "Збереження схеми", "Неповні дані", "Назва директорії схеми та базова директорія мають бути вказані.");
                    return;
                }

                // Зберігаємо останню використану базову директорію
                prefs.put(PREF_LAST_SAVE_BASE_DIR, baseDirectoryPath.toString());


                statusBarLabel.setText("Збереження схеми '" + schemaToSave.getName() + "'...");
                if (saveSchemaMenuItem != null) saveSchemaMenuItem.setDisable(true);

                Task<Void> saveTask = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        schemaService.saveSchemaToFile(schemaToSave, baseDirectoryPath, finalSchemaDirectoryName);
                        return null;
                    }
                };

                saveTask.setOnSucceeded(event -> {
                    statusBarLabel.setText("Схему '" + schemaToSave.getName() + "' успішно збережено у '" + finalSchemaDirectoryName + "'.");
                    if (saveSchemaMenuItem != null) saveSchemaMenuItem.setDisable(false);
                    updateSchemaActionMenuItemsState();
                    showAlert(AlertType.INFORMATION, "Збереження схеми", "Успіх", "Схему '" + schemaToSave.getName() + "' успішно збережено в директорію:\n" + baseDirectoryPath.resolve(finalSchemaDirectoryName).toString());
                });
                saveTask.setOnFailed(event -> {
                    handleTaskFailure(saveTask, "збереження схеми");
                    if (saveSchemaMenuItem != null) saveSchemaMenuItem.setDisable(false);
                    updateSchemaActionMenuItemsState();
                });
                saveTask.setOnCancelled(event -> {
                    handleTaskCancellation("збереження схеми");
                    if (saveSchemaMenuItem != null) saveSchemaMenuItem.setDisable(false);
                    updateSchemaActionMenuItemsState();
                });
                new Thread(saveTask).start();
            } else {
                statusBarLabel.setText("Збереження скасовано.");
            }

        } catch (IOException e) {
            e.printStackTrace();
            showAlert(AlertType.ERROR, "Помилка збереження", "Не вдалося відкрити діалог збереження", "Сталася помилка: " + e.getMessage());
        }
    }


    private void showComparisonResultsWindow(List<Difference> differences, String sourceSchemaName, String targetSchemaName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/depavlo/ddlschematorfx/view/ComparisonResultsView.fxml"));
            AnchorPane page = loader.load();

            Stage resultsStage = new Stage();
            resultsStage.setTitle("Результати порівняння схем");
            resultsStage.initModality(Modality.WINDOW_MODAL);
            resultsStage.initOwner(primaryStage);
            Scene scene = new Scene(page);
            resultsStage.setScene(scene);

            ComparisonResultsController controller = loader.getController();
            controller.setDialogStage(resultsStage);
            controller.setDifferences(differences, sourceSchemaName, targetSchemaName);

            resultsStage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert(AlertType.ERROR, "Помилка відображення", "Не вдалося відкрити вікно результатів",
                    "Сталася помилка: " + e.getMessage());
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

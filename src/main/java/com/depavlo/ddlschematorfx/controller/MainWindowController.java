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
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
// import javafx.scene.control.ListCell; // Закоментовано, якщо ConnectionDetailsListCell не використовується
import javafx.scene.control.MenuItem;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
// import java.sql.SQLException; // Не використовується напряму
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

public class MainWindowController {

    @FXML
    private Label statusBarLabel;
    @FXML
    private MenuItem compareSchemasMenuItem;
    @FXML
    private MenuItem saveSchemaMenuItem; // "Зберегти схему як..."
    @FXML
    private MenuItem saveSchemaDirectMenuItem; // "Зберегти"
    @FXML
    private MenuItem extractSchemaMenuItem;

    private Stage primaryStage;
    private ConnectionConfigManager connectionConfigManager;
    private SchemaService schemaService;
    private SchemaComparisonService schemaComparisonService;

    private static final DateTimeFormatter DIRECTORY_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String PREF_LAST_SAVE_BASE_DIR = "lastSaveBaseDir";
    private static final String PREF_LAST_LOAD_DIR = "lastLoadBaseDir";

    private Schema activeSchema = null;


    @FXML
    private void initialize() {
        if (compareSchemasMenuItem != null) compareSchemasMenuItem.setDisable(true);
        if (saveSchemaMenuItem != null) saveSchemaMenuItem.setDisable(true);
        if (saveSchemaDirectMenuItem != null) saveSchemaDirectMenuItem.setDisable(true);
        if (extractSchemaMenuItem != null) extractSchemaMenuItem.setDisable(true);
        // Стан extractSchemaMenuItem буде оновлено в setConnectionConfigManager
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public void setConnectionConfigManager(ConnectionConfigManager connectionConfigManager) {
        this.connectionConfigManager = connectionConfigManager;
        if (extractSchemaMenuItem != null) {
            extractSchemaMenuItem.setDisable(this.connectionConfigManager == null || this.connectionConfigManager.loadConnections().isEmpty());
        }
    }

    public void setSchemaService(SchemaService schemaService) {
        this.schemaService = schemaService;
        updateSchemaActionMenuItemsState(); // Оновлюємо стан меню після ініціалізації сервісу
    }

    public void setSchemaComparisonService(SchemaComparisonService schemaComparisonService) {
        this.schemaComparisonService = schemaComparisonService;
    }

    private void setActiveSchema(Schema schema) {
        this.activeSchema = schema;
        updateSchemaActionMenuItemsState();
    }

    private void updateSchemaActionMenuItemsState() {
        List<Schema> allSchemas = (schemaService != null) ? schemaService.getAllSchemas() : new ArrayList<>();
        boolean activeSchemaAvailable = activeSchema != null;

        if (compareSchemasMenuItem != null) {
            compareSchemasMenuItem.setDisable(allSchemas.size() < 2);
        }
        if (saveSchemaMenuItem != null) { // "Зберегти як..."
            saveSchemaMenuItem.setDisable(!activeSchemaAvailable); // Активно, якщо є активна схема
        }
        if (saveSchemaDirectMenuItem != null) { // "Зберегти"
            saveSchemaDirectMenuItem.setDisable(!(activeSchemaAvailable && activeSchema.getLastSavedPath() != null));
        }
        // Стан extractSchemaMenuItem оновлюється в setConnectionConfigManager та handleDbSettings
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
            if (extractSchemaMenuItem != null) {
                extractSchemaMenuItem.setDisable(this.connectionConfigManager == null || this.connectionConfigManager.loadConnections().isEmpty());
            }
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(AlertType.ERROR, "Помилка", "Помилка завантаження вікна", "Виникла помилка: " + e.getMessage());
        }
    }

    @FXML
    private void handleExtractSchema() {
        if (connectionConfigManager == null) {
            showAlert(AlertType.ERROR, "Помилка конфігурації", "Менеджер конфігурацій не ініціалізовано.", null);
            return;
        }
        List<ConnectionDetails> connections = connectionConfigManager.loadConnections();
        if (connections.isEmpty()) {
            showAlert(AlertType.INFORMATION, "Витягнення схеми", "Немає налаштованих підключень", "Будь ласка, спочатку налаштуйте підключення до бази даних.");
            return;
        }

        List<String> connectionNames = connections.stream().map(ConnectionDetails::getName).collect(Collectors.toList());
        ChoiceDialog<String> dialog = new ChoiceDialog<>(null, connectionNames);
        dialog.setTitle("Вибір підключення");
        dialog.setHeaderText("Виберіть підключення для витягнення схеми:");
        dialog.setContentText("Підключення:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(selectedName -> {
            connections.stream()
                    .filter(c -> c.getName().equals(selectedName))
                    .findFirst()
                    .ifPresent(this::performSchemaExtraction);
        });
    }

    private void performSchemaExtraction(ConnectionDetails selectedConnection) {
        if (extractSchemaMenuItem != null) extractSchemaMenuItem.setDisable(true);
        statusBarLabel.setText("Витягнення схеми для " + selectedConnection.getSchemaName() + " з " + selectedConnection.getName() + "...");

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
            setActiveSchema(extractedSchema);
            statusBarLabel.setText("Схему '" + extractedSchema.getName() + "' успішно витягнуто/оновлено.");
            showAlert(AlertType.INFORMATION, "Витягнення схеми", "Успіх", "Схему '" + extractedSchema.getName() + "' витягнуто/оновлено!");
        });
        extractionTask.setOnFailed(event -> handleTaskFailure(extractionTask, "витягнення схеми"));
        extractionTask.setOnCancelled(event -> handleTaskCancellation("витягнення схеми"));

        extractionTask.runningProperty().addListener((obs, wasRunning, isRunning) -> {
            if (!isRunning) {
                if (extractSchemaMenuItem != null) extractSchemaMenuItem.setDisable(false);
                updateSchemaActionMenuItemsState();
            }
        });
        new Thread(extractionTask).start();
    }

    @FXML
    private void handleLoadSchemaFromDirectory() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Виберіть директорію зі збереженою схемою");
        Preferences prefs = Preferences.userNodeForPackage(MainWindowController.class);
        String lastUsedLoadDir = prefs.get(PREF_LAST_LOAD_DIR, null);
        if (lastUsedLoadDir != null) {
            File initialDir = new File(lastUsedLoadDir);
            if (initialDir.isDirectory()) directoryChooser.setInitialDirectory(initialDir);
        }

        File selectedDirectory = directoryChooser.showDialog(primaryStage);
        if (selectedDirectory != null) {
            prefs.put(PREF_LAST_LOAD_DIR, selectedDirectory.getAbsolutePath());
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
                loadedSchema.setLastSavedPath(schemaDirectoryPath);
                schemaService.addSchema(loadedSchema);
                setActiveSchema(loadedSchema);
                statusBarLabel.setText("Схему '" + loadedSchema.getName() + "' успішно завантажено/оновлено.");
                showAlert(AlertType.INFORMATION, "Завантаження схеми", "Успіх", "Схему '" + loadedSchema.getName() + "' завантажено/оновлено.");
            });
            loadTask.setOnFailed(event -> handleTaskFailure(loadTask, "завантаження схеми з директорії"));
            loadTask.setOnCancelled(event -> handleTaskCancellation("завантаження схеми з директорії"));

            loadTask.runningProperty().addListener((obs, wasRunning, isRunning) -> {
                if (!isRunning) {
                    updateSchemaActionMenuItemsState();
                }
            });
            new Thread(loadTask).start();
        } else {
            statusBarLabel.setText("Завантаження схеми скасовано.");
        }
    }

    @FXML
    private void handleSaveSchemaDirectAction() {
        if (activeSchema == null) {
            showAlert(AlertType.WARNING, "Збереження схеми", "Немає активної схеми", "Будь ласка, спочатку витягніть або завантажте схему.");
            return;
        }
        if (activeSchema.getLastSavedPath() == null) {
            showAlert(AlertType.WARNING, "Збереження схеми", "Шлях невідомий", "Ця схема ще не зберігалася. Будь ласка, використайте 'Зберегти схему як...'.");
            return;
        }

        final Path targetPath = activeSchema.getLastSavedPath();
        final Schema schemaToSave = activeSchema;

        statusBarLabel.setText("Збереження схеми '" + schemaToSave.getName() + "' у " + targetPath.getFileName() + "...");
        if (saveSchemaDirectMenuItem != null) saveSchemaDirectMenuItem.setDisable(true);
        if (saveSchemaMenuItem != null) saveSchemaMenuItem.setDisable(true);

        Task<Void> saveTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                schemaService.clearDirectory(targetPath);
                schemaService.saveSchemaToFile(schemaToSave, targetPath.getParent(), targetPath.getFileName().toString());
                return null;
            }
        };

        saveTask.setOnSucceeded(event -> {
            statusBarLabel.setText("Схему '" + schemaToSave.getName() + "' успішно збережено у '" + targetPath.getFileName() + "'.");
            showAlert(AlertType.INFORMATION, "Збереження схеми", "Успіх", "Схему '" + schemaToSave.getName() + "' успішно збережено.");
            // Оновлюємо currentSourceIdentifier, оскільки вміст за цим шляхом оновлено
            // OriginalSourceIdentifier залишається тим самим.
            // Важливо: `setCurrentSourceIdentifier` має бути методом в класі Schema, якщо його немає, треба додати.
            // Або, якщо `currentSourceIdentifier` завжди "DIR::шлях" для збережених, то це оновлення може бути не потрібне
            // якщо `SchemaService.saveSchemaToFile` вже правильно встановлює це в meta.properties.
            // Поточна логіка `SchemaService.saveSchemaToFile` вже зберігає правильний currentSourceIdentifier в meta.properties.
            // Тому тут оновлювати об'єкт в пам'яті не обов'язково, якщо він не буде знову завантажуватися з цього ж місця без перезапуску.
            // Але для консистентності стану в пам'яті, якщо цей об'єкт буде далі використовуватися, оновити можна:
            schemaToSave.setCurrentSourceIdentifier("DIR::" + targetPath.toAbsolutePath().toString());
            // setActiveSchema(schemaToSave); // Активна схема не змінилась, лише її стан (якщо currentSourceIdentifier є частиною її "активності")
            // updateSchemaActionMenuItemsState() викликається в runningProperty listener
        });
        saveTask.setOnFailed(event -> handleTaskFailure(saveTask, "збереження схеми (перезапис)"));
        saveTask.setOnCancelled(event -> handleTaskCancellation("збереження схеми (перезапис)"));

        saveTask.runningProperty().addListener((obs, wasRunning, isRunning) -> {
            if (!isRunning) {
                updateSchemaActionMenuItemsState();
            }
        });

        new Thread(saveTask).start();
    }

    @FXML
    private void handleSaveSchemaAction() { // "Зберегти схему як..."
        Schema schemaToSave = activeSchema;
        if (schemaToSave == null) {
            List<Schema> availableSchemas = schemaService.getAllSchemas();
            if (availableSchemas.isEmpty()) {
                showAlert(AlertType.WARNING, "Збереження схеми", "Немає доступних схем", null);
                return;
            }
            if (availableSchemas.size() == 1) {
                schemaToSave = availableSchemas.get(0);
            } else {
                List<String> schemaDisplayNames = availableSchemas.stream().map(this::getSchemaDisplayName).collect(Collectors.toList());
                ChoiceDialog<String> schemaChoiceDialog = new ChoiceDialog<>(null, schemaDisplayNames);
                schemaChoiceDialog.setTitle("Вибір схеми для збереження");
                schemaChoiceDialog.setHeaderText("Виберіть схему, яку потрібно зберегти:");
                schemaChoiceDialog.setContentText("Схема:");
                Optional<String> chosenSchemaResult = schemaChoiceDialog.showAndWait();

                if (chosenSchemaResult.isEmpty()) {
                    statusBarLabel.setText("Збереження скасовано.");
                    return;
                }
                schemaToSave = findSchemaByDisplayName(chosenSchemaResult.get(), availableSchemas);
            }
        }

        if (schemaToSave == null) {
            showAlert(AlertType.ERROR, "Збереження схеми", "Помилка вибору", "Не вдалося визначити схему для збереження.");
            return;
        }
        final Schema finalSchemaToSave = schemaToSave;
        // setActiveSchema(finalSchemaToSave); // Встановлюємо активну схему вже після успішного збереження "як"

        String proposedDirName;
        // Якщо схема вже має lastSavedPath (була збережена або завантажена з файлів),
        // пропонуємо назву цієї директорії для "Зберегти як..."
        if (finalSchemaToSave.getLastSavedPath() != null) {
            proposedDirName = finalSchemaToSave.getLastSavedPath().getFileName().toString();
        } else {
            // Для нових схем (з БД, ще не збережених) або якщо lastSavedPath чомусь null
            proposedDirName = finalSchemaToSave.getName().replaceAll("[^a-zA-Z0-9_.-]", "_") +
                    "_" +
                    finalSchemaToSave.getExtractionTimestamp().format(DIRECTORY_TIMESTAMP_FORMATTER);
            if (finalSchemaToSave.getSourceConnection() != null && finalSchemaToSave.getSourceConnection().getName() != null) {
                proposedDirName = finalSchemaToSave.getSourceConnection().getName().replaceAll("[^a-zA-Z0-9_.-]", "_") + "_" + proposedDirName;
            }
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/depavlo/ddlschematorfx/view/SaveSchemaDialog.fxml"));
            VBox page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Параметри збереження схеми (Зберегти як...)");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(primaryStage);
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);

            SaveSchemaDialogController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setProposedSchemaDirectoryName(proposedDirName);

            Preferences prefs = Preferences.userNodeForPackage(MainWindowController.class);
            String lastUsedBaseDir = prefs.get(PREF_LAST_SAVE_BASE_DIR, null);
            if (lastUsedBaseDir != null) {
                File initialDir = new File(lastUsedBaseDir);
                if (initialDir.isDirectory()) controller.setInitialBaseDirectory(initialDir);
            }

            dialogStage.showAndWait();

            if (controller.isSaveClicked()) {
                final String finalSchemaDirectoryName = controller.getSchemaDirectoryName();
                final Path baseDirectoryPath = controller.getSelectedBaseDirectoryPath();

                if (finalSchemaDirectoryName == null || finalSchemaDirectoryName.trim().isEmpty() || baseDirectoryPath == null) {
                    showAlert(AlertType.ERROR, "Збереження схеми", "Неповні дані", "Назва директорії та базова директорія мають бути вказані.");
                    return;
                }

                final Path targetSchemaDir = baseDirectoryPath.resolve(finalSchemaDirectoryName);

                if (Files.exists(targetSchemaDir)) {
                    Alert confirmationDialog = new Alert(AlertType.CONFIRMATION);
                    confirmationDialog.setTitle("Підтвердження перезапису");
                    confirmationDialog.setHeaderText("Директорія '" + finalSchemaDirectoryName + "' вже існує у вибраному місці.");
                    confirmationDialog.setContentText("Ви впевнені, що хочете видалити її вміст та зберегти поточну схему?");

                    Optional<ButtonType> confirmationResult = confirmationDialog.showAndWait();
                    if (confirmationResult.isEmpty() || confirmationResult.get() != ButtonType.OK) {
                        statusBarLabel.setText("Збереження скасовано користувачем (відмова від перезапису).");
                        return;
                    }
                }

                prefs.put(PREF_LAST_SAVE_BASE_DIR, baseDirectoryPath.toString());

                statusBarLabel.setText("Збереження схеми '" + finalSchemaToSave.getName() + "' як '" + finalSchemaDirectoryName + "'...");
                if (saveSchemaMenuItem != null) saveSchemaMenuItem.setDisable(true);
                if (saveSchemaDirectMenuItem != null) saveSchemaDirectMenuItem.setDisable(true);

                Task<Void> saveTask = new Task<>() {
                    @Override
                    protected Void call() throws Exception {
                        if (Files.exists(targetSchemaDir)) {
                            schemaService.clearDirectory(targetSchemaDir);
                        }
                        schemaService.saveSchemaToFile(finalSchemaToSave, baseDirectoryPath, finalSchemaDirectoryName);
                        return null;
                    }
                };

                saveTask.setOnSucceeded(event -> {
                    finalSchemaToSave.setLastSavedPath(targetSchemaDir);
                    // Оновлюємо currentSourceIdentifier об'єкта в пам'яті, оскільки це нове місце збереження
                    finalSchemaToSave.setCurrentSourceIdentifier("DIR::" + targetSchemaDir.toAbsolutePath().toString());
                    // OriginalSourceIdentifier залишається тим, яким він був до цієї операції "Зберегти як..."
                    // або встановлюється як current, якщо original був null
                    if (finalSchemaToSave.getOriginalSourceIdentifier() == null ||
                            finalSchemaToSave.getOriginalSourceIdentifier().startsWith("DB::") || // Якщо оригінал був з БД
                            !finalSchemaToSave.getOriginalSourceIdentifier().equals(finalSchemaToSave.getCurrentSourceIdentifier())) { // або якщо це дійсно нове місце
                        // Якщо originalSourceIdentifier не був встановлений або був з БД,
                        // то нове місце стає і original (для майбутніх завантажень з цього місця).
                        // Або якщо ми зберігаємо в дійсно нове місце, то original не міняємо, він зберігає первинне джерело.
                        // Поточна логіка Schema.java та SchemaService.saveSchemaToFile вже має це враховувати
                        // при записі originalSourceIdentifier в meta.properties.
                        // Тут головне оновити currentSourceIdentifier і lastSavedPath.
                    }

                    setActiveSchema(finalSchemaToSave);
                    statusBarLabel.setText("Схему '" + finalSchemaToSave.getName() + "' успішно збережено як '" + finalSchemaDirectoryName + "'.");
                    showAlert(AlertType.INFORMATION, "Збереження схеми", "Успіх", "Схему збережено в:\n" + targetSchemaDir.toString());
                });
                saveTask.setOnFailed(event -> handleTaskFailure(saveTask, "збереження схеми (як)"));
                saveTask.setOnCancelled(event -> handleTaskCancellation("збереження схеми (як)"));

                saveTask.runningProperty().addListener((obs, wasRunning, isRunning) -> {
                    if (!isRunning) {
                        updateSchemaActionMenuItemsState();
                    }
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

        String defaultSource = (activeSchema != null && availableSchemas.contains(activeSchema)) ? getSchemaDisplayName(activeSchema) : null;
        ChoiceDialog<String> sourceDialog = new ChoiceDialog<>(defaultSource, schemaDisplayNames);
        sourceDialog.setTitle("Вибір схеми-джерела");
        sourceDialog.setHeaderText("Виберіть першу схему (джерело) для порівняння:");
        sourceDialog.setContentText("Схема-джерело:");
        Optional<String> sourceResult = sourceDialog.showAndWait();

        if (sourceResult.isEmpty()) {
            statusBarLabel.setText("Порівняння скасовано.");
            return;
        }
        final Schema sourceSchema = findSchemaByDisplayName(sourceResult.get(), availableSchemas);
        if(sourceSchema != null) setActiveSchema(sourceSchema);

        List<String> targetSchemaDisplayNames = schemaDisplayNames.stream()
                .filter(name -> !name.equals(sourceResult.get()))
                .collect(Collectors.toList());
        if (targetSchemaDisplayNames.isEmpty() && availableSchemas.size() >=2) {
            targetSchemaDisplayNames.add(schemaDisplayNames.stream().filter(name -> !name.equals(sourceResult.get())).findFirst().orElse(null));
        }

        ChoiceDialog<String> targetDialog = new ChoiceDialog<>(null, targetSchemaDisplayNames);
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
            if (differences.isEmpty()) {
                showAlert(AlertType.INFORMATION, "Результат порівняння", "Відмінностей не знайдено",
                        "Схеми '" + sourceSchema.getName() + "' та '" + targetSchema.getName() + "' ідентичні.");
            } else {
                showComparisonResultsWindow(differences, sourceSchema.getName(), targetSchema.getName());
            }
        });
        comparisonTask.setOnFailed(event -> handleTaskFailure(comparisonTask, "порівняння схем"));
        comparisonTask.setOnCancelled(event -> handleTaskCancellation("порівняння схем"));

        comparisonTask.runningProperty().addListener((obs, wasRunning, isRunning) -> {
            if (!isRunning) {
                if (compareSchemasMenuItem != null) compareSchemasMenuItem.setDisable(false);
                updateSchemaActionMenuItemsState();
            }
        });
        new Thread(comparisonTask).start();
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
        if (primaryStage != null && primaryStage.getScene() != null && primaryStage.getScene().getWindow() != null && primaryStage.getScene().getWindow().isShowing()) {
            showAlert(AlertType.ERROR, "Помилка операції", "Помилка: " + operationName, "Не вдалося виконати операцію: " + exception.getMessage());
        }
    }

    private void handleTaskCancellation(String operationName) {
        statusBarLabel.setText("Операцію '" + operationName + "' скасовано.");
        if (primaryStage != null && primaryStage.getScene() != null && primaryStage.getScene().getWindow() != null && primaryStage.getScene().getWindow().isShowing()) {
            showAlert(AlertType.WARNING, "Операцію скасовано", "Скасовано", "Операцію '" + operationName + "' було скасовано.");
        }
    }

    private void showAlert(AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private String getSchemaDisplayName(Schema schema) {
        if (schema == null) return "N/A";
        String sourceInfo = "N/A";
        if (schema.getCurrentSourceIdentifier() != null) {
            if (schema.getCurrentSourceIdentifier().startsWith("DB::")) {
                sourceInfo = "DB: " + schema.getName();
                if (schema.getSourceConnection() != null && schema.getSourceConnection().getName() != null) {
                    sourceInfo = "DB: " + schema.getSourceConnection().getName() + "/" + schema.getName();
                }
            } else if (schema.getCurrentSourceIdentifier().startsWith("DIR::")) {
                Path dirPath = Paths.get(schema.getCurrentSourceIdentifier().substring(5));
                sourceInfo = "DIR: " + dirPath.getFileName().toString();
            }
        }
        String schemaIdPart = (schema.getId() != null && schema.getId().length() >= 8) ? schema.getId().substring(0, 8) : "N/A";
        return schema.getName() + " (" + sourceInfo + ", ID: " + schemaIdPart + "...)";
    }

    private Schema findSchemaByDisplayName(String displayName, List<Schema> schemas) {
        if (displayName == null || schemas == null) return null;
        for (Schema schema : schemas) {
            if (schema != null && displayName.equals(getSchemaDisplayName(schema))) {
                return schema;
            }
        }
        return null;
    }
}

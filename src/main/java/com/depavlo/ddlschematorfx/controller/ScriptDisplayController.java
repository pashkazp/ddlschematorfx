package com.depavlo.ddlschematorfx.controller;

import com.depavlo.ddlschematorfx.model.MigrationScript;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.prefs.Preferences; // Для збереження останньої директорії

public class ScriptDisplayController {

    @FXML
    private ListView<MigrationScript> scriptListView;

    @FXML
    private Label scriptContentHeaderLabel;

    @FXML
    private TextArea scriptContentTextArea;

    @FXML
    private Button copyScriptButton;

    @FXML
    private Button saveAllScriptsButton;

    @FXML
    private Button closeButton;

    private Stage dialogStage;
    private ObservableList<MigrationScript> migrationScripts = FXCollections.observableArrayList();

    // Для збереження шляху останньої використаної директорії для збереження скриптів
    private static final String PREF_LAST_SAVE_SCRIPTS_DIR = "lastSaveScriptsDir";


    @FXML
    private void initialize() {
        // Налаштовуємо відображення об'єктів MigrationScript у ListView (показуємо ім'я файлу)
        scriptListView.setCellFactory(lv -> new javafx.scene.control.ListCell<MigrationScript>() {
            @Override
            protected void updateItem(MigrationScript script, boolean empty) {
                super.updateItem(script, empty);
                setText(empty || script == null ? null : script.getFileName());
            }
        });

        // Додаємо слухача для вибору елемента у ListView
        scriptListView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> showScriptContent(newValue));

        // Початково кнопка копіювання неактивна
        copyScriptButton.setDisable(true);
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    /**
     * Завантажує список згенерованих скриптів у вікно.
     * @param scripts Список об'єктів MigrationScript.
     */
    public void setMigrationScripts(List<MigrationScript> scripts) {
        migrationScripts.setAll(scripts);
        scriptListView.setItems(migrationScripts);

        if (!migrationScripts.isEmpty()) {
            scriptListView.getSelectionModel().selectFirst();
        } else {
            showScriptContent(null); // Очищаємо, якщо скриптів немає
            saveAllScriptsButton.setDisable(true); // Деактивуємо кнопку збереження, якщо немає скриптів
        }
    }

    /**
     * Відображає вміст вибраного скрипта.
     * @param script Вибраний MigrationScript або null.
     */
    private void showScriptContent(MigrationScript script) {
        if (script != null) {
            scriptContentHeaderLabel.setText("Вміст скрипта: " + script.getFileName());
            scriptContentTextArea.setText(script.getScriptContent());
            copyScriptButton.setDisable(false);
        } else {
            scriptContentHeaderLabel.setText("Вміст скрипта:");
            scriptContentTextArea.setText("");
            copyScriptButton.setDisable(true);
        }
    }

    @FXML
    private void handleCopyScript() {
        MigrationScript selectedScript = scriptListView.getSelectionModel().getSelectedItem();
        if (selectedScript != null && selectedScript.getScriptContent() != null) {
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            final ClipboardContent content = new ClipboardContent();
            content.putString(selectedScript.getScriptContent());
            clipboard.setContent(content);
            showAlert(Alert.AlertType.INFORMATION, "Копіювання", "Успіх", "Вміст скрипта '" + selectedScript.getFileName() + "' скопійовано до буфера обміну.");
        } else {
            showAlert(Alert.AlertType.WARNING, "Копіювання", "Помилка", "Не вибрано скрипт або вміст скрипта порожній.");
        }
    }

    @FXML
    private void handleSaveAllScripts() {
        if (migrationScripts.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Збереження скриптів", "Немає скриптів", "Немає згенерованих скриптів для збереження.");
            return;
        }

        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Виберіть директорію для збереження всіх скриптів");

        Preferences prefs = Preferences.userNodeForPackage(ScriptDisplayController.class);
        String lastUsedDir = prefs.get(PREF_LAST_SAVE_SCRIPTS_DIR, null);
        if (lastUsedDir != null) {
            File initialDir = new File(lastUsedDir);
            if (initialDir.isDirectory()) {
                directoryChooser.setInitialDirectory(initialDir);
            }
        }


        File selectedDirectory = directoryChooser.showDialog(dialogStage);

        if (selectedDirectory != null) {
            prefs.put(PREF_LAST_SAVE_SCRIPTS_DIR, selectedDirectory.getAbsolutePath()); // Зберігаємо останню директорію
            Path basePath = selectedDirectory.toPath();
            int savedCount = 0;
            int errorCount = 0;

            for (MigrationScript script : migrationScripts) {
                Path filePath = basePath.resolve(script.getFileName());
                try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
                    writer.write(script.getScriptContent());
                    savedCount++;
                } catch (IOException e) {
                    errorCount++;
                    System.err.println("Помилка збереження файлу " + script.getFileName() + ": " + e.getMessage());
                    // Можна додати логування або більш детальне повідомлення про помилку
                }
            }

            if (errorCount == 0) {
                showAlert(Alert.AlertType.INFORMATION, "Збереження скриптів", "Успіх", savedCount + " скриптів успішно збережено у директорію:\n" + basePath.toString());
            } else {
                showAlert(Alert.AlertType.WARNING, "Збереження скриптів", "Завершено з помилками",
                        "Успішно збережено: " + savedCount + " скриптів.\n" +
                                "Не вдалося зберегти: " + errorCount + " скриптів.\n" +
                                "Деталі помилок дивіться в консолі.");
            }
        }
    }

    @FXML
    private void handleClose() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.initOwner(dialogStage); // Встановлюємо власника для діалогових вікон
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}

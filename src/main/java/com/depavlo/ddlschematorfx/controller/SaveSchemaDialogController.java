package com.depavlo.ddlschematorfx.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SaveSchemaDialogController {

    @FXML
    private TextField schemaDirectoryNameField;

    @FXML
    private TextField baseDirectoryPathField;

    @FXML
    private Button browseBaseDirectoryButton;

    @FXML
    private Button saveButton;

    @FXML
    private Button cancelButton;

    private Stage dialogStage;
    private boolean saveClicked = false;
    private Path selectedBaseDirectoryPath;

    @FXML
    private void initialize() {
        // Можна додати слухачів або початкові налаштування тут, якщо потрібно
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    /**
     * Встановлює запропоновану назву для директорії схеми.
     * @param proposedName Запропонована назва.
     */
    public void setProposedSchemaDirectoryName(String proposedName) {
        schemaDirectoryNameField.setText(proposedName);
    }

    /**
     * Встановлює початкову базову директорію (якщо є, наприклад, остання використана).
     * @param initialBaseDir Початкова базова директорія.
     */
    public void setInitialBaseDirectory(File initialBaseDir) {
        if (initialBaseDir != null && initialBaseDir.isDirectory()) {
            selectedBaseDirectoryPath = initialBaseDir.toPath();
            baseDirectoryPathField.setText(selectedBaseDirectoryPath.toString());
        }
    }


    @FXML
    private void handleBrowseBaseDirectory() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Виберіть базову директорію");

        // Встановлюємо початкову директорію для DirectoryChooser, якщо вона вже була вибрана
        if (selectedBaseDirectoryPath != null) {
            File initialDir = selectedBaseDirectoryPath.toFile();
            if (initialDir.isDirectory()) {
                directoryChooser.setInitialDirectory(initialDir);
            }
        } else {
            // Можна встановити якусь директорію за замовчуванням, наприклад, домашню директорію користувача
            String userHome = System.getProperty("user.home");
            if (userHome != null) {
                File homeDir = new File(userHome);
                if (homeDir.isDirectory()) {
                    directoryChooser.setInitialDirectory(homeDir);
                }
            }
        }


        File chosenDir = directoryChooser.showDialog(dialogStage);
        if (chosenDir != null) {
            selectedBaseDirectoryPath = chosenDir.toPath();
            baseDirectoryPathField.setText(selectedBaseDirectoryPath.toString());
        }
    }

    @FXML
    private void handleSave() {
        if (isInputValid()) {
            saveClicked = true;
            dialogStage.close();
        }
    }

    @FXML
    private void handleCancel() {
        dialogStage.close();
    }

    /**
     * Перевіряє, чи користувач натиснув "Зберегти".
     * @return true, якщо натиснуто "Зберегти", інакше false.
     */
    public boolean isSaveClicked() {
        return saveClicked;
    }

    /**
     * Повертає введену назву директорії схеми.
     * @return Назва директорії схеми.
     */
    public String getSchemaDirectoryName() {
        return schemaDirectoryNameField.getText().trim();
    }

    /**
     * Повертає вибраний шлях до базової директорії.
     * @return Path до базової директорії або null, якщо не вибрано.
     */
    public Path getSelectedBaseDirectoryPath() {
        return selectedBaseDirectoryPath;
    }

    /**
     * Валідація введених даних.
     * @return true, якщо дані валідні, інакше false.
     */
    private boolean isInputValid() {
        String errorMessage = "";

        if (schemaDirectoryNameField.getText() == null || schemaDirectoryNameField.getText().trim().isEmpty()) {
            errorMessage += "Назва директорії схеми не може бути порожньою!\n";
        }
        if (selectedBaseDirectoryPath == null) {
            errorMessage += "Необхідно вибрати базову директорію!\n";
        }

        if (errorMessage.isEmpty()) {
            return true;
        } else {
            // Показуємо повідомлення про помилку
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(dialogStage);
            alert.setTitle("Некоректні дані");
            alert.setHeaderText("Будь ласка, виправте некоректні поля");
            alert.setContentText(errorMessage);
            alert.showAndWait();
            return false;
        }
    }
}

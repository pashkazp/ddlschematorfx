package com.depavlo.ddlschematorfx.controller;

import com.depavlo.ddlschematorfx.model.Difference;
import com.depavlo.ddlschematorfx.model.DifferenceType;
import com.depavlo.ddlschematorfx.model.ObjectType;
import com.depavlo.ddlschematorfx.model.Schema;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;

import java.util.List;

public class ComparisonResultsController {

    @FXML
    private Label comparisonTitleLabel;

    @FXML
    private TableView<Difference> differencesTableView;

    @FXML
    private TableColumn<Difference, DifferenceType> diffTypeColumn;

    @FXML
    private TableColumn<Difference, ObjectType> objectTypeColumn;

    @FXML
    private TableColumn<Difference, String> objectNameColumn;

    @FXML
    private TableColumn<Difference, String> objectOwnerColumn;

    @FXML
    private TextArea sourceDdlTextArea;

    @FXML
    private TextArea targetDdlTextArea;

    private Stage dialogStage;
    private ObservableList<Difference> differencesData = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        // Ініціалізація колонок таблиці
        diffTypeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        objectTypeColumn.setCellValueFactory(new PropertyValueFactory<>("objectType"));
        objectNameColumn.setCellValueFactory(new PropertyValueFactory<>("objectName"));
        objectOwnerColumn.setCellValueFactory(new PropertyValueFactory<>("objectOwner"));

        // Встановлюємо дані в таблицю
        differencesTableView.setItems(differencesData);

        // Додаємо слухача для вибору рядка в таблиці
        differencesTableView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> showDifferenceDetails(newValue));

        // Очищаємо текстові області за замовчуванням
        showDifferenceDetails(null);
    }

    /**
     * Встановлює Stage (вікно) для цього контролера.
     * @param dialogStage Stage
     */
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    /**
     * Завантажує дані про відмінності та назви схем.
     * @param differences Список відмінностей.
     * @param sourceSchemaName Назва схеми-джерела.
     * @param targetSchemaName Назва цільової схеми.
     */
    public void setDifferences(List<Difference> differences, String sourceSchemaName, String targetSchemaName) {
        differencesData.setAll(differences);
        comparisonTitleLabel.setText("Результати порівняння: " + sourceSchemaName + " (Source) vs " + targetSchemaName + " (Target)");

        // Якщо є відмінності, вибираємо першу для відображення деталей
        if (!differencesData.isEmpty()) {
            differencesTableView.getSelectionModel().selectFirst();
        } else {
            showDifferenceDetails(null); // Очищаємо, якщо відмінностей немає
        }
    }

    /**
     * Відображає DDL для вибраної відмінності у текстових областях.
     * @param difference Об'єкт Difference або null, якщо нічого не вибрано.
     */
    private void showDifferenceDetails(Difference difference) {
        if (difference != null) {
            sourceDdlTextArea.setText(difference.getSourceDdl() != null ? difference.getSourceDdl() : "");
            targetDdlTextArea.setText(difference.getTargetDdl() != null ? difference.getTargetDdl() : "");
        } else {
            sourceDdlTextArea.setText("");
            targetDdlTextArea.setText("");
        }
    }

    // Можна додати метод для закриття вікна, якщо буде кнопка "Закрити"
    // @FXML
    // private void handleClose() {
    //     if (dialogStage != null) {
    //         dialogStage.close();
    //     }
    // }
}

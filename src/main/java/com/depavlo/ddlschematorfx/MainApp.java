package com.depavlo.ddlschematorfx; // Замініть на ваш пакет

import com.depavlo.ddlschematorfx.controller.DbSettingsController;
import javafx.application.Application;
import javafx.application.Platform; // Імпорт для Platform.exit()
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        // Завантаження головного FXML файлу інтерфейсу.
        // Переконайтеся, що файл src/main/resources/com/depavlo/ddlschematorfx/main.fxml існує
        // та містить валідний FXML вміст.
        FXMLLoader loader = new FXMLLoader(getClass().getResource("main.fxml"));
        Parent root = loader.load();

        // Якщо ви використовуєте контролер, відмінний від MainApp,
        // отримайте його тут, щоб передати дані або викликати методи.
        // Наприклад:
        // MainWindowController controller = loader.getController();
        // controller.setStage(primaryStage); // Якщо контролеру потрібен доступ до Stage

        Scene scene = new Scene(root);

        primaryStage.setTitle("DDL Schema Sync"); // Заголовок вікна
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // Обробник для пункту меню "Вихід" (або іншої дії виходу)
    // Цей метод викликається, коли користувач обирає пункт меню "Вихід"
    // завдяки атрибуту onAction="#handleExit" у main.fxml
    public void handleExit() {
        Platform.exit(); // Завершує роботу JavaFX додатку
    }
    // Додайте цей метод до MainApp.java (або вашого MainWindowController)
    @FXML
    private void handleDbSettings() {
        try {
            // Завантажуємо FXML файл вікна налаштувань
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("DbSettings.fxml")); // Переконайтеся, що шлях правильний
            AnchorPane page = loader.load();

            // Створюємо нове вікно (Stage) для діалогу
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Налаштування підключень до БД");
            // Можна встановити власника вікна, якщо потрібно
            // dialogStage.initOwner(primaryStage);
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);

            // Передаємо Stage до контролера, щоб він міг закрити вікно
            DbSettingsController controller = loader.getController();
            controller.setDialogStage(dialogStage);

            // TODO: Передати список збережених підключень до контролера
            // controller.setConnections(loadConnections());

            // Показуємо вікно та чекаємо, доки користувач його закриє
            dialogStage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace(); // Обробка помилки завантаження FXML
            // TODO: Показати повідомлення про помилку користувачеві
        }
    }
    public static void main(String[] args) {
        // Метод запуску JavaFX додатку.
        launch(args);
    }
}

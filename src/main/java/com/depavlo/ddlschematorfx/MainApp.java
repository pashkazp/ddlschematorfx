package com.depavlo.ddlschematorfx;

import com.depavlo.ddlschematorfx.controller.MainWindowController; // Імпорт контролера головного вікна
import com.depavlo.ddlschematorfx.persistence.ConnectionConfigManager; // Імпорт менеджера конфігурацій
import com.depavlo.ddlschematorfx.service.SchemaService; // Імпорт сервісу схем

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApp extends Application {

    // Створюємо екземпляри менеджерів та сервісів, які будуть доступні контролерам
    private final ConnectionConfigManager connectionConfigManager = new ConnectionConfigManager();
    private final SchemaService schemaService = new SchemaService();
    // TODO: Додати інші менеджери/сервіси (GitLab, Audit тощо)

    @Override
    public void start(Stage primaryStage) throws IOException {
        // Завантаження головного FXML файлу інтерфейсу.
        FXMLLoader loader = new FXMLLoader(getClass().getResource("main_window.fxml")); // Переконайтеся, що шлях правильний
        Parent root = loader.load();

        // Отримуємо контролер головного вікна
        MainWindowController mainWindowController = loader.getController();

        // Передаємо залежності до контролера
        mainWindowController.setPrimaryStage(primaryStage); // Передаємо головний Stage
        mainWindowController.setConnectionConfigManager(connectionConfigManager); // Передаємо менеджер конфігурацій
        mainWindowController.setSchemaService(schemaService); // Передаємо сервіс схем
        // TODO: Передати інші залежності

        Scene scene = new Scene(root);

        primaryStage.setTitle("DDL Schema Sync"); // Заголовок вікна
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

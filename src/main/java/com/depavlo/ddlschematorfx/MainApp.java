package com.depavlo.ddlschematorfx;

import com.depavlo.ddlschematorfx.controller.MainWindowController;
import com.depavlo.ddlschematorfx.persistence.ConnectionConfigManager;
import com.depavlo.ddlschematorfx.service.SchemaComparisonService; // Імпорт нового сервісу
import com.depavlo.ddlschematorfx.service.SchemaService;

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
    private final SchemaComparisonService schemaComparisonService = new SchemaComparisonService(); // Створюємо екземпляр

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("main_window.fxml"));
        Parent root = loader.load();

        MainWindowController mainWindowController = loader.getController();

        mainWindowController.setPrimaryStage(primaryStage);
        mainWindowController.setConnectionConfigManager(connectionConfigManager);
        mainWindowController.setSchemaService(schemaService);
        mainWindowController.setSchemaComparisonService(schemaComparisonService); // Передаємо новий сервіс

        Scene scene = new Scene(root);

        primaryStage.setTitle("DDL Schema Sync");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

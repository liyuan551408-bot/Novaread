
package com.scifi.reader;

import atlantafx.base.theme.NordDark;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

public class GuiMain extends Application {

    private Stage primaryStage;
    private Scene mainScene;

    // Global reader state
    private String currentBookId = null;
    private int currentPage = 1;
    private int totalPages = 1;
    private String userLearningGoal = "Deep analysis";
    private long pageLoadTime = System.currentTimeMillis();

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        Application.setUserAgentStylesheet(new NordDark().getUserAgentStylesheet());

        // Initialize databases: main data and covers
        DatabaseManager.initDatabase();
        DatabaseManager.initCoverDatabase(); // Initialize the cover database
        DatabaseManager.importBooksFromFolder("book");

        // Show the welcome screen
        navigateTo(new WelcomeScreen(this).build());

        primaryStage.setTitle("NovaRead - AI-powered deep reading and knowledge learning system");
        primaryStage.setScene(mainScene);
        primaryStage.show();
    }
    /**
     * Switches screens
     */
    public void navigateTo(Region targetScreen) {
        if (mainScene == null) {
            mainScene = new Scene(targetScreen, 1400, 850);
        } else {
            mainScene.setRoot(targetScreen);
        }
        primaryStage.setScene(mainScene);
    }

    /**
     * Runs an AI task and updates the TextArea asynchronously
     */
    public void runAiTask(java.util.function.Supplier<String> aiCall, TextArea resultArea) {
        resultArea.setText("......");
        new Thread(() -> {
            String result = aiCall.get();
            javafx.application.Platform.runLater(() -> resultArea.setText(result));
        }).start();
    }

    /**
     * Converts Color to a hex string
     */
    public String toHexString(javafx.scene.paint.Color color) {
        return String.format("#%02X%02X%02X",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255));
    }

    // -------- getter / setter for global state ----------
    public String getCurrentBookId() { return currentBookId; }
    public void setCurrentBookId(String currentBookId) { this.currentBookId = currentBookId; }
    public int getCurrentPage() { return currentPage; }
    public void setCurrentPage(int currentPage) { this.currentPage = currentPage; }
    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
    public String getUserLearningGoal() { return userLearningGoal; }
    public long getPageLoadTime() { return pageLoadTime; }
    public void setPageLoadTime(long pageLoadTime) { this.pageLoadTime = pageLoadTime; }
}
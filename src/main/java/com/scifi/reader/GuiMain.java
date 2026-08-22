
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

    // 阅读器需要的全局状态
    private String currentBookId = null;
    private int currentPage = 1;
    private int totalPages = 1;
    private String userLearningGoal = "深度解析";
    private long pageLoadTime = System.currentTimeMillis();

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        Application.setUserAgentStylesheet(new NordDark().getUserAgentStylesheet());

        // 初始化数据库（主数据库和封面数据库）
        DatabaseManager.initDatabase();
        DatabaseManager.initCoverDatabase(); // 新增：初始化封面数据库
        DatabaseManager.importBooksFromFolder("book");

        // 显示欢迎界面
        navigateTo(new WelcomeScreen(this).build());

        primaryStage.setTitle("NovaRead 新星阅读 - 基于AI驱动的智能深度阅读与知识学习系统 ");
        primaryStage.setScene(mainScene);
        primaryStage.show();
    }
    /**
     * 切换场景
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
     * 执行 AI 任务，异步更新 TextArea
     */
    public void runAiTask(java.util.function.Supplier<String> aiCall, TextArea resultArea) {
        resultArea.setText("......");
        new Thread(() -> {
            String result = aiCall.get();
            javafx.application.Platform.runLater(() -> resultArea.setText(result));
        }).start();
    }

    /**
     * 将 Color 转换为十六进制字符串
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
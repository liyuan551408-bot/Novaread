package com.scifi.reader;

import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

/**
 * 背单词界面，基于SRS间隔重复算法进行选择题考核
 */
public class VocabReviewScreen {
    private GuiMain app;

    public VocabReviewScreen(GuiMain app) {
        this.app = app;
    }

    public Region build() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1e293b;");

        HBox topBar = new HBox(20);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(20, 30, 20, 30));
        topBar.setStyle("-fx-background-color: #0f172a; -fx-border-color: transparent transparent #334155 transparent; -fx-border-width: 1px;");

        Button backBtn = new Button("返回书架", new FontIcon("fas-chevron-left"));
        backBtn.getStyleClass().addAll(Styles.FLAT, Styles.DANGER);
        backBtn.setOnAction(e -> app.navigateTo(new LibraryScreen(app).build()));

        Label title = new Label("神经元考核 ", new FontIcon("fas-microchip"));
        title.getStyleClass().add(Styles.TITLE_2);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("刷新题库", new FontIcon("fas-sync"));
        refreshBtn.getStyleClass().add(Styles.SUCCESS);

        topBar.getChildren().addAll(backBtn, title, spacer, refreshBtn);
        root.setTop(topBar);

        SplitPane splitPane = new SplitPane();
        splitPane.setStyle("-fx-background-color: transparent;");

        ListView<DatabaseManager.Flashcard> listView = new ListView<>();
        listView.getItems().addAll(DatabaseManager.getFlashcards());
        listView.setStyle("-fx-background-color: #1e293b; -fx-control-inner-background: #1e293b;");
        listView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(DatabaseManager.Flashcard item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String status = item.urgency >= 1.0 ? "🔴" : "🟢";
                    setText(status + " " + item.word);
                    setTextFill(Color.web("#f8fafc"));
                }
            }
        });

        VBox detailsBox = new VBox(30);
        detailsBox.setPadding(new Insets(50));
        detailsBox.setStyle("-fx-background-color: #0f172a;");
        detailsBox.setAlignment(Pos.TOP_CENTER);

        Label wordLabel = new Label("请选择左侧词条");
        wordLabel.setFont(Font.font("Segoe UI", 48));
        wordLabel.setTextFill(Color.web("#38bdf8"));

        VBox optionsBox = new VBox(15);
        optionsBox.setAlignment(Pos.CENTER_LEFT);
        optionsBox.setMaxWidth(600);
        ToggleGroup toggleGroup = new ToggleGroup();
        RadioButton optA = new RadioButton("A.");
        RadioButton optB = new RadioButton("B.");
        RadioButton optC = new RadioButton("C.");
        RadioButton optD = new RadioButton("D.");
        RadioButton[] options = {optA, optB, optC, optD};
        for (RadioButton opt : options) {
            opt.setToggleGroup(toggleGroup);
            opt.setTextFill(Color.web("#e2e8f0"));
            opt.setStyle("-fx-font-size: 16px;");
            opt.setWrapText(true);
        }
        optionsBox.getChildren().addAll(optA, optB, optC, optD);

        Button submitBtn = new Button("提交校验", new FontIcon("fas-check-circle"));
        submitBtn.getStyleClass().addAll(Styles.ACCENT, Styles.LARGE);

        Label feedbackLabel = new Label();
        feedbackLabel.setStyle("-fx-font-size: 16px;");
        feedbackLabel.setWrapText(true);

        detailsBox.getChildren().addAll(wordLabel, optionsBox, submitBtn, feedbackLabel);
        optionsBox.setVisible(false);
        submitBtn.setVisible(false);

        final String[] currentCorrectAnswer = {""};

        refreshBtn.setOnAction(e -> {
            int selectedIndex = listView.getSelectionModel().getSelectedIndex();
            listView.getItems().setAll(DatabaseManager.getFlashcards());
            if (selectedIndex >= 0 && selectedIndex < listView.getItems().size()) {
                listView.getSelectionModel().select(selectedIndex);
            }
            feedbackLabel.setText("题库已刷新。");
            feedbackLabel.setTextFill(Color.web("#a3be8c"));
        });

        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                wordLabel.setText(newVal.word);
                toggleGroup.selectToggle(null);
                feedbackLabel.setText("");
                submitBtn.setText("提交校验");
                if (submitBtn.getGraphic() instanceof FontIcon) {
                    ((FontIcon) submitBtn.getGraphic()).setIconLiteral("fas-check-circle");
                }
                optionsBox.setDisable(false);

                if (newVal.optA == null || newVal.optA.isEmpty() || newVal.optA.contains("生成失败")) {
                    optionsBox.setVisible(false);
                    submitBtn.setVisible(false);
                    feedbackLabel.setText("选项题库正在后台生成中...\n(如果等待超过 5 秒，请点击右上角的【刷新题库】按钮重试)");
                    feedbackLabel.setTextFill(Color.web("#fbbf24"));
                } else {
                    optA.setText(newVal.optA);
                    optB.setText(newVal.optB);
                    optC.setText(newVal.optC);
                    optD.setText(newVal.optD);
                    currentCorrectAnswer[0] = newVal.correctAns;

                    optionsBox.setVisible(true);
                    submitBtn.setVisible(true);
                    submitBtn.setDisable(false);
                }
            }
        });

        submitBtn.setOnAction(e -> {
            if ("下一词".equals(submitBtn.getText())) {
                listView.getItems().setAll(DatabaseManager.getFlashcards());
                if (!listView.getItems().isEmpty()) {
                    listView.getSelectionModel().select(0);
                } else {
                    submitBtn.setVisible(false);
                    optionsBox.setVisible(false);
                    wordLabel.setText("考核已清空");
                    feedbackLabel.setText("");
                }
                return;
            }

            DatabaseManager.Flashcard selected = listView.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            RadioButton selectedRb = (RadioButton) toggleGroup.getSelectedToggle();
            if (selectedRb == null) {
                feedbackLabel.setText("请先选择一个选项！");
                feedbackLabel.setTextFill(Color.web("#fbbf24"));
                return;
            }

            String userChoice = selectedRb.getText().substring(0, 1).toUpperCase();

            // 兼容数据库脏数据，提取正确答案字母
            String cleanCorrectAns = "A";
            if (currentCorrectAnswer[0] != null) {
                String temp = currentCorrectAnswer[0].replaceAll("[^A-Da-d]", "");
                if (!temp.isEmpty()) {
                    cleanCorrectAns = temp.substring(0, 1).toUpperCase();
                }
            }
            boolean isCorrect = userChoice.equals(cleanCorrectAns);

            DatabaseManager.submitReview(selected.word, isCorrect);

            if (isCorrect) {
                feedbackLabel.setText("正确！记忆链路已强化。\n已为您推迟该词的下次考核时间。");
                feedbackLabel.setTextFill(Color.web("#a3be8c"));
            } else {
                feedbackLabel.setText("错误！突触已断裂。\n正确答案应为：[" + cleanCorrectAns + "]\n记忆已重置，您可以切换到【生词本】查看具体语境。");
                feedbackLabel.setTextFill(Color.web("#bf616a"));
            }

            submitBtn.setText("下一词");
            if (submitBtn.getGraphic() instanceof FontIcon) {
                ((FontIcon) submitBtn.getGraphic()).setIconLiteral("fas-arrow-right");
            }
            optionsBox.setDisable(true);
        });

        splitPane.getItems().addAll(listView, detailsBox);
        splitPane.setDividerPositions(0.35);
        root.setCenter(splitPane);
        return root;
    }
}
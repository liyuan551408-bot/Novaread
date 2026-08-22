package com.scifi.reader;

import atlantafx.base.theme.Styles;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

/**
 * 生词本界面，展示所有生词，点击显示原文语境，可调用AI深度解析或删除
 */
public class VocabBookScreen {
    private GuiMain app;

    public VocabBookScreen(GuiMain app) {
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
        Label title = new Label("沉浸式生词本", new FontIcon("fas-book-open"));
        title.getStyleClass().add(Styles.TITLE_2);
        topBar.getChildren().addAll(backBtn, title);
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
                if (empty || item == null) setText(null);
                else {
                    setText(item.word);
                    setTextFill(Color.web("#f8fafc"));
                }
            }
        });

        VBox detailsBox = new VBox(20);
        detailsBox.setPadding(new Insets(30));
        detailsBox.setStyle("-fx-background-color: #0f172a;");

        Label wordLabel = new Label("请选择生词");
        wordLabel.getStyleClass().addAll(Styles.TITLE_1);
        wordLabel.setTextFill(Color.web("#38bdf8"));

        TextArea contextArea = new TextArea();
        contextArea.setWrapText(true);
        contextArea.setEditable(false);
        contextArea.setPrefRowCount(3);
        contextArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 16px;");

        HBox actionBox = new HBox(15);
        actionBox.setAlignment(Pos.CENTER_LEFT);
        Button aiResolveBtn = new Button("呼叫 AI 深度解析语境", new FontIcon("fas-brain"));
        aiResolveBtn.getStyleClass().addAll(Styles.ACCENT);
        Button deleteBtn = new Button("擦除此神经元", new FontIcon("fas-trash"));
        deleteBtn.getStyleClass().addAll(Styles.DANGER);
        actionBox.getChildren().addAll(aiResolveBtn, deleteBtn);
        actionBox.setVisible(false);

        TextArea aiExplainArea = new TextArea();
        aiExplainArea.setWrapText(true);
        aiExplainArea.setEditable(false);
        aiExplainArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-text-fill: #a3be8c;");
        VBox.setVgrow(aiExplainArea, Priority.ALWAYS);

        detailsBox.getChildren().addAll(wordLabel,
                new Label("【原始语境】", new FontIcon("fas-quote-left")),
                contextArea, actionBox, aiExplainArea);

        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                wordLabel.setText(newVal.word);
                String displayContext = newVal.context;
                if (displayContext != null) {
                    String[] sentences = displayContext.split("(?<=[.!?。！?])\\s*|\n+");
                    for (String sentence : sentences) {
                        if (sentence.toLowerCase().contains(newVal.word.toLowerCase())) {
                            displayContext = sentence.trim();
                            break;
                        }
                    }
                }
                String highlightContext = displayContext.replaceAll("(?i)(" + newVal.word + ")", "【 $1 】");
                contextArea.setText(highlightContext);
                aiExplainArea.setText("");
                actionBox.setVisible(true);
            } else {
                actionBox.setVisible(false);
            }
        });

        aiResolveBtn.setOnAction(e -> {
            DatabaseManager.Flashcard selected = listView.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            aiExplainArea.setText("正在解析中...");
            new Thread(() -> {
                String exp = AiClient.contextualLookup(selected.word, selected.context, false, app.getUserLearningGoal());
                Platform.runLater(() -> aiExplainArea.setText(exp));
            }).start();
        });

        deleteBtn.setOnAction(e -> {
            DatabaseManager.Flashcard selected = listView.getSelectionModel().getSelectedItem();
            if (selected == null) return;

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "确定要将【" + selected.word + "】从记忆网络中永久抹除吗？",
                    ButtonType.YES, ButtonType.NO);
            alert.setHeaderText("危险操作警报");
            DialogPane dialogPane = alert.getDialogPane();
            dialogPane.setStyle("-fx-background-color: #1e293b; -fx-text-fill: #f8fafc;");
            dialogPane.lookupAll(".label").forEach(node -> ((Label) node).setTextFill(Color.web("#f8fafc")));

            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    DatabaseManager.deleteFlashcard(selected.word);
                    int selectedIndex = listView.getSelectionModel().getSelectedIndex();
                    listView.getItems().setAll(DatabaseManager.getFlashcards());

                    if (listView.getItems().isEmpty()) {
                        wordLabel.setText("生词本已清空");
                        contextArea.clear();
                        aiExplainArea.clear();
                        actionBox.setVisible(false);
                    } else {
                        int newIndex = Math.min(selectedIndex, listView.getItems().size() - 1);
                        listView.getSelectionModel().select(newIndex);
                    }
                }
            });
        });

        splitPane.getItems().addAll(listView, detailsBox);
        splitPane.setDividerPositions(0.35);
        root.setCenter(splitPane);
        return root;
    }
}
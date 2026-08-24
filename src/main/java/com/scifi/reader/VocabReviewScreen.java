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
 * Vocabulary review screen using SRS interval repetition and multiple-choice quizzes
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

        Button backBtn = new Button("Back to Library", new FontIcon("fas-chevron-left"));
        backBtn.getStyleClass().addAll(Styles.FLAT, Styles.DANGER);
        backBtn.setOnAction(e -> app.navigateTo(new LibraryScreen(app).build()));

        Label title = new Label("Vocabulary Review", new FontIcon("fas-microchip"));
        title.getStyleClass().add(Styles.TITLE_2);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshBtn = new Button("Refresh Quiz Bank", new FontIcon("fas-sync"));
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
                    String status = item.urgency >= 1.0 ? "[Due]" : "[OK]";
                    setText(status + " " + item.word);
                    setTextFill(Color.web("#f8fafc"));
                }
            }
        });

        VBox detailsBox = new VBox(30);
        detailsBox.setPadding(new Insets(50));
        detailsBox.setStyle("-fx-background-color: #0f172a;");
        detailsBox.setAlignment(Pos.TOP_CENTER);

        Label wordLabel = new Label("Select a word on the left");
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

        Button submitBtn = new Button("Submit Answer", new FontIcon("fas-check-circle"));
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
            feedbackLabel.setText("Quiz bank refreshed.");
            feedbackLabel.setTextFill(Color.web("#a3be8c"));
        });

        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                wordLabel.setText(newVal.word);
                toggleGroup.selectToggle(null);
                feedbackLabel.setText("");
                submitBtn.setText("Submit Answer");
                if (submitBtn.getGraphic() instanceof FontIcon) {
                    ((FontIcon) submitBtn.getGraphic()).setIconLiteral("fas-check-circle");
                }
                optionsBox.setDisable(false);

                if (newVal.optA == null || newVal.optA.isEmpty() || newVal.optA.contains("generation failed")) {
                    optionsBox.setVisible(false);
                    submitBtn.setVisible(false);
                    feedbackLabel.setText("Quiz options are being generated in the background...\nIf this takes more than 5 seconds, click Refresh Quiz Bank and try again.");
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
            if ("Next Word".equals(submitBtn.getText())) {
                listView.getItems().setAll(DatabaseManager.getFlashcards());
                if (!listView.getItems().isEmpty()) {
                    listView.getSelectionModel().select(0);
                } else {
                    submitBtn.setVisible(false);
                    optionsBox.setVisible(false);
                    wordLabel.setText("Review queue is empty");
                    feedbackLabel.setText("");
                }
                return;
            }

            DatabaseManager.Flashcard selected = listView.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            RadioButton selectedRb = (RadioButton) toggleGroup.getSelectedToggle();
            if (selectedRb == null) {
                feedbackLabel.setText("Please choose an option first.");
                feedbackLabel.setTextFill(Color.web("#fbbf24"));
                return;
            }

            String userChoice = selectedRb.getText().substring(0, 1).toUpperCase();

            // Extract the answer letter defensively for legacy or dirty data
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
                feedbackLabel.setText("Correct. Memory strength increased.\nThe next review time has been pushed back.");
                feedbackLabel.setTextFill(Color.web("#a3be8c"));
            } else {
                feedbackLabel.setText("Incorrect.\nThe correct answer is: [" + cleanCorrectAns + "]\nMemory progress has been reset. Open Vocabulary Book to review the context.");
                feedbackLabel.setTextFill(Color.web("#bf616a"));
            }

            submitBtn.setText("Next Word");
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
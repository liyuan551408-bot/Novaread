package com.scifi.reader;

import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.List;

/**
 * Library screen with book cards, vocabulary entry points, and refresh support
 */
public class LibraryScreen {
    private GuiMain app;

    public LibraryScreen(GuiMain app) {
        this.app = app;
    }

    public Region build() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #1e293b;");

        HBox topBar = buildTopBar();
        FlowPane bookGrid = buildBookGrid();
        ScrollPane scrollPane = new ScrollPane(bookGrid);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent;");

        root.setTop(topBar);
        root.setCenter(scrollPane);
        return root;
    }

    private HBox buildTopBar() {
        HBox topBar = new HBox(20);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(20, 30, 20, 30));
        topBar.setStyle("-fx-background-color: #0f172a; -fx-border-color: transparent transparent #334155 transparent; -fx-border-width: 1px;");

        Label libTitle = new Label("My Library Matrix", new FontIcon("fas-layer-group"));
        libTitle.getStyleClass().addAll(Styles.TITLE_2);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button vocabBookBtn = new Button("Vocabulary Book", new FontIcon("fas-book-open"));
        vocabBookBtn.getStyleClass().add(Styles.ACCENT);
        vocabBookBtn.setOnAction(e -> app.navigateTo(new VocabBookScreen(app).build()));

        Button vocabReviewBtn = new Button("Review Words", new FontIcon("fas-brain"));
        vocabReviewBtn.getStyleClass().add(Styles.SUCCESS);
        vocabReviewBtn.setOnAction(e -> app.navigateTo(new VocabReviewScreen(app).build()));

        Button refreshBtn = new Button("Scan Local Folder", new FontIcon("fas-sync"));
        refreshBtn.getStyleClass().add(Styles.FLAT);
        refreshBtn.setOnAction(e -> {
            DatabaseManager.importBooksFromFolder("book");
            app.navigateTo(new LibraryScreen(app).build());
        });

        topBar.getChildren().addAll(libTitle, spacer, vocabBookBtn, vocabReviewBtn, refreshBtn);
        return topBar;
    }

    private FlowPane buildBookGrid() {
        FlowPane bookGrid = new FlowPane();
        bookGrid.setPadding(new Insets(30));
        bookGrid.setHgap(25);
        bookGrid.setVgap(30);
        bookGrid.setAlignment(Pos.TOP_LEFT);

        List<DatabaseManager.BookRank> rankedBooks = DatabaseManager.getRankedBooks();
        for (DatabaseManager.BookRank rank : rankedBooks) {
            bookGrid.getChildren().add(createBookCard(rank.bookId));
        }
        return bookGrid;
    }

    private VBox createBookCard(String bookId) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(15));
        card.getStyleClass().addAll(Styles.ELEVATED_2);
        card.setStyle("-fx-background-color: #334155; -fx-background-radius: 10px; -fx-cursor: hand;");
        card.setMaxWidth(160);

        // Create the cover container, either an ImageView or a placeholder
        ImageView coverView = new ImageView();
        coverView.setFitWidth(130);
        coverView.setFitHeight(180);
        Rectangle clip = new Rectangle(130, 180);
        clip.setArcWidth(10);
        clip.setArcHeight(10);
        coverView.setClip(clip);

        // Load the cover from the database first
        byte[] coverData = DatabaseManager.getCoverData(bookId);
        Image coverImage = null;
        if (coverData != null) {
            coverImage = new Image(new ByteArrayInputStream(coverData));
            coverView.setImage(coverImage);
            card.getChildren().add(coverView);
        } else {
            // If the database has no cover, try the file system
            String cleanTitle = DatabaseManager.getBookTitle(bookId);
            File imgJpg = new File("covers/" + cleanTitle + ".jpg");
            File imgPng = new File("covers/" + cleanTitle + ".png");
            if (imgJpg.exists()) {
                coverImage = new Image(imgJpg.toURI().toString());
                coverView.setImage(coverImage);
                card.getChildren().add(coverView);
                // Import into the database asynchronously to speed up the next startup
                new Thread(() -> DatabaseManager.importCoverFromFile(bookId, "covers/")).start();
            } else if (imgPng.exists()) {
                coverImage = new Image(imgPng.toURI().toString());
                coverView.setImage(coverImage);
                card.getChildren().add(coverView);
                new Thread(() -> DatabaseManager.importCoverFromFile(bookId, "covers/")).start();
            } else {
                // Show a placeholder when no cover exists
                VBox placeholder = new VBox();
                placeholder.setAlignment(Pos.CENTER);
                placeholder.setPrefSize(130, 180);
                placeholder.setStyle("-fx-background-color: linear-gradient(to bottom right, #475569, #1e293b); -fx-background-radius: 5px;");
                FontIcon bookIcon = new FontIcon("fas-book");
                bookIcon.setIconSize(50);
                bookIcon.setIconColor(Color.web("#94a3b8"));
                placeholder.getChildren().add(bookIcon);
                card.getChildren().add(placeholder);
            }
        }

        Label titleLabel = new Label(DatabaseManager.getBookTitle(bookId));
        titleLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #f8fafc;");
        titleLabel.setWrapText(true);
        titleLabel.setAlignment(Pos.CENTER);

        int lastPage = DatabaseManager.getLastReadPage(bookId);
        Label progressLabel = new Label("Read to page " + lastPage + "");
        progressLabel.getStyleClass().add(Styles.TEXT_MUTED);

        card.getChildren().addAll(titleLabel, progressLabel);
        card.setOnMouseClicked(e -> app.navigateTo(new ReaderScreen(app, bookId, lastPage).build()));
        card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: #475569; -fx-background-radius: 10px; -fx-cursor: hand;"));
        card.setOnMouseExited(e -> card.setStyle("-fx-background-color: #334155; -fx-background-radius: 10px; -fx-cursor: hand;"));
        return card;
    }
}
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
 * 书架界面，展示书籍卡片，提供生词本/背单词入口，支持刷新
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

        Label libTitle = new Label("我的矩阵书架", new FontIcon("fas-layer-group"));
        libTitle.getStyleClass().addAll(Styles.TITLE_2);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button vocabBookBtn = new Button("生词本", new FontIcon("fas-book-open"));
        vocabBookBtn.getStyleClass().add(Styles.ACCENT);
        vocabBookBtn.setOnAction(e -> app.navigateTo(new VocabBookScreen(app).build()));

        Button vocabReviewBtn = new Button("背单词", new FontIcon("fas-brain"));
        vocabReviewBtn.getStyleClass().add(Styles.SUCCESS);
        vocabReviewBtn.setOnAction(e -> app.navigateTo(new VocabReviewScreen(app).build()));

        Button refreshBtn = new Button("扫描本地目录", new FontIcon("fas-sync"));
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

        // 创建封面容器（ImageView 或占位符）
        ImageView coverView = new ImageView();
        coverView.setFitWidth(130);
        coverView.setFitHeight(180);
        Rectangle clip = new Rectangle(130, 180);
        clip.setArcWidth(10);
        clip.setArcHeight(10);
        coverView.setClip(clip);

        // 优先从数据库加载封面
        byte[] coverData = DatabaseManager.getCoverData(bookId);
        Image coverImage = null;
        if (coverData != null) {
            coverImage = new Image(new ByteArrayInputStream(coverData));
            coverView.setImage(coverImage);
            card.getChildren().add(coverView);
        } else {
            // 数据库无封面，尝试从文件系统加载
            String cleanTitle = DatabaseManager.getBookTitle(bookId);
            File imgJpg = new File("covers/" + cleanTitle + ".jpg");
            File imgPng = new File("covers/" + cleanTitle + ".png");
            if (imgJpg.exists()) {
                coverImage = new Image(imgJpg.toURI().toString());
                coverView.setImage(coverImage);
                card.getChildren().add(coverView);
                // 异步导入到数据库（可选，提升下次启动速度）
                new Thread(() -> DatabaseManager.importCoverFromFile(bookId, "covers/")).start();
            } else if (imgPng.exists()) {
                coverImage = new Image(imgPng.toURI().toString());
                coverView.setImage(coverImage);
                card.getChildren().add(coverView);
                new Thread(() -> DatabaseManager.importCoverFromFile(bookId, "covers/")).start();
            } else {
                // 无封面，显示占位符
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
        Label progressLabel = new Label("读至 第 " + lastPage + " 页");
        progressLabel.getStyleClass().add(Styles.TEXT_MUTED);

        card.getChildren().addAll(titleLabel, progressLabel);
        card.setOnMouseClicked(e -> app.navigateTo(new ReaderScreen(app, bookId, lastPage).build()));
        card.setOnMouseEntered(e -> card.setStyle("-fx-background-color: #475569; -fx-background-radius: 10px; -fx-cursor: hand;"));
        card.setOnMouseExited(e -> card.setStyle("-fx-background-color: #334155; -fx-background-radius: 10px; -fx-cursor: hand;"));
        return card;
    }
}
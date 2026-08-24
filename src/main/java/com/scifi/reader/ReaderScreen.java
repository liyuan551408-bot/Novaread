package com.scifi.reader;

import atlantafx.base.theme.Styles;
import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalTime;
import java.util.List;

public class ReaderScreen {
    private static final String SETTING_BG_COLOR = "reader_bg_color";
    private static final String SETTING_FONT_COLOR = "reader_font_color";
    private static final String SETTING_FONT_SIZE = "reader_font_size";

    private GuiMain app;
    private String bookId;
    private int startPage;

    private VBox centerPanel;
    private WebView webView;
    private WebEngine webEngine;
    private ColorPicker bgColorPicker;
    private ColorPicker fontColorPicker;
    private Spinner<Integer> fontSizeSpinner;
    private Label pageLabel;
    private TextArea aiResultArea;
    private WebView graphWebView;
    private WebEngine graphWebEngine;
    private String lastGraphJson = "{}";

    private ToggleButton autoScrollBtn;
    private Spinner<Integer> scrollSpeedSpinner;
    private AnimationTimer autoScrollTimer;
    private long lastScrollTime = 0;
    private double scrollAccumulator = 0.0;
    private boolean isWaitingToTurnPage = false;
    private long pageBottomReachTime = 0;

    private Timeline readingTimer;

    private final String GRAPH_HTML = "<!DOCTYPE html>\n" +
            "<html><head><meta charset='UTF-8'>\n" +
            "<script src='https://cdnjs.cloudflare.com/ajax/libs/cytoscape/3.28.1/cytoscape.min.js'></script>\n" +
            "<style>\n" +
            "  body { background-color: #1e293b; margin: 0; overflow: hidden; font-family: 'Segoe UI', sans-serif; }\n" +
            "  #cy { width: 100vw; height: 100vh; display: block; }\n" +
            "</style></head><body>\n" +
            "<div id='cy'></div>\n" +
            "<script>\n" +
            "  var cy = cytoscape({\n" +
            "    container: document.getElementById('cy'),\n" +
            "    style: [\n" +
            "      { selector: 'node', style: { 'background-color': '#38bdf8', 'label': 'data(label)', 'color': '#f8fafc', 'font-size': '12px', 'text-valign': 'center', 'text-halign': 'center', 'width': 'label', 'height': 'label', 'padding': '8px', 'shape': 'round-rectangle', 'border-width': 1, 'border-color': '#0ea5e9', 'text-wrap': 'wrap', 'text-max-width': '80px' } },\n" +
            "      { selector: 'edge', style: { 'width': 2, 'line-color': '#475569', 'target-arrow-color': '#475569', 'target-arrow-shape': 'triangle', 'curve-style': 'bezier', 'label': 'data(label)', 'color': '#cbd5e1', 'font-size': '10px', 'text-rotation': 'autorotate', 'text-background-color': '#1e293b', 'text-background-opacity': 1, 'text-background-padding': '2px' } }\n" +
            "    ],\n" +
            "    layout: { name: 'cose', animate: true }\n" +
            "  });\n" +
            "  function renderNewGraph(jsonData) {\n" +
            "    cy.elements().remove();\n" +
            "    try {\n" +
            "      cy.add(JSON.parse(jsonData));\n" +
            "      cy.layout({ name: 'cose', animate: true, idealEdgeLength: 100, nodeOverlap: 20, refresh: 20, fit: true, padding: 30, randomize: false, componentSpacing: 100, nodeRepulsion: 400000, edgeElasticity: 100, nestingFactor: 5 }).run();\n" +
            "    } catch (e) { console.error('Render Error:', e); }\n" +
            "  }\n" +
            "</script></body></html>";

    public ReaderScreen(GuiMain app, String bookId, int startPage) {
        this.app = app;
        this.bookId = bookId;
        this.startPage = startPage;
    }

    public Region build() {
        app.setCurrentBookId(bookId);
        BorderPane root = new BorderPane();

        HBox navBar = new HBox();
        navBar.setPadding(new Insets(10, 20, 10, 20));
        navBar.setStyle("-fx-background-color: #0f172a;");

        Button backBtn = new Button("Back to Library", new FontIcon("fas-chevron-left"));
        backBtn.getStyleClass().addAll(Styles.FLAT, Styles.DANGER);
        backBtn.setOnAction(e -> {
            if (autoScrollTimer != null) autoScrollTimer.stop();
            if (readingTimer != null) readingTimer.stop();
            DatabaseManager.updateProgress(app.getCurrentBookId(), app.getCurrentPage(),
                    (System.currentTimeMillis() - app.getPageLoadTime()) / 1000.0);
            app.navigateTo(new LibraryScreen(app).build());
        });
        navBar.getChildren().add(backBtn);
        root.setTop(navBar);

        centerPanel = new VBox(20);
        centerPanel.setPadding(new Insets(10, 25, 25, 25));
        HBox appearanceBar = buildAppearanceBar();

        webView = new WebView();
        webEngine = webView.getEngine();
        webView.setContextMenuEnabled(false);
        VBox.setVgrow(webView, Priority.ALWAYS);

        setupWebViewContextMenu();

        HBox paginationBox = buildPaginationBox();
        centerPanel.getChildren().addAll(appearanceBar, webView, paginationBox);
        root.setCenter(centerPanel);

        VBox rightPanel = buildRightPanel();
        root.setRight(rightPanel);

        readingTimer = new Timeline(new KeyFrame(Duration.seconds(10), e -> {
            if (app.getCurrentBookId() != null) {
                DatabaseManager.updateProgress(app.getCurrentBookId(), app.getCurrentPage(),
                        (System.currentTimeMillis() - app.getPageLoadTime()) / 1000.0);
                app.setPageLoadTime(System.currentTimeMillis());
            }
        }));
        readingTimer.setCycleCount(Animation.INDEFINITE);
        readingTimer.play();

        loadBook(bookId, startPage);
        return root;
    }

    private HBox buildAppearanceBar() {
        HBox bar = new HBox(15);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 0, 15, 0));
        bgColorPicker = new ColorPicker();
        fontColorPicker = new ColorPicker();
        fontSizeSpinner = new Spinner<>(12, 48, 18);
        fontSizeSpinner.setPrefWidth(80);

        // Load saved settings
        String savedBg = DatabaseManager.getSetting(SETTING_BG_COLOR, "#2A2A2A");
        String savedFontColor = DatabaseManager.getSetting(SETTING_FONT_COLOR, "#D4D4D4");
        int savedFontSize = Integer.parseInt(DatabaseManager.getSetting(SETTING_FONT_SIZE, "18"));

        bgColorPicker.setValue(Color.web(savedBg));
        fontColorPicker.setValue(Color.web(savedFontColor));
        fontSizeSpinner.getValueFactory().setValue(savedFontSize);

        bgColorPicker.setOnAction(e -> applyCustomAppearance());
        fontColorPicker.setOnAction(e -> applyCustomAppearance());
        fontSizeSpinner.valueProperty().addListener((obs, oldVal, newVal) -> applyCustomAppearance());

        bar.getChildren().addAll(
                new Label("Page:"), bgColorPicker,
                new Label("Text:"), fontColorPicker,
                new Label("Size:"), fontSizeSpinner
        );
        return bar;
    }

    private HBox buildPaginationBox() {
        HBox paginationBox = new HBox(15);
        paginationBox.setAlignment(Pos.CENTER);

        Button prevBtn = new Button("Previous Page", new FontIcon("fas-arrow-left"));
        Button nextBtn = new Button("Next Page");
        nextBtn.setGraphic(new FontIcon("fas-arrow-right"));
        nextBtn.setContentDisplay(ContentDisplay.RIGHT);
        prevBtn.getStyleClass().add(Styles.FLAT);
        nextBtn.getStyleClass().add(Styles.FLAT);

        pageLabel = new Label();
        pageLabel.getStyleClass().add(Styles.TEXT_MUTED);

        prevBtn.setOnAction(e -> {
            if (app.getCurrentPage() > 1) loadNextOrPrevPage(app.getCurrentPage() - 1);
        });
        nextBtn.setOnAction(e -> {
            if (app.getCurrentPage() < app.getTotalPages()) loadNextOrPrevPage(app.getCurrentPage() + 1);
        });

        autoScrollBtn = new ToggleButton("Auto Read", new FontIcon("fas-play"));
        autoScrollBtn.getStyleClass().addAll(Styles.ACCENT);
        scrollSpeedSpinner = new Spinner<>(1, 10, 3);
        scrollSpeedSpinner.setPrefWidth(70);
        Label speedLabel = new Label("Speed:");
        speedLabel.getStyleClass().add(Styles.TEXT_MUTED);

        setupWebAutoScrollLogic();

        paginationBox.getChildren().addAll(prevBtn, pageLabel, nextBtn,
                new Separator(Orientation.VERTICAL), autoScrollBtn, speedLabel, scrollSpeedSpinner);
        return paginationBox;
    }

    private VBox buildRightPanel() {
        VBox rightPanel = new VBox(15);
        rightPanel.setPadding(new Insets(20));
        rightPanel.setPrefWidth(420);
        rightPanel.setStyle("-fx-background-color: #1e293b; -fx-border-color: transparent transparent transparent #334155; -fx-border-width: 1px;");

        Label aiLabel = new Label("AI Command Center", new FontIcon("fas-brain"));
        aiLabel.getStyleClass().addAll(Styles.TITLE_3);

        HBox macroBox = new HBox(10);
        Button simplifyBtn = new Button("Simplify Text", new FontIcon("fas-magic"));
        simplifyBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(simplifyBtn, Priority.ALWAYS);
        Button authorBtn = new Button("Ask the Author", new FontIcon("fas-pen-nib"));
        authorBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(authorBtn, Priority.ALWAYS);
        macroBox.getChildren().addAll(simplifyBtn, authorBtn);

        HBox ragBox = new HBox(10);
        Button saveMemoryBtn = new Button("Save Memory", new FontIcon("fas-save"));
        saveMemoryBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(saveMemoryBtn, Priority.ALWAYS);
        Button sparkBtn = new Button("Scan Cross-Book Links", new FontIcon("fas-bolt"));
        sparkBtn.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(sparkBtn, Priority.ALWAYS);
        ragBox.getChildren().addAll(saveMemoryBtn, sparkBtn);

        Button toggleShelfBtn = new Button("Toggle Starred Shelf", new FontIcon("fas-star"));
        toggleShelfBtn.setMaxWidth(Double.MAX_VALUE);

        Label graphLabel = new Label("Dynamic Relationship Graph (select text and right-click)", new FontIcon("fas-project-diagram"));
        graphLabel.getStyleClass().add(Styles.TEXT_MUTED);

        Button expandBtn = new Button("", new FontIcon("fas-expand"));
        expandBtn.getStyleClass().addAll(Styles.BUTTON_CIRCLE, Styles.FLAT);
        expandBtn.setTooltip(new Tooltip("Expand graph view"));

        Region graphSpacer = new Region();
        HBox.setHgrow(graphSpacer, Priority.ALWAYS);
        HBox graphHeader = new HBox(graphLabel, graphSpacer, expandBtn);
        graphHeader.setAlignment(Pos.CENTER_LEFT);

        graphWebView = new WebView();
        graphWebEngine = graphWebView.getEngine();
        graphWebView.setPrefHeight(250);
        graphWebView.setMinHeight(250);
        graphWebEngine.loadContent(GRAPH_HTML);

        expandBtn.setOnAction(event -> {
            Stage fullScreenStage = new Stage();
            fullScreenStage.setTitle("NovaRead - Neural Matrix Graph View");
            WebView fullWebView = new WebView();
            WebEngine fullWebEngine = fullWebView.getEngine();
            fullWebEngine.loadContent(GRAPH_HTML);
            fullWebEngine.getLoadWorker().stateProperty().addListener((obs, old, newVal) -> {
                if (newVal == javafx.concurrent.Worker.State.SUCCEEDED)
                    fullWebEngine.executeScript("renderNewGraph('" + lastGraphJson + "');");
            });
            Scene fullScene = new Scene(fullWebView, 1000, 800);
            fullScreenStage.setScene(fullScene);
            fullScreenStage.show();
        });

        Label outputLabel = new Label("Output Channel:");
        outputLabel.getStyleClass().add(Styles.TEXT_MUTED);
        aiResultArea = new TextArea("Standby...\nSelect text in the reading area and right-click to run analysis or generate a graph.");
        aiResultArea.setWrapText(true);
        aiResultArea.setEditable(false);
        aiResultArea.setStyle("-fx-font-family: 'Consolas', monospace; -fx-text-fill: #a3be8c;");
        VBox.setVgrow(aiResultArea, Priority.ALWAYS);

        simplifyBtn.setOnAction(e -> {
            String fullText = getFullWebText();
            app.runAiTask(() -> AiClient.simplifyText(fullText, app.getUserLearningGoal()), aiResultArea);
        });
        authorBtn.setOnAction(e -> {
            String fullText = getFullWebText();
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Author Interview");
            dialog.setHeaderText("Ask the author...");
            dialog.showAndWait().ifPresent(msg -> {
                if (!msg.trim().isEmpty())
                    app.runAiTask(() -> AiClient.authorChat(fullText, msg), aiResultArea);
            });
        });
        saveMemoryBtn.setOnAction(e -> {
            String fullText = getFullWebText();
            app.runAiTask(() -> {
                String compressed = AiClient.compressMemoryForRag(fullText);
                return DatabaseManager.saveMemoryChunk(app.getCurrentBookId(), compressed) + "\n\nExtracted content: " + compressed;
            }, aiResultArea);
        });
        sparkBtn.setOnAction(e -> {
            String fullText = getFullWebText();
            app.runAiTask(() -> AiClient.crossBookSpark(fullText), aiResultArea);
        });
        toggleShelfBtn.setOnAction(e -> {
            boolean current = DatabaseManager.isInBookshelf(app.getCurrentBookId());
            DatabaseManager.setInBookshelf(app.getCurrentBookId(), !current);
            app.runAiTask(() -> current ? "Removed from the starred shelf." : "Added to the starred shelf.", aiResultArea);
        });

        rightPanel.getChildren().addAll(aiLabel, macroBox, ragBox, toggleShelfBtn,
                new Separator(), graphHeader, graphWebView, outputLabel, aiResultArea);
        return rightPanel;
    }

    private void setupWebViewContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        final String[] lastSelectedText = {""};

        MenuItem translateItem = new MenuItem("Quick Translate", new FontIcon("fas-globe"));
        translateItem.setOnAction(e -> app.runAiTask(() -> AiClient.translateText(lastSelectedText[0]), aiResultArea));

        MenuItem lookupItem = new MenuItem("Contextual Lookup", new FontIcon("fas-search"));
        lookupItem.setOnAction(e -> {
            String fullText = getFullWebText();
            app.runAiTask(() -> AiClient.contextualLookup(lastSelectedText[0], fullText,
                    LocalTime.now().getHour() >= 21 || LocalTime.now().getHour() < 6,
                    app.getUserLearningGoal()), aiResultArea);
        });

        MenuItem analyzeItem = new MenuItem("Syntax Analysis", new FontIcon("fas-sitemap"));
        analyzeItem.setOnAction(e -> app.runAiTask(() -> AiClient.analyzeStructure(lastSelectedText[0]), aiResultArea));

        MenuItem graphItem = new MenuItem("Generate Interactive Graph", new FontIcon("fas-project-diagram"));
        graphItem.setOnAction(e -> {
            aiResultArea.setText("Processing: extracting entities and building structured graph data...");
            new Thread(() -> {
                String jsonResult = AiClient.generateGraphJson(lastSelectedText[0]);
                Platform.runLater(() -> {
                    try {
                        String safeJson = jsonResult.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", "");
                        lastGraphJson = safeJson;
                        graphWebEngine.executeScript("renderNewGraph('" + safeJson + "');");
                        aiResultArea.setText("Entity links parsed. You can drag nodes directly or use the expand button for a larger view.");
                    } catch (Exception ex) {
                        aiResultArea.setText("Graph rendering failed.\nError: " + ex.getMessage());
                    }
                });
            }).start();
        });

        MenuItem saveVocabItem = new MenuItem("Add to SRS Memory", new FontIcon("fas-brain"));
        saveVocabItem.setOnAction(e -> {
            String fullText = getFullWebText();
            app.runAiTask(() -> DatabaseManager.saveFlashcard(lastSelectedText[0], fullText,
                    (System.currentTimeMillis() - app.getPageLoadTime()) / 1000.0), aiResultArea);
        });

        contextMenu.getItems().addAll(translateItem, lookupItem, new SeparatorMenuItem(),
                analyzeItem, graphItem, new SeparatorMenuItem(), saveVocabItem);

        webView.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                String selected = getSelectedWebText();
                if (selected != null && !selected.trim().isEmpty()) {
                    lastSelectedText[0] = selected.trim();
                    contextMenu.show(webView, e.getScreenX(), e.getScreenY());
                    e.consume();
                } else {
                    contextMenu.hide();
                }
            } else if (e.getButton() == MouseButton.PRIMARY) {
                contextMenu.hide();
            }
        });
        webView.setOnContextMenuRequested(javafx.event.Event::consume);
    }

    private void setupWebAutoScrollLogic() {
        autoScrollTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastScrollTime == 0) {
                    lastScrollTime = now;
                    return;
                }
                double deltaTime = (now - lastScrollTime) / 1_000_000_000.0;
                lastScrollTime = now;
                double speedPerSecond = scrollSpeedSpinner.getValue() * 25.0;
                double increment = speedPerSecond * deltaTime;
                scrollAccumulator += increment;
                if (scrollAccumulator >= 1.0) {
                    int pixelsToScroll = (int) scrollAccumulator;
                    scrollAccumulator -= pixelsToScroll;
                    try {
                        webEngine.executeScript("window.scrollBy(0, " + pixelsToScroll + ");");
                        String checkBottomJs = "Math.ceil(window.innerHeight + window.scrollY) >= document.documentElement.scrollHeight - 10;";
                        Boolean isBottom = (Boolean) webEngine.executeScript(checkBottomJs);
                        if (isBottom) {
                            if (!isWaitingToTurnPage) {
                                isWaitingToTurnPage = true;
                                pageBottomReachTime = System.currentTimeMillis();
                            } else if (System.currentTimeMillis() - pageBottomReachTime > 1500) {
                                if (app.getCurrentPage() < app.getTotalPages())
                                    loadNextOrPrevPage(app.getCurrentPage() + 1);
                                else
                                    autoScrollBtn.fire();
                                isWaitingToTurnPage = false;
                            }
                        } else {
                            isWaitingToTurnPage = false;
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
        };

        autoScrollBtn.setOnAction(e -> {
            if (autoScrollBtn.isSelected()) {
                autoScrollBtn.setText("Pause Reading");
                ((FontIcon) autoScrollBtn.getGraphic()).setIconLiteral("fas-pause");
                lastScrollTime = 0;
                scrollAccumulator = 0.0;
                autoScrollTimer.start();
            } else {
                autoScrollBtn.setText("Auto Read");
                ((FontIcon) autoScrollBtn.getGraphic()).setIconLiteral("fas-play");
                autoScrollTimer.stop();
                isWaitingToTurnPage = false;
            }
        });
    }

    private void loadBook(String bookId, int page) {
        app.setCurrentPage(page);
        app.setTotalPages(DatabaseManager.getTotalPages(bookId));
        List<String> paragraphs = DatabaseManager.getPageContent(bookId, page);
        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<!DOCTYPE html><html><head><meta charset='utf-8'><style id='dynamic-style'></style>");
        htmlBuilder.append("<style>::-webkit-scrollbar { width: 0px; background: transparent; } p { margin-bottom: 1.2em; text-indent: 2em; }</style></head><body>");
        for (String p : paragraphs) {
            htmlBuilder.append("<p>").append(p).append("</p>");
        }
        htmlBuilder.append("</body></html>");
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) applyCustomAppearance();
        });
        webEngine.loadContent(htmlBuilder.toString());
        pageLabel.setText("Page " + app.getCurrentPage() + " / " + app.getTotalPages());
        aiResultArea.setText("...");
        app.setPageLoadTime(System.currentTimeMillis());
    }

    private void loadNextOrPrevPage(int targetPage) {
        DatabaseManager.updateProgress(app.getCurrentBookId(), app.getCurrentPage(),
                (System.currentTimeMillis() - app.getPageLoadTime()) / 1000.0);
        loadBook(app.getCurrentBookId(), targetPage);
    }

    private void applyCustomAppearance() {
        if (webEngine == null || centerPanel == null) return;
        String bgHex = app.toHexString(bgColorPicker.getValue());
        String fontHex = app.toHexString(fontColorPicker.getValue());
        int fontSize = fontSizeSpinner.getValue();
        centerPanel.setStyle("-fx-background-color: " + bgHex + ";");
        String cssInjection = String.format(
                "document.getElementById('dynamic-style').innerHTML = \"body { background-color: %s; color: %s; font-size: %dpx; font-family: 'Segoe UI', sans-serif; line-height: 1.8; padding: 10px 20px; }\";",
                bgHex, fontHex, fontSize);
        try {
            webEngine.executeScript(cssInjection);
        } catch (Exception e) {
            // ignore
        }

        // Save user preferences
        DatabaseManager.saveSetting(SETTING_BG_COLOR, app.toHexString(bgColorPicker.getValue()));
        DatabaseManager.saveSetting(SETTING_FONT_COLOR, app.toHexString(fontColorPicker.getValue()));
        DatabaseManager.saveSetting(SETTING_FONT_SIZE, String.valueOf(fontSizeSpinner.getValue()));
    }

    private String getSelectedWebText() {
        try {
            return (String) webEngine.executeScript("window.getSelection().toString();");
        } catch (Exception e) {
            return "";
        }
    }

    private String getFullWebText() {
        try {
            return (String) webEngine.executeScript("document.body.innerText;");
        } catch (Exception e) {
            return "";
        }
    }
}
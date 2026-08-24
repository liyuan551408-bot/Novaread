package com.scifi.reader;

import atlantafx.base.theme.Styles;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Welcome screen with pulse animation and an enter button
 */
public class WelcomeScreen {
    private GuiMain app;

    public WelcomeScreen(GuiMain app) {
        this.app = app;
    }

    public Region build() {
        VBox root = new VBox(30);
        root.setAlignment(Pos.CENTER);
        root.setStyle("-fx-background-color: #0f172a;");

        FontIcon mainIcon = new FontIcon("fas-brain");
        mainIcon.setIconSize(120);
        mainIcon.setIconColor(Color.web("#38bdf8"));

        Label title = new Label("NovaRead");
        title.setStyle("-fx-font-size: 64px; -fx-font-weight: bold; -fx-text-fill: #f8fafc;");
        Label subtitle = new Label("AI-powered deep reading and knowledge learning system");
        subtitle.setStyle("-fx-font-size: 24px; -fx-text-fill: #94a3b8;");

        FadeTransition pulse = new FadeTransition(Duration.seconds(1.5), mainIcon);
        pulse.setFromValue(1.0);
        pulse.setToValue(0.4);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.setAutoReverse(true);
        pulse.play();

        Button enterBtn = new Button("Enter NovaRead", new FontIcon("fas-link"));
        enterBtn.getStyleClass().addAll(Styles.ACCENT, Styles.LARGE);
        enterBtn.setStyle("-fx-font-size: 18px; -fx-padding: 15px 40px;");

        enterBtn.setOnAction(e -> {
            pulse.stop();
            app.navigateTo(new LibraryScreen(app).build());
        });

        root.getChildren().addAll(mainIcon, title, subtitle, enterBtn);
        return root;
    }
}
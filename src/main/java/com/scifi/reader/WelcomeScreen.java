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
 * 欢迎界面，带有脉冲动画和进入按钮
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
        Label subtitle = new Label("基于AI驱动的智能深度阅读与知识学习系统 ");
        subtitle.setStyle("-fx-font-size: 24px; -fx-text-fill: #94a3b8;");

        FadeTransition pulse = new FadeTransition(Duration.seconds(1.5), mainIcon);
        pulse.setFromValue(1.0);
        pulse.setToValue(0.4);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.setAutoReverse(true);
        pulse.play();

        Button enterBtn = new Button("建立神经连接 (Enter)", new FontIcon("fas-link"));
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
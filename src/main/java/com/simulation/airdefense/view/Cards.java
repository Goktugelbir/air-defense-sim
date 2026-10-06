package com.simulation.airdefense.view;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Kart tabanlı paneller için küçük yardımcılar. */
final class Cards {

    private Cards() {
    }

    /** Başlıklı bir kart oluşturur. */
    static VBox card(String title, Node... content) {
        Label header = new Label(title);
        header.getStyleClass().add("card-title");
        VBox box = new VBox(6);
        box.getStyleClass().add("card");
        box.getChildren().add(header);
        box.getChildren().addAll(content);
        return box;
    }

    /** Verilen stil sınıflarına sahip bir etiket. */
    static Label label(String text, String... styleClasses) {
        Label label = new Label(text);
        label.getStyleClass().addAll(styleClasses);
        return label;
    }

    /** Etiketin dinamik stil sınıfını (önek ile başlayan) değiştirir. */
    static void setVariant(Node node, String prefix, String variant) {
        node.getStyleClass().removeIf(c -> c.startsWith(prefix));
        if (variant != null) {
            node.getStyleClass().add(prefix + variant);
        }
    }
}

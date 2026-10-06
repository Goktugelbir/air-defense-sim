package com.simulation.airdefense.view;

import com.simulation.airdefense.event.SimulationEvent;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;

/**
 * Zaman damgalı, olay türüne göre renklendirilmiş log paneli.
 * En fazla {@code maxLines} satır tutar ve yeni satır geldiğinde otomatik aşağı kayar.
 */
public final class LogPanel extends VBox {

    private static final String CATEGORY_PREFIX = "log-";

    private final ObservableList<SimulationEvent> items = FXCollections.observableArrayList();
    private final ListView<SimulationEvent> listView = new ListView<>(items);
    private final int maxLines;

    public LogPanel(int maxLines) {
        this.maxLines = maxLines;
        getStyleClass().add("log-panel");

        Label title = Cards.label("OLAY GÜNLÜĞÜ", "card-title");
        BorderPane header = new BorderPane();
        header.setLeft(title);
        header.getStyleClass().add("log-header");

        listView.getStyleClass().add("log-list");
        listView.setFocusTraversable(false);
        listView.setCellFactory(lv -> new LogCell());
        VBox.setVgrow(listView, Priority.ALWAYS);

        getChildren().addAll(header, listView);
    }

    /** Bir batch olayı ekler. JavaFX thread'inde çağrılmalıdır. */
    public void append(List<SimulationEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        items.addAll(events);
        int overflow = items.size() - maxLines;
        if (overflow > 0) {
            items.remove(0, overflow);
        }
        listView.scrollTo(items.size() - 1);
    }

    public void clear() {
        items.clear();
    }

    private static final class LogCell extends ListCell<SimulationEvent> {
        @Override
        protected void updateItem(SimulationEvent event, boolean empty) {
            super.updateItem(event, empty);
            getStyleClass().removeIf(c -> c.startsWith(CATEGORY_PREFIX));
            if (empty || event == null) {
                setText(null);
            } else {
                setText(event.format());
                getStyleClass().add(CATEGORY_PREFIX + event.type().category().name().toLowerCase(Locale.ROOT));
            }
        }
    }
}

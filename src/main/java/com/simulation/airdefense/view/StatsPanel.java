package com.simulation.airdefense.view;

import com.simulation.airdefense.model.Geometry;
import com.simulation.airdefense.simulation.SimulationSnapshot;
import com.simulation.airdefense.simulation.SimulationSnapshot.PriorityEntry;
import com.simulation.airdefense.simulation.SimulationSnapshot.Stats;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;

/** Sağ paneldeki istatistik kartları: durum, sayaçlar, sistem, öncelikli hedef ve ilk 5 liste. */
public final class StatsPanel extends VBox {

    private final Label stateLabel = Cards.label("HAZIR", "badge");
    private final Label elapsedLabel = Cards.label("00:00", "value-mono");
    private final Label scenarioLabel = Cards.label("—", "value-mono");
    private final Label perfLabel = Cards.label("FPS — · adım — ms", "value-mono", "muted");

    private final Label activeValue = statValue();
    private final Label detectedValue = statValue();
    private final Label highValue = statValue();
    private final Label mediumValue = statValue();
    private final Label lowValue = statValue();
    private final Label neutralizedValue = statValue();
    private final Label escapedValue = statValue();
    private final Label leftAreaValue = statValue();

    private final Label rangeLabel = Cards.label("—", "value-mono");
    private final Label speedLabel = Cards.label("—", "value-mono");
    private final Label radarLabel = Cards.label("—", "value-mono");
    private final Label defenseLabel = Cards.label("—", "value-mono");

    private final Label priorityId = Cards.label("—", "priority-id");
    private final Label priorityScore = Cards.label("", "priority-score");
    private final Label priorityDetails = Cards.label("Algılanan hedef yok", "value-mono", "muted");

    private final Label[] topRows = new Label[5];

    public StatsPanel() {
        super(10);
        getStyleClass().add("side-panel");

        VBox simulation = Cards.card("SİMÜLASYON",
                row("Durum", stateLabel),
                row("Geçen süre", elapsedLabel),
                row("Senaryo", scenarioLabel),
                perfLabel);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.add(statCell("AKTİF", activeValue, null), 0, 0);
        grid.add(statCell("ALGILANAN", detectedValue, null), 1, 0);
        grid.add(statCell("YÜKSEK", highValue, "stat-high"), 0, 1);
        grid.add(statCell("ORTA", mediumValue, "stat-medium"), 1, 1);
        grid.add(statCell("DÜŞÜK", lowValue, "stat-low"), 0, 2);
        grid.add(statCell("ETKİSİZ", neutralizedValue, "stat-neutralized"), 1, 2);
        grid.add(statCell("SIZAN", escapedValue, "stat-escaped"), 0, 3);
        grid.add(statCell("ALANDAN ÇIKAN", leftAreaValue, null), 1, 3);
        VBox targets = Cards.card("HEDEFLER", grid);

        VBox system = Cards.card("SİSTEM",
                row("Radar menzili", rangeLabel),
                row("Radar", radarLabel),
                row("Simülasyon hızı", speedLabel),
                row("Savunma", defenseLabel));

        HBox priorityHeader = new HBox(8, priorityId, spacer(), priorityScore);
        priorityHeader.setAlignment(Pos.CENTER_LEFT);
        priorityDetails.setWrapText(true);
        VBox priority = Cards.card("ÖNCELİKLİ HEDEF", priorityHeader, priorityDetails);
        priority.getStyleClass().add("priority-card");

        VBox topList = new VBox(3);
        for (int i = 0; i < topRows.length; i++) {
            topRows[i] = Cards.label("", "value-mono", "top-row");
            topList.getChildren().add(topRows[i]);
        }
        VBox top = Cards.card("İLK 5 ÖNCELİK", topList);

        getChildren().addAll(simulation, targets, system, priority, top);
    }

    /** Snapshot'tan tüm kartları günceller. JavaFX thread'inde çağrılmalıdır. */
    public void update(SimulationSnapshot s, double fps) {
        stateLabel.setText(s.state().label());
        Cards.setVariant(stateLabel, "state-", s.state().name().toLowerCase(Locale.ROOT));
        int seconds = (int) s.simulationTime();
        elapsedLabel.setText("%02d:%02d".formatted(seconds / 60, seconds % 60));
        scenarioLabel.setText(s.scenarioName());
        perfLabel.setText(String.format(Locale.ROOT, "FPS %.0f · adım %.2f ms · %d hedef",
                fps, s.tickMillis(), s.targets().size()));

        Stats st = s.stats();
        activeValue.setText(String.valueOf(st.active()));
        detectedValue.setText(String.valueOf(st.detected()));
        highValue.setText(String.valueOf(st.high()));
        mediumValue.setText(String.valueOf(st.medium()));
        lowValue.setText(String.valueOf(st.low()));
        neutralizedValue.setText(String.valueOf(st.neutralized()));
        escapedValue.setText(String.valueOf(st.escaped()));
        leftAreaValue.setText(String.valueOf(st.leftArea()));

        rangeLabel.setText(String.format(Locale.ROOT, "%.0f m", s.radar().range()));
        radarLabel.setText(s.radar().active() ? "AÇIK" : "KAPALI");
        speedLabel.setText(String.format(Locale.ROOT, "%sx", trim(s.speedMultiplier())));
        defenseLabel.setText(s.defense().state().label());

        List<PriorityEntry> priorities = s.priorities();
        if (priorities.isEmpty()) {
            priorityId.setText("—");
            priorityScore.setText("");
            Cards.setVariant(priorityScore, "level-", null);
            priorityDetails.setText("Algılanan hedef yok");
        } else {
            PriorityEntry p = priorities.getFirst();
            priorityId.setText(p.id());
            priorityScore.setText(String.valueOf(Math.round(p.score())));
            Cards.setVariant(priorityScore, "level-", p.level().name().toLowerCase(Locale.ROOT));
            priorityDetails.setText(String.format(Locale.ROOT, "%s · %.0f m · %.0f km/h · Varış %s",
                    p.type().displayName(), p.distance(), Geometry.toKmh(p.speed()), formatTti(p.timeToImpact())));
        }
        for (int i = 0; i < topRows.length; i++) {
            Label row = topRows[i];
            if (i < priorities.size()) {
                PriorityEntry p = priorities.get(i);
                row.setText(String.format(Locale.ROOT, "#%d  %-9s %3d  %5.1f km",
                        p.rank(), p.id(), Math.round(p.score()), p.distance() / 1000.0));
                Cards.setVariant(row, "level-", p.level().name().toLowerCase(Locale.ROOT));
            } else {
                row.setText(i == 0 && priorities.isEmpty() ? "—" : "");
                Cards.setVariant(row, "level-", null);
            }
        }
    }

    static String formatTti(double tti) {
        return Double.isInfinite(tti) ? "—" : String.format(Locale.ROOT, "%.0f s", tti);
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private static Label statValue() {
        return Cards.label("0", "stat-value");
    }

    private static VBox statCell(String caption, Label value, String variant) {
        Label cap = Cards.label(caption, "stat-caption");
        VBox cell = new VBox(0, value, cap);
        cell.getStyleClass().add("stat-cell");
        if (variant != null) {
            cell.getStyleClass().add(variant);
        }
        cell.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(cell, Priority.ALWAYS);
        return cell;
    }

    static HBox row(String name, Label value) {
        Label key = Cards.label(name, "key");
        HBox box = new HBox(8, key, spacer(), value);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }
}

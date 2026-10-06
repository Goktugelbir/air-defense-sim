package com.simulation.airdefense.view;

import com.simulation.airdefense.model.Geometry;
import com.simulation.airdefense.model.ThreatResult;
import com.simulation.airdefense.simulation.SimulationSnapshot.TargetView;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Locale;

/** Seçili hedefin canlı bilgi kartı ve tehdit skoru bileşen çubukları. */
public final class TargetInfoPanel extends VBox {

    private static final String[] COMPONENT_NAMES = {"Mesafe", "Hız", "Yön", "Varış süresi", "Tür"};

    private final Label placeholder = Cards.label("İncelemek için radarda bir hedefe tıkla.", "muted");
    private final Label idLabel = Cards.label("—", "value-mono");
    private final Label typeLabel = Cards.label("—", "value-mono");
    private final Label positionLabel = Cards.label("—", "value-mono");
    private final Label speedLabel = Cards.label("—", "value-mono");
    private final Label headingLabel = Cards.label("—", "value-mono");
    private final Label distanceLabel = Cards.label("—", "value-mono");
    private final Label ttiLabel = Cards.label("—", "value-mono");
    private final Label scoreLabel = Cards.label("—", "value-mono");
    private final Label statusLabel = Cards.label("—", "value-mono");
    private final Label swarmLabel = Cards.label("—", "value-mono");
    private final ProgressBar[] bars = new ProgressBar[COMPONENT_NAMES.length];
    private final Label[] barValues = new Label[COMPONENT_NAMES.length];
    private final VBox details;

    public TargetInfoPanel() {
        super(10);
        VBox breakdown = new VBox(4);
        breakdown.getChildren().add(Cards.label("TEHDİT DAĞILIMI", "subsection"));
        for (int i = 0; i < COMPONENT_NAMES.length; i++) {
            Label name = Cards.label(COMPONENT_NAMES[i], "key", "bar-name");
            name.setMinWidth(92);
            bars[i] = new ProgressBar(0);
            bars[i].getStyleClass().add("component-bar");
            bars[i].setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(bars[i], Priority.ALWAYS);
            barValues[i] = Cards.label("0.00", "value-mono", "bar-value");
            barValues[i].setMinWidth(34);
            HBox row = new HBox(6, name, bars[i], barValues[i]);
            row.setAlignment(Pos.CENTER_LEFT);
            breakdown.getChildren().add(row);
        }

        details = new VBox(4,
                StatsPanel.row("ID", idLabel),
                StatsPanel.row("Tür", typeLabel),
                StatsPanel.row("Konum", positionLabel),
                StatsPanel.row("Hız", speedLabel),
                StatsPanel.row("Rota", headingLabel),
                StatsPanel.row("Mesafe", distanceLabel),
                StatsPanel.row("Merkeze varış", ttiLabel),
                StatsPanel.row("Tehdit skoru", scoreLabel),
                StatsPanel.row("Durum", statusLabel),
                StatsPanel.row("Sürü", swarmLabel),
                breakdown);

        VBox card = Cards.card("HEDEF BİLGİSİ", placeholder, details);
        getChildren().add(card);
        showDetails(false);
    }

    private void showDetails(boolean show) {
        details.setVisible(show);
        details.setManaged(show);
        placeholder.setVisible(!show);
        placeholder.setManaged(!show);
    }

    /**
     * Seçili hedefin bilgilerini gösterir.
     *
     * @param t hedef; {@code null} ise (seçim yok veya hedef artık yok) yer tutucu gösterilir
     */
    public void update(TargetView t) {
        if (t == null) {
            showDetails(false);
            return;
        }
        showDetails(true);
        idLabel.setText(t.id());
        typeLabel.setText(t.type().displayName());
        positionLabel.setText(String.format(Locale.ROOT, "X %.0f m / Y %.0f m", t.x(), t.y()));
        speedLabel.setText(String.format(Locale.ROOT, "%.0f km/h (%.0f m/s)", Geometry.toKmh(t.speed()), t.speed()));
        headingLabel.setText(String.format(Locale.ROOT, "%03.0f°", t.heading()));
        distanceLabel.setText(String.format(Locale.ROOT, "%.0f m", t.distance()));
        statusLabel.setText(t.status().label());
        swarmLabel.setText(t.swarmId() == null ? "—" : t.swarmId());

        ThreatResult threat = t.threat();
        if (t.detected()) {
            ttiLabel.setText(StatsPanel.formatTti(threat.timeToImpact()));
            scoreLabel.setText(String.format(Locale.ROOT, "%d (%s)", threat.roundedScore(), threat.level().label()));
            Cards.setVariant(scoreLabel, "level-", threat.level().name().toLowerCase(Locale.ROOT));
        } else {
            ttiLabel.setText("—");
            scoreLabel.setText("algılanmadı");
            Cards.setVariant(scoreLabel, "level-", null);
        }
        double[] components = {
                threat.distanceScore(), threat.speedScore(), threat.directionScore(),
                threat.timeToImpactScore(), threat.typeScore()
        };
        for (int i = 0; i < components.length; i++) {
            double v = t.detected() ? components[i] : 0;
            bars[i].setProgress(v);
            barValues[i].setText(String.format(Locale.ROOT, "%.2f", v));
        }
    }
}

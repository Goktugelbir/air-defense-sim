package com.simulation.airdefense.view;

import com.simulation.airdefense.model.TargetType;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Locale;

/**
 * Sol paneldeki kullanıcı kontrolleri. Yalnızca görünümü oluşturur; davranışı
 * {@code MainController} bağlar (View ve Controller ayrımı).
 */
public final class ControlPanel extends VBox {

    /** Hız seçenekleri (çarpan). */
    public static final double[] SPEED_OPTIONS = {0.5, 1.0, 2.0, 4.0};

    private final Button startButton = button("Başlat", "btn-primary");
    private final Button pauseButton = button("Duraklat");
    private final Button resumeButton = button("Devam");
    private final Button resetButton = button("Sıfırla", "btn-danger");

    private final Button singleScenario = button("Tek Hedef");
    private final Button swarmScenario = button("Sürü Saldırısı");
    private final Button randomScenario = button("Rastgele Saldırı");
    private final Button heavyScenario = button("Yoğun Saldırı");
    private final TextField seedField = new TextField();

    private final ToggleGroup speedGroup = new ToggleGroup();
    private final ToggleButton[] speedButtons = new ToggleButton[SPEED_OPTIONS.length];

    private final Slider rangeSlider;
    private final Label rangeValue = Cards.label("", "value-mono");
    private final CheckBox radarActive = new CheckBox("Radar açık");

    private final ComboBox<TargetType> typeCombo = new ComboBox<>();
    private final Slider spawnSpeedSlider = new Slider(60, 360, 180);
    private final Label spawnSpeedValue = Cards.label("", "value-mono");
    private final Button spawnButton = button("Hedef Ekle", "btn-primary");

    private final CheckBox showTrails = new CheckBox("İzleri göster");
    private final CheckBox showPrediction = new CheckBox("Tahmini rotayı göster");
    private final CheckBox showLabels = new CheckBox("Etiketleri göster");

    /**
     * @param minRange     menzil slider'ının minimumu (m)
     * @param maxRange     menzil slider'ının maksimumu (m)
     * @param initialRange başlangıç menzili (m)
     * @param defaultSeed  senaryo seed'i
     */
    public ControlPanel(double minRange, double maxRange, double initialRange, long defaultSeed) {
        super(10);
        getStyleClass().add("side-panel");

        // Simulation
        GridPane simGrid = grid2(startButton, pauseButton, resumeButton, resetButton);
        VBox simulation = Cards.card("SİMÜLASYON", simGrid);

        // Scenario
        tooltip(singleScenario, "Tek Hedef — 1 İHA, tek yönden");
        tooltip(swarmScenario, "Sürü Saldırısı — 20 üyeli sürü, birkaç yem");
        tooltip(randomScenario, "Rastgele Saldırı — 15–25 karışık hedef, zamana yayılmış");
        tooltip(heavyScenario, "Yoğun Saldırı — 56 hedef aynı anda");
        seedField.setText(String.valueOf(defaultSeed));
        seedField.setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("-?\\d{0,18}") ? change : null));
        seedField.getStyleClass().add("seed-field");
        HBox seedRow = new HBox(8, Cards.label("Tohum", "key"), seedField);
        seedRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(seedField, Priority.ALWAYS);
        GridPane scenarioButtons = grid2(singleScenario, swarmScenario, randomScenario, heavyScenario);
        VBox scenario = Cards.card("SENARYO", scenarioButtons, seedRow);

        // Speed
        HBox speedRow = new HBox(4);
        for (int i = 0; i < SPEED_OPTIONS.length; i++) {
            ToggleButton b = new ToggleButton(formatSpeed(SPEED_OPTIONS[i]));
            b.setUserData(SPEED_OPTIONS[i]);
            b.setToggleGroup(speedGroup);
            b.setMaxWidth(Double.MAX_VALUE);
            b.getStyleClass().add("speed-toggle");
            // Odak almasın: aksi halde ok tuşları ToggleGroup içinde gezinip hızı fark ettirmeden değiştirir.
            b.setFocusTraversable(false);
            HBox.setHgrow(b, Priority.ALWAYS);
            speedButtons[i] = b;
            speedRow.getChildren().add(b);
        }
        speedButtons[1].setSelected(true);
        // Bir seçenek her zaman seçili kalsın.
        speedGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });
        VBox speed = Cards.card("HIZ", speedRow);

        // Radar
        rangeSlider = new Slider(minRange, maxRange, initialRange);
        rangeSlider.setMajorTickUnit(1000);
        rangeSlider.setMinorTickCount(1);
        rangeSlider.setSnapToTicks(true);
        rangeSlider.setBlockIncrement(500);
        rangeValue.textProperty().bind(rangeSlider.valueProperty().asString(Locale.ROOT, "%.0f m"));
        radarActive.setSelected(true);
        VBox radar = Cards.card("RADAR", StatsPanel.row("Menzil", rangeValue), rangeSlider, radarActive);

        // Spawn
        typeCombo.getItems().addAll(TargetType.values());
        typeCombo.getSelectionModel().select(TargetType.DRONE);
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        spawnSpeedSlider.setMajorTickUnit(60);
        spawnSpeedSlider.setBlockIncrement(10);
        spawnSpeedValue.textProperty().bind(spawnSpeedSlider.valueProperty().asString(Locale.ROOT, "%.0f km/h"));
        spawnButton.setMaxWidth(Double.MAX_VALUE);
        Label hint = Cards.label("Hızlı İHA temel hızın 2.5 katıyla uçar", "muted", "hint");
        VBox spawn = Cards.card("HEDEF EKLE", typeCombo,
                StatsPanel.row("Temel hız", spawnSpeedValue), spawnSpeedSlider, hint, spawnButton);

        // View
        showTrails.setSelected(true);
        showPrediction.setSelected(true);
        showLabels.setSelected(true);
        Label shortcuts = Cards.label("SPACE başlat/duraklat · R sıfırla", "value-mono", "muted", "shortcuts");
        VBox view = Cards.card("GÖRÜNÜM", showTrails, showPrediction, showLabels, shortcuts);

        getChildren().addAll(simulation, scenario, speed, radar, spawn, view);
    }

    /** Kısa buton metinleri için açıklayıcı ipucu ekler. */
    private static void tooltip(Button button, String text) {
        button.setTooltip(new Tooltip(text));
    }

    private static Button button(String text, String... styleClasses) {
        Button b = new Button(text);
        b.getStyleClass().addAll(styleClasses);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setFocusTraversable(false);
        return b;
    }

    private static GridPane grid2(Button a, Button b, Button c, Button d) {
        GridPane grid = new GridPane();
        grid.setHgap(6);
        grid.setVgap(6);
        grid.add(a, 0, 0);
        grid.add(b, 1, 0);
        grid.add(c, 0, 1);
        grid.add(d, 1, 1);
        for (Button button : new Button[]{a, b, c, d}) {
            GridPane.setHgrow(button, Priority.ALWAYS);
        }
        return grid;
    }

    private static String formatSpeed(double v) {
        return (v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v)) + "x";
    }

    /** Seed alanındaki değer; boş veya geçersizse {@code fallback}. */
    public long seed(long fallback) {
        try {
            return Long.parseLong(seedField.getText().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public Button startButton() {
        return startButton;
    }

    public Button pauseButton() {
        return pauseButton;
    }

    public Button resumeButton() {
        return resumeButton;
    }

    public Button resetButton() {
        return resetButton;
    }

    public Button singleScenarioButton() {
        return singleScenario;
    }

    public Button swarmScenarioButton() {
        return swarmScenario;
    }

    public Button randomScenarioButton() {
        return randomScenario;
    }

    public Button heavyScenarioButton() {
        return heavyScenario;
    }

    public ToggleGroup speedGroup() {
        return speedGroup;
    }

    public Slider rangeSlider() {
        return rangeSlider;
    }

    public CheckBox radarActiveCheckBox() {
        return radarActive;
    }

    public ComboBox<TargetType> typeCombo() {
        return typeCombo;
    }

    public Slider spawnSpeedSlider() {
        return spawnSpeedSlider;
    }

    public Button spawnButton() {
        return spawnButton;
    }

    public CheckBox showTrailsCheckBox() {
        return showTrails;
    }

    public CheckBox showPredictionCheckBox() {
        return showPrediction;
    }

    public CheckBox showLabelsCheckBox() {
        return showLabels;
    }
}

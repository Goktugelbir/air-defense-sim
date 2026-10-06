package com.simulation.airdefense.controller;

import com.simulation.airdefense.config.DefenseConfig;
import com.simulation.airdefense.config.SimulationConfig;
import com.simulation.airdefense.config.ThreatConfig;
import com.simulation.airdefense.event.EventBus;
import com.simulation.airdefense.event.EventLogger;
import com.simulation.airdefense.model.Geometry;
import com.simulation.airdefense.model.TargetType;
import com.simulation.airdefense.scenario.HighDensityScenario;
import com.simulation.airdefense.scenario.RandomAttackScenario;
import com.simulation.airdefense.scenario.Scenario;
import com.simulation.airdefense.scenario.SingleTargetScenario;
import com.simulation.airdefense.scenario.SwarmScenario;
import com.simulation.airdefense.simulation.SimulationEngine;
import com.simulation.airdefense.simulation.SimulationSnapshot;
import com.simulation.airdefense.simulation.SimulationSnapshot.TargetView;
import com.simulation.airdefense.simulation.SimulationState;
import com.simulation.airdefense.view.ControlPanel;
import com.simulation.airdefense.view.CoordinateMapper;
import com.simulation.airdefense.view.LogPanel;
import com.simulation.airdefense.view.RadarRenderer;
import com.simulation.airdefense.view.StatsPanel;
import com.simulation.airdefense.view.TargetInfoPanel;
import com.simulation.airdefense.view.ViewOptions;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.util.function.LongFunction;

/**
 * Uygulamanın denetleyicisi: motoru, olay logger'ını ve görünümleri oluşturur, kullanıcı
 * etkileşimlerini motor komutlarına çevirir ve {@link AnimationTimer} ile çizimi yönetir.
 * <p>
 * Simülasyon mantığı UI thread'inde çalışmaz; her karede yalnızca motorun en son
 * {@link SimulationSnapshot}'ı okunur.
 */
public final class MainController {

    private static final double PICK_TOLERANCE_PX = 14.0;
    private static final long PANEL_REFRESH_NANOS = 100_000_000L;
    private static final long FPS_WINDOW_NANOS = 500_000_000L;
    private static final double LOG_PANEL_HEIGHT = 190;
    private static final double SIDE_PANEL_WIDTH = 300;

    private final SimulationConfig config = SimulationConfig.defaults();
    private final DefenseConfig defenseConfig = DefenseConfig.defaults();
    private final EventBus eventBus = new EventBus();
    private final SimulationEngine engine;
    private final EventLogger eventLogger;

    private final BorderPane root = new BorderPane();
    private final Canvas canvas = new Canvas();
    private final CoordinateMapper mapper = new CoordinateMapper();
    private final RadarRenderer renderer;
    private final ViewOptions viewOptions = new ViewOptions();
    private final ControlPanel controlPanel;
    private final StatsPanel statsPanel = new StatsPanel();
    private final TargetInfoPanel targetInfoPanel = new TargetInfoPanel();
    private final LogPanel logPanel;
    private final AnimationTimer timer;

    private String selectedTargetId;
    private SimulationSnapshot lastSnapshot;
    private long lastPanelRefresh;
    private long fpsWindowStart;
    private int fpsFrames;
    private double fps;

    public MainController() {
        engine = new SimulationEngine(config, ThreatConfig.defaults(), defenseConfig, eventBus);
        renderer = new RadarRenderer(config.ringSpacing(), defenseConfig.engagementRange(), defenseConfig.escapeRadius());
        controlPanel = new ControlPanel(config.minRadarRange(), config.maxRadarRange(),
                config.defaultRadarRange(), config.defaultSeed());
        logPanel = new LogPanel(config.maxLogLines());

        // Log thread'i olayları toplu halde tek bir runLater ile UI'a aktarır.
        eventLogger = new EventLogger(batch -> Platform.runLater(() -> logPanel.append(batch)));
        eventBus.subscribe(eventLogger);
        eventLogger.start();

        buildLayout();
        wireControls();

        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                onFrame(now);
            }
        };
        timer.start();

        engine.loadScenario(new SwarmScenario(config.defaultSeed()));
    }

    // ------------------------------------------------------------------ layout

    private void buildLayout() {
        root.getStyleClass().add("root-pane");

        Pane radarPane = new Pane(canvas);
        radarPane.getStyleClass().add("radar-pane");
        radarPane.setMinSize(0, 0);
        canvas.widthProperty().bind(radarPane.widthProperty());
        canvas.heightProperty().bind(radarPane.heightProperty());
        canvas.addEventHandler(MouseEvent.MOUSE_PRESSED, this::onCanvasPressed);
        root.setCenter(radarPane);

        root.setLeft(scroll(controlPanel));

        VBox right = new VBox(10, statsPanel, targetInfoPanel);
        right.getStyleClass().add("side-panel");
        root.setRight(scroll(right));

        logPanel.setPrefHeight(LOG_PANEL_HEIGHT);
        logPanel.setMinHeight(LOG_PANEL_HEIGHT);
        root.setBottom(logPanel);
    }

    private static ScrollPane scroll(Parent content) {
        ScrollPane pane = new ScrollPane(content);
        pane.setFitToWidth(true);
        pane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pane.setPrefWidth(SIDE_PANEL_WIDTH);
        pane.setMinWidth(SIDE_PANEL_WIDTH);
        pane.getStyleClass().add("side-scroll");
        // Odak almasın: aksi halde ok/Page tuşları paneli kendiliğinden kaydırır.
        pane.setFocusTraversable(false);
        return pane;
    }

    // ------------------------------------------------------------------ kontroller

    private void wireControls() {
        ControlPanel c = controlPanel;
        c.startButton().setOnAction(e -> engine.start());
        c.pauseButton().setOnAction(e -> engine.pause());
        c.resumeButton().setOnAction(e -> engine.resume());
        c.resetButton().setOnAction(e -> reset());

        c.singleScenarioButton().setOnAction(e -> loadScenario(SingleTargetScenario::new));
        c.swarmScenarioButton().setOnAction(e -> loadScenario(SwarmScenario::new));
        c.randomScenarioButton().setOnAction(e -> loadScenario(RandomAttackScenario::new));
        c.heavyScenarioButton().setOnAction(e -> loadScenario(HighDensityScenario::new));

        c.speedGroup().selectedToggleProperty().addListener((obs, old, toggle) -> {
            if (toggle != null) {
                engine.setSpeed((Double) toggle.getUserData());
            }
        });

        c.rangeSlider().valueChangingProperty().addListener((obs, was, changing) -> {
            if (!changing) {
                engine.setRadarRange(c.rangeSlider().getValue());
            }
        });
        c.rangeSlider().valueProperty().addListener((obs, old, value) -> {
            if (!c.rangeSlider().isValueChanging()) {
                engine.setRadarRange(value.doubleValue());
            }
        });
        c.radarActiveCheckBox().selectedProperty().addListener((obs, old, active) -> engine.setRadarActive(active));

        c.spawnButton().setOnAction(e -> {
            TargetType type = c.typeCombo().getValue();
            if (type != null) {
                engine.spawnTarget(type, Geometry.toMetersPerSecond(c.spawnSpeedSlider().getValue()));
            }
        });

        c.showTrailsCheckBox().selectedProperty().addListener((obs, o, v) -> viewOptions.setShowTrails(v));
        c.showPredictionCheckBox().selectedProperty().addListener((obs, o, v) -> viewOptions.setShowPrediction(v));
        c.showLabelsCheckBox().selectedProperty().addListener((obs, o, v) -> viewOptions.setShowLabels(v));
    }

    private void loadScenario(LongFunction<Scenario> factory) {
        selectedTargetId = null;
        logPanel.clear();
        engine.loadScenario(factory.apply(controlPanel.seed(config.defaultSeed())));
    }

    private void reset() {
        selectedTargetId = null;
        logPanel.clear();
        engine.reset();
    }

    /** Sahneye klavye kısayollarını bağlar: Space (başlat/duraklat), R (sıfırla). */
    public void attachScene(Scene scene) {
        // Açılışta odak bir metin alanında kalmasın; aksi halde kısayollar çalışmaz.
        Platform.runLater(canvas::requestFocus);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (scene.getFocusOwner() instanceof TextInputControl) {
                return;
            }
            if (event.getCode() == KeyCode.SPACE) {
                toggleRunPause();
                event.consume();
            } else if (event.getCode() == KeyCode.R) {
                reset();
                event.consume();
            }
        });
    }

    private void toggleRunPause() {
        switch (engine.getState()) {
            case READY -> engine.start();
            case RUNNING -> engine.pause();
            case PAUSED -> engine.resume();
            case FINISHED -> {
                // Bitmiş simülasyon Reset ile yeniden başlatılır.
            }
        }
    }

    private void onCanvasPressed(MouseEvent event) {
        canvas.requestFocus();
        SimulationSnapshot snapshot = engine.getSnapshot();
        String best = null;
        double bestDist = PICK_TOLERANCE_PX;
        for (TargetView t : snapshot.targets()) {
            double dx = mapper.toScreenX(t.x()) - event.getX();
            double dy = mapper.toScreenY(t.y()) - event.getY();
            double d = Math.hypot(dx, dy);
            if (d <= bestDist) {
                bestDist = d;
                best = t.id();
            }
        }
        selectedTargetId = best;
        if (lastSnapshot != null) {
            targetInfoPanel.update(lastSnapshot.findTarget(selectedTargetId));
        }
    }

    // ------------------------------------------------------------------ render döngüsü

    private void onFrame(long now) {
        SimulationSnapshot snapshot = engine.getSnapshot();
        lastSnapshot = snapshot;

        double width = canvas.getWidth();
        double height = canvas.getHeight();
        if (width > 0 && height > 0) {
            renderer.render(canvas.getGraphicsContext2D(), width, height, snapshot, mapper,
                    viewOptions, selectedTargetId, now / 1e9);
        }

        fpsFrames++;
        if (fpsWindowStart == 0) {
            fpsWindowStart = now;
        } else if (now - fpsWindowStart >= FPS_WINDOW_NANOS) {
            fps = fpsFrames * 1e9 / (now - fpsWindowStart);
            fpsFrames = 0;
            fpsWindowStart = now;
        }

        if (now - lastPanelRefresh >= PANEL_REFRESH_NANOS) {
            lastPanelRefresh = now;
            refreshPanels(snapshot);
        }
    }

    private void refreshPanels(SimulationSnapshot snapshot) {
        statsPanel.update(snapshot, fps);
        targetInfoPanel.update(snapshot.findTarget(selectedTargetId));

        SimulationState state = snapshot.state();
        controlPanel.startButton().setDisable(state != SimulationState.READY);
        controlPanel.pauseButton().setDisable(state != SimulationState.RUNNING);
        controlPanel.resumeButton().setDisable(state != SimulationState.PAUSED);
    }

    public Parent getRoot() {
        return root;
    }

    /** Zamanlayıcıyı durdurur ve tüm arka plan thread'lerini kapatır. */
    public void shutdown() {
        timer.stop();
        engine.shutdown();
        eventLogger.shutdown();
    }
}

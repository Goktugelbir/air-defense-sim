package com.simulation.airdefense.view;

import com.simulation.airdefense.model.ThreatLevel;
import com.simulation.airdefense.service.DefenseState;
import com.simulation.airdefense.simulation.SimulationSnapshot;
import com.simulation.airdefense.simulation.SimulationSnapshot.Effect;
import com.simulation.airdefense.simulation.SimulationSnapshot.TargetView;
import com.simulation.airdefense.simulation.SimulationState;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

import java.util.Arrays;
import java.util.Locale;

/**
 * Radar ekranını tek bir Canvas üzerine çizer. Her hedef için ayrı Node oluşturulmaz.
 * <p>
 * Render döngüsünde nesne üretimini azaltmak için renkler, fontlar ve çokgen
 * tamponları önceden oluşturulur; saydamlık {@code globalAlpha} ile ayarlanır.
 */
public final class RadarRenderer {

    /** Görüntülenen dünya yarıçapının radar menziline oranı (menzil dışındaki hedefler de görünsün). */
    public static final double VIEW_EXTENT_FACTOR = 1.14;
    /** Seçili hedef için ileri tahmin süresi (s). */
    private static final double PREDICTION_SECONDS = 30.0;
    private static final double PREDICTION_MARK_INTERVAL = 10.0;
    private static final double TARGET_SIZE = 7.0;
    private static final double HEADING_LINE_PX = 18.0;
    private static final int SWEEP_SLICES = 60;
    private static final double SWEEP_TRAIL_DEG = 90.0;
    private static final double SWEEP_MAX_ALPHA = 0.32;
    private static final double GLOW_RADIUS = 13.0;
    private static final int MAX_LABELS = 128;
    private static final double LABEL_CHAR_WIDTH = 6.0;
    private static final double LABEL_HEIGHT = 12.0;

    private static final Color BACKGROUND = Color.web("#0a0f14");
    private static final Color GRID = Color.web("#14e0b0");
    private static final Color GRID_TEXT = Color.web("#5fd4b8");
    private static final Color SWEEP = Color.web("#20ffb4");
    private static final Color LOW = Color.web("#39d353");
    private static final Color MEDIUM = Color.web("#f2cc0c");
    private static final Color HIGH = Color.web("#ff4d4d");
    private static final Color UNDETECTED = Color.web("#8a96a3");
    private static final Color LABEL = Color.web("#d6e2ea");
    private static final Color LOCK = Color.web("#ff9f1c");
    private static final Color ENGAGE = Color.web("#ff3b3b");
    private static final Color SELECTION = Color.web("#ffffff");
    private static final Color PREDICTION = Color.web("#7fdcff");
    private static final Color PING = Color.web("#5ff7ff");
    private static final Color NEUTRALIZED = Color.web("#ffb347");
    private static final Color MISS = Color.web("#f2cc0c");
    private static final Color ESCAPED = Color.web("#ff2d55");
    private static final Color DEFENSE_IDLE = Color.web("#20ffb4");
    private static final Color DEFENSE_COOLDOWN = Color.web("#6b8cae");
    private static final Color OVERLAY_TEXT = Color.web("#e8f1f5");

    private static final Font RING_FONT = Font.font("Consolas", 10);
    private static final Font LABEL_FONT = Font.font("Consolas", 10);
    private static final Font HUD_FONT = Font.font("Consolas", FontWeight.BOLD, 12);
    private static final Font OVERLAY_FONT = Font.font("Consolas", FontWeight.BOLD, 26);
    private static final Font COMPASS_FONT = Font.font("Consolas", FontWeight.BOLD, 13);
    private static final Font BEARING_FONT = Font.font("Consolas", 9);
    private static final Stop[] SCOPE_STOPS = {
            new Stop(0.0, Color.web("#0f2a26")),
            new Stop(0.65, Color.web("#0b1a1b")),
            new Stop(1.0, Color.web("#081114"))
    };

    private static final double[] DASH_SHORT = {4, 4};
    private static final double[] DASH_LONG = {8, 6};
    private static final double[] DASH_DOTTED = {2, 5};

    private final double ringSpacing;
    private final double engagementRange;
    private final double escapeRadius;
    private final double[] polyX = new double[6];
    private final double[] polyY = new double[6];
    private final double[] labelRects = new double[MAX_LABELS * 4];
    private final TargetView[] labeledTargets = new TargetView[MAX_LABELS];
    private int placedLabels;

    /**
     * @param ringSpacing     menzil halkaları arası mesafe (m)
     * @param engagementRange savunma angajman menzili (m), kesikli halka olarak çizilir
     * @param escapeRadius    sızma yarıçapı (m)
     */
    public RadarRenderer(double ringSpacing, double engagementRange, double escapeRadius) {
        this.ringSpacing = ringSpacing;
        this.engagementRange = engagementRange;
        this.escapeRadius = escapeRadius;
    }

    /**
     * Bir kare çizer.
     *
     * @param now        animasyon zamanı (s), titreşim efektleri için
     * @param selectedId seçili hedef ID'si veya {@code null}
     */
    public void render(GraphicsContext gc, double width, double height, SimulationSnapshot snapshot,
                       CoordinateMapper mapper, ViewOptions options, String selectedId, double now) {
        double range = snapshot.radar().range();
        mapper.update(width, height, range * VIEW_EXTENT_FACTOR, 16);

        gc.setGlobalAlpha(1.0);
        gc.setLineDashes((double[]) null);
        gc.setLineCap(StrokeLineCap.ROUND);
        gc.setFill(BACKGROUND);
        gc.fillRect(0, 0, width, height);

        drawGrid(gc, mapper, range);
        if (snapshot.radar().active()) {
            drawSweep(gc, mapper, range, snapshot.radar().sweepAngle());
        }

        TargetView locked = snapshot.findTarget(snapshot.defense().targetId());
        TargetView selected = snapshot.findTarget(selectedId);

        if (options.showTrails()) {
            for (TargetView t : snapshot.targets()) {
                drawTrail(gc, mapper, t);
            }
        }
        // Radar verisi olmayan (algılanmamış/kayıp) hedef için rota tahmini çizilmez.
        if (selected != null && selected.detected() && options.showPrediction()) {
            drawPrediction(gc, mapper, selected);
        }
        if (locked != null) {
            drawTrackingLine(gc, mapper, locked, snapshot.defense().state(), now);
        }
        for (TargetView t : snapshot.targets()) {
            drawTarget(gc, mapper, t);
        }
        if (options.showLabels()) {
            drawLabels(gc, mapper, snapshot, selected, locked);
        }
        if (locked != null) {
            drawLockRing(gc, mapper, locked, snapshot.defense().state(), now);
        }
        if (selected != null) {
            drawSelection(gc, mapper, selected);
        }
        for (Effect effect : snapshot.effects()) {
            drawEffect(gc, mapper, effect);
        }
        drawDefenseIcon(gc, mapper, snapshot.defense().state(), now);
        drawHud(gc, width, height, snapshot);
        // Snapshot nesnelerine referans tutulmasın.
        Arrays.fill(labeledTargets, 0, placedLabels, null);
        placedLabels = 0;

        gc.setGlobalAlpha(1.0);
        gc.setLineDashes((double[]) null);
    }

    // ------------------------------------------------------------------ katmanlar

    private void drawGrid(GraphicsContext gc, CoordinateMapper m, double range) {
        double cx = m.getCenterX();
        double cy = m.getCenterY();
        double rangePx = m.toPixels(range);

        // Radar camı: merkezde hafif yeşil ışıma, kenara doğru koyulaşan radyal gradyan
        gc.setFill(new RadialGradient(0, 0, cx, cy, rangePx, false, CycleMethod.NO_CYCLE, SCOPE_STOPS));
        gc.fillOval(cx - rangePx, cy - rangePx, rangePx * 2, rangePx * 2);

        gc.setStroke(GRID);
        gc.setLineWidth(1);

        // Eksenler
        gc.setGlobalAlpha(0.10);
        gc.strokeLine(cx - rangePx, cy, cx + rangePx, cy);
        gc.strokeLine(cx, cy - rangePx, cx, cy + rangePx);
        double diag = rangePx * Math.sqrt(0.5);
        gc.strokeLine(cx - diag, cy - diag, cx + diag, cy + diag);
        gc.strokeLine(cx - diag, cy + diag, cx + diag, cy - diag);

        // Menzil halkaları ve etiketleri
        gc.setFont(RING_FONT);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.BOTTOM);
        for (double r = ringSpacing; r < range - 1; r += ringSpacing) {
            double px = m.toPixels(r);
            gc.setGlobalAlpha(0.22);
            gc.strokeOval(cx - px, cy - px, px * 2, px * 2);
            gc.setGlobalAlpha(0.65);
            gc.setFill(GRID_TEXT);
            gc.fillText(formatDistance(r), cx + 4, cy - px - 1);
        }

        // Kerteriz işaretleri: her 5°'de kısa, her 30°'de uzun çizgi ve derece etiketi
        gc.setLineWidth(1);
        gc.setFont(BEARING_FONT);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        for (int deg = 0; deg < 360; deg += 5) {
            double rad = Math.toRadians(deg);
            double sin = Math.sin(rad);
            double cos = Math.cos(rad);
            boolean major = deg % 30 == 0;
            double inner = rangePx + 2;
            double outer = rangePx + (major ? 9 : deg % 10 == 0 ? 6 : 4);
            gc.setGlobalAlpha(major ? 0.7 : 0.35);
            gc.strokeLine(cx + sin * inner, cy - cos * inner, cx + sin * outer, cy - cos * outer);
            if (major && deg % 90 != 0) {
                double lr = rangePx + 20;
                gc.setGlobalAlpha(0.55);
                gc.setFill(GRID_TEXT);
                gc.fillText("%03d".formatted(deg), cx + sin * lr, cy - cos * lr);
            }
        }
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.BOTTOM);
        gc.setFont(RING_FONT);

        // Menzil sınırı
        gc.setGlobalAlpha(0.55);
        gc.setLineWidth(1.5);
        gc.strokeOval(cx - rangePx, cy - rangePx, rangePx * 2, rangePx * 2);
        gc.setGlobalAlpha(0.85);
        gc.setFill(GRID_TEXT);
        gc.setTextBaseline(VPos.TOP);
        gc.fillText(formatDistance(range), cx + 4, cy - rangePx + 2);

        // Angajman menzili
        if (engagementRange < range) {
            double ePx = m.toPixels(engagementRange);
            gc.setStroke(LOCK);
            gc.setLineWidth(1);
            gc.setGlobalAlpha(0.25);
            gc.setLineDashes(DASH_LONG);
            gc.strokeOval(cx - ePx, cy - ePx, ePx * 2, ePx * 2);
            gc.setLineDashes((double[]) null);
        }

        // Sızma yarıçapı
        double sPx = Math.max(3, m.toPixels(escapeRadius));
        gc.setStroke(ESCAPED);
        gc.setGlobalAlpha(0.45);
        gc.setLineDashes(DASH_DOTTED);
        gc.strokeOval(cx - sPx, cy - sPx, sPx * 2, sPx * 2);
        gc.setLineDashes((double[]) null);

        // Pusula
        gc.setGlobalAlpha(0.9);
        gc.setFill(GRID_TEXT);
        gc.setFont(COMPASS_FONT);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setTextBaseline(VPos.CENTER);
        double off = rangePx + 20;
        gc.fillText("K", cx, cy - off);
        gc.fillText("G", cx, cy + off);
        gc.fillText("D", cx + off, cy);
        gc.fillText("B", cx - off, cy);
        gc.setGlobalAlpha(1.0);
    }

    private void drawSweep(GraphicsContext gc, CoordinateMapper m, double range, double sweepAngle) {
        double cx = m.getCenterX();
        double cy = m.getCenterY();
        double r = m.toPixels(range);
        double slice = SWEEP_TRAIL_DEG / SWEEP_SLICES;
        gc.setFill(SWEEP);
        for (int k = 0; k < SWEEP_SLICES; k++) {
            // Tarama saat yönünde döner; iz, mevcut açının gerisinde (daha küçük kerterizlerde) kalır.
            double alpha = SWEEP_MAX_ALPHA * (1.0 - (double) k / SWEEP_SLICES);
            gc.setGlobalAlpha(alpha * alpha / SWEEP_MAX_ALPHA);
            double startFx = 90.0 - (sweepAngle - k * slice);
            gc.fillArc(cx - r, cy - r, r * 2, r * 2, startFx, slice, ArcType.ROUND);
        }
        double rad = Math.toRadians(sweepAngle);
        double ex = cx + Math.sin(rad) * r;
        double ey = cy - Math.cos(rad) * r;
        gc.setStroke(SWEEP);
        // Işıma: geniş ve soluk çizginin üstüne ince ve parlak çizgi
        gc.setGlobalAlpha(0.12);
        gc.setLineWidth(8);
        gc.strokeLine(cx, cy, ex, ey);
        gc.setGlobalAlpha(0.95);
        gc.setLineWidth(2);
        gc.strokeLine(cx, cy, ex, ey);
        gc.setGlobalAlpha(1.0);
    }

    private void drawTrail(GraphicsContext gc, CoordinateMapper m, TargetView t) {
        int n = t.trailCount();
        if (n == 0) {
            return;
        }
        gc.setFill(colorFor(t));
        double base = t.detected() ? 0.55 : 0.25;
        for (int i = 0; i < n; i++) {
            double fade = (i + 1.0) / (n + 1.0);
            gc.setGlobalAlpha(base * fade);
            double size = 1.5 + 2.0 * fade;
            gc.fillOval(m.toScreenX(t.trailX()[i]) - size / 2, m.toScreenY(t.trailY()[i]) - size / 2, size, size);
        }
        gc.setGlobalAlpha(1.0);
    }

    private void drawPrediction(GraphicsContext gc, CoordinateMapper m, TargetView t) {
        double sx = m.toScreenX(t.x());
        double sy = m.toScreenY(t.y());
        double ex = m.toScreenX(t.x() + t.velocityX() * PREDICTION_SECONDS);
        double ey = m.toScreenY(t.y() + t.velocityY() * PREDICTION_SECONDS);
        gc.setStroke(PREDICTION);
        gc.setLineWidth(1.2);
        gc.setGlobalAlpha(0.8);
        gc.setLineDashes(DASH_SHORT);
        gc.strokeLine(sx, sy, ex, ey);
        gc.setLineDashes((double[]) null);
        gc.setFill(PREDICTION);
        for (double s = PREDICTION_MARK_INTERVAL; s <= PREDICTION_SECONDS; s += PREDICTION_MARK_INTERVAL) {
            double px = m.toScreenX(t.x() + t.velocityX() * s);
            double py = m.toScreenY(t.y() + t.velocityY() * s);
            gc.fillOval(px - 2, py - 2, 4, 4);
        }
        gc.setGlobalAlpha(1.0);
    }

    private void drawTrackingLine(GraphicsContext gc, CoordinateMapper m, TargetView t, DefenseState state, double now) {
        boolean engaging = state == DefenseState.ENGAGING;
        gc.setStroke(engaging ? ENGAGE : LOCK);
        gc.setLineWidth(engaging ? 2.0 : 1.2);
        gc.setGlobalAlpha(engaging ? 0.6 + 0.4 * Math.abs(Math.sin(now * 12)) : 0.7);
        gc.setLineDashes(DASH_LONG);
        gc.setLineDashOffset(-now * 30);
        gc.strokeLine(m.getCenterX(), m.getCenterY(), m.toScreenX(t.x()), m.toScreenY(t.y()));
        gc.setLineDashOffset(0);
        gc.setLineDashes((double[]) null);
        gc.setGlobalAlpha(1.0);
    }

    private void drawTarget(GraphicsContext gc, CoordinateMapper m, TargetView t) {
        double sx = m.toScreenX(t.x());
        double sy = m.toScreenY(t.y());
        Color color = colorFor(t);
        double alpha = t.detected() ? 1.0 : 0.45;
        double headingRad = Math.toRadians(t.heading());
        double dirX = Math.sin(headingRad);
        double dirY = -Math.cos(headingRad);

        // Algılanmış hedefin etrafında hafif ışıma
        if (t.detected()) {
            gc.setFill(color);
            gc.setGlobalAlpha(0.16);
            gc.fillOval(sx - GLOW_RADIUS, sy - GLOW_RADIUS, GLOW_RADIUS * 2, GLOW_RADIUS * 2);
        }

        // Yön çizgisi
        gc.setStroke(color);
        gc.setLineWidth(1.2);
        gc.setGlobalAlpha(alpha * 0.8);
        gc.strokeLine(sx, sy, sx + dirX * HEADING_LINE_PX, sy + dirY * HEADING_LINE_PX);

        gc.setGlobalAlpha(alpha);
        gc.setFill(color);
        double r = TARGET_SIZE;
        switch (t.type()) {
            case DRONE -> {
                // Yöne bakan üçgen
                polyX[0] = sx + dirX * r * 1.3;
                polyY[0] = sy + dirY * r * 1.3;
                double left = headingRad + Math.toRadians(140);
                double right = headingRad - Math.toRadians(140);
                polyX[1] = sx + Math.sin(left) * r;
                polyY[1] = sy - Math.cos(left) * r;
                polyX[2] = sx + Math.sin(right) * r;
                polyY[2] = sy - Math.cos(right) * r;
                gc.fillPolygon(polyX, polyY, 3);
            }
            case FAST_DRONE -> {
                // Elmas
                polyX[0] = sx;
                polyY[0] = sy - r * 1.15;
                polyX[1] = sx + r * 0.8;
                polyY[1] = sy;
                polyX[2] = sx;
                polyY[2] = sy + r * 1.15;
                polyX[3] = sx - r * 0.8;
                polyY[3] = sy;
                gc.fillPolygon(polyX, polyY, 4);
            }
            case MANEUVERING_DRONE -> {
                // Kare
                double h = r * 0.8;
                gc.fillRect(sx - h, sy - h, h * 2, h * 2);
            }
            case DECOY -> {
                // İçi boş daire
                double h = r * 0.8;
                gc.setStroke(color);
                gc.setLineWidth(2);
                gc.strokeOval(sx - h, sy - h, h * 2, h * 2);
                gc.fillOval(sx - 1.5, sy - 1.5, 3, 3);
            }
        }
        gc.setGlobalAlpha(1.0);
    }

    /**
     * Etiketleri çakışmayacak şekilde yerleştirir. Önce seçili hedef, kilitli hedef ve
     * öncelik listesi, ardından diğer algılanmış hedefler denenir; başka bir etiketle
     * çakışan etiket atlanır. Algılanmamış hedeflerin kimliği bilinmediği için etiketlenmez.
     */
    private void drawLabels(GraphicsContext gc, CoordinateMapper m, SimulationSnapshot snapshot,
                            TargetView selected, TargetView locked) {
        gc.setFont(LABEL_FONT);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.CENTER);
        gc.setFill(LABEL);
        gc.setGlobalAlpha(0.9);
        placedLabels = 0;
        tryLabel(gc, m, selected);
        tryLabel(gc, m, locked);
        for (SimulationSnapshot.PriorityEntry p : snapshot.priorities()) {
            tryLabel(gc, m, snapshot.findTarget(p.id()));
        }
        for (TargetView t : snapshot.targets()) {
            tryLabel(gc, m, t);
        }
        gc.setGlobalAlpha(1.0);
    }

    private void tryLabel(GraphicsContext gc, CoordinateMapper m, TargetView t) {
        if (t == null || !t.detected() || placedLabels >= MAX_LABELS) {
            return;
        }
        for (int i = 0; i < placedLabels; i++) {
            if (labeledTargets[i] == t) {
                return;
            }
        }
        String text = t.id() + " " + t.score();
        double x = m.toScreenX(t.x()) + TARGET_SIZE + 4;
        double y = m.toScreenY(t.y()) - TARGET_SIZE;
        double w = text.length() * LABEL_CHAR_WIDTH;
        double top = y - LABEL_HEIGHT / 2;
        for (int i = 0; i < placedLabels; i++) {
            if (x < labelRects[i * 4 + 2] && x + w > labelRects[i * 4]
                    && top < labelRects[i * 4 + 3] && top + LABEL_HEIGHT > labelRects[i * 4 + 1]) {
                return;
            }
        }
        labelRects[placedLabels * 4] = x;
        labelRects[placedLabels * 4 + 1] = top;
        labelRects[placedLabels * 4 + 2] = x + w;
        labelRects[placedLabels * 4 + 3] = top + LABEL_HEIGHT;
        labeledTargets[placedLabels] = t;
        placedLabels++;
        gc.fillText(text, x, y);
    }

    private void drawLockRing(GraphicsContext gc, CoordinateMapper m, TargetView t, DefenseState state, double now) {
        double sx = m.toScreenX(t.x());
        double sy = m.toScreenY(t.y());
        boolean engaging = state == DefenseState.ENGAGING;
        double r = engaging ? 14 + 3 * Math.sin(now * 14) : 15;
        gc.setStroke(engaging ? ENGAGE : LOCK);
        gc.setLineWidth(1.6);
        gc.setGlobalAlpha(0.95);
        gc.strokeOval(sx - r, sy - r, r * 2, r * 2);
        double tick = 6;
        gc.strokeLine(sx - r - tick, sy, sx - r + 2, sy);
        gc.strokeLine(sx + r - 2, sy, sx + r + tick, sy);
        gc.strokeLine(sx, sy - r - tick, sx, sy - r + 2);
        gc.strokeLine(sx, sy + r - 2, sx, sy + r + tick);
        gc.setGlobalAlpha(1.0);
    }

    private void drawSelection(GraphicsContext gc, CoordinateMapper m, TargetView t) {
        double sx = m.toScreenX(t.x());
        double sy = m.toScreenY(t.y());
        double r = 20;
        gc.setStroke(SELECTION);
        gc.setLineWidth(1.2);
        gc.setGlobalAlpha(0.9);
        gc.setLineDashes(DASH_SHORT);
        gc.strokeOval(sx - r, sy - r, r * 2, r * 2);
        gc.setLineDashes((double[]) null);
        gc.setGlobalAlpha(1.0);
    }

    private void drawEffect(GraphicsContext gc, CoordinateMapper m, Effect effect) {
        double sx = m.toScreenX(effect.x());
        double sy = m.toScreenY(effect.y());
        double p = effect.progress();
        double fade = 1.0 - p;
        switch (effect.type()) {
            case PING -> {
                double r = 4 + 26 * p;
                gc.setStroke(PING);
                gc.setLineWidth(2 * fade + 0.5);
                gc.setGlobalAlpha(fade);
                gc.strokeOval(sx - r, sy - r, r * 2, r * 2);
            }
            case NEUTRALIZED -> {
                double core = 10 * fade + 2;
                gc.setFill(NEUTRALIZED);
                gc.setGlobalAlpha(0.85 * fade);
                gc.fillOval(sx - core, sy - core, core * 2, core * 2);
                double r = 6 + 24 * p;
                gc.setStroke(NEUTRALIZED);
                gc.setLineWidth(1.5);
                gc.setGlobalAlpha(fade);
                gc.strokeOval(sx - r, sy - r, r * 2, r * 2);
            }
            case MISS -> {
                double r = 6 + 12 * p;
                gc.setStroke(MISS);
                gc.setLineWidth(1.2);
                gc.setGlobalAlpha(fade);
                gc.setLineDashes(DASH_SHORT);
                gc.strokeOval(sx - r, sy - r, r * 2, r * 2);
                gc.setLineDashes((double[]) null);
            }
            case ESCAPED -> {
                double r = 8 + 50 * p;
                gc.setStroke(ESCAPED);
                gc.setLineWidth(2.5 * fade + 0.5);
                gc.setGlobalAlpha(fade);
                gc.strokeOval(sx - r, sy - r, r * 2, r * 2);
            }
        }
        gc.setGlobalAlpha(1.0);
    }

    private void drawDefenseIcon(GraphicsContext gc, CoordinateMapper m, DefenseState state, double now) {
        double cx = m.getCenterX();
        double cy = m.getCenterY();
        Color color = switch (state) {
            case IDLE -> DEFENSE_IDLE;
            case SELECTING, TRACKING -> LOCK;
            case ENGAGING -> ENGAGE;
            case COOLDOWN -> DEFENSE_COOLDOWN;
        };
        double r = 9;
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(60 * i + 30);
            polyX[i] = cx + Math.cos(a) * r;
            polyY[i] = cy + Math.sin(a) * r;
        }
        gc.setGlobalAlpha(0.25);
        gc.setFill(color);
        gc.fillPolygon(polyX, polyY, 6);
        gc.setGlobalAlpha(1.0);
        gc.setStroke(color);
        gc.setLineWidth(2);
        gc.strokePolygon(polyX, polyY, 6);
        double pulse = state == DefenseState.IDLE ? 2.5 : 2.5 + Math.abs(Math.sin(now * 6)) * 1.5;
        gc.fillOval(cx - pulse, cy - pulse, pulse * 2, pulse * 2);
    }

    private void drawHud(GraphicsContext gc, double width, double height, SimulationSnapshot s) {
        gc.setFont(HUD_FONT);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.TOP);
        gc.setGlobalAlpha(0.85);
        gc.setFill(GRID_TEXT);
        gc.fillText(String.format(Locale.ROOT, "RADAR %s   TARAMA %03.0f°   MENZİL %s",
                s.radar().active() ? "AÇIK" : "KAPALI", s.radar().sweepAngle(),
                formatDistance(s.radar().range())), 12, 10);
        String def = s.defense().targetId() == null
                ? "SAVUNMA %s".formatted(s.defense().state().label())
                : "SAVUNMA %s → %s".formatted(s.defense().state().label(), s.defense().targetId());
        gc.fillText(def, 12, 28);

        gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText("Yalnızca simülasyon — soyut model", width - 12, 10);

        String overlay = switch (s.state()) {
            case READY -> "HAZIR — BAŞLAT'a veya SPACE'e bas";
            case PAUSED -> "DURAKLATILDI";
            case FINISHED -> "SİMÜLASYON BİTTİ";
            case RUNNING -> null;
        };
        if (!s.radar().active() && s.state() == SimulationState.RUNNING) {
            overlay = "RADAR KAPALI";
        }
        if (overlay != null) {
            gc.setFont(OVERLAY_FONT);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.setTextBaseline(VPos.BOTTOM);
            gc.setGlobalAlpha(0.75);
            gc.setFill(OVERLAY_TEXT);
            gc.fillText(overlay, width / 2, height - 18);
        }
        gc.setGlobalAlpha(1.0);
    }

    // ------------------------------------------------------------------ yardımcılar

    /** Tehdit seviyesine göre renk; algılanmamış hedefler soluk gri. */
    public static Color colorFor(TargetView t) {
        if (!t.detected()) {
            return UNDETECTED;
        }
        return colorFor(t.level());
    }

    public static Color colorFor(ThreatLevel level) {
        return switch (level) {
            case LOW -> LOW;
            case MEDIUM -> MEDIUM;
            case HIGH -> HIGH;
        };
    }

    private static String formatDistance(double meters) {
        return meters >= 1000
                ? String.format(Locale.ROOT, "%.1f km", meters / 1000.0)
                : String.format(Locale.ROOT, "%.0f m", meters);
    }
}

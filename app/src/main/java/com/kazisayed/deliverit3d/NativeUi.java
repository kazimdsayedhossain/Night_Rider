package com.kazisayed.deliverit3d;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Transparent Low-Poly Android Mobile HUD & UI.
 * Features:
 * - Standard Mobile Loading Screen with game branding, low-poly loading bar, percentage readout (no technical asset file names)
 * - Transparent Low-Poly Controls (Drive & Brake pedals, Steer arrowheads, Pause) with vector chevrons and zero neon colors
 * - Racing-style Speedometer with floating transparent white digits only (no background box or gauge)
 * - Top-Left Route info pill with low-poly chamfered frame and pure vector glyphs (zero emojis/ascii)
 * - Top-Right Radar Minimap with translucent bezel and low-poly chevron markers
 * - Restructured 2-Column Transparent Settings Dashboard with grouped low-poly frosted cards, aligned controls, and live slider value badges
 */
public final class NativeUi extends FrameLayout {
    // Frosted Low-Poly Color Palette (Translucent Silver, Graphite & White - Zero Neon)
    private static final int COLOR_OVERLAY_DARK = 0xF5080C14;
    private static final int COLOR_OVERLAY_TRANSPARENT = 0x66080C14; // Preserved transparent frosted glass
    private static final int COLOR_MODAL_BG = 0xEE0B111A;
    private static final int COLOR_CARD_FILL = 0x14FFFFFF;
    private static final int COLOR_CARD_STROKE = 0x2AFFFFFF;
    private static final int COLOR_BTN_FILL = 0x1AFFFFFF;
    private static final int COLOR_BTN_STROKE = 0x40FFFFFF;
    private static final int COLOR_BTN_PRIMARY_FILL = 0xEEF8FAFC;
    private static final int COLOR_WHITE = 0xFFF8FAFC;
    private static final int COLOR_WHITE_TRANSPARENT = 0xEEFFFFFF;
    private static final int COLOR_MUTED = 0xFF94A3B8;
    private static final int COLOR_SILVER = 0xFFCBD5E1;
    private static final int COLOR_DIM = 0xFF64748B;

    private final MainActivity activity;
    private final DispatchGame game;
    private final PlayerInput input;
    private final SaveStore saves;
    private final TiltController tilt;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;

    private final FrameLayout controls;
    private View activePanel;
    private String section = "";
    private DispatchGame.State lastState;
    private DispatchGame.Snapshot data;

    // Loading Screen Elements
    private LowPolyProgressBar loadingBar;
    private TextView loadingPercent;

    /** Geometric chamfered polygon drawable for low-poly cards, buttons and badges. */
    public static final class ChamferedDrawable extends Drawable {
        private final int fillColor;
        private final int strokeColor;
        private final float strokeWidth;
        private final float chamfer;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        public ChamferedDrawable(int fillColor, int strokeColor, float strokeWidth, float chamfer) {
            this.fillColor = fillColor;
            this.strokeColor = strokeColor;
            this.strokeWidth = strokeWidth;
            this.chamfer = chamfer;
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            path.reset();
            float l = bounds.left + strokeWidth * 0.5f;
            float t = bounds.top + strokeWidth * 0.5f;
            float r = bounds.right - strokeWidth * 0.5f;
            float b = bounds.bottom - strokeWidth * 0.5f;
            float ch = Math.min(chamfer, Math.min((r - l) * 0.45f, (b - t) * 0.45f));
            path.moveTo(l + ch, t);
            path.lineTo(r - ch, t);
            path.lineTo(r, t + ch);
            path.lineTo(r, b - ch);
            path.lineTo(r - ch, b);
            path.lineTo(l + ch, b);
            path.lineTo(l, b - ch);
            path.lineTo(l, t + ch);
            path.close();
        }

        @Override
        public void draw(Canvas canvas) {
            if (fillColor != 0) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(fillColor);
                canvas.drawPath(path, p);
            }
            if (strokeWidth > 0 && strokeColor != 0) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(strokeWidth);
                p.setColor(strokeColor);
                canvas.drawPath(path, p);
            }
        }

        @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter colorFilter) { p.setColorFilter(colorFilter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Low-poly horizontal progress bar with chamfered track and fill. */
    public static final class LowPolyProgressBar extends View {
        private float progress = 0f;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float chamfer;

        public LowPolyProgressBar(Context context, float chamfer) {
            super(context);
            this.chamfer = chamfer;
        }

        public void setProgress(float p) {
            this.progress = Math.max(0f, Math.min(1f, p));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float ch = Math.min(chamfer, h * 0.45f);

            // 1. Background track
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x18FFFFFF);
            drawChamferedRect(canvas, 0, 0, w, h, ch, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.2f);
            paint.setColor(0x35FFFFFF);
            drawChamferedRect(canvas, 0.6f, 0.6f, w - 0.6f, h - 0.6f, ch, paint);

            // 2. Active fill
            if (progress > 0.01f) {
                float fillW = Math.max(ch * 2, w * progress);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xEEFFFFFF);
                drawChamferedRect(canvas, 1.5f, 1.5f, fillW - 1.5f, h - 1.5f, Math.max(1f, ch - 1f), paint);
            }
        }
    }

    public enum ControlType { DRIVE, BRAKE, LEFT, RIGHT, PAUSE }

    /**
     * Transparent low-poly faceted control view.
     * Replaces old circular colored pedals and steer buttons with chamfered transparent glass polygons.
     */
    public final class LowPolyControlView extends View {
        private final ControlType type;
        private final float chamfer;
        private final Consumer<Boolean> onStateChange;
        private boolean isDown;
        private int activePointerId = -1;

        public LowPolyControlView(Context context, ControlType type, float chamfer, Consumer<Boolean> callback) {
            super(context);
            this.type = type;
            this.chamfer = chamfer;
            this.onStateChange = callback;
            setClickable(true);
            setFocusable(false);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            int index = event.getActionIndex();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (!isDown) {
                        activePointerId = event.getPointerId(index);
                        setDown(true);
                        activity.feedback(12);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP:
                    if (event.getPointerId(index) == activePointerId) {
                        setDown(false);
                        activePointerId = -1;
                        performClick();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    setDown(false);
                    activePointerId = -1;
                    return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        private void setDown(boolean down) {
            if (isDown != down) {
                isDown = down;
                if (onStateChange != null) onStateChange.accept(down);
                animate().scaleX(down ? 0.93f : 1.0f).scaleY(down ? 0.93f : 1.0f).setDuration(50).start();
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float cx = w * 0.5f;
            float cy = h * 0.5f;

            // 1. Transparent low-poly faceted surface
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(isDown ? 0x48FFFFFF : 0x14FFFFFF);
            drawChamferedRect(canvas, 1, 1, w - 1, h - 1, chamfer, paint);

            // 2. Translucent silver facet border
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(px(isDown ? 2.2f : 1.3f));
            paint.setColor(isDown ? 0xDDFFFFFF : 0x3EFFFFFF);
            drawChamferedRect(canvas, 1, 1, w - 1, h - 1, chamfer, paint);

            // 3. Vector Icons & Typography (Strictly no neon, no ascii emojis)
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(isDown ? 0xFFFFFFFF : 0xDDFFFFFF);

            switch (type) {
                case DRIVE -> {
                    // Double sharp upward chevrons
                    paint.setStrokeWidth(px(isDown ? 3.4f : 2.8f));
                    drawChevronUp(canvas, cx, cy - px(9), px(13), px(8), px(6), paint);

                    // DRIVE text label
                    paint.setStyle(Paint.Style.FILL);
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setTextSize(px(10.5f));
                    paint.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
                    paint.setLetterSpacing(0.12f);
                    paint.setColor(isDown ? 0xFFFFFFFF : 0xCCFFFFFF);
                    canvas.drawText("DRIVE", cx, cy + px(22), paint);
                    paint.setLetterSpacing(0f);
                }
                case BRAKE -> {
                    // Double sharp downward chevrons
                    paint.setStrokeWidth(px(isDown ? 3.4f : 2.8f));
                    drawChevronDown(canvas, cx, cy - px(9), px(13), px(8), px(6), paint);

                    // BRAKE text label
                    paint.setStyle(Paint.Style.FILL);
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setTextSize(px(10.5f));
                    paint.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
                    paint.setLetterSpacing(0.12f);
                    paint.setColor(isDown ? 0xFFFFFFFF : 0xCCFFFFFF);
                    canvas.drawText("BRAKE", cx, cy + px(22), paint);
                    paint.setLetterSpacing(0f);
                }
                case LEFT -> {
                    // Sharp left chevron
                    paint.setStrokeWidth(px(isDown ? 3.2f : 2.5f));
                    drawChevronLeft(canvas, cx, cy, px(11), px(9), paint);
                }
                case RIGHT -> {
                    // Sharp right chevron
                    paint.setStrokeWidth(px(isDown ? 3.2f : 2.5f));
                    drawChevronRight(canvas, cx, cy, px(11), px(9), paint);
                }
                case PAUSE -> {
                    // Minimalist twin vertical pause bars
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(isDown ? 0xFFFFFFFF : 0xDDFFFFFF);
                    drawPauseBars(canvas, cx, cy, px(3.5f), px(13), px(3f), paint);
                }
            }
        }
    }

    public NativeUi(MainActivity context, DispatchGame game, PlayerInput input, SaveStore saves, TiltController tilt) {
        super(context);
        this.activity = context;
        this.game = game;
        this.input = input;
        this.saves = saves;
        this.tilt = tilt;
        this.dp = getResources().getDisplayMetrics().density;
        setWillNotDraw(false);
        setFocusable(false);
        controls = new FrameLayout(context);
        addView(controls, new LayoutParams(-1, -1));
        buildControls();
    }

    private int px(float n) { return Math.round(n * dp); }

    private static void drawChamferedRect(Canvas c, float l, float t, float r, float b, float ch, Paint p) {
        Path path = new Path();
        ch = Math.min(ch, Math.min((r - l) * 0.45f, (b - t) * 0.45f));
        path.moveTo(l + ch, t);
        path.lineTo(r - ch, t);
        path.lineTo(r, t + ch);
        path.lineTo(r, b - ch);
        path.lineTo(r - ch, b);
        path.lineTo(l + ch, b);
        path.lineTo(l, b - ch);
        path.lineTo(l, t + ch);
        path.close();
        c.drawPath(path, p);
    }

    private static void drawChevronUp(Canvas c, float cx, float cy, float w, float h, float gap, Paint p) {
        Path path = new Path();
        // Top chevron
        path.moveTo(cx - w, cy - gap);
        path.lineTo(cx, cy - gap - h);
        path.lineTo(cx + w, cy - gap);
        // Bottom chevron
        path.moveTo(cx - w, cy + gap);
        path.lineTo(cx, cy + gap - h);
        path.lineTo(cx + w, cy + gap);
        c.drawPath(path, p);
    }

    private static void drawChevronDown(Canvas c, float cx, float cy, float w, float h, float gap, Paint p) {
        Path path = new Path();
        // Top chevron
        path.moveTo(cx - w, cy - gap);
        path.lineTo(cx, cy - gap + h);
        path.lineTo(cx + w, cy - gap);
        // Bottom chevron
        path.moveTo(cx - w, cy + gap);
        path.lineTo(cx, cy + gap + h);
        path.lineTo(cx + w, cy + gap);
        c.drawPath(path, p);
    }

    private static void drawChevronLeft(Canvas c, float cx, float cy, float w, float h, Paint p) {
        Path path = new Path();
        path.moveTo(cx + w * 0.5f, cy - h);
        path.lineTo(cx - w * 0.5f, cy);
        path.lineTo(cx + w * 0.5f, cy + h);
        c.drawPath(path, p);
    }

    private static void drawChevronRight(Canvas c, float cx, float cy, float w, float h, Paint p) {
        Path path = new Path();
        path.moveTo(cx - w * 0.5f, cy - h);
        path.lineTo(cx + w * 0.5f, cy);
        path.lineTo(cx - w * 0.5f, cy + h);
        c.drawPath(path, p);
    }

    private static void drawPauseBars(Canvas c, float cx, float cy, float barW, float barH, float gap, Paint p) {
        c.drawRect(cx - gap - barW, cy - barH * 0.5f, cx - gap, cy + barH * 0.5f, p);
        c.drawRect(cx + gap, cy - barH * 0.5f, cx + gap + barW, cy + barH * 0.5f, p);
    }

    private TextView makeText(String content, float sp, int color, boolean bold) {
        TextView t = new TextView(getContext());
        t.setText(content);
        t.setTextColor(color);
        t.setTextSize(sp);
        t.setTypeface(Typeface.create(bold ? "sans-serif-bold" : "sans-serif", Typeface.NORMAL));
        t.setFontFeatureSettings("tnum");
        return t;
    }

    private Button makeChamferedButton(String label, boolean primary, Runnable action) {
        Button b = new Button(getContext());
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14f);
        b.setTypeface(Typeface.create("sans-serif-bold", Typeface.NORMAL));
        b.setTextColor(primary ? 0xFF0B111A : COLOR_WHITE);
        b.setBackground(new ChamferedDrawable(
                primary ? COLOR_BTN_PRIMARY_FILL : COLOR_BTN_FILL,
                primary ? 0 : COLOR_BTN_STROKE,
                px(1.2f),
                px(12)
        ));
        b.setMinHeight(px(48));
        b.setPadding(px(18), px(10), px(18), px(10));
        b.setOnClickListener(v -> {
            activity.feedback(12);
            action.run();
        });
        return b;
    }

    /** Builds the new transparent low-poly in-game control elements. */
    private void buildControls() {
        controls.removeAllViews();

        // 1. Transparent Low-Poly Pause Button (Top Right, adjacent to radar)
        LowPolyControlView pause = new LowPolyControlView(getContext(), ControlType.PAUSE, px(8), down -> {
            if (down) {
                activity.feedback(10);
                activity.command("pause");
            }
        });
        LayoutParams pp = new LayoutParams(px(44), px(44), Gravity.TOP | Gravity.RIGHT);
        pp.topMargin = px(14);
        pp.rightMargin = px(96);
        controls.addView(pause, pp);

        // 2. Large Low-Poly Faceted BRAKE Pedal (Bottom Left, 88dp)
        LowPolyControlView brake = new LowPolyControlView(getContext(), ControlType.BRAKE, px(16), down -> input.brake = down);
        LayoutParams bp = new LayoutParams(px(88), px(88), Gravity.BOTTOM | Gravity.LEFT);
        bp.leftMargin = px(22);
        bp.bottomMargin = px(22);
        controls.addView(brake, bp);

        // 3. Large Low-Poly Faceted DRIVE Pedal (Bottom Right, 92dp)
        LowPolyControlView drive = new LowPolyControlView(getContext(), ControlType.DRIVE, px(16), down -> input.pedal = down);
        LayoutParams dpParam = new LayoutParams(px(92), px(92), Gravity.BOTTOM | Gravity.RIGHT);
        dpParam.rightMargin = px(22);
        dpParam.bottomMargin = px(22);
        controls.addView(drive, dpParam);

        // 4. Low-Poly Steer Left & Right Buttons (Bottom Left, beside brake)
        LowPolyControlView left = new LowPolyControlView(getContext(), ControlType.LEFT, px(10), down -> input.left = down);
        LayoutParams lp = new LayoutParams(px(52), px(52), Gravity.BOTTOM | Gravity.LEFT);
        lp.leftMargin = px(122);
        lp.bottomMargin = px(24);
        controls.addView(left, lp);

        LowPolyControlView right = new LowPolyControlView(getContext(), ControlType.RIGHT, px(10), down -> input.right = down);
        LayoutParams rp = new LayoutParams(px(52), px(52), Gravity.BOTTOM | Gravity.LEFT);
        rp.leftMargin = px(184);
        rp.bottomMargin = px(24);
        controls.addView(right, rp);
    }

    public void refresh(DispatchGame.Snapshot snapshot) {
        this.data = snapshot;
        if (snapshot == null) return;
        if (lastState != snapshot.state()) {
            lastState = snapshot.state();
            input.release();
            if (snapshot.state() == DispatchGame.State.PLAYING) {
                removeActivePanel();
                buildControls();
                controls.setVisibility(VISIBLE);
            } else {
                controls.setVisibility(GONE);
                showState(snapshot.state());
            }
        }
        // Standard loading bar & percentage updates (suppressing all technical asset filenames)
        if (snapshot.state() == DispatchGame.State.LOADING) {
            if (loadingBar != null) {
                loadingBar.setProgress(snapshot.loading());
            }
            if (loadingPercent != null) {
                loadingPercent.setText(Math.round(snapshot.loading() * 100) + "%");
            }
        }
        invalidate();
    }

    /** Standard mobile game loading screen overlay (clean title, low-poly loading bar, percentage only). */
    private void showLoadingScreen() {
        removeActivePanel();
        section = "loading";

        FrameLayout loadingLayout = new FrameLayout(getContext());
        loadingLayout.setBackgroundColor(COLOR_OVERLAY_DARK);

        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);

        // Title
        TextView title = makeText("NIGHT RIDER", 32, COLOR_WHITE, true);
        title.setLetterSpacing(0.20f);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        // Subtitle
        TextView sub = makeText("COURIER DELIVERY ARCADE", 11, COLOR_MUTED, true);
        sub.setLetterSpacing(0.24f);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, px(4), 0, px(32));
        box.addView(sub);

        // Low-Poly Loading Bar (Chamfered track & fill)
        loadingBar = new LowPolyProgressBar(getContext(), px(4));
        LinearLayout.LayoutParams barParam = new LinearLayout.LayoutParams(px(280), px(8));
        box.addView(loadingBar, barParam);

        // Percentage Readout (Never show technical asset logs / file paths)
        loadingPercent = makeText("0%", 12f, COLOR_SILVER, true);
        loadingPercent.setGravity(Gravity.CENTER);
        loadingPercent.setPadding(0, px(12), 0, 0);
        box.addView(loadingPercent);

        LayoutParams centerParam = new LayoutParams(-2, -2, Gravity.CENTER);
        loadingLayout.addView(box, centerParam);

        activePanel = loadingLayout;
        addView(loadingLayout, new LayoutParams(-1, -1));
    }

    /** Creates a standard centered modal scroll container with low-poly chamfered card styling. */
    private LinearLayout openCenteredModal(String name, int widthDp) {
        input.release();
        section = name;
        removeActivePanel();

        ScrollView scroll = new ScrollView(getContext());
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackground(new ChamferedDrawable(COLOR_MODAL_BG, COLOR_CARD_STROKE, px(1.2f), px(16)));

        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(px(26), px(22), px(26), px(24));
        scroll.addView(panel, new ScrollView.LayoutParams(-1, -2));

        LayoutParams p = new LayoutParams(px(widthDp), -2, Gravity.CENTER);
        p.topMargin = p.bottomMargin = px(14);
        addView(scroll, p);
        scroll.getLayoutParams().height = Math.min(px(420), Math.max(px(240), getHeight() - px(28)));

        activePanel = scroll;
        return panel;
    }

    private void modalHeader(LinearLayout panel, String eyebrow, String title, String subtitle) {
        if (!eyebrow.isEmpty()) {
            TextView eb = makeText(eyebrow.toUpperCase(Locale.ROOT), 10.5f, COLOR_SILVER, true);
            eb.setLetterSpacing(0.14f);
            panel.addView(eb);
        }

        if (!title.isEmpty()) {
            TextView h = makeText(title, 22, COLOR_WHITE, true);
            h.setPadding(0, px(2), 0, px(2));
            panel.addView(h);
        }

        if (!subtitle.isEmpty()) {
            TextView sub = makeText(subtitle, 12f, COLOR_MUTED, false);
            sub.setLineSpacing(px(2), 1.0f);
            panel.addView(sub);
        }
    }

    private void addModalAction(LinearLayout panel, String title, boolean primary, Runnable action) {
        Button b = makeChamferedButton(title, primary, action);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, px(48));
        p.topMargin = px(10);
        panel.addView(b, p);
    }

    private void addStatTile(LinearLayout parent, String label, String value, int valColor) {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(new ChamferedDrawable(COLOR_CARD_FILL, COLOR_CARD_STROKE, px(1f), px(10)));
        box.setPadding(px(14), px(10), px(14), px(10));
        TextView l = makeText(label.toUpperCase(Locale.ROOT), 9.5f, COLOR_MUTED, true);
        l.setLetterSpacing(0.08f);
        box.addView(l);
        TextView v = makeText(value, 15.5f, valColor, true);
        v.setPadding(0, px(2), 0, 0);
        box.addView(v);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1.0f);
        p.leftMargin = p.rightMargin = px(4);
        parent.addView(box, p);
    }

    private void showState(DispatchGame.State state) {
        switch (state) {
            case LOADING -> showLoadingScreen();
            case MENU -> {
                LinearLayout panel = openCenteredModal("menu", 460);
                // Clean header with no additional tutorial/briefing text
                modalHeader(panel, "ROUTE " + String.format(Locale.US, "%02d", data.level()), data.district(), "");

                LinearLayout r1 = new LinearLayout(getContext());
                r1.setOrientation(LinearLayout.HORIZONTAL);
                r1.setPadding(0, px(10), 0, px(4));
                addStatTile(r1, "Quota", data.quota() + " parcels", COLOR_WHITE);
                int sec = (int) Math.ceil(data.time());
                addStatTile(r1, "Time Limit", String.format(Locale.US, "%d:%02d", sec / 60, sec % 60), COLOR_SILVER);
                addStatTile(r1, "On Rack", data.inventory() + " parcels", COLOR_WHITE);
                panel.addView(r1);

                addModalAction(panel, "START SHIFT ▶", true, () -> activity.command("start"));
                addModalAction(panel, "GARAGE & GEAR", false, () -> garage(true));
                addModalAction(panel, "SETTINGS", false, this::settingsModal);
            }
            case PAUSED -> {
                LinearLayout panel = openCenteredModal("pause", 420);
                // Clean header without "Take a Breather" or "Your route and timer are currently on hold"
                modalHeader(panel, "PAUSED", "", "");
                addModalAction(panel, "RESUME SHIFT ▶", true, () -> activity.command("resume"));
                addModalAction(panel, "RESTART ROUTE", false, () -> activity.command("restart"));
                addModalAction(panel, "SETTINGS", false, this::settingsModal);
                addModalAction(panel, "GARAGE", false, () -> garage(true));
                addModalAction(panel, "MAIN MENU", false, () -> activity.command("menu"));
            }
            case RESULT -> {
                LinearLayout panel = openCenteredModal("result", 460);
                boolean won = data.won();
                // Clean header without verbose paragraphs
                modalHeader(panel, won ? "SHIFT COMPLETE" : "ROUTE TERMINATED", won ? "Route Mastered!" : "Courier Down", "");

                LinearLayout stats = new LinearLayout(getContext());
                stats.setOrientation(LinearLayout.HORIZONTAL);
                stats.setPadding(0, px(10), 0, px(4));
                addStatTile(stats, "Delivered", data.delivered() + " / " + data.quota(), won ? COLOR_WHITE : COLOR_SILVER);
                addStatTile(stats, "Score", String.format(Locale.US, "%,d", data.score()), COLOR_WHITE);
                addStatTile(stats, "Wallet", "$" + String.format(Locale.US, "%,d", data.cash()), COLOR_SILVER);
                panel.addView(stats);

                addModalAction(panel, won ? "NEXT ROUTE ▶" : "RETRY SHIFT ↺", true, () -> activity.command(won ? "next" : "restart"));
                addModalAction(panel, "GARAGE", false, () -> garage(true));
                addModalAction(panel, "MAIN MENU", false, () -> activity.command("menu"));
            }
            case ERROR -> {
                LinearLayout panel = openCenteredModal("error", 400);
                modalHeader(panel, "ERROR", "Dispatch Failure", data.status());
                addModalAction(panel, "CLOSE APP", false, activity::finish);
            }
            default -> removeActivePanel();
        }
    }

    /**
     * Restructured 2-Column Transparent Settings Dashboard:
     * - Preserves background transparency (frosted obsidian glass, city streets visible)
     * - Parallel 2-column landscape dashboard:
     *     Column 1: Steering & Controls
     *     Column 2: Audio & Ambience + Display & Fidelity
     * - Clean right-alignment with live readout badges and zero neon colors.
     */
    private void settingsModal() {
        input.release();
        section = "settings";
        removeActivePanel();

        // 1. Full-Screen Root Container with preserved high transparency
        LinearLayout fullScreen = new LinearLayout(getContext());
        fullScreen.setOrientation(LinearLayout.VERTICAL);
        fullScreen.setBackgroundColor(COLOR_OVERLAY_TRANSPARENT);

        // 2. Header Bar with Back Button & Centered Screen Title
        RelativeLayout header = new RelativeLayout(getContext());
        header.setPadding(px(24), px(12), px(24), px(10));

        Button backBtn = new Button(getContext());
        backBtn.setText("◀  BACK");
        backBtn.setTextSize(12f);
        backBtn.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
        backBtn.setTextColor(COLOR_WHITE);
        backBtn.setBackground(new ChamferedDrawable(COLOR_BTN_FILL, COLOR_BTN_STROKE, px(1.2f), px(10)));
        backBtn.setPadding(px(16), px(6), px(16), px(6));
        backBtn.setOnClickListener(v -> {
            activity.feedback(10);
            showState(lastState);
        });
        RelativeLayout.LayoutParams backParam = new RelativeLayout.LayoutParams(-2, px(38));
        backParam.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        backParam.addRule(RelativeLayout.CENTER_VERTICAL);
        header.addView(backBtn, backParam);

        TextView title = makeText("SETTINGS", 18, COLOR_WHITE, true);
        title.setLetterSpacing(0.16f);
        RelativeLayout.LayoutParams titleParam = new RelativeLayout.LayoutParams(-2, -2);
        titleParam.addRule(RelativeLayout.CENTER_IN_PARENT);
        header.addView(title, titleParam);

        fullScreen.addView(header, new LinearLayout.LayoutParams(-1, -2));

        // 3. Scrollable Content Area (wrapping 2-column layout for small screen resilience)
        ScrollView scroll = new ScrollView(getContext());
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout body = new LinearLayout(getContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(px(24), 0, px(24), px(16));

        // 2-Column Landscape Row
        LinearLayout twoCols = new LinearLayout(getContext());
        twoCols.setOrientation(LinearLayout.HORIZONTAL);
        twoCols.setGravity(Gravity.TOP);

        // Left Column: STEERING & CONTROLS
        LinearLayout colLeft = new LinearLayout(getContext());
        colLeft.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpLeft = new LinearLayout.LayoutParams(0, -2, 1.0f);
        lpLeft.rightMargin = px(10);
        twoCols.addView(colLeft, lpLeft);

        LinearLayout gControls = addSettingCard(colLeft, "01 · STEERING & CONTROLS");
        addSettingSwitch(gControls, "Tilt Steering", "Steer vehicle by phone tilt", "native-tilt", true);
        addSettingDivider(gControls);
        addSettingSlider(gControls, "Tilt Sensitivity", "Responsiveness to angle", "native-tilt-sensitivity", 1.0f, 0.4f, 1.8f, "x", true);
        addSettingDivider(gControls);
        addSettingSwitch(gControls, "Invert Steering", "Reverse tilt direction", "native-tilt-invert", false);
        addSettingDivider(gControls);
        addSettingSwitch(gControls, "Haptic Feedback", "Tactile touch vibrations", "native-haptics", true);

        // Right Column: AUDIO & SOUNDS + DISPLAY & FIDELITY
        LinearLayout colRight = new LinearLayout(getContext());
        colRight.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpRight = new LinearLayout.LayoutParams(0, -2, 1.0f);
        lpRight.leftMargin = px(10);
        twoCols.addView(colRight, lpRight);

        LinearLayout gAudio = addSettingCard(colRight, "02 · AUDIO & SOUNDS");
        addSettingSlider(gAudio, "Master Volume", "Overall game volume", "dispatch-volume-master", 0.75f, 0f, 1.0f, "%", false);
        addSettingDivider(gAudio);
        addSettingSlider(gAudio, "Sound Effects", "Engine sound and horn", "dispatch-volume-sfx", 0.8f, 0f, 1.0f, "%", false);
        addSettingDivider(gAudio);
        addSettingSlider(gAudio, "Rain & Ambience", "Weather & city ambience", "dispatch-volume-ambient", 0.5f, 0f, 1.0f, "%", false);

        LinearLayout gDisplay = addSettingCard(colRight, "03 · DISPLAY & FIDELITY");
        addSettingSwitch(gDisplay, "Headlight Beam", "Night street illumination", "native-headlights", true);
        addSettingDivider(gDisplay);
        addSettingSwitch(gDisplay, "Dynamic Weather", "Rain cycles and wet reflections", "native-weather-auto", true);

        body.addView(twoCols, new LinearLayout.LayoutParams(-1, -2));

        // Bottom Done Action
        Button doneBtn = makeChamferedButton("DONE  ✓", true, () -> showState(lastState));
        LinearLayout.LayoutParams doneParam = new LinearLayout.LayoutParams(px(240), px(44));
        doneParam.gravity = Gravity.CENTER_HORIZONTAL;
        doneParam.topMargin = px(10);
        body.addView(doneBtn, doneParam);

        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        fullScreen.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        activePanel = fullScreen;
        addView(fullScreen, new LayoutParams(-1, -1));
    }

    private LinearLayout addSettingCard(LinearLayout parent, String sectionTitle) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(new ChamferedDrawable(COLOR_CARD_FILL, COLOR_CARD_STROKE, px(1.2f), px(12)));
        card.setPadding(px(16), px(12), px(16), px(12));

        TextView secText = makeText(sectionTitle, 10.5f, COLOR_SILVER, true);
        secText.setLetterSpacing(0.14f);
        secText.setPadding(0, 0, 0, px(6));
        card.addView(secText);

        LinearLayout.LayoutParams cardParam = new LinearLayout.LayoutParams(-1, -2);
        cardParam.bottomMargin = px(10);
        parent.addView(card, cardParam);
        return card;
    }

    private void addSettingDivider(LinearLayout card) {
        View div = new View(getContext());
        div.setBackgroundColor(0x14FFFFFF);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, px(1));
        dp.topMargin = dp.bottomMargin = px(6);
        card.addView(div, dp);
    }

    private void addSettingSwitch(LinearLayout card, String title, String subtitle, String key, boolean fallback) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textCol = new LinearLayout(getContext());
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView t = makeText(title, 13f, COLOR_WHITE, true);
        textCol.addView(t);
        if (!subtitle.isEmpty()) {
            TextView sub = makeText(subtitle, 10.5f, COLOR_MUTED, false);
            sub.setPadding(0, px(1), 0, 0);
            textCol.addView(sub);
        }
        row.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1.0f));

        Switch sw = new Switch(getContext());
        sw.setChecked(saves.flag(key, fallback));
        // Frosted white thumb and track (strictly no neon green/orange)
        sw.setThumbTintList(ColorStateList.valueOf(COLOR_WHITE));
        sw.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{0x88FFFFFF, 0x2AFFFFFF}
        ));
        sw.setOnCheckedChangeListener((b, checked) -> {
            activity.feedback(10);
            saves.put(key, checked);
            activity.applySettings();
        });
        row.addView(sw, new LinearLayout.LayoutParams(-2, -2));

        card.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addSettingSlider(LinearLayout card, String title, String subtitle, String key, float fallback, float min, float max, String unitSuffix, boolean isMultiplier) {
        LinearLayout topRow = new LinearLayout(getContext());
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textCol = new LinearLayout(getContext());
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView t = makeText(title, 13f, COLOR_WHITE, true);
        textCol.addView(t);
        if (!subtitle.isEmpty()) {
            TextView sub = makeText(subtitle, 10.5f, COLOR_MUTED, false);
            sub.setPadding(0, px(1), 0, 0);
            textCol.addView(sub);
        }
        topRow.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1.0f));

        // Live readout badge
        float curVal = saves.decimal(key, fallback);
        String initialText = isMultiplier ? String.format(Locale.US, "%.1fx", curVal) : Math.round(curVal * 100) + "%";
        TextView badge = makeText(initialText, 11f, COLOR_WHITE, true);
        badge.setBackground(new ChamferedDrawable(0x22FFFFFF, 0x38FFFFFF, px(1f), px(6)));
        badge.setPadding(px(8), px(2), px(8), px(2));
        topRow.addView(badge, new LinearLayout.LayoutParams(-2, -2));

        card.addView(topRow, new LinearLayout.LayoutParams(-1, -2));

        // Slider track
        SeekBar slider = new SeekBar(getContext());
        slider.setMax(100);
        int currentProgress = Math.round((curVal - min) / (max - min) * 100);
        slider.setProgress(currentProgress);
        slider.setProgressTintList(ColorStateList.valueOf(0xCCFFFFFF));
        slider.setThumbTintList(ColorStateList.valueOf(0xFFFFFFFF));
        slider.setProgressBackgroundTintList(ColorStateList.valueOf(0x24FFFFFF));

        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                if (fromUser) {
                    float val = min + (p / 100.0f) * (max - min);
                    saves.put(key, val);
                    badge.setText(isMultiplier ? String.format(Locale.US, "%.1fx", val) : p + "%");
                    activity.applySettings();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar b) {}
            @Override public void onStopTrackingTouch(SeekBar b) {}
        });

        LinearLayout.LayoutParams sliderParam = new LinearLayout.LayoutParams(-1, px(32));
        sliderParam.topMargin = px(2);
        card.addView(slider, sliderParam);
    }

    private void garage(boolean vehicles) {
        LinearLayout panel = openCenteredModal("garage", 480);
        modalHeader(panel, "GARAGE", vehicles ? "Vehicle Fleet" : "Courier Outfits",
                "Wallet: $" + String.format(Locale.US, "%,d", data.cash()) + "  ·  " + data.tickets() + " Tickets");

        if (vehicles) {
            shopItem(panel, true, "bike", "Classic Delivery Bicycle", 0, 0);
            shopItem(panel, true, "scooter", "Urban Delivery Scooter", 1200, 0);
            shopItem(panel, true, "superbike", "Heavy Cruiser Motorcycle", 3500, 0);
            shopItem(panel, true, "gold", "Special Edition Gold Bike", 0, 40);
            addModalAction(panel, "SWITCH TO HEADWEAR", false, () -> garage(false));
        } else {
            shopItem(panel, false, "cap", "Courier Cap", 0, 0);
            shopItem(panel, false, "helmet", "Aerodynamic Helmet", 800, 0);
            shopItem(panel, false, "crown", "Golden Crown", 0, 25);
            addModalAction(panel, "SWITCH TO VEHICLES", false, () -> garage(true));
        }
        addModalAction(panel, "DONE", true, () -> showState(lastState));
    }

    private void shopItem(LinearLayout panel, boolean isVehicles, String id, String name, int priceCash, int priceTokens) {
        boolean owned = saves.owned(isVehicles).contains(id);
        boolean equipped = saves.text(isVehicles ? "deliver3d_vehicle" : "deliver3d_hat", isVehicles ? "bike" : "cap").equals(id);

        String status = equipped ? "✓ EQUIPPED" : owned ? "EQUIP" : priceTokens > 0 ? (priceTokens + " Tickets") : ("$" + priceCash);
        Button btn = makeChamferedButton(name + "  ·  " + status, equipped, () -> {
            activity.post(() -> game.buy(isVehicles, id, priceCash, priceTokens));
            postDelayed(() -> {
                data = game.snapshot;
                garage(isVehicles);
            }, 120);
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, px(46));
        p.topMargin = px(8);
        panel.addView(btn, p);
    }

    private void removeActivePanel() {
        if (activePanel != null) {
            removeView(activePanel);
            activePanel = null;
        }
        loadingBar = null;
        loadingPercent = null;
        section = "";
    }

    public boolean closePanel() {
        if (section.equals("settings") || section.equals("garage")) {
            showState(lastState);
            return true;
        }
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (data == null || data.state() != DispatchGame.State.PLAYING) return;

        canvas.save();
        canvas.translate(getPaddingLeft(), getPaddingTop());
        canvas.scale(dp, dp);
        float w = (getWidth() - getPaddingLeft() - getPaddingRight()) / dp;
        float h = (getHeight() - getPaddingTop() - getPaddingBottom()) / dp;

        // 1. Top-Left: Low-Poly Chamfered Route Info Pill (Zero emojis / ASCII)
        float pillW = 286, pillH = 36;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x220B111A);
        drawChamferedRect(canvas, 16, 14, 16 + pillW, 14 + pillH, 8, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f);
        paint.setColor(0x35FFFFFF);
        drawChamferedRect(canvas, 16, 14, 16 + pillW, 14 + pillH, 8, paint);

        // Vector icon glyphs and metrics inside Pill
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
        paint.setTextSize(11);
        paint.setColor(COLOR_WHITE);
        canvas.drawText("R" + String.format(Locale.US, "%02d", data.level()), 28, 36, paint);

        // Stopwatch vector icon & time
        drawVectorStopwatch(canvas, 68, 32, 5, paint);
        int sec = Math.max(0, (int) Math.ceil(data.time()));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(sec < 18 ? 0xFFFFAA88 : COLOR_WHITE_TRANSPARENT);
        canvas.drawText(String.format(Locale.US, "%d:%02d", sec / 60, sec % 60), 78, 36, paint);

        // Parcel isometric cube vector icon & quota
        paint.setColor(COLOR_WHITE_TRANSPARENT);
        drawVectorCube(canvas, 134, 32, 5, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawText(data.delivered() + "/" + data.quota(), 144, 36, paint);

        // Rack inventory vector icon
        drawVectorBag(canvas, 192, 32, 5, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawText(data.inventory() + "/4", 202, 36, paint);

        // Distance vector flag/pin
        drawVectorPin(canvas, 244, 32, 5, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_SILVER);
        canvas.drawText(Math.round(data.distance()) + "m", 253, 36, paint);

        // 2. Top-Right: Low-Poly Radar Minimap
        drawRadarMap(canvas, w - 48, 48, 34);

        // 3. Bottom-Center: RACING SPEEDOMETER (Pure Floating White Digits Only - ZERO Background Card)
        int kmh = Math.round(Math.abs(data.speed()) * 3.6f);
        String speedStr = String.valueOf(kmh);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        paint.setTextSkewX(-0.16f); // Dynamic racing slant
        paint.setTextSize(42);
        paint.setColor(COLOR_WHITE_TRANSPARENT); // Crisp transparent white
        paint.setShadowLayer(4, 0, 2, 0x99000000); // Subtle drop-shadow for crystal-clear readability over road

        float numW = paint.measureText(speedStr);

        paint.setTextSize(11);
        paint.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
        float unitW = paint.measureText("KM/H");

        float totalW = numW + 6 + unitW;
        float startX = (w - totalW) * 0.5f;
        float baselineY = h - 34;

        // Draw digits
        paint.setTextSize(42);
        paint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        canvas.drawText(speedStr, startX, baselineY, paint);

        // Draw "KM/H" sub-label
        paint.setTextSize(11);
        paint.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
        paint.setColor(0x88FFFFFF);
        paint.setLetterSpacing(0.08f);
        canvas.drawText("KM/H", startX + numW + 6, baselineY - 4, paint);

        // Reset paint effects
        paint.clearShadowLayer();
        paint.setTextSkewX(0f);
        paint.setLetterSpacing(0f);

        // Tilt steering center bar (if active)
        if (input.tilt) {
            float cx = w * 0.5f;
            float barY = h - 14;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.2f);
            paint.setColor(0x28FFFFFF);
            canvas.drawLine(cx - 24, barY, cx + 24, barY, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(COLOR_WHITE);
            canvas.drawCircle(cx + data.steering() * 22, barY, 2.5f, paint);
        }

        // 4. Center Screen Notification Toast (Low-poly translucent pill)
        if (!data.notice().isEmpty()) {
            float noteW = Math.min(w - 220, 360);
            float noteX = w * 0.5f - noteW * 0.5f;
            float noteY = h - 110;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x350B111A);
            drawChamferedRect(canvas, noteX, noteY, noteX + noteW, noteY + 28, 8, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.0f);
            paint.setColor(0x38FFFFFF);
            drawChamferedRect(canvas, noteX, noteY, noteX + noteW, noteY + 28, 8, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(12);
            paint.setTypeface(Typeface.create("sans-serif-bold", Typeface.NORMAL));
            paint.setColor(COLOR_WHITE);
            canvas.drawText(data.notice(), w * 0.5f, noteY + 18, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        canvas.restore();
    }

    private static void drawVectorStopwatch(Canvas c, float cx, float cy, float r, Paint p) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f);
        c.drawCircle(cx, cy + 1, r, p);
        c.drawLine(cx - 2, cy - r, cx + 2, cy - r, p);
        c.drawLine(cx, cy + 1, cx, cy + 1 - r * 0.5f, p);
        c.drawLine(cx, cy + 1, cx + r * 0.45f, cy + 1, p);
    }

    private static void drawVectorCube(Canvas c, float cx, float cy, float s, Paint p) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f);
        Path top = new Path();
        top.moveTo(cx, cy - s);
        top.lineTo(cx + s * 0.866f, cy - s * 0.5f);
        top.lineTo(cx, cy);
        top.lineTo(cx - s * 0.866f, cy - s * 0.5f);
        top.close();
        c.drawPath(top, p);
        c.drawLine(cx, cy, cx, cy + s, p);
        c.drawLine(cx - s * 0.866f, cy - s * 0.5f, cx - s * 0.866f, cy + s * 0.5f, p);
        c.drawLine(cx + s * 0.866f, cy - s * 0.5f, cx + s * 0.866f, cy + s * 0.5f, p);
        c.drawLine(cx - s * 0.866f, cy + s * 0.5f, cx, cy + s, p);
        c.drawLine(cx + s * 0.866f, cy + s * 0.5f, cx, cy + s, p);
    }

    private static void drawVectorBag(Canvas c, float cx, float cy, float s, Paint p) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f);
        Path bag = new Path();
        bag.moveTo(cx - s * 0.7f, cy - s * 0.3f);
        bag.lineTo(cx + s * 0.7f, cy - s * 0.3f);
        bag.lineTo(cx + s * 0.6f, cy + s * 0.8f);
        bag.lineTo(cx - s * 0.6f, cy + s * 0.8f);
        bag.close();
        c.drawPath(bag, p);
        Path handle = new Path();
        handle.moveTo(cx - s * 0.35f, cy - s * 0.3f);
        handle.quadTo(cx, cy - s * 0.85f, cx + s * 0.35f, cy - s * 0.3f);
        c.drawPath(handle, p);
    }

    private static void drawVectorPin(Canvas c, float cx, float cy, float s, Paint p) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f);
        Path pin = new Path();
        pin.moveTo(cx, cy + s);
        pin.lineTo(cx - s * 0.6f, cy - s * 0.2f);
        pin.lineTo(cx + s * 0.6f, cy - s * 0.2f);
        pin.close();
        c.drawPath(pin, p);
    }

    private void drawRadarMap(Canvas c, float cx, float cy, float radius) {
        // Outer bezel (translucent dark glass with silver border)
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x260B111A);
        c.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f);
        paint.setColor(0x35FFFFFF);
        c.drawCircle(cx, cy, radius, paint);

        // Clip circle
        c.save();
        Path clip = new Path();
        clip.addCircle(cx, cy, radius - 2, Path.Direction.CW);
        c.clipPath(clip);

        // Road grid lines
        float scale = 0.32f;
        paint.setColor(0x20FFFFFF);
        paint.setStrokeWidth(1.6f);
        for (int i = -2; i <= 2; i++) {
            float roadX = (Math.round(data.x() / 160) + i) * 160;
            float sx = cx - (roadX - data.x()) * scale;
            c.drawLine(sx, cy - radius, sx, cy + radius, paint);

            float roadZ = (Math.round(data.z() / 160) + i) * 160;
            float sy = cy - (roadZ - data.z()) * scale;
            c.drawLine(cx - radius, sy, cx + radius, sy, paint);
        }

        // Markers: Crisp white for customer drops, light ice-blue for depots
        paint.setStyle(Paint.Style.FILL);
        for (DispatchGame.Marker m : data.markers()) {
            paint.setColor(m.type() == 0 ? COLOR_WHITE : 0xCCA5B4FC);
            c.drawCircle(cx - (m.x() - data.x()) * scale, cy - (m.z() - data.z()) * scale, 2.6f, paint);
        }
        c.restore();

        // Player orientation arrow (Low-poly faceted chevron)
        c.save();
        c.translate(cx, cy);
        c.rotate(-data.yaw() * 180 / (float) Math.PI);
        Path arrow = new Path();
        arrow.moveTo(0, -6);
        arrow.lineTo(-4, 5);
        arrow.lineTo(0, 2.5f);
        arrow.lineTo(4, 5);
        arrow.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_WHITE);
        c.drawPath(arrow, paint);
        c.restore();

        // North indicator 'N'
        paint.setColor(COLOR_MUTED);
        paint.setTextSize(9);
        paint.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
        c.drawText("N", cx - 3.5f, cy - radius + 11, paint);
    }
}

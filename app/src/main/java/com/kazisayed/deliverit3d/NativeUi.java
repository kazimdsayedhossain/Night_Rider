package com.kazisayed.deliverit3d;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/**
 * Authentic Android mobile arcade HUD & UI.
 * Features:
 * - Clean minimal Top-Left Route & Delivery badge (Route, Time, Deliveries, Bag, Distance)
 * - Top-Right circular Radar Minimap & Pause button
 * - Large glowing circular BRAKE (bottom-left) and DRIVE (bottom-right) pedals with tactile touch response
 * - Centered bottom digital Speedometer with dynamic gauge
 * - Polished Shift Briefing Start Menu, Pause breather, instant Game Over retry screen, and categorized Settings
 */
public final class NativeUi extends FrameLayout {
    private static final int BG_PANEL = 0xF40A0F17;
    private static final int BG_CARD = 0xE6101722;
    private static final int BORDER_COLOR = 0xFF223042;
    private static final int BORDER_MUTED = 0xFF1E293B;
    private static final int COLOR_AMBER = 0xFFF59E0B;
    private static final int COLOR_RED = 0xFFEF4444;
    private static final int COLOR_GREEN = 0xFF10B981;
    private static final int COLOR_WHITE = 0xFFF8FAFC;
    private static final int COLOR_MUTED = 0xFF94A3B8;
    private static final int COLOR_DARK_BTN = 0xFF161F2C;

    private final MainActivity activity;
    private final DispatchGame game;
    private final PlayerInput input;
    private final SaveStore saves;
    private final TiltController tilt;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;

    private final FrameLayout controls;
    private LinearLayout panel;
    private ScrollView scroll;
    private String section = "";
    private DispatchGame.State lastState;
    private DispatchGame.Snapshot data;
    private TextView loadingText;

    /** Interactive glowing circular mobile pedal control. */
    private final class PedalView extends View {
        private final boolean isBrake;
        private final String label;
        private final java.util.function.Consumer<Boolean> onStateChange;
        private boolean isDown;
        private int activePointerId = -1;

        public PedalView(Context context, boolean isBrake, String label, java.util.function.Consumer<Boolean> callback) {
            super(context);
            this.isBrake = isBrake;
            this.label = label;
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
                        activity.feedback(16);
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
                onStateChange.accept(down);
                animate().scaleX(down ? 0.93f : 1.0f).scaleY(down ? 0.93f : 1.0f).setDuration(60).start();
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() * 0.5f;
            float cy = getHeight() * 0.5f;
            float r = Math.min(cx, cy) - px(3);
            int primary = isBrake ? COLOR_RED : COLOR_AMBER;

            // Outer glow halo
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(px(isDown ? 5.5f : 3.5f));
            paint.setColor(isDown ? (primary | 0x88000000) : (primary & 0x44FFFFFF));
            canvas.drawCircle(cx, cy, r, paint);

            // Inner dark glass surface
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(isDown ? (isBrake ? 0xFF351218 : 0xFF36240C) : 0xEE111822);
            canvas.drawCircle(cx, cy, r - px(2), paint);

            // Ring stroke
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(px(2.5f));
            paint.setColor(isDown ? primary : (isBrake ? 0xDDDC2626 : 0xDDF59E0B));
            canvas.drawCircle(cx, cy, r - px(2), paint);

            // Icon & text
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            if (isBrake) {
                paint.setColor(isDown ? COLOR_WHITE : COLOR_RED);
                paint.setTextSize(px(20));
                paint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
                canvas.drawText("(!)", cx, cy - px(4), paint);

                paint.setColor(isDown ? COLOR_WHITE : 0xEEF1F5F9);
                paint.setTextSize(px(12));
                paint.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
                canvas.drawText("BRAKE", cx, cy + px(16), paint);
            } else {
                paint.setColor(isDown ? COLOR_WHITE : COLOR_AMBER);
                paint.setTextSize(px(20));
                paint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
                canvas.drawText("⚡", cx, cy - px(4), paint);

                paint.setColor(isDown ? COLOR_WHITE : 0xEEF1F5F9);
                paint.setTextSize(px(12));
                paint.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
                canvas.drawText("DRIVE", cx, cy + px(16), paint);
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

    private GradientDrawable rounded(int color, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(px(radiusDp));
        if (strokeDp > 0) d.setStroke(px(strokeDp), strokeColor);
        return d;
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

    private Button makeButton(String label, boolean primary, Runnable action) {
        Button b = new Button(getContext());
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14.5f);
        b.setTypeface(Typeface.create("sans-serif-bold", Typeface.NORMAL));
        b.setTextColor(primary ? Color.BLACK : COLOR_WHITE);
        b.setBackground(rounded(primary ? COLOR_AMBER : COLOR_DARK_BTN, 14, primary ? 0 : BORDER_COLOR, 1));
        b.setMinHeight(px(50));
        b.setPadding(px(18), px(10), px(18), px(10));
        b.setOnClickListener(v -> {
            activity.feedback(12);
            action.run();
        });
        return b;
    }

    private void buildControls() {
        controls.removeAllViews();

        // 1. Pause Button: Circular glass at top right
        Button pause = new Button(getContext());
        pause.setText("❚❚");
        pause.setTextSize(14);
        pause.setTypeface(Typeface.create("sans-serif-bold", Typeface.NORMAL));
        pause.setTextColor(COLOR_WHITE);
        pause.setBackground(rounded(0xCC111822, 22, BORDER_COLOR, 1.2f));
        pause.setContentDescription("Pause");
        pause.setOnClickListener(v -> {
            activity.feedback(10);
            activity.command("pause");
        });
        LayoutParams pp = new LayoutParams(px(46), px(46), Gravity.TOP | Gravity.RIGHT);
        pp.topMargin = px(14);
        pp.rightMargin = px(96);
        controls.addView(pause, pp);

        // 2. Large Circular BRAKE Pedal (Bottom Left, 88dp)
        PedalView brake = new PedalView(getContext(), true, "BRAKE", down -> input.brake = down);
        LayoutParams bp = new LayoutParams(px(88), px(88), Gravity.BOTTOM | Gravity.LEFT);
        bp.leftMargin = px(22);
        bp.bottomMargin = px(20);
        controls.addView(brake, bp);

        // 3. Large Circular DRIVE Pedal (Bottom Right, 96dp)
        PedalView drive = new PedalView(getContext(), false, "DRIVE", down -> input.pedal = down);
        LayoutParams dpParam = new LayoutParams(px(96), px(96), Gravity.BOTTOM | Gravity.RIGHT);
        dpParam.rightMargin = px(22);
        dpParam.bottomMargin = px(20);
        controls.addView(drive, dpParam);

        // 4. Touch Steer buttons (Always available alongside tilt steering)
        Button left = new Button(getContext());
        left.setText("◀");
        left.setTextSize(18);
        left.setTextColor(COLOR_WHITE);
        left.setBackground(rounded(0xDD111822, 25, BORDER_COLOR, 1.5f));
        hold(left, v -> input.left = v);
        LayoutParams lp = new LayoutParams(px(56), px(56), Gravity.BOTTOM | Gravity.LEFT);
        lp.leftMargin = px(122);
        lp.bottomMargin = px(22);
        controls.addView(left, lp);

        Button right = new Button(getContext());
        right.setText("▶");
        right.setTextSize(18);
        right.setTextColor(COLOR_WHITE);
        right.setBackground(rounded(0xDD111822, 25, BORDER_COLOR, 1.5f));
        hold(right, v -> input.right = v);
        LayoutParams rp = new LayoutParams(px(56), px(56), Gravity.BOTTOM | Gravity.LEFT);
        rp.leftMargin = px(188);
        rp.bottomMargin = px(22);
        controls.addView(right, rp);
    }

    private void hold(Button button, java.util.function.Consumer<Boolean> update) {
        button.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    update.accept(true);
                    v.setAlpha(0.65f);
                    activity.feedback(10);
                    return true;
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    update.accept(false);
                    v.setAlpha(1.0f);
                    return true;
                }
            }
            return false;
        });
    }

    public void refresh(DispatchGame.Snapshot snapshot) {
        this.data = snapshot;
        if (snapshot == null) return;
        if (lastState != snapshot.state()) {
            lastState = snapshot.state();
            input.release();
            if (snapshot.state() == DispatchGame.State.PLAYING) {
                removePanel();
                buildControls();
                controls.setVisibility(VISIBLE);
            } else {
                controls.setVisibility(GONE);
                showState(snapshot.state());
            }
        }
        if (snapshot.state() == DispatchGame.State.LOADING && loadingText != null) {
            loadingText.setText(snapshot.status() + " (" + Math.round(snapshot.loading() * 100) + "%)");
        }
        invalidate();
    }

    private void openPanel(String name) {
        input.release();
        section = name;
        removePanelViews();
        scroll = new ScrollView(getContext());
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackground(rounded(BG_PANEL, 22, BORDER_COLOR, 1.2f));

        panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(px(26), px(22), px(26), px(24));
        scroll.addView(panel, new ScrollView.LayoutParams(-1, -2));

        LayoutParams p = new LayoutParams(px(name.equals("loading") ? 360 : 460), -2, Gravity.CENTER);
        p.topMargin = p.bottomMargin = px(14);
        addView(scroll, p);
        scroll.getLayoutParams().height = Math.min(px(400), Math.max(px(240), getHeight() - px(28)));
    }

    private void modalHeader(String eyebrow, String title, String subtitle) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView eb = makeText(eyebrow.toUpperCase(Locale.ROOT), 11, COLOR_AMBER, true);
        eb.setLetterSpacing(0.12f);
        row.addView(eb);
        panel.addView(row);

        TextView h = makeText(title, 24, COLOR_WHITE, true);
        h.setPadding(0, px(4), 0, px(4));
        panel.addView(h);

        if (!subtitle.isEmpty()) {
            TextView sub = makeText(subtitle, 13, COLOR_MUTED, false);
            sub.setLineSpacing(px(2), 1.0f);
            panel.addView(sub);
        }
    }

    private void addAction(String title, boolean primary, Runnable action) {
        Button b = makeButton(title, primary, action);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, px(52));
        p.topMargin = px(10);
        panel.addView(b, p);
    }

    private void addStatTile(LinearLayout parent, String label, String value, int valColor) {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(rounded(BG_CARD, 12, BORDER_MUTED, 1));
        box.setPadding(px(14), px(10), px(14), px(10));
        TextView l = makeText(label.toUpperCase(Locale.ROOT), 9.5f, COLOR_MUTED, true);
        l.setLetterSpacing(0.08f);
        box.addView(l);
        TextView v = makeText(value, 16, valColor, true);
        v.setPadding(0, px(2), 0, 0);
        box.addView(v);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1.0f);
        p.leftMargin = p.rightMargin = px(4);
        parent.addView(box, p);
    }

    private void showState(DispatchGame.State state) {
        switch (state) {
            case LOADING -> {
                openPanel("loading");
                modalHeader("DISPATCH 3D", "Preparing City", "Streaming high-definition streets, vehicles and assets...");
                loadingText = makeText("Loading... 0%", 14, COLOR_AMBER, true);
                loadingText.setPadding(0, px(16), 0, px(8));
                panel.addView(loadingText);
            }
            case MENU -> {
                openPanel("menu");
                modalHeader("ROUTE " + String.format(Locale.US, "%02d", data.level()) + " · SHIFT BRIEFING",
                        data.district(),
                        "Pick up parcels and make deliveries before the clock runs out.");

                LinearLayout r1 = new LinearLayout(getContext());
                r1.setOrientation(LinearLayout.HORIZONTAL);
                r1.setPadding(0, px(12), 0, px(4));
                addStatTile(r1, "Quota", data.quota() + " parcels", COLOR_AMBER);
                int sec = (int) Math.ceil(data.time());
                addStatTile(r1, "Time Limit", String.format(Locale.US, "%d:%02d", sec / 60, sec % 60), COLOR_WHITE);
                addStatTile(r1, "On Rack", data.inventory() + " parcels", COLOR_WHITE);
                panel.addView(r1);

                addAction("START SHIFT ▶", true, () -> activity.command("start"));
                addAction("🚲  GARAGE & GEAR", false, () -> garage(true));
                addAction("⚙  SETTINGS", false, this::settingsModal);
            }
            case PAUSED -> {
                openPanel("pause");
                modalHeader("SHIFT PAUSED", "Take a Breather", "Your route and timer are currently on hold.");
                addAction("RESUME SHIFT ▶", true, () -> activity.command("resume"));
                addAction("↺  RESTART ROUTE", false, () -> activity.command("restart"));
                addAction("⚙  SETTINGS", false, this::settingsModal);
                addAction("🚲  GARAGE", false, () -> garage(true));
                addAction("MAIN MENU", false, () -> activity.command("menu"));
            }
            case RESULT -> {
                openPanel("result");
                boolean won = data.won();
                if (won) {
                    modalHeader("SHIFT COMPLETE · ★★★", "Route Mastered!",
                            "All scheduled deliveries were successfully delivered on time.");
                } else {
                    modalHeader("ACCIDENT · SHIFT TERMINATED", "Courier Down!",
                            data.status() + "\nRoute ended before all deliveries were fulfilled.");
                }

                LinearLayout stats = new LinearLayout(getContext());
                stats.setOrientation(LinearLayout.HORIZONTAL);
                stats.setPadding(0, px(12), 0, px(4));
                addStatTile(stats, "Delivered", data.delivered() + " / " + data.quota(), won ? COLOR_GREEN : COLOR_AMBER);
                addStatTile(stats, "Score", String.format(Locale.US, "%,d", data.score()), COLOR_WHITE);
                addStatTile(stats, "Wallet", "$" + String.format(Locale.US, "%,d", data.cash()), COLOR_AMBER);
                panel.addView(stats);

                addAction(won ? "NEXT ROUTE ▶" : "RETRY SHIFT ↺", true, () -> activity.command(won ? "next" : "restart"));
                addAction("🚲  GARAGE", false, () -> garage(true));
                addAction("MAIN MENU", false, () -> activity.command("menu"));
            }
            case ERROR -> {
                openPanel("error");
                modalHeader("ERROR", "Dispatch Failure", data.status());
                addAction("CLOSE APP", false, activity::finish);
            }
            default -> removePanel();
        }
    }

    private void settingsModal() {
        openPanel("settings");
        modalHeader("SETTINGS", "Controls & Graphics", "Configure steering and visual fidelity.");

        // Section: Controls
        panel.addView(makeText("STEERING CONTROLS", 11, COLOR_AMBER, true));
        toggleSwitch("Tilt Phone like a Steering Wheel", "native-tilt", true);
        sliderRow("Tilt Sensitivity", "native-tilt-sensitivity", 1.0f, 0.4f, 1.8f);
        toggleSwitch("Invert Steering", "native-tilt-invert", false);
        toggleSwitch("Haptic Touch Feedback", "native-haptics", true);

        // Section: Audio
        panel.addView(makeText("AUDIO SETTINGS", 11, COLOR_AMBER, true));
        sliderRow("Master Volume", "dispatch-volume-master", 0.75f, 0, 1);
        sliderRow("SFX & Horn", "dispatch-volume-sfx", 0.8f, 0, 1);
        sliderRow("Rain & Traffic Ambience", "dispatch-volume-ambient", 0.5f, 0, 1);

        // Section: Display
        panel.addView(makeText("DISPLAY & WEATHER", 11, COLOR_AMBER, true));
        toggleSwitch("Bicycle Headlight Beam", "native-headlights", true);
        toggleSwitch("Changing Rain/Weather", "native-weather-auto", true);

        addAction("DONE", true, () -> showState(lastState));
    }

    private void toggleSwitch(String label, String key, boolean fallback) {
        Switch s = new Switch(getContext());
        s.setText(label);
        s.setTextColor(COLOR_WHITE);
        s.setTextSize(13.5f);
        s.setPadding(0, px(8), 0, px(8));
        s.setChecked(saves.flag(key, fallback));
        s.setOnCheckedChangeListener((b, checked) -> {
            saves.put(key, checked);
            activity.applySettings();
        });
        panel.addView(s);
    }

    private void sliderRow(String title, String key, float fallback, float min, float max) {
        TextView label = makeText(title, 12.5f, COLOR_MUTED, false);
        label.setPadding(0, px(6), 0, 0);
        panel.addView(label);
        SeekBar slider = new SeekBar(getContext());
        slider.setMax(100);
        slider.setProgress(Math.round((saves.decimal(key, fallback) - min) / (max - min) * 100));
        slider.setProgressTintList(android.content.res.ColorStateList.valueOf(COLOR_AMBER));
        slider.setThumbTintList(android.content.res.ColorStateList.valueOf(COLOR_AMBER));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int p, boolean user) {
                if (user) {
                    saves.put(key, min + p / 100f * (max - min));
                    activity.applySettings();
                }
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        panel.addView(slider, new LinearLayout.LayoutParams(-1, px(38)));
    }

    private void garage(boolean vehicles) {
        openPanel("garage");
        modalHeader("GARAGE", vehicles ? "Vehicle Fleet" : "Courier Outfits",
                "Wallet: $" + String.format(Locale.US, "%,d", data.cash()) + "  ·  " + data.tickets() + " Tickets");

        if (vehicles) {
            shopItem(true, "bike", "Classic Delivery Bicycle", 0, 0);
            shopItem(true, "scooter", "Urban Delivery Scooter", 1200, 0);
            shopItem(true, "superbike", "Heavy Cruiser Motorcycle", 3500, 0);
            shopItem(true, "gold", "Special Edition Gold Bike", 0, 40);
            addAction("SWITCH TO HEADWEAR", false, () -> garage(false));
        } else {
            shopItem(false, "cap", "Courier Cap", 0, 0);
            shopItem(false, "helmet", "Aerodynamic Helmet", 800, 0);
            shopItem(false, "crown", "Golden Crown", 0, 25);
            addAction("SWITCH TO VEHICLES", false, () -> garage(true));
        }
        addAction("DONE", true, () -> showState(lastState));
    }

    private void shopItem(boolean isVehicles, String id, String name, int priceCash, int priceTokens) {
        boolean owned = saves.owned(isVehicles).contains(id);
        boolean equipped = saves.text(isVehicles ? "deliver3d_vehicle" : "deliver3d_hat", isVehicles ? "bike" : "cap").equals(id);

        String status = equipped ? "✓ EQUIPPED" : owned ? "EQUIP" : priceTokens > 0 ? (priceTokens + " Tickets") : ("$" + priceCash);
        Button btn = makeButton(name + "  ·  " + status, equipped, () -> {
            activity.post(() -> game.buy(isVehicles, id, priceCash, priceTokens));
            postDelayed(() -> {
                data = game.snapshot;
                garage(isVehicles);
            }, 120);
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, px(48));
        p.topMargin = px(8);
        panel.addView(btn, p);
    }

    private void removePanelViews() {
        if (scroll != null) {
            removeView(scroll);
            scroll = null;
            panel = null;
        }
        loadingText = null;
    }

    private void removePanel() {
        removePanelViews();
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

        // 1. Top-Left: Sleek Route Badge Pill
        float pillW = 296, pillH = 38;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xDD0D131A);
        canvas.drawRoundRect(16, 14, 16 + pillW, 14 + pillH, 19, 19, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f);
        paint.setColor(BORDER_COLOR);
        canvas.drawRoundRect(16, 14, 16 + pillW, 14 + pillH, 19, 19, paint);

        // Text inside Pill
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
        paint.setTextSize(11);
        paint.setColor(COLOR_AMBER);
        canvas.drawText("ROUTE " + String.format(Locale.US, "%02d", data.level()), 30, 37, paint);

        int sec = Math.max(0, (int) Math.ceil(data.time()));
        paint.setColor(sec < 18 ? COLOR_AMBER : COLOR_WHITE);
        canvas.drawText("⏱ " + String.format(Locale.US, "%d:%02d", sec / 60, sec % 60), 102, 37, paint);

        paint.setColor(COLOR_WHITE);
        canvas.drawText("📦 " + data.delivered() + "/" + data.quota(), 160, 37, paint);
        canvas.drawText("🛍 " + data.inventory() + "/4", 206, 37, paint);

        paint.setColor(COLOR_AMBER);
        canvas.drawText("▲ " + Math.round(data.distance()) + "m", 250, 37, paint);

        // 2. Top-Right: Radar Minimap
        drawRadarMap(canvas, w - 48, 48, 34);

        // 3. Bottom-Center: Sleek Speedometer Card
        float speedW = 120, speedH = 46;
        float speedX = w * 0.5f - speedW * 0.5f;
        float speedY = h - 60;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xDD0D131A);
        canvas.drawRoundRect(speedX, speedY, speedX + speedW, speedY + speedH, 16, 16, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f);
        paint.setColor(BORDER_COLOR);
        canvas.drawRoundRect(speedX, speedY, speedX + speedW, speedY + speedH, 16, 16, paint);

        // Speed Number & KM/H
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        paint.setTextSize(26);
        paint.setColor(COLOR_WHITE);
        int kmh = Math.round(Math.abs(data.speed()) * 3.6f);
        canvas.drawText(String.valueOf(kmh), speedX + 32, speedY + 30, paint);

        paint.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
        paint.setTextSize(10.5f);
        paint.setColor(COLOR_AMBER);
        canvas.drawText("KM/H", speedX + 74, speedY + 28, paint);

        // Speedometer gauge line underneath
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF263344);
        canvas.drawRoundRect(speedX + 16, speedY + 37, speedX + speedW - 16, speedY + 39.5f, 1.5f, 1.5f, paint);
        paint.setColor(COLOR_AMBER);
        float fillRatio = Math.min(1.0f, kmh / 45.0f);
        canvas.drawRoundRect(speedX + 16, speedY + 37, speedX + 16 + (speedW - 32) * fillRatio, speedY + 39.5f, 1.5f, 1.5f, paint);

        // Tilt steering center indicator (if tilt active)
        if (input.tilt) {
            float cx = w * 0.5f;
            paint.setColor(0xFF334155);
            canvas.drawRoundRect(cx - 24, h - 9, cx + 24, h - 7, 1, 1, paint);
            paint.setColor(COLOR_AMBER);
            canvas.drawCircle(cx + data.steering() * 22, h - 8, 2.5f, paint);
        }

        // 4. Center Screen Notification / Toast
        if (!data.notice().isEmpty()) {
            float noteW = Math.min(w - 220, 360);
            float noteX = w * 0.5f - noteW * 0.5f;
            float noteY = h - 118;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xEE0D131A);
            canvas.drawRoundRect(noteX, noteY, noteX + noteW, noteY + 30, 15, 15, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.0f);
            paint.setColor(COLOR_AMBER);
            canvas.drawRoundRect(noteX, noteY, noteX + noteW, noteY + 30, 15, 15, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(12);
            paint.setTypeface(Typeface.create("sans-serif-bold", Typeface.NORMAL));
            paint.setColor(COLOR_WHITE);
            canvas.drawText(data.notice(), w * 0.5f, noteY + 19, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }

        canvas.restore();
    }

    private void drawRadarMap(Canvas c, float cx, float cy, float radius) {
        // Outer circular bezel
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xD20D131A);
        c.drawCircle(cx, cy, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.4f);
        paint.setColor(BORDER_COLOR);
        c.drawCircle(cx, cy, radius, paint);

        // Clip circle
        c.save();
        Path clip = new Path();
        clip.addCircle(cx, cy, radius - 2, Path.Direction.CW);
        c.clipPath(clip);

        // Road grid lines
        float scale = 0.32f;
        paint.setColor(0xFF263344);
        paint.setStrokeWidth(2.0f);
        for (int i = -2; i <= 2; i++) {
            float roadX = (Math.round(data.x() / 160) + i) * 160;
            float sx = cx - (roadX - data.x()) * scale;
            c.drawLine(sx, cy - radius, sx, cy + radius, paint);

            float roadZ = (Math.round(data.z() / 160) + i) * 160;
            float sy = cy - (roadZ - data.z()) * scale;
            c.drawLine(cx - radius, sy, cx + radius, sy, paint);
        }

        // Markers: Gold for Customers, Cyan for Depots
        paint.setStyle(Paint.Style.FILL);
        for (DispatchGame.Marker m : data.markers()) {
            paint.setColor(m.type() == 0 ? COLOR_AMBER : 0xFF38BDF8);
            c.drawCircle(cx - (m.x() - data.x()) * scale, cy - (m.z() - data.z()) * scale, 2.6f, paint);
        }
        c.restore();

        // Player orientation arrow
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

package com.kazisayed.deliverit3d;



import android.animation.*;

import android.content.Context;

import android.content.res.ColorStateList;

import android.graphics.*;

import android.graphics.drawable.Drawable;

import android.view.*;

import android.view.animation.*;

import android.widget.*;

import java.util.*;

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

    private int safeLeft = 0;

    private int safeTop = 0;

    private int safeRight = 0;

    private int safeBottom = 0;



    public void setSafeInsets(int left, int top, int right, int bottom) {

        this.safeLeft = left;

        this.safeTop = top;

        this.safeRight = right;

        this.safeBottom = bottom;

        requestLayout();

    }





        private LowPolyControlView btnLeft, btnRight, btnDrive, btnBrake, btnPause;

private final FrameLayout controls;

    // In-Game Dispatch Call Notification

    private LinearLayout dispatchCard;

    private TextView tvDispatchTitle, tvDispatchTimer, tvDispatchRoute, tvDispatchMeta;

    private TextView btnDispatchAccept, btnDispatchDecline;



    private View activePanel;
    private View centerModal;
    private TextView menuTitleView;
    private FrameLayout rightContentArea;

    private String section = "";

    private DispatchGame.State lastState;

    private DispatchGame.Snapshot data;



    // Loading Screen Elements

    private LowPolyProgressBar loadingBar;

    private TextView loadingPercent;

    private LoadingAtmosphereView loadingAtmosphere;

    private final List<Animator> loadingAnimators = new ArrayList<>();



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



    /**

     * Tactile multi-layered button drawable.

     * Features:

     * - Multi-layer shadows: Deep ambient drop shadow + sharp directional contact shadow.

     * - Chamfered modern button geometry with smooth lighting gradients.

     * - Top-edge specular highlight rim for physical 3D depth.

     * - Interactive press state: shadows compress and face drops down.

     */

    public static final class TactileButtonDrawable extends Drawable {

        private final boolean primary;

        private final float strokeWidth;

        private final float chamfer;

        private boolean pressed = false;



        private final Paint shadowPaint1 = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint shadowPaint2 = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);



        private final Path facePath = new Path();

        private final Path shadowPath1 = new Path();

        private final Path shadowPath2 = new Path();

        private final Path highlightPath = new Path();



        public TactileButtonDrawable(boolean primary, float strokeWidth, float chamfer) {

            this.primary = primary;

            this.strokeWidth = strokeWidth;

            this.chamfer = chamfer;



            fillPaint.setStyle(Paint.Style.FILL);

            strokePaint.setStyle(Paint.Style.STROKE);

            strokePaint.setStrokeWidth(strokeWidth);



            highlightPaint.setStyle(Paint.Style.STROKE);

            highlightPaint.setStrokeWidth(Math.max(1f, strokeWidth * 0.9f));

            highlightPaint.setColor(primary ? 0x66FFFFFF : 0x48FFFFFF);



            shadowPaint1.setStyle(Paint.Style.FILL);

            shadowPaint1.setColor(0x40000000);



            shadowPaint2.setStyle(Paint.Style.FILL);

            shadowPaint2.setColor(0x65000000);

        }



        public void setPressed(boolean p) {

            if (this.pressed != p) {

                this.pressed = p;

                onBoundsChange(getBounds());

                invalidateSelf();

            }

        }



        @Override

        protected void onBoundsChange(Rect bounds) {

            super.onBoundsChange(bounds);

            if (bounds.width() <= 0 || bounds.height() <= 0) return;



            facePath.reset();

            shadowPath1.reset();

            shadowPath2.reset();

            highlightPath.reset();



            // Margin for shadow expansion

            float mx = 10f;

            float my = 8f;



            float pressY = pressed ? 2.5f : 0f;



            float l = bounds.left + mx;

            float t = bounds.top + my + pressY;

            float r = bounds.right - mx;

            float b = bounds.bottom - my - (pressed ? 1.5f : 5f) + pressY;



            float ch = Math.min(chamfer, Math.min((r - l) * 0.45f, (b - t) * 0.45f));



            // 1. Build Face Path

            buildChamferedPath(facePath, l, t, r, b, ch);



            // 2. Build Shadow Layer 1 (Deep ambient shadow, offset + blur)

            float s1Dy = pressed ? 2f : 6f;

            buildChamferedPath(shadowPath1, l, t + s1Dy, r, b + s1Dy, ch);

            shadowPaint1.setShadowLayer(pressed ? 4f : 14f, 0, s1Dy * 0.5f, 0x55000000);



            // 3. Build Shadow Layer 2 (Directional ground contact shadow)

            float s2Dy = pressed ? 1f : 2.5f;

            buildChamferedPath(shadowPath2, l, t + s2Dy, r, b + s2Dy, ch);

            shadowPaint2.setShadowLayer(pressed ? 2f : 6f, 0, s2Dy, 0x85000000);



            // 4. Build Top Highlight Bevel Path

            highlightPath.moveTo(l + ch, t + strokeWidth * 0.5f);

            highlightPath.lineTo(r - ch, t + strokeWidth * 0.5f);



            // 5. Linear Gradient for Face and Stroke

            if (primary) {

                LinearGradient grad = new LinearGradient(

                        0, t, 0, b,

                        new int[]{0xFFFFFFFF, 0xFFE6EEF6, 0xFFD4DFE8},

                        new float[]{0f, 0.60f, 1f},

                        Shader.TileMode.CLAMP

                );

                fillPaint.setShader(grad);

                strokePaint.setShader(null);

                strokePaint.setColor(0x28000000);

            } else {

                LinearGradient grad = new LinearGradient(

                        0, t, 0, b,

                        new int[]{0xEE222E3E, 0xF6151E2A, 0xFC0D131C},

                        new float[]{0f, 0.55f, 1f},

                        Shader.TileMode.CLAMP

                );

                fillPaint.setShader(grad);



                LinearGradient strokeGrad = new LinearGradient(

                        0, t, 0, b,

                        new int[]{0x58FFFFFF, 0x28FFFFFF, 0x14FFFFFF},

                        new float[]{0f, 0.5f, 1f},

                        Shader.TileMode.CLAMP

                );

                strokePaint.setShader(strokeGrad);

            }

        }



        @Override

        public void draw(Canvas canvas) {

            // Layer 1: Ambient Drop Shadow

            canvas.drawPath(shadowPath1, shadowPaint1);



            // Layer 2: Directional Contact Shadow

            canvas.drawPath(shadowPath2, shadowPaint2);



            // Layer 3: Button Face Fill

            canvas.drawPath(facePath, fillPaint);



            // Layer 4: Contour Stroke

            canvas.drawPath(facePath, strokePaint);



            // Layer 5: Top Specular Edge Highlight

            canvas.drawPath(highlightPath, highlightPaint);

        }



        @Override public void setAlpha(int alpha) {

            fillPaint.setAlpha(alpha);

            strokePaint.setAlpha(alpha);

            shadowPaint1.setAlpha(alpha);

            shadowPaint2.setAlpha(alpha);

        }

        @Override public void setColorFilter(ColorFilter colorFilter) {

            fillPaint.setColorFilter(colorFilter);

        }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    }



    /** Low-poly horizontal progress bar with chamfered track, smooth interpolation, and animated sheen sweep. */

    public static final class LowPolyProgressBar extends View {

        private float targetProgress = 0f;

        private float displayProgress = 0f;

        private float sheenPhase = 0f;

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint sheenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Path clipPath = new Path();

        private final float chamfer;

        private ValueAnimator progressAnim;

        private ValueAnimator sheenAnim;

        private Consumer<Float> onProgressUpdate;



        public LowPolyProgressBar(Context context, float chamfer) {

            super(context);

            this.chamfer = chamfer;

            initSheen();

        }



        public void setOnProgressUpdate(Consumer<Float> callback) {

            this.onProgressUpdate = callback;

        }



        private void initSheen() {

            sheenAnim = ValueAnimator.ofFloat(0f, 1f);

            sheenAnim.setDuration(1600);

            sheenAnim.setRepeatMode(ValueAnimator.RESTART);

            sheenAnim.setRepeatCount(ValueAnimator.INFINITE);

            sheenAnim.setInterpolator(new LinearInterpolator());

            sheenAnim.addUpdateListener(a -> {

                sheenPhase = (float) a.getAnimatedValue();

                invalidate();

            });

            sheenAnim.start();

        }



        public void setProgress(float p) {

            float clamped = Math.max(0f, Math.min(1f, p));

            if (Math.abs(clamped - targetProgress) < 0.001f) return;

            targetProgress = clamped;

            if (progressAnim != null && progressAnim.isRunning()) {

                progressAnim.cancel();

            }

            progressAnim = ValueAnimator.ofFloat(displayProgress, targetProgress);

            progressAnim.setDuration(360);

            progressAnim.setInterpolator(new DecelerateInterpolator());

            progressAnim.addUpdateListener(a -> {

                displayProgress = (float) a.getAnimatedValue();

                if (onProgressUpdate != null) {

                    onProgressUpdate.accept(displayProgress);

                }

                invalidate();

            });

            progressAnim.start();

        }



        public float getDisplayProgress() {

            return displayProgress;

        }



        @Override

        protected void onDetachedFromWindow() {

            super.onDetachedFromWindow();

            if (progressAnim != null) progressAnim.cancel();

            if (sheenAnim != null) sheenAnim.cancel();

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

            if (displayProgress > 0.008f) {

                float fillW = Math.max(ch * 2, w * displayProgress);

                paint.setStyle(Paint.Style.FILL);

                paint.setShader(new LinearGradient(0, 0, w, 0,

                    new int[]{0xFF00B0FF, 0xFF00E5FF, 0xFF64FFDA},

                    new float[]{0f, 0.65f, 1f}, Shader.TileMode.CLAMP));

                float inCh = Math.max(1f, ch - 1f);

                drawChamferedRect(canvas, 1.5f, 1.5f, fillW - 1.5f, h - 1.5f, inCh, paint);

                paint.setShader(null);



                // 3. Shimmer sheen sweep across fill

                buildChamferedPath(clipPath, 1.5f, 1.5f, fillW - 1.5f, h - 1.5f, inCh);

                canvas.save();

                canvas.clipPath(clipPath);



                float sheenX = -fillW * 0.3f + (fillW * 1.6f) * sheenPhase;

                float sheenBand = Math.max(fillW * 0.35f, 40f);

                LinearGradient grad = new LinearGradient(

                    sheenX - sheenBand, 0, sheenX + sheenBand, 0,

                    new int[]{0x00FFFFFF, 0x44FFFFFF, 0xAAFFFFFF, 0x44FFFFFF, 0x00FFFFFF},

                    new float[]{0f, 0.3f, 0.5f, 0.7f, 1.0f},

                    Shader.TileMode.CLAMP

                );

                sheenPaint.setShader(grad);

                canvas.drawRect(sheenX - sheenBand, 0, sheenX + sheenBand, h, sheenPaint);

                canvas.restore();

            }

        }

    }



    /** Low-poly rotating geometric beacon accent for loading screen. */

    public static final class LowPolyBeacon extends View {

        private float angle = 0f;

        private float pulse = 1f;

        private final Paint pStroke = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);

        private ValueAnimator rotateAnim;

        private ValueAnimator pulseAnim;



        public LowPolyBeacon(Context context) {

            super(context);

            pStroke.setStyle(Paint.Style.STROKE);

            pStroke.setStrokeWidth(1.8f);

            pStroke.setColor(0x88FFFFFF);



            pFill.setStyle(Paint.Style.FILL);

            pFill.setColor(0x24FFFFFF);



            rotateAnim = ValueAnimator.ofFloat(0f, 360f);

            rotateAnim.setDuration(3000);

            rotateAnim.setRepeatCount(ValueAnimator.INFINITE);

            rotateAnim.setRepeatMode(ValueAnimator.RESTART);

            rotateAnim.setInterpolator(new LinearInterpolator());

            rotateAnim.addUpdateListener(a -> {

                angle = (float) a.getAnimatedValue();

                invalidate();

            });

            rotateAnim.start();



            pulseAnim = ValueAnimator.ofFloat(0.88f, 1.12f);

            pulseAnim.setDuration(1400);

            pulseAnim.setRepeatCount(ValueAnimator.INFINITE);

            pulseAnim.setRepeatMode(ValueAnimator.REVERSE);

            pulseAnim.setInterpolator(new AccelerateDecelerateInterpolator());

            pulseAnim.addUpdateListener(a -> {

                pulse = (float) a.getAnimatedValue();

                invalidate();

            });

            pulseAnim.start();

        }



        @Override

        protected void onDetachedFromWindow() {

            super.onDetachedFromWindow();

            if (rotateAnim != null) rotateAnim.cancel();

            if (pulseAnim != null) pulseAnim.cancel();

        }



        @Override

        protected void onDraw(Canvas canvas) {

            float cx = getWidth() / 2f;

            float cy = getHeight() / 2f;

            float r = Math.min(cx, cy) * 0.72f * pulse;



            canvas.save();

            canvas.rotate(angle, cx, cy);



            // Outer rotating chamfered rhombus

            Path d1 = new Path();

            d1.moveTo(cx, cy - r);

            d1.lineTo(cx + r * 0.75f, cy);

            d1.lineTo(cx, cy + r);

            d1.lineTo(cx - r * 0.75f, cy);

            d1.close();

            pStroke.setColor(0x66FFFFFF);

            pFill.setColor(0x18FFFFFF);

            canvas.drawPath(d1, pFill);

            canvas.drawPath(d1, pStroke);



            // Inner counter-rotating diamond

            canvas.rotate(-angle * 2.2f, cx, cy);

            Path d2 = new Path();

            float ir = r * 0.52f;

            d2.moveTo(cx, cy - ir);

            d2.lineTo(cx + ir * 0.75f, cy);

            d2.lineTo(cx, cy + ir);

            d2.lineTo(cx - ir * 0.75f, cy);

            d2.close();

            pFill.setColor(0x40FFFFFF);

            pStroke.setColor(0xDDFFFFFF);

            canvas.drawPath(d2, pFill);

            canvas.drawPath(d2, pStroke);



            canvas.restore();

        }

    }



    /**

     * Atmospheric motion canvas view for the loading screen.

     * Renders real-time drifting neon rain streaks and floating bokeh light embers.

     */

    public static final class LoadingAtmosphereView extends View {

        private static final int RAIN_COUNT = 45;

        private static final int BOKEH_COUNT = 16;



        private static final class RainDrop {

            float x, y, len, speed, alpha, width;

            int color;

        }



        private static final class BokehEmber {

            float x, y, radius, speedX, speedY, phase, baseAlpha;

            int color;

        }



        private final RainDrop[] rain = new RainDrop[RAIN_COUNT];

        private final BokehEmber[] embers = new BokehEmber[BOKEH_COUNT];

        private final Paint rainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Paint emberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private ValueAnimator loopAnimator;

        private long lastNanos = 0;



        public LoadingAtmosphereView(Context context) {

            super(context);

            rainPaint.setStyle(Paint.Style.STROKE);

            rainPaint.setStrokeCap(Paint.Cap.ROUND);

            emberPaint.setStyle(Paint.Style.FILL);

            initParticles();

        }



        private void initParticles() {

            Random rnd = new Random(42);

            int[] rainColors = {0xAAFFFFFF, 0x8898C8FF, 0x77FFD590, 0x66FF88BB};

            for (int i = 0; i < RAIN_COUNT; i++) {

                RainDrop r = new RainDrop();

                r.x = rnd.nextFloat();

                r.y = rnd.nextFloat();

                r.len = 16f + rnd.nextFloat() * 28f;

                r.speed = 1.2f + rnd.nextFloat() * 1.8f;

                r.width = 1.0f + rnd.nextFloat() * 1.2f;

                r.alpha = 0.25f + rnd.nextFloat() * 0.55f;

                r.color = rainColors[rnd.nextInt(rainColors.length)];

                rain[i] = r;

            }



            int[] emberColors = {0x66FFD080, 0x5590D0FF, 0x44FFA0D0, 0x50FFFFFF};

            for (int i = 0; i < BOKEH_COUNT; i++) {

                BokehEmber e = new BokehEmber();

                e.x = rnd.nextFloat();

                e.y = rnd.nextFloat();

                e.radius = 3.5f + rnd.nextFloat() * 7f;

                e.speedX = (rnd.nextFloat() - 0.5f) * 0.04f;

                e.speedY = -(0.03f + rnd.nextFloat() * 0.06f);

                e.phase = rnd.nextFloat() * 6.28f;

                e.baseAlpha = 0.20f + rnd.nextFloat() * 0.40f;

                e.color = emberColors[rnd.nextInt(emberColors.length)];

                embers[i] = e;

            }

        }



        public void start() {

            if (loopAnimator != null && loopAnimator.isRunning()) return;

            lastNanos = System.nanoTime();

            loopAnimator = ValueAnimator.ofFloat(0f, 1f);

            loopAnimator.setDuration(1000);

            loopAnimator.setRepeatCount(ValueAnimator.INFINITE);

            loopAnimator.setRepeatMode(ValueAnimator.RESTART);

            loopAnimator.setInterpolator(new LinearInterpolator());

            loopAnimator.addUpdateListener(a -> {

                long now = System.nanoTime();

                float dt = lastNanos > 0 ? (now - lastNanos) / 1_000_000_000f : 0.016f;

                lastNanos = now;

                dt = Math.min(0.05f, Math.max(0.001f, dt));

                updateParticles(dt);

                invalidate();

            });

            loopAnimator.start();

        }



        public void stop() {

            if (loopAnimator != null) {

                loopAnimator.cancel();

                loopAnimator = null;

            }

        }



        @Override

        protected void onDetachedFromWindow() {

            super.onDetachedFromWindow();

            stop();

        }



        private void updateParticles(float dt) {

            for (RainDrop r : rain) {

                r.y += r.speed * dt;

                r.x -= r.speed * 0.25f * dt;

                if (r.y > 1.05f) {

                    r.y = -0.05f;

                    r.x = (float) Math.random() * 1.2f;

                }

                if (r.x < -0.05f) r.x = 1.05f;

            }



            for (BokehEmber e : embers) {

                e.phase += dt * 1.5f;

                e.y += e.speedY * dt;

                e.x += (float) (Math.sin(e.phase) * 0.03f) * dt + e.speedX * dt;

                if (e.y < -0.05f) {

                    e.y = 1.05f;

                    e.x = (float) Math.random();

                }

                if (e.x < -0.05f) e.x = 1.05f;

                else if (e.x > 1.05f) e.x = -0.05f;

            }

        }



        @Override

        protected void onDraw(Canvas canvas) {

            super.onDraw(canvas);

            int w = getWidth();

            int h = getHeight();

            if (w <= 0 || h <= 0) return;



            // Draw rain streaks

            for (RainDrop r : rain) {

                float sx = r.x * w;

                float sy = r.y * h;

                float ex = sx - r.len * 0.35f;

                float ey = sy + r.len;

                rainPaint.setColor(r.color);

                rainPaint.setStrokeWidth(r.width);

                rainPaint.setAlpha((int) (r.alpha * 255));

                canvas.drawLine(sx, sy, ex, ey, rainPaint);

            }



            // Draw floating bokeh embers

            for (BokehEmber e : embers) {

                float cx = e.x * w;

                float cy = e.y * h;

                float pulse = 0.75f + 0.25f * (float) Math.sin(e.phase);

                emberPaint.setColor(e.color);

                emberPaint.setAlpha((int) (e.baseAlpha * pulse * 255));

                canvas.drawCircle(cx, cy, e.radius * pulse, emberPaint);

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



    public static void buildChamferedPath(Path path, float l, float t, float r, float b, float ch) {

        path.reset();

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

    }



    private static void drawChamferedRect(Canvas c, float l, float t, float r, float b, float ch, Paint p) {

        Path path = new Path();

        buildChamferedPath(path, l, t, r, b, ch);

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



    private Button makePauseResumeButton(String label, Runnable action) {

        Button b = new Button(getContext());

        b.setText(label);

        b.setAllCaps(false);

        b.setTextSize(14f);

        b.setLetterSpacing(0.08f);

        b.setTypeface(Typeface.create("sans-serif-bold", Typeface.NORMAL));

        b.setTextColor(0xFF040A12);



        Drawable bg = new Drawable() {

            private boolean pressed = false;

            private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

            private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

            private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);

            private final Path path = new Path();



            @Override

            public void draw(Canvas c) {

                Rect bnd = getBounds();

                if (bnd.width() <= 0 || bnd.height() <= 0) return;

                path.reset();

                float ch = px(11);

                float padY = pressed ? 2f : 0f;

                float l = bnd.left + 2f, t = bnd.top + 2f + padY, r = bnd.right - 2f, bt = bnd.bottom - 4f + padY;

                buildChamferedPath(path, l, t, r, bt, ch);



                glow.setStyle(Paint.Style.FILL);

                glow.setColor(pressed ? 0x5500E5FF : 0x3300E5FF);

                c.drawPath(path, glow);



                fill.setStyle(Paint.Style.FILL);

                fill.setShader(new LinearGradient(0, t, 0, bt,

                        new int[]{pressed ? 0xFF00CCE0 : 0xFF3BE8FF, 0xFF00C0D8, 0xFF009AB0},

                        new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));

                c.drawPath(path, fill);



                stroke.setStyle(Paint.Style.STROKE);

                stroke.setStrokeWidth(px(1.2f));

                stroke.setColor(0x99FFFFFF);

                c.drawPath(path, stroke);

            }

            @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); }

            @Override public void setColorFilter(ColorFilter cf) { fill.setColorFilter(cf); }

            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

            @Override protected boolean onStateChange(int[] state) {

                boolean p = false;

                for (int s : state) if (s == android.R.attr.state_pressed) p = true;

                if (pressed != p) { pressed = p; invalidateSelf(); return true; }

                return super.onStateChange(state);

            }

            @Override public boolean isStateful() { return true; }

        };

        b.setBackground(bg);

        b.setOnTouchListener((v, event) -> {

            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:

                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(60).start();

                    break;

                case MotionEvent.ACTION_UP:

                case MotionEvent.ACTION_CANCEL:

                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160)

                            .setInterpolator(new OvershootInterpolator(1.4f)).start();

                    break;

            }

            return false;

        });

        b.setOnClickListener(v -> {

            activity.feedback(20);

            activity.playCue("ui_click");

            if (action != null) action.run();

        });

        return b;

    }



    private Button makePauseGridButton(String icon, String label, Runnable action) {

        Button b = new Button(getContext());

        b.setText(icon + "  " + label);

        b.setAllCaps(false);

        b.setTextSize(12f);

        b.setLetterSpacing(0.06f);

        b.setTypeface(Typeface.create("sans-serif-bold", Typeface.NORMAL));

        b.setTextColor(0xFFE2EAF2);



        Drawable bg = new Drawable() {

            private boolean pressed = false;

            private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);

            private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);

            private final Path path = new Path();



            @Override

            public void draw(Canvas c) {

                Rect bnd = getBounds();

                if (bnd.width() <= 0 || bnd.height() <= 0) return;

                path.reset();

                float ch = px(8);

                float padY = pressed ? 1.5f : 0f;

                float l = bnd.left + 2f, t = bnd.top + 2f + padY, r = bnd.right - 2f, bt = bnd.bottom - 3f + padY;

                buildChamferedPath(path, l, t, r, bt, ch);



                fill.setStyle(Paint.Style.FILL);

                fill.setShader(new LinearGradient(0, t, 0, bt,

                        pressed ? new int[]{0xEE1E2C3D, 0xF5131C28, 0xFA0B1017}

                                : new int[]{0xEE162230, 0xF50F1622, 0xFA0A0F17},

                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));

                c.drawPath(path, fill);



                stroke.setStyle(Paint.Style.STROKE);

                stroke.setStrokeWidth(px(1.0f));

                stroke.setColor(pressed ? 0x8800E5FF : 0x33FFFFFF);

                c.drawPath(path, stroke);

            }

            @Override public void setAlpha(int alpha) { fill.setAlpha(alpha); }

            @Override public void setColorFilter(ColorFilter cf) { fill.setColorFilter(cf); }

            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

            @Override protected boolean onStateChange(int[] state) {

                boolean p = false;

                for (int s : state) if (s == android.R.attr.state_pressed) p = true;

                if (pressed != p) { pressed = p; invalidateSelf(); return true; }

                return super.onStateChange(state);

            }

            @Override public boolean isStateful() { return true; }

        };

        b.setBackground(bg);

        b.setOnTouchListener((v, event) -> {

            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:

                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(60).start();

                    break;

                case MotionEvent.ACTION_UP:

                case MotionEvent.ACTION_CANCEL:

                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160)

                            .setInterpolator(new OvershootInterpolator(1.4f)).start();

                    break;

            }

            return false;

        });

        b.setOnClickListener(v -> {

            activity.feedback(15);

            activity.playCue("ui_click");

            if (action != null) action.run();

        });

        return b;

    }



    private Button makeChamferedButton(String label, boolean primary, Runnable action) {

        Button b = new Button(getContext());

        b.setText(label);

        b.setAllCaps(false);

        b.setTextSize(13.5f);

        b.setLetterSpacing(0.06f);

        b.setTypeface(Typeface.create("sans-serif-bold", Typeface.NORMAL));

        b.setTextColor(primary ? 0xFF0D141E : 0xFFF0F4F8);

        TactileButtonDrawable bg = new TactileButtonDrawable(primary, px(1.2f), px(10));

        b.setBackground(bg);

        b.setMinHeight(px(48));

        b.setPadding(px(22), px(11), px(22), px(11));

        b.setOnTouchListener((v, event) -> {

            switch (event.getAction()) {

                case MotionEvent.ACTION_DOWN:

                    bg.setPressed(true);

                    v.animate().scaleX(0.965f).scaleY(0.965f).setDuration(70).start();

                    break;

                case MotionEvent.ACTION_UP:

                case MotionEvent.ACTION_CANCEL:

                    bg.setPressed(false);

                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160)

                            .setInterpolator(new OvershootInterpolator(1.4f)).start();

                    break;

            }

            return false;

        });

        b.setOnClickListener(v -> {

            activity.feedback(15);

            activity.playCue("ui_click");

            if (action != null) action.run();

        });

        return b;

    }



    /** Builds the new transparent low-poly in-game control elements. */

    private void buildControls() {

        controls.removeAllViews();



        // 1. Transparent Low-Poly Pause Button (Top-Right, left of radar)

        btnPause = new LowPolyControlView(getContext(), ControlType.PAUSE, px(8), down -> {

            if (down) {

                activity.feedback(10);

                activity.command("pause");

            }

        });

        LayoutParams pp = new LayoutParams(px(42), px(42), Gravity.TOP | Gravity.RIGHT);

        pp.topMargin = px(14);

        pp.rightMargin = px(100);

        controls.addView(btnPause, pp);



        // 2. Steer Left & Right Buttons (Bottom-Left zone for left thumb)

        btnLeft = new LowPolyControlView(getContext(), ControlType.LEFT, px(12), down -> input.left = down);

        LayoutParams lp = new LayoutParams(px(64), px(64), Gravity.BOTTOM | Gravity.LEFT);

        lp.leftMargin = px(20);

        lp.bottomMargin = px(20);

        controls.addView(btnLeft, lp);



        btnRight = new LowPolyControlView(getContext(), ControlType.RIGHT, px(12), down -> input.right = down);

        LayoutParams rp = new LayoutParams(px(64), px(64), Gravity.BOTTOM | Gravity.LEFT);

        rp.leftMargin = px(94);

        rp.bottomMargin = px(20);

        controls.addView(btnRight, rp);



        // 3. Drive & Brake Pedals (Bottom-Right zone for right thumb)

        btnDrive = new LowPolyControlView(getContext(), ControlType.DRIVE, px(16), down -> input.pedal = down);

        LayoutParams dpParam = new LayoutParams(px(88), px(88), Gravity.BOTTOM | Gravity.RIGHT);

        dpParam.rightMargin = px(20);

        dpParam.bottomMargin = px(20);

        controls.addView(btnDrive, dpParam);



        btnBrake = new LowPolyControlView(getContext(), ControlType.BRAKE, px(14), down -> input.brake = down);

        LayoutParams bp = new LayoutParams(px(72), px(72), Gravity.BOTTOM | Gravity.RIGHT);

        bp.rightMargin = px(118);

        bp.bottomMargin = px(20);

        controls.addView(btnBrake, bp);



        // 4. Dynamic In-Game Dispatch Call Card (Top-Right under radar)

        dispatchCard = new LinearLayout(getContext());

        dispatchCard.setOrientation(LinearLayout.VERTICAL);

        dispatchCard.setBackground(new ChamferedDrawable(0xEE0D121D, 0x44FFFFFF, px(1.2f), px(8)));

        dispatchCard.setPadding(px(14), px(10), px(14), px(10));

        dispatchCard.setVisibility(GONE);



        LinearLayout dHeader = new LinearLayout(getContext());

        dHeader.setOrientation(LinearLayout.HORIZONTAL);

        dHeader.setGravity(Gravity.CENTER_VERTICAL);



        tvDispatchTitle = makeText("DISPATCH OFFER", 11f, 0xFFFFA826, true);

        tvDispatchTitle.setLetterSpacing(0.1f);

        dHeader.addView(tvDispatchTitle);



        View dSpacer = new View(getContext());

        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(0, 0, 1.0f);

        dHeader.addView(dSpacer, spLp);



        tvDispatchTimer = makeText("12s", 11f, 0xFF887C92, true);

        dHeader.addView(tvDispatchTimer);

        dispatchCard.addView(dHeader);



        tvDispatchRoute = makeText("High-Priority Contract", 13.5f, COLOR_WHITE, true);

        tvDispatchRoute.setPadding(0, px(3), 0, px(1));

        dispatchCard.addView(tvDispatchRoute);



        tvDispatchMeta = makeText("Payout: $450   Dist: 320m", 11f, 0xFF00E5FF, false);

        dispatchCard.addView(tvDispatchMeta);



        LinearLayout dBtnRow = new LinearLayout(getContext());

        dBtnRow.setOrientation(LinearLayout.HORIZONTAL);

        dBtnRow.setPadding(0, px(8), 0, 0);



        btnDispatchDecline = new TextView(getContext());

        btnDispatchDecline.setText("DECLINE");

        btnDispatchDecline.setTextColor(0xFF887C92);

        btnDispatchDecline.setTextSize(11f);

        btnDispatchDecline.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));

        btnDispatchDecline.setPadding(px(12), px(6), px(12), px(6));

        btnDispatchDecline.setBackground(new ChamferedDrawable(0x22FFFFFF, 0x44FFFFFF, px(1f), px(6)));

        btnDispatchDecline.setOnClickListener(v -> {

            activity.feedback(15);

            game.declineCall();

            dispatchCard.setVisibility(GONE);

        });

        dBtnRow.addView(btnDispatchDecline);



        View btnGap = new View(getContext());

        dBtnRow.addView(btnGap, new LinearLayout.LayoutParams(px(10), 0));



        btnDispatchAccept = new TextView(getContext());

        btnDispatchAccept.setText("ACCEPT [A]");

        btnDispatchAccept.setTextColor(0xFF0D141E);

        btnDispatchAccept.setTextSize(11f);

        btnDispatchAccept.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));

        btnDispatchAccept.setPadding(px(16), px(6), px(16), px(6));

        btnDispatchAccept.setBackground(new ChamferedDrawable(0xFFF5C842, 0xFFFFFFFF, px(1f), px(6)));

        btnDispatchAccept.setOnClickListener(v -> {

            activity.feedback(25);

            game.acceptCall();

            dispatchCard.setVisibility(GONE);

        });

        dBtnRow.addView(btnDispatchAccept);

        dispatchCard.addView(dBtnRow);



        LayoutParams dCardLp = new LayoutParams(px(240), -2, Gravity.TOP | Gravity.RIGHT);

        dCardLp.topMargin = px(92);

        dCardLp.rightMargin = px(16);

        controls.addView(dispatchCard, dCardLp);

    }



    @Override

    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {

        super.onLayout(changed, left, top, right, bottom);

        if (changed) {

            updateControlsLayout();

        }

    }



    private void updateControlsLayout() {

        if (controls == null) return;

        int padL = getPaddingLeft();

        int padR = getPaddingRight();

        int padT = getPaddingTop();

        int padB = getPaddingBottom();



        if (btnLeft != null && btnLeft.getLayoutParams() instanceof LayoutParams lp) {

            lp.leftMargin = padL + px(20);

            lp.bottomMargin = padB + px(20);

            btnLeft.setLayoutParams(lp);

        }

        if (btnRight != null && btnRight.getLayoutParams() instanceof LayoutParams rp) {

            rp.leftMargin = padL + px(94);

            rp.bottomMargin = padB + px(20);

            btnRight.setLayoutParams(rp);

        }

        if (btnDrive != null && btnDrive.getLayoutParams() instanceof LayoutParams dp) {

            dp.rightMargin = padR + px(20);

            dp.bottomMargin = padB + px(20);

            btnDrive.setLayoutParams(dp);

        }

        if (btnBrake != null && btnBrake.getLayoutParams() instanceof LayoutParams bp) {

            bp.rightMargin = padR + px(118);

            bp.bottomMargin = padB + px(20);

            btnBrake.setLayoutParams(bp);

        }

        if (btnPause != null && btnPause.getLayoutParams() instanceof LayoutParams pp) {

            pp.rightMargin = padR + px(100);

            pp.topMargin = padT + px(14);

            btnPause.setLayoutParams(pp);

        }

        if (dispatchCard != null && dispatchCard.getLayoutParams() instanceof LayoutParams dcp) {

            dcp.rightMargin = padR + px(16);

            dcp.topMargin = padT + px(92);

            dispatchCard.setLayoutParams(dcp);

        }

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

        // Update dynamic in-game dispatch call card

        DispatchGame.DispatchCall dCall = game.getPendingCall();

        if (dCall != null && dispatchCard != null) {

            if (dispatchCard.getVisibility() != VISIBLE) {

                dispatchCard.setVisibility(VISIBLE);

                dispatchCard.setAlpha(0f);

                dispatchCard.animate().alpha(1f).setDuration(200).start();

            }

            tvDispatchRoute.setText(dCall.route);

            tvDispatchMeta.setText(String.format(Locale.US, "+$%d  |  %ds limit", dCall.payout, dCall.timeLimit));

            tvDispatchTimer.setText(Math.round(dCall.ringTimer) + "s");

        } else if (dispatchCard != null && dispatchCard.getVisibility() == VISIBLE) {

            dispatchCard.setVisibility(GONE);

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



    /** Standard mobile game loading screen overlay with cinematic motion background and real-time atmosphere. */

    private void showLoadingScreen() {

        removeActivePanel();

        section = "loading";



        FrameLayout loadingLayout = new FrameLayout(getContext());

        loadingLayout.setBackgroundColor(0xFF06090D);



        // 1. Full-screen Cinematic Foggy Forest Motorcycle Road Background

        ImageView bgView = new ImageView(getContext());

        bgView.setScaleType(ImageView.ScaleType.CENTER_CROP);

        try (java.io.InputStream is = getContext().getAssets().open("loading_bg.jpg")) {

            Bitmap bmp = BitmapFactory.decodeStream(is);

            if (bmp != null) bgView.setImageBitmap(bmp);

        } catch (Exception ignored) {}

        loadingLayout.addView(bgView, new LayoutParams(-1, -1));



        // 2. Subtle right-side soft shadow for text legibility

        View vignette = new View(getContext());

        vignette.setBackground(new Drawable() {

            @Override

            public void draw(Canvas canvas) {

                Rect b = getBounds();

                if (b.width() <= 0 || b.height() <= 0) return;

                LinearGradient grad = new LinearGradient(

                        b.width() * 0.45f, 0, b.width(), 0,

                        new int[]{0x00000000, 0x44000000, 0x99000000},

                        new float[]{0f, 0.4f, 1f},

                        Shader.TileMode.CLAMP

                );

                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

                p.setShader(grad);

                canvas.drawRect(b, p);

            }

            @Override public void setAlpha(int alpha) {}

            @Override public void setColorFilter(ColorFilter cf) {}

            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

        });

        loadingLayout.addView(vignette, new LayoutParams(-1, -1));



        // 3. Right-Side Boxed Title & Sleek Minimal Loading Line

        LinearLayout rightGroup = new LinearLayout(getContext());

        rightGroup.setOrientation(LinearLayout.VERTICAL);

        rightGroup.setGravity(Gravity.RIGHT);



        // Boxed Logo Title

        LinearLayout titleBox = new LinearLayout(getContext());

        titleBox.setOrientation(LinearLayout.VERTICAL);

        titleBox.setGravity(Gravity.CENTER);

        titleBox.setBackground(new ChamferedDrawable(0xAA0B0F14, 0xDDE0E0E0, px(1.5f), px(3)));

        titleBox.setPadding(px(18), px(8), px(18), px(8));



        TextView titleText = makeText("NIGHT RIDER", 24f, COLOR_WHITE, true);

        titleText.setLetterSpacing(0.22f);

        titleText.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));

        titleBox.addView(titleText);



        LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(-2, -2);

        tbLp.bottomMargin = px(22);

        rightGroup.addView(titleBox, tbLp);



        // Ultra-minimal thin progress bar (2dp height, bone-white)

        loadingBar = new LowPolyProgressBar(getContext(), px(1));

        LinearLayout.LayoutParams barParam = new LinearLayout.LayoutParams(px(220), px(3));

        barParam.bottomMargin = px(8);

        rightGroup.addView(loadingBar, barParam);



        // Minimal tracking percentage label below line

        loadingPercent = makeText("LOADING CITY... 0%", 11.5f, 0xFF88A0B8, true);

        loadingPercent.setLetterSpacing(0.18f);

        loadingPercent.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));

        rightGroup.addView(loadingPercent, new LinearLayout.LayoutParams(-2, -2));



        loadingBar.setOnProgressUpdate(p -> {

            if (loadingPercent != null) {

                int pct = Math.round(p * 100);

                loadingPercent.setText("LOADING CITY... " + pct + "%");

            }

        });



        int padR = Math.max(safeRight, px(20));

        int padT = Math.max(safeTop, px(18));

        LayoutParams rParam = new LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);

        rParam.rightMargin = padR;

        rParam.topMargin = padT;

        loadingLayout.addView(rightGroup, rParam);



        activePanel = loadingLayout;

        addView(loadingLayout, new LayoutParams(-1, -1));

    }



    private void showState(DispatchGame.State state) {
        lastState = state;
        switch (state) {
            case LOADING -> showLoadingScreen();
            case MENU -> openMasterDetailMenu(false);
            case PAUSED -> openMasterDetailMenu(true);
            case RESULT -> openResultMenu(data != null && data.won());
            default -> removeActivePanel();
        }
    }

    private void openMasterDetailMenu(boolean isPaused) {
        input.release();
        section = isPaused ? "PAUSED" : "MENU";
        removeActivePanel();

        FrameLayout masterRoot = new FrameLayout(getContext());

        // ==================== RIGHT NAVIGATION SECTION (STAYS ON RIGHT) ====================
        LinearLayout rightNav = new LinearLayout(getContext());
        rightNav.setOrientation(LinearLayout.VERTICAL);
        rightNav.setGravity(Gravity.LEFT);

        // Boxed Title: Left-aligned with the option buttons below inside rightNav
        LinearLayout titleBox = new LinearLayout(getContext());
        titleBox.setOrientation(LinearLayout.HORIZONTAL);
        titleBox.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        titleBox.setBackground(new ChamferedDrawable(0x22000000, 0xDDCCCCCC, px(1.5f), 0));
        titleBox.setPadding(px(16), px(8), px(16), px(8));

        menuTitleView = makeText(isPaused ? "PAUSED" : "NIGHT RIDER", 22f, COLOR_WHITE, true);
        menuTitleView.setLetterSpacing(0.20f);
        menuTitleView.setGravity(Gravity.LEFT);
        menuTitleView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        titleBox.addView(menuTitleView);

        LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(-2, -2);
        tbLp.bottomMargin = px(18);
        rightNav.addView(titleBox, tbLp);

        // Navigation Options List (Strictly Left-aligned, matching title's left edge, NO dark background)
        LinearLayout navList = new LinearLayout(getContext());
        navList.setOrientation(LinearLayout.VERTICAL);
        navList.setGravity(Gravity.LEFT);
        navList.setBackground(null);

        if (isPaused) {
            addNavButton(navList, "RESUME", true, () -> activity.command("resume"));
            addNavButton(navList, "PROFILE", false, this::showProfileInLeft);
            addNavButton(navList, "GARAGE", false, () -> showGarageInLeft(true));
            addNavButton(navList, "LEADERBOARD", false, this::showLeaderboardInLeft);
            addNavButton(navList, "SETTINGS", false, this::showSettingsInLeft);
            addNavButton(navList, "RESTART ROUTE", false, () -> activity.command("restart"));
            addNavButton(navList, "MAIN MENU", false, () -> activity.command("menu"));
        } else {
            addNavButton(navList, "RIDE", true, () -> activity.command("start"));
            addNavButton(navList, "PROFILE", false, this::showProfileInLeft);
            addNavButton(navList, "GARAGE", false, () -> showGarageInLeft(true));
            addNavButton(navList, "LEADERBOARD", false, this::showLeaderboardInLeft);
            addNavButton(navList, "SETTINGS", false, this::showSettingsInLeft);
        }

        addNavButton(navList, "SIGN OUT", false, () -> {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
            android.content.Intent intent = new android.content.Intent(activity, AuthActivity.class);
            intent.putExtra("force_auth", true);
            activity.startActivity(intent);
            activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            activity.finish();
        });

        rightNav.addView(navList, new LinearLayout.LayoutParams(px(290), -2));

        int padR = Math.max(safeRight, px(20));
        int padT = Math.max(safeTop, px(32));
        LayoutParams rightLp = new LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        rightLp.rightMargin = padR;
        rightLp.topMargin = padT;
        masterRoot.addView(rightNav, rightLp);

        // ==================== LEFT BIG CONTENT AREA (NO TITLE, NO DARK BACKGROUND) ====================
        rightContentArea = new FrameLayout(getContext());
        rightContentArea.setBackground(null);
        int padL = Math.max(safeLeft, px(32));
        LayoutParams leftLp = new LayoutParams(px(560), -2, Gravity.TOP | Gravity.LEFT);
        leftLp.leftMargin = padL;
        leftLp.topMargin = padT;
        masterRoot.addView(rightContentArea, leftLp);

        masterRoot.setAlpha(0f);
        masterRoot.animate().alpha(1f).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();

        activePanel = masterRoot;
        addView(masterRoot, new LayoutParams(-1, -1));

        // Default: display Profile details in the left content area
        showProfileInLeft();
    }


    private void openResultMenu(boolean won) {
        input.release();
        section = "result";
        removeActivePanel();

        FrameLayout masterRoot = new FrameLayout(getContext());

        // ==================== RIGHT NAVIGATION SECTION (STAYS ON RIGHT) ====================
        LinearLayout rightNav = new LinearLayout(getContext());
        rightNav.setOrientation(LinearLayout.VERTICAL);
        rightNav.setGravity(Gravity.LEFT);

        LinearLayout titleBox = new LinearLayout(getContext());
        titleBox.setOrientation(LinearLayout.HORIZONTAL);
        titleBox.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        titleBox.setBackground(new ChamferedDrawable(0x22000000, 0xDDCCCCCC, px(1.5f), 0));
        titleBox.setPadding(px(16), px(8), px(16), px(8));

        menuTitleView = makeText(won ? "ROUTE COMPLETE" : "TERMINATED", 22f, COLOR_WHITE, true);
        menuTitleView.setLetterSpacing(0.20f);
        menuTitleView.setGravity(Gravity.LEFT);
        menuTitleView.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        titleBox.addView(menuTitleView);

        LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(-2, -2);
        tbLp.bottomMargin = px(18);
        rightNav.addView(titleBox, tbLp);

        LinearLayout navList = new LinearLayout(getContext());
        navList.setOrientation(LinearLayout.VERTICAL);
        navList.setGravity(Gravity.LEFT);
        navList.setBackground(null);

        addNavButton(navList, "RETRY ROUTE", true, () -> activity.command("restart"));
        addNavButton(navList, "PROFILE", false, this::showProfileInLeft);
        addNavButton(navList, "GARAGE", false, () -> showGarageInLeft(true));
        addNavButton(navList, "LEADERBOARD", false, this::showLeaderboardInLeft);
        addNavButton(navList, "SETTINGS", false, this::showSettingsInLeft);
        addNavButton(navList, "MAIN MENU", false, () -> activity.command("menu"));

        rightNav.addView(navList, new LinearLayout.LayoutParams(px(290), -2));

        int padR = Math.max(safeRight, px(20));
        int padT = Math.max(safeTop, px(32));
        LayoutParams rightLp = new LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        rightLp.rightMargin = padR;
        rightLp.topMargin = padT;
        masterRoot.addView(rightNav, rightLp);

        rightContentArea = new FrameLayout(getContext());
        rightContentArea.setBackground(null);
        int padL = Math.max(safeLeft, px(32));
        LayoutParams leftLp = new LayoutParams(px(560), -2, Gravity.TOP | Gravity.LEFT);
        leftLp.leftMargin = padL;
        leftLp.topMargin = padT;
        masterRoot.addView(rightContentArea, leftLp);

        masterRoot.setAlpha(0f);
        masterRoot.animate().alpha(1f).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();

        activePanel = masterRoot;
        addView(masterRoot, new LayoutParams(-1, -1));

        showResultInLeft(won);
    }

    private void showResultInLeft(boolean won) {
        if (menuTitleView != null) menuTitleView.setText(won ? "ROUTE COMPLETE" : "TERMINATED");
        section = "result";
        if (rightContentArea == null) return;

        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.LEFT);
        card.setBackground(null);
        card.setPadding(0, 0, 0, 0);

        TextView statusTv = makeText(won ? "ROUTE SUCCESSFUL // DISPATCH COMPLETE" : "COURIER CASUALTY // VEHICLE COLLISION", 16f, won ? 0xFFFFFFFF : 0xFFEF4444, true);
        statusTv.setGravity(Gravity.LEFT);
        statusTv.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        statusTv.setLetterSpacing(0.12f);
        card.addView(statusTv);

        TextView subTv = makeText(won ? "ALL PACKAGES DELIVERED ON SCHEDULE" : "ACCIDENT DETECTED - VEHICLE CRITICAL DAMAGE", 11f, 0xFF94A3B8, false);
        subTv.setGravity(Gravity.LEFT);
        subTv.setLetterSpacing(0.10f);
        subTv.setPadding(0, px(2), 0, px(12));
        card.addView(subTv);

        View div = new View(getContext());
        div.setBackgroundColor(0x33CBD5E1);
        card.addView(div, new LinearLayout.LayoutParams(-1, px(1)));

        if (won && data != null) {
            String starsStr = switch (Math.max(1, Math.min(3, data.stars()))) {
                case 3 -> "★  ★  ★";
                case 2 -> "★  ★  ☆";
                default -> "★  ☆  ☆";
            };
            addProfileRow(card, "PERFORMANCE RATING", starsStr);
        }

        String distStr = (data != null && data.district() != null && !data.district().isEmpty()) ? data.district().toUpperCase(Locale.ROOT) : "DISTRICT TRANSIT";
        addProfileRow(card, "DISTRICT", distStr);

        int delivered = data != null ? data.delivered() : 0;
        int quota = data != null ? data.quota() : 0;
        addProfileRow(card, "DELIVERIES RECORDED", delivered + " / " + quota);

        int cashVal = data != null ? data.cash() : saves.cash();
        addProfileRow(card, "PAYOUT SECURED", "$" + String.format(Locale.US, "%,d", cashVal));

        int coinVal = data != null ? data.tickets() : saves.tickets();
        addProfileRow(card, "STREET COINS", String.valueOf(coinVal));

        int elapsedSec = data != null ? (int) data.time() : 0;
        addProfileRow(card, "RUN DURATION", (elapsedSec / 60) + "m " + (elapsedSec % 60) + "s");

        setLeftContentWithAnim(card);
    }

    private TextView addNavButton(LinearLayout panel, String text, boolean primary, Runnable action) {
        TextView tv = new TextView(getContext());
        tv.setText(text.toUpperCase(Locale.ROOT));
        tv.setTextColor(primary ? 0xFFFFFFFF : 0xFFCBD5E1);
        tv.setTextSize(15.5f);
        tv.setTypeface(Typeface.create("sans-serif-condensed", primary ? Typeface.BOLD : Typeface.NORMAL));
        tv.setLetterSpacing(0.14f);
        tv.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        tv.setPadding(px(16), px(9), px(16), px(9));
        tv.setShadowLayer(4, 1, 2, 0xEE000000);

        tv.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN -> {
                    tv.setTextColor(0xFFFFFFFF);
                    tv.setBackgroundColor(0x33FFFFFF);
                    tv.animate().scaleX(1.03f).scaleY(1.03f).setDuration(60).start();
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tv.setTextColor(primary ? 0xFFFFFFFF : 0xFFCBD5E1);
                    tv.setBackgroundColor(0x00000000);
                    tv.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                }
            }
            return false;
        });

        tv.setOnClickListener(v -> {
            activity.feedback(15);
            activity.playCue("ui_click");
            if (action != null) action.run();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = px(4);
        panel.addView(tv, lp);
        return tv;
    }

    private TextView addContentButton(LinearLayout panel, String text, boolean primary, Runnable action) {
        TextView tv = new TextView(getContext());
        tv.setText(text.toUpperCase(Locale.ROOT));
        tv.setTextColor(primary ? 0xFFFFFFFF : 0xFFCBD5E1);
        tv.setTextSize(14f);
        tv.setTypeface(Typeface.create("sans-serif-condensed", primary ? Typeface.BOLD : Typeface.NORMAL));
        tv.setLetterSpacing(0.12f);
        tv.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        tv.setPadding(0, px(8), px(16), px(8));
        tv.setShadowLayer(4, 1, 2, 0xEE000000);

        tv.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN -> {
                    tv.setTextColor(0xFFFFFFFF);
                    tv.animate().scaleX(1.02f).scaleY(1.02f).setDuration(60).start();
                }
                case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tv.setTextColor(primary ? 0xFFFFFFFF : 0xFFCBD5E1);
                    tv.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start();
                }
            }
            return false;
        });

        tv.setOnClickListener(v -> {
            activity.feedback(15);
            activity.playCue("ui_click");
            if (action != null) action.run();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = px(4);
        panel.addView(tv, lp);
        return tv;
    }

    private void showProfileInLeft() {
        if (menuTitleView != null) menuTitleView.setText("PROFILE");
        section = "profile";
        if (rightContentArea == null) return;

        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.LEFT);
        card.setBackground(null);
        card.setPadding(0, 0, 0, 0);

        UserManager user = UserManager.get();

        TextView nameTv = makeText(user.getUsername().toUpperCase(Locale.ROOT), 21f, 0xFFFFFFFF, true);
        nameTv.setGravity(Gravity.LEFT);
        nameTv.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        nameTv.setLetterSpacing(0.14f);
        card.addView(nameTv);

        String opId = user.getUid() != null && user.getUid().length() >= 8 ? user.getUid().substring(0, 8).toUpperCase(Locale.ROOT) : "COURIER";
        TextView subTv = makeText("SYNDICATE OPERATOR // ID: " + opId, 11f, 0xFF94A3B8, false);
        subTv.setGravity(Gravity.LEFT);
        subTv.setLetterSpacing(0.10f);
        subTv.setPadding(0, px(2), 0, px(12));
        card.addView(subTv);

        View div = new View(getContext());
        div.setBackgroundColor(0x33CBD5E1);
        card.addView(div, new LinearLayout.LayoutParams(-1, px(1)));

        addProfileRow(card, "GLOBAL RANK", "#" + user.getGlobalRank());
        addProfileRow(card, "COMPLETED DELIVERIES", String.valueOf(user.getCompletedDeliveries()));
        addProfileRow(card, "STREET COINS", String.format(Locale.US, "%,d", user.getTotalCoins()));
        addProfileRow(card, "TOTAL EARNINGS", "$" + String.format(Locale.US, "%,d", user.getTotalMoney()));

        long mins = user.getTotalPlaytime() / 60;
        long hrs = mins / 60;
        long sec = user.getTotalPlaytime() % 60;
        String timeStr = hrs > 0 ? (hrs + "h " + (mins % 60) + "m") : (mins + "m " + sec + "s");
        addProfileRow(card, "TIME IN TRANSIT", timeStr);

        setLeftContentWithAnim(card);
    }

    private void addProfileRow(LinearLayout panel, String label, String value) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, px(6), 0, px(2));

        TextView lbl = makeText(label.toUpperCase(Locale.ROOT), 10.5f, 0xFF94A3B8, false);
        lbl.setGravity(Gravity.LEFT);
        lbl.setLetterSpacing(0.12f);
        row.addView(lbl);

        TextView val = makeText(value, 15f, 0xFFFFFFFF, true);
        val.setGravity(Gravity.LEFT);
        val.setLetterSpacing(0.10f);
        val.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        row.addView(val);

        panel.addView(row);
    }

    private void showGarageInLeft(boolean unused) {
        if (menuTitleView != null) menuTitleView.setText("GARAGE");
        section = "garage";
        if (rightContentArea == null) return;

        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.LEFT);
        card.setBackground(null);
        card.setPadding(0, 0, 0, 0);

        int cashVal = data != null ? data.cash() : saves.cash();
        int ticketVal = data != null ? data.tickets() : saves.tickets();
        TextView wallet = makeText("WALLET: $" + String.format(Locale.US, "%,d", cashVal) + "  |  " + ticketVal + " TICKETS", 11.5f, 0xFF94A3B8, false);
        wallet.setGravity(Gravity.LEFT);
        wallet.setLetterSpacing(0.10f);
        wallet.setPadding(0, 0, 0, px(10));
        card.addView(wallet);

        View div = new View(getContext());
        div.setBackgroundColor(0x33CBD5E1);
        card.addView(div, new LinearLayout.LayoutParams(-1, px(1)));

        LinearLayout itemsList = new LinearLayout(getContext());
        itemsList.setOrientation(LinearLayout.VERTICAL);
        itemsList.setGravity(Gravity.LEFT);
        itemsList.setPadding(0, px(6), 0, 0);

        addGarageItemRow(itemsList, true, "bike", "CLASSIC BICYCLE", 0, 0);
        addGarageItemRow(itemsList, true, "scooter", "URBAN SCOOTER", 1200, 0);
        addGarageItemRow(itemsList, true, "superbike", "CRUISER MOTORCYCLE", 3500, 0);
        addGarageItemRow(itemsList, true, "gold", "GOLD SPECIAL BIKE", 0, 40);
        addGarageItemRow(itemsList, false, "cap", "COURIER CAP", 0, 0);
        addGarageItemRow(itemsList, false, "helmet", "AERO HELMET", 800, 0);
        addGarageItemRow(itemsList, false, "crown", "GOLD CROWN", 0, 25);

        card.addView(itemsList);
        setLeftContentWithAnim(card);
    }

    private void addGarageItemRow(LinearLayout panel, boolean isVehicles, String id, String name, int priceCash, int priceTokens) {
        boolean owned = saves.owned(isVehicles).contains(id);
        boolean equipped = saves.text(isVehicles ? "deliver3d_vehicle" : "deliver3d_hat", isVehicles ? "bike" : "cap").equals(id);

        String tag = equipped ? "[EQUIPPED]" : owned ? "SELECT" : priceTokens > 0 ? (priceTokens + " TICKETS") : ("$" + priceCash);
        String label = name + "  |  " + tag;

        addContentButton(panel, label, equipped, () -> {
            activity.feedback(20);
            activity.post(() -> game.buy(isVehicles, id, priceCash, priceTokens));
            activity.playCue(owned ? "equip" : "buy");
            postDelayed(() -> {
                data = game.snapshot;
                showGarageInLeft(isVehicles);
            }, 120);
        });
    }

    private void showLeaderboardInLeft() {
        if (menuTitleView != null) menuTitleView.setText("LEADERBOARD");
        section = "leaderboard";
        if (rightContentArea == null) return;

        UserManager self = UserManager.get();

        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.LEFT);
        card.setBackground(null);
        card.setPadding(0, 0, 0, 0);

        TextView subtitle = makeText("WORLDWIDE COURIER SYNDICATE RANKINGS", 11f, 0xFF94A3B8, false);
        subtitle.setGravity(Gravity.LEFT);
        subtitle.setLetterSpacing(0.12f);
        subtitle.setPadding(0, 0, 0, px(10));
        card.addView(subtitle);

        View div = new View(getContext());
        div.setBackgroundColor(0x33CBD5E1);
        card.addView(div, new LinearLayout.LayoutParams(-1, px(1)));

        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(LinearLayout.VERTICAL);
        list.setGravity(Gravity.LEFT);
        list.setPadding(0, px(6), 0, 0);

        String myName = self.getUsername().toUpperCase(Locale.ROOT) + " (YOU)";
        String[][] top = {
            {"#1", "GHOST_RIDER", "48 DELIVERIES", "54,200 PTS"},
            {"#2", "APEX_RUNNER", "37 DELIVERIES", "42,150 PTS"},
            {"#3", "SHADOW_COURIER", "29 DELIVERIES", "31,800 PTS"},
            {"#4", myName, self.getCompletedDeliveries() + " DELIVERIES", String.format(Locale.US, "%,d", self.getPoints()) + " PTS"},
            {"#5", "NEON_DRIFTER", "18 DELIVERIES", "18,400 PTS"},
        };

        for (String[] entry : top) {
            boolean isSelf = entry[1].contains("(YOU)");
            String line = entry[0] + "  " + entry[1] + "  |  " + entry[2] + "  |  " + entry[3];
            TextView tv = makeText(line, 13f, isSelf ? 0xFFFFFFFF : 0xFFCBD5E1, isSelf);
            tv.setGravity(Gravity.LEFT);
            tv.setLetterSpacing(0.08f);
            tv.setPadding(0, px(7), 0, px(7));
            tv.setShadowLayer(4, 1, 2, 0xEE000000);
            if (isSelf) {
                tv.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            }
            list.addView(tv);
        }

        card.addView(list);
        setLeftContentWithAnim(card);
    }

    private void showSettingsInLeft() {
        if (menuTitleView != null) menuTitleView.setText("SETTINGS");
        section = "settings";
        if (rightContentArea == null) return;

        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.LEFT);
        card.setBackground(null);
        card.setPadding(0, 0, 0, 0);

        TextView hint = makeText("VEHICLE & ENVIRONMENT CONTROLS", 11f, 0xFF94A3B8, false);
        hint.setLetterSpacing(0.12f);
        hint.setPadding(0, 0, 0, px(12));
        card.addView(hint);

        View div = new View(getContext());
        div.setBackgroundColor(0x33CBD5E1);
        card.addView(div, new LinearLayout.LayoutParams(-1, px(1)));

        boolean tiltOn = saves.flag("native-tilt", true);
        addContentButton(card, "GYRO STEERING: " + (tiltOn ? "ENABLED" : "TOUCH CONTROLS"), false, () -> {
            saves.put("native-tilt", !tiltOn);
            activity.applySettings();
            showSettingsInLeft();
        });

        float sens = saves.decimal("native-tilt-sensitivity", 1.0f);
        addContentButton(card, "STEERING SENSITIVITY: " + String.format(Locale.US, "%.1fX", sens), false, () -> {
            float nextSens = sens >= 1.6f ? 0.7f : (sens + 0.3f);
            saves.put("native-tilt-sensitivity", nextSens);
            activity.applySettings();
            showSettingsInLeft();
        });

        boolean haptics = saves.flag("native-haptics", true);
        addContentButton(card, "HAPTIC FEEDBACK: " + (haptics ? "ON" : "OFF"), false, () -> {
            saves.put("native-haptics", !haptics);
            activity.applySettings();
            showSettingsInLeft();
        });

        float vol = saves.decimal("dispatch-volume-master", 0.75f);
        addContentButton(card, "MASTER VOLUME: " + Math.round(vol * 100) + "%", false, () -> {
            float nextVol = vol <= 0.05f ? 1.0f : Math.max(0f, vol - 0.25f);
            saves.put("dispatch-volume-master", nextVol);
            activity.applySettings();
            showSettingsInLeft();
        });

        boolean lights = saves.flag("native-headlights", true);
        addContentButton(card, "HEADLIGHT BEAM: " + (lights ? "ON" : "OFF"), false, () -> {
            saves.put("native-headlights", !lights);
            activity.applySettings();
            showSettingsInLeft();
        });

        boolean weatherAuto = saves.flag("native-weather-auto", true);
        addContentButton(card, "DYNAMIC WEATHER: " + (weatherAuto ? "ON" : "OFF"), false, () -> {
            saves.put("native-weather-auto", !weatherAuto);
            activity.applySettings();
            showSettingsInLeft();
        });

        setLeftContentWithAnim(card);
    }

    private void setLeftContentWithAnim(View card) {
        if (rightContentArea == null) return;
        rightContentArea.removeAllViews();
        rightContentArea.addView(card, new FrameLayout.LayoutParams(-1, -2));
        card.setAlpha(0f);
        card.setTranslationX(px(-20));
        card.animate().alpha(1f).translationX(0f).setDuration(160).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    private void removeActivePanel() {
        if (loadingAtmosphere != null) {
            loadingAtmosphere.stop();
            loadingAtmosphere = null;
        }
        for (Animator a : loadingAnimators) {
            a.cancel();
        }
        loadingAnimators.clear();
        if (activePanel != null) {
            removeView(activePanel);
            activePanel = null;
        }
        rightContentArea = null;
        menuTitleView = null;
    }

    public boolean closePanel() {
        if (activePanel != null && !"loading".equals(section)) {
            removeActivePanel();
            return true;
        }
        return false;
    }
}

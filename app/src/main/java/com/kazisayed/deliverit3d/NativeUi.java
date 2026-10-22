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

    private final FrameLayout controls;
    private View activePanel;
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

    /** Standard mobile game loading screen overlay with cinematic motion background and real-time atmosphere. */
    private void showLoadingScreen() {
        removeActivePanel();
        section = "loading";

        FrameLayout loadingLayout = new FrameLayout(getContext());
        loadingLayout.setBackgroundColor(COLOR_OVERLAY_DARK);

        // 1. Cinematic Background Image with Ken Burns camera pan/zoom motion
        ImageView bgView = new ImageView(getContext());
        bgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try (java.io.InputStream is = getContext().getAssets().open("loading_bg.jpg")) {
            Bitmap bmp = BitmapFactory.decodeStream(is);
            if (bmp != null) bgView.setImageBitmap(bmp);
        } catch (Exception ignored) {}

        bgView.setScaleX(1.08f);
        bgView.setScaleY(1.08f);

        ObjectAnimator zoomAnim = ObjectAnimator.ofFloat(bgView, "scaleX", 1.06f, 1.18f);
        zoomAnim.setDuration(11000);
        zoomAnim.setRepeatMode(ValueAnimator.REVERSE);
        zoomAnim.setRepeatCount(ValueAnimator.INFINITE);
        zoomAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        zoomAnim.addUpdateListener(a -> bgView.setScaleY((float) a.getAnimatedValue()));
        zoomAnim.start();
        loadingAnimators.add(zoomAnim);

        ObjectAnimator panXAnim = ObjectAnimator.ofFloat(bgView, "translationX", -px(26), px(26));
        panXAnim.setDuration(15000);
        panXAnim.setRepeatMode(ValueAnimator.REVERSE);
        panXAnim.setRepeatCount(ValueAnimator.INFINITE);
        panXAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        panXAnim.start();
        loadingAnimators.add(panXAnim);

        ObjectAnimator panYAnim = ObjectAnimator.ofFloat(bgView, "translationY", -px(12), px(12));
        panYAnim.setDuration(9500);
        panYAnim.setRepeatMode(ValueAnimator.REVERSE);
        panYAnim.setRepeatCount(ValueAnimator.INFINITE);
        panYAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        panYAnim.start();
        loadingAnimators.add(panYAnim);

        loadingLayout.addView(bgView, new LayoutParams(-1, -1));

        // 2. Moody Vignette Scrim (Ensures text & loading bar pop with maximum legibility)
        View scrim = new View(getContext());
        scrim.setBackground(new Drawable() {
            @Override
            public void draw(Canvas canvas) {
                Rect b = getBounds();
                if (b.width() <= 0 || b.height() <= 0) return;
                RadialGradient rad = new RadialGradient(
                        b.exactCenterX(), b.exactCenterY(),
                        Math.max(b.width(), b.height()) * 0.68f,
                        new int[]{0x00000000, 0x3306080D, 0x88030508},
                        new float[]{0f, 0.65f, 1f},
                        Shader.TileMode.CLAMP
                );
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setShader(rad);
                canvas.drawRect(b, p);
            }
            @Override public void setAlpha(int alpha) {}
            @Override public void setColorFilter(ColorFilter colorFilter) {}
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        });
        loadingLayout.addView(scrim, new LayoutParams(-1, -1));

        // 3. Real-Time Atmospheric Rain & Bokeh Motion Overlay
        loadingAtmosphere = new LoadingAtmosphereView(getContext());
        loadingAtmosphere.start();
        loadingLayout.addView(loadingAtmosphere, new LayoutParams(-1, -1));

        // 4. Top Branding Header (Never blocks the central courier motorcycle artwork)
        LinearLayout topBranding = new LinearLayout(getContext());
        topBranding.setOrientation(LinearLayout.VERTICAL);
        topBranding.setGravity(Gravity.CENTER_HORIZONTAL);
        topBranding.setPadding(0, px(24), 0, 0);

        TextView title = makeText("NIGHT RIDER", 30, COLOR_WHITE, true);
        title.setLetterSpacing(0.22f);
        title.setGravity(Gravity.CENTER);
        ObjectAnimator titlePulse = ObjectAnimator.ofFloat(title, "alpha", 0.82f, 1.0f);
        titlePulse.setDuration(1400);
        titlePulse.setRepeatMode(ValueAnimator.REVERSE);
        titlePulse.setRepeatCount(ValueAnimator.INFINITE);
        titlePulse.setInterpolator(new AccelerateDecelerateInterpolator());
        titlePulse.start();
        loadingAnimators.add(titlePulse);
        topBranding.addView(title);
        loadingLayout.addView(topBranding, new LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        // 5. Bottom Cyber HUD Card (Clean minimal progress card without excessive text clutter)
        LinearLayout bottomCard = new LinearLayout(getContext());
        bottomCard.setOrientation(LinearLayout.VERTICAL);
        bottomCard.setGravity(Gravity.CENTER_HORIZONTAL);
        bottomCard.setBackground(new ChamferedDrawable(0x99080E17, 0x4400E5FF, px(1.2f), px(14)));
        bottomCard.setPadding(px(24), px(12), px(24), px(12));

        LinearLayout statusRow = new LinearLayout(getContext());
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);

        loadingPercent = makeText("0%", 13f, 0xFF00E5FF, true);
        loadingPercent.setLetterSpacing(0.08f);
        statusRow.addView(loadingPercent);
        bottomCard.addView(statusRow, new LinearLayout.LayoutParams(-2, -2));

        loadingBar = new LowPolyProgressBar(getContext(), px(4));
        LinearLayout.LayoutParams barParam = new LinearLayout.LayoutParams(px(280), px(7));
        barParam.topMargin = px(6);
        bottomCard.addView(loadingBar, barParam);

        loadingBar.setOnProgressUpdate(p -> {
            if (loadingPercent != null) {
                int pct = Math.round(p * 100);
                loadingPercent.setText(pct + "%");
            }
        });

        LayoutParams bottomParam = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bottomParam.bottomMargin = px(24);
        loadingLayout.addView(bottomCard, bottomParam);

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

                if (won) {
                    int stars = Math.max(1, Math.min(3, data.stars()));
                    TextView starRow = new TextView(getContext());
                    String starStr = switch (stars) {
                        case 3 -> "★  ★  ★";
                        case 2 -> "★  ★  ☆";
                        default -> "★  ☆  ☆";
                    };
                    starRow.setText(starStr);
                    starRow.setTextSize(26);
                    starRow.setTextColor(0xFFFFA826);
                    starRow.setGravity(Gravity.CENTER);
                    starRow.setPadding(0, px(4), 0, px(6));
                    panel.addView(starRow);
                }

                LinearLayout stats = new LinearLayout(getContext());
                stats.setOrientation(LinearLayout.HORIZONTAL);
                stats.setPadding(0, px(8), 0, px(4));
                addStatTile(stats, "Delivered", data.delivered() + " / " + data.quota(), won ? COLOR_WHITE : COLOR_SILVER);
                addStatTile(stats, "Time Left", String.format(Locale.US, "%d:%02d", (int) data.time() / 60, (int) data.time() % 60), won ? COLOR_WHITE : COLOR_SILVER);
                addStatTile(stats, "Score", String.format(Locale.US, "%,d", data.score()), COLOR_WHITE);
                addStatTile(stats, "Tickets", "+" + Math.max(1, data.stars()) + " ★", 0xFFFFA826);
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
        backBtn.setLetterSpacing(0.04f);
        backBtn.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
        backBtn.setTextColor(0xFFF0F4F8);
        TactileButtonDrawable backDrawable = new TactileButtonDrawable(false, px(1.2f), px(9));
        backBtn.setBackground(backDrawable);
        backBtn.setPadding(px(16), px(7), px(16), px(7));
        Runnable closeAction = () -> {
            activity.feedback(10);
            activity.playCue("ui_click");
            fullScreen.animate()
                    .alpha(0f)
                    .scaleX(0.97f)
                    .scaleY(0.97f)
                    .setDuration(160)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> showState(lastState))
                    .start();
        };
        backBtn.setOnClickListener(v -> closeAction.run());
        backBtn.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    backDrawable.setPressed(true);
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(70).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    backDrawable.setPressed(false);
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150)
                            .setInterpolator(new OvershootInterpolator(1.3f)).start();
                    break;
            }
            return false;
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
        Button doneBtn = makeChamferedButton("DONE  ✓", true, closeAction::run);
        LinearLayout.LayoutParams doneParam = new LinearLayout.LayoutParams(px(240), px(44));
        doneParam.gravity = Gravity.CENTER_HORIZONTAL;
        doneParam.topMargin = px(10);
        body.addView(doneBtn, doneParam);

        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        fullScreen.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        activePanel = fullScreen;
        addView(fullScreen, new LayoutParams(-1, -1));

        // Smooth staggered entrance animation
        fullScreen.setAlpha(0f);
        fullScreen.animate().alpha(1f).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();

        header.setTranslationY(-px(24));
        header.setAlpha(0f);
        header.animate().translationY(0).alpha(1f).setDuration(300).setInterpolator(new DecelerateInterpolator()).start();

        colLeft.setTranslationX(-px(28));
        colLeft.setAlpha(0f);
        colLeft.animate().translationX(0).alpha(1f).setDuration(360).setStartDelay(50).setInterpolator(new DecelerateInterpolator()).start();

        colRight.setTranslationX(px(28));
        colRight.setAlpha(0f);
        colRight.animate().translationX(0).alpha(1f).setDuration(360).setStartDelay(110).setInterpolator(new DecelerateInterpolator()).start();

        doneBtn.setTranslationY(px(24));
        doneBtn.setAlpha(0f);
        doneBtn.animate().translationY(0).alpha(1f).setDuration(340).setStartDelay(170).setInterpolator(new OvershootInterpolator(1.25f)).start();
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
            activity.playCue("ui_toggle");
            saves.put(key, checked);
            activity.applySettings();
            row.animate().scaleX(1.025f).scaleY(1.025f).setDuration(70).withEndAction(() -> {
                row.animate().scaleX(1.0f).scaleY(1.0f).setDuration(130).setInterpolator(new OvershootInterpolator(1.3f)).start();
            }).start();
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
            @Override
            public void onStartTrackingTouch(SeekBar b) {
                badge.animate().scaleX(1.22f).scaleY(1.22f).setDuration(110).setInterpolator(new OvershootInterpolator(1.4f)).start();
            }
            @Override
            public void onStopTrackingTouch(SeekBar b) {
                badge.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160).setInterpolator(new DecelerateInterpolator()).start();
            }
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

        // 1b. Top-Center: COMBO & FEVER MODE BADGE
        if (data.combo() > 0) {
            float comboW = 126, comboH = 36;
            float comboX = 312;
            boolean fever = data.fever();
            int strokeColor = fever ? 0xDDFFA826 : 0x45FFFFFF;
            int fillColor = fever ? 0x2A33220A : 0x220B111A;

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(fillColor);
            drawChamferedRect(canvas, comboX, 14, comboX + comboW, 14 + comboH, 8, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(fever ? 1.6f : 1.2f);
            paint.setColor(strokeColor);
            drawChamferedRect(canvas, comboX, 14, comboX + comboW, 14 + comboH, 8, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
            paint.setTextSize(11);
            paint.setColor(fever ? 0xFFFFA826 : COLOR_WHITE);
            String label = fever ? "⚡ FEVER x" + data.combo() : "COMBO x" + data.combo();
            canvas.drawText(label, comboX + 10, 29, paint);

            paint.setTypeface(Typeface.create("sans-serif-bold", Typeface.BOLD));
            paint.setTextSize(9);
            paint.setColor(fever ? 0xEEFFE4A0 : COLOR_SILVER);
            float m = Rules.multiplier(data.combo());
            String multStr = String.format(Locale.US, "%.2fx", m);
            float mw = paint.measureText(multStr);
            canvas.drawText(multStr, comboX + comboW - 10 - mw, 29, paint);

            float barX = comboX + 8, barY = 38, barW = comboW - 16, barH = 5;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x22FFFFFF);
            canvas.drawRect(barX, barY, barX + barW, barY + barH, paint);

            float fillW = Math.max(0, Math.min(barW, barW * data.comboFraction()));
            if (fillW > 0) {
                paint.setColor(fever ? 0xFFFFA826 : 0xCCFFFFFF);
                canvas.drawRect(barX, barY, barX + fillW, barY + barH, paint);
            }
        }

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

package com.kazisayed.deliverit3d;

import android.content.Context;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

public final class ScoreCardGenerator {
    private static final String TAG = "ScoreCard";

    public static Bitmap generateScoreCard(Context context) {
        UserManager user = UserManager.get();
        SaveStore saves = new SaveStore(context);

        int width = 1280;
        int height = 720;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 1. Load Background: First loading screen image (loading_bg.jpg from assets or bg_foggy_forest)
        Bitmap bg = null;
        try (InputStream is = context.getAssets().open("loading_bg.jpg")) {
            bg = BitmapFactory.decodeStream(is);
        } catch (Exception e) {
            try {
                bg = BitmapFactory.decodeResource(context.getResources(), R.drawable.bg_foggy_forest);
            } catch (Exception ignored) {}
        }

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (bg != null) {
            Rect src = new Rect(0, 0, bg.getWidth(), bg.getHeight());
            Rect dst = new Rect(0, 0, width, height);
            canvas.drawBitmap(bg, src, dst, paint);
        } else {
            canvas.drawColor(0xFF070B12);
        }

        // 2. Translucent Vignette & Atmospheric Tint (ensures perfect readability)
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xB3060A10); // 70% dark frosted glass
        canvas.drawRect(0, 0, width, height, paint);

        // Linear gradient for bottom/top depth
        Paint gradPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Shader vGrad = new LinearGradient(0, 0, 0, height,
                new int[]{0xCC000000, 0x44000000, 0xDD000000},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        gradPaint.setShader(vGrad);
        canvas.drawRect(0, 0, width, height, gradPaint);

        // 3. Cyberpunk Low-Poly Frame & Corner Accents
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0x40FFFFFF);
        canvas.drawRect(36, 36, width - 36, height - 36, paint);

        paint.setStrokeWidth(3f);
        paint.setColor(0xCC38BDF8); // Cyan corner brackets
        // Top-Left corner
        canvas.drawLine(32, 32, 80, 32, paint);
        canvas.drawLine(32, 32, 32, 80, paint);
        // Top-Right corner
        canvas.drawLine(width - 80, 32, width - 32, 32, paint);
        canvas.drawLine(width - 32, 32, width - 32, 80, paint);
        // Bottom-Left corner
        canvas.drawLine(32, height - 32, 80, height - 32, paint);
        canvas.drawLine(32, height - 80, 32, height - 32, paint);
        // Bottom-Right corner
        canvas.drawLine(width - 80, height - 32, width - 32, height - 32, paint);
        canvas.drawLine(width - 32, height - 80, width - 32, height - 32, paint);

        // 4. Header: Game Logo & Title
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextSize(36f);
        paint.setLetterSpacing(0.22f);
        paint.setColor(0xFFFFFFFF);
        paint.setShadowLayer(8, 0, 3, 0xEE000000);
        canvas.drawText("NIGHT RIDER", 64, 96, paint);
        paint.clearShadowLayer();

        paint.setTextSize(13f);
        paint.setLetterSpacing(0.16f);
        paint.setColor(0xFF38BDF8); // Cyan
        canvas.drawText("SYNDICATE COURIER DOSSIER // OFFICIAL DISPATCH RECORD", 64, 122, paint);

        paint.setStrokeWidth(1.5f);
        paint.setColor(0x33CBD5E1);
        canvas.drawLine(64, 138, width - 64, 138, paint);

        // 5. Left Column: Operator Profile Card
        float col1X = 64;
        float col1Y = 164;
        float col1W = 340;
        float col1H = 470;

        // Card background
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x2EFFFFFF);
        drawChamfered(canvas, col1X, col1Y, col1X + col1W, col1Y + col1H, 10, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(0x40FFFFFF);
        drawChamfered(canvas, col1X, col1Y, col1X + col1W, col1Y + col1H, 10, paint);

        // Avatar icon
        int avatarRes = UserManager.getAvatarDrawable(user.getAvatarId());
        try {
            Bitmap avBmp = BitmapFactory.decodeResource(context.getResources(), avatarRes);
            if (avBmp != null) {
                Rect avDst = new Rect((int)(col1X + col1W / 2 - 50), (int)(col1Y + 30), (int)(col1X + col1W / 2 + 50), (int)(col1Y + 130));
                canvas.drawBitmap(avBmp, null, avDst, paint);
            }
        } catch (Exception ignored) {}

        // Avatar frame
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0x8838BDF8);
        canvas.drawCircle(col1X + col1W / 2, col1Y + 80, 52, paint);

        // Operator name
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextSize(26f);
        paint.setLetterSpacing(0.12f);
        paint.setColor(0xFFFFFFFF);
        canvas.drawText(user.getUsername().toUpperCase(Locale.ROOT), col1X + col1W / 2, col1Y + 172, paint);

        // Operator UID
        String uid = user.getUid() != null && user.getUid().length() >= 8 ? user.getUid().substring(0, 8).toUpperCase(Locale.ROOT) : "GUEST";
        paint.setTextSize(13f);
        paint.setLetterSpacing(0.14f);
        paint.setColor(0xFF94A3B8);
        canvas.drawText("OPERATOR ID: #" + uid, col1X + col1W / 2, col1Y + 196, paint);

        // Divider inside profile card
        paint.setColor(0x33CBD5E1);
        canvas.drawLine(col1X + 24, col1Y + 220, col1X + col1W - 24, col1Y + 220, paint);

        // Global Rank Pill
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x33F59E0B);
        drawChamfered(canvas, col1X + 36, col1Y + 242, col1X + col1W - 36, col1Y + 288, 6, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(0x88F59E0B);
        drawChamfered(canvas, col1X + 36, col1Y + 242, col1X + col1W - 36, col1Y + 288, 6, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextSize(16f);
        paint.setLetterSpacing(0.14f);
        paint.setColor(0xFFFCD34D);
        canvas.drawText("GLOBAL RANK #" + user.getGlobalRank(), col1X + col1W / 2, col1Y + 272, paint);

        // Equipped vehicle badge
        String eqVeh = saves.text("deliver3d_vehicle", "bike");
        String vehName = switch (eqVeh) {
            case "scooter" -> "URBAN SCOOTER";
            case "superbike" -> "CRUISER MOTORCYCLE";
            case "gold" -> "GOLD SPECIAL BIKE";
            default -> "CLASSIC BICYCLE";
        };
        paint.setTextSize(11f);
        paint.setLetterSpacing(0.12f);
        paint.setColor(0xFF94A3B8);
        canvas.drawText("FLEET DEPLOYED", col1X + col1W / 2, col1Y + 340, paint);
        paint.setTextSize(15f);
        paint.setColor(0xFFFFFFFF);
        canvas.drawText(vehName, col1X + col1W / 2, col1Y + 364, paint);

        // Rating
        paint.setTextSize(11f);
        paint.setColor(0xFF94A3B8);
        canvas.drawText("CLEARANCE RATING", col1X + col1W / 2, col1Y + 410, paint);
        paint.setTextSize(18f);
        paint.setColor(0xFF38BDF8);
        canvas.drawText("\u2605 \u2605 \u2605 ELITE", col1X + col1W / 2, col1Y + 436, paint);

        // 6. Right Side: Statistics Grid (4 Big Stat Cards)
        paint.setTextAlign(Paint.Align.LEFT);
        float gridX = 432;
        float gridY = 164;
        float cardW = 380;
        float cardH = 104;
        float gapX = 24;
        float gapY = 18;

        // Stat 1: Total Points
        drawStatCard(canvas, gridX, gridY, cardW, cardH,
                "TOTAL REPUTATION POINTS",
                String.format(Locale.US, "%,d PTS", user.getPoints()),
                "ACHIEVED ON URBAN TRANSIT ROUTES", 0xFF38BDF8, paint);

        // Stat 2: Total Coins Collected
        drawStatCard(canvas, gridX + cardW + gapX, gridY, cardW, cardH,
                "TOTAL COINS COLLECTED",
                String.format(Locale.US, "%,d COINS", user.getTotalCoins()),
                "STREET TOKENS SECURED", 0xFFF59E0B, paint);

        // Stat 3: Total Money Earned
        drawStatCard(canvas, gridX, gridY + cardH + gapY, cardW, cardH,
                "LIFETIME DISPATCH EARNINGS",
                "$" + String.format(Locale.US, "%,d", user.getTotalMoney()),
                "CONTRACT PAYOUTS RECEIVED", 0xFF10B981, paint);

        // Stat 4: Deliveries Completed
        drawStatCard(canvas, gridX + cardW + gapX, gridY + cardH + gapY, cardW, cardH,
                "COMPLETED DELIVERIES",
                user.getCompletedDeliveries() + " PACKAGES",
                "100% ROUTE SUCCESS RATIO", 0xFFA78BFA, paint);

        // Stat 5: Playtime (Wide banner card spanning both columns)
        float bannerY = gridY + (cardH + gapY) * 2;
        float bannerW = cardW * 2 + gapX;
        float bannerH = 110;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x28FFFFFF);
        drawChamfered(canvas, gridX, bannerY, gridX + bannerW, bannerY + bannerH, 8, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f);
        paint.setColor(0x35FFFFFF);
        drawChamfered(canvas, gridX, bannerY, gridX + bannerW, bannerY + bannerH, 8, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextSize(12f);
        paint.setLetterSpacing(0.14f);
        paint.setColor(0xFF94A3B8);
        canvas.drawText("LIFETIME TIME IN TRANSIT", gridX + 24, bannerY + 34, paint);

        long totalSecs = user.getTotalPlaytime();
        long hours = totalSecs / 3600;
        long mins = (totalSecs % 3600) / 60;
        long secs = totalSecs % 60;
        String playtimeStr = (hours > 0 ? (hours + "h ") : "") + mins + "m " + secs + "s";

        paint.setTextSize(32f);
        paint.setLetterSpacing(0.10f);
        paint.setColor(0xFFF8FAFC);
        canvas.drawText(playtimeStr, gridX + 24, bannerY + 76, paint);

        paint.setTextSize(11f);
        paint.setLetterSpacing(0.12f);
        paint.setColor(0xFF64748B);
        canvas.drawText("AUTO-SYNCED TO WORLDWIDE SYNDICATE DATABASE", gridX + 24, bannerY + 96, paint);

        // 7. Footer: Verification Watermark
        paint.setTextSize(10f);
        paint.setLetterSpacing(0.15f);
        paint.setColor(0xFF64748B);
        paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("NIGHT RIDER 3D // DISPATCH ENGINE v2.0 // VERIFIED DOSSIER", width - 64, height - 48, paint);

        return bitmap;
    }

    private static void drawStatCard(Canvas canvas, float x, float y, float w, float h, String label, String value, String sub, int accentColor, Paint paint) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x26FFFFFF);
        drawChamfered(canvas, x, y, x + w, y + h, 8, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.2f);
        paint.setColor(0x33FFFFFF);
        drawChamfered(canvas, x, y, x + w, y + h, 8, paint);

        // Accent indicator bar on left edge of card
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accentColor);
        canvas.drawRect(x, y + 16, x + 4, y + h - 16, paint);

        // Label
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextSize(11f);
        paint.setLetterSpacing(0.14f);
        paint.setColor(0xFF94A3B8);
        canvas.drawText(label, x + 20, y + 28, paint);

        // Value
        paint.setTextSize(26f);
        paint.setLetterSpacing(0.08f);
        paint.setColor(accentColor);
        canvas.drawText(value, x + 20, y + 66, paint);

        // Subtitle
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        paint.setTextSize(10f);
        paint.setLetterSpacing(0.10f);
        paint.setColor(0xFF64748B);
        canvas.drawText(sub, x + 20, y + 88, paint);
    }

    private static void drawChamfered(Canvas c, float l, float t, float r, float b, float ch, Paint p) {
        Path path = new Path();
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

    public static void shareScoreCard(Context context) {
        if (context == null) return;
        Toast.makeText(context, "Generating Courier Dossier...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                Bitmap bmp = generateScoreCard(context);
                File dir = new File(context.getCacheDir(), "shared_score");
                if (!dir.exists()) dir.mkdirs();
                File imgFile = new File(dir, "night_rider_dossier.png");

                try (FileOutputStream fos = new FileOutputStream(imgFile)) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.flush();
                }

                Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", imgFile);

                UserManager user = UserManager.get();
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("image/png");
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Night Rider: " + user.getUsername() + "'s Courier Dossier");
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Check out my Courier Dossier in Night Rider 3D! Score: "
                        + user.getPoints() + " pts | Deliveries: " + user.getCompletedDeliveries() + " | Coins: " + user.getTotalCoins());
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                Intent chooser = Intent.createChooser(shareIntent, "Share Courier Dossier via");
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(chooser);
            } catch (Exception e) {
                Log.e(TAG, "Failed to share score card", e);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        Toast.makeText(context, "Could not generate share image", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}

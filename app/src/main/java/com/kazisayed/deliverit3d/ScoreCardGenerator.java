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

/**
 * ScoreCardGenerator: Minimal, elegant, monochrome Courier Dossier image.
 * Zero multicolor / AI-slop graphics, no colored vertical or horizontal lines,
 * minimal text, crisp condensed typography.
 */
public final class ScoreCardGenerator {
    private static final String TAG = "ScoreCard";

    public static Bitmap generateScoreCard(Context context) {
        UserManager user = UserManager.get();
        SaveStore saves = new SaveStore(context);

        int width = 1280;
        int height = 720;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 1. Dark Cinematic Background
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
            canvas.drawColor(0xFF090D14);
        }

        // 2. Heavy 82% dark vignette overlay (guarantees high text contrast, no colored wash)
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xD1080C12);
        canvas.drawRect(0, 0, width, height, paint);

        // Subtle gradient
        Paint gradPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Shader vGrad = new LinearGradient(0, 0, 0, height,
                new int[]{0xEE05080E, 0x9905080E, 0xF505080E},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        gradPaint.setShader(vGrad);
        canvas.drawRect(0, 0, width, height, gradPaint);

        // 3. Ultra-subtle border frame
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(0x22FFFFFF); // Faint clean white border
        canvas.drawRect(40, 40, width - 40, height - 40, paint);

        // 4. Header: Game Logo & Title (Monochrome, crisp condensed)
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextSize(34f);
        paint.setLetterSpacing(0.22f);
        paint.setColor(0xFFFFFFFF);
        canvas.drawText("NIGHT RIDER", 70, 96, paint);

        paint.setTextSize(12.5f);
        paint.setLetterSpacing(0.14f);
        paint.setColor(0xFF94A3B8);
        canvas.drawText("COURIER DOSSIER // OFFICIAL DISPATCH RECORD", 70, 124, paint);

        // Subtle horizontal divider (1px faint white)
        paint.setStrokeWidth(1f);
        paint.setColor(0x1FFFFFFF);
        canvas.drawLine(70, 148, width - 70, 148, paint);

        // 5. Rider Section: Avatar + Name + ID
        float avatarX = 70;
        float avatarY = 176;
        float avatarSize = 76;

        // Draw Avatar
        try {
            Bitmap avBmp = null;
            if (user.hasCustomProfilePic()) {
                Bitmap custom = user.getProfilePicBitmap();
                if (custom != null) avBmp = UserManager.getCircularBitmap(custom);
            }
            if (avBmp == null) {
                int defRes = UserManager.getDefaultAvatarDrawable(user.getAvatarId());
                Bitmap defBmp = BitmapFactory.decodeResource(context.getResources(), defRes);
                if (defBmp != null) avBmp = UserManager.getCircularBitmap(defBmp);
            }
            if (avBmp != null) {
                Rect aSrc = new Rect(0, 0, avBmp.getWidth(), avBmp.getHeight());
                RectF aDst = new RectF(avatarX, avatarY, avatarX + avatarSize, avatarY + avatarSize);
                canvas.drawBitmap(avBmp, aSrc, aDst, paint);
            } else {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF1E293B);
                canvas.drawCircle(avatarX + avatarSize / 2f, avatarY + avatarSize / 2f, avatarSize / 2f, paint);
            }
        } catch (Exception ignored) {}

        // Avatar subtle outline
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(0x40FFFFFF);
        canvas.drawCircle(avatarX + avatarSize / 2f, avatarY + avatarSize / 2f, avatarSize / 2f, paint);

        // Rider Name & ID
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextSize(26f);
        paint.setLetterSpacing(0.08f);
        paint.setColor(0xFFFFFFFF);
        canvas.drawText(user.getUsername().toUpperCase(Locale.ROOT), avatarX + avatarSize + 22, avatarY + 34, paint);

        String uid = user.getUid();
        String shortUid = (uid != null && uid.length() > 8) ? uid.substring(0, 8).toUpperCase(Locale.ROOT) : (uid != null ? uid.toUpperCase(Locale.ROOT) : "GUEST");
        paint.setTextSize(12f);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        paint.setLetterSpacing(0.12f);
        paint.setColor(0xFF94A3B8);
        canvas.drawText("ID: #" + shortUid + "  |  REGISTERED COURIER", avatarX + avatarSize + 22, avatarY + 62, paint);

        // Divider
        paint.setStrokeWidth(1f);
        paint.setColor(0x1FFFFFFF);
        canvas.drawLine(70, 280, width - 70, 280, paint);

        // 6. Minimal 4-Column Key Statistics (Pure White bold numbers, muted slate labels, NO multicolor!)
        float startX = 70;
        float statY = 325;
        float colWidth = (width - 140) / 4f;

        // Stat 1: Total Earnings
        drawStatColumn(canvas, startX + 0 * colWidth, statY, "TOTAL EARNINGS", "$" + String.format(Locale.US, "%,d", user.getCash()), paint);

        // Stat 2: Syndicate Score
        drawStatColumn(canvas, startX + 1 * colWidth, statY, "SYNDICATE SCORE", String.format(Locale.US, "%,d PTS", user.getPoints()), paint);

        // Stat 3: Deliveries
        drawStatColumn(canvas, startX + 2 * colWidth, statY, "DELIVERIES", user.getCompletedDeliveries() + " COMPLETED", paint);

        // Stat 4: Time in Transit
        long totalSecs = user.getTotalPlaytime();
        long hours = totalSecs / 3600;
        long mins = (totalSecs % 3600) / 60;
        long secs = totalSecs % 60;
        String playtimeStr = (hours > 0 ? (hours + "H ") : "") + mins + "M " + secs + "S";
        drawStatColumn(canvas, startX + 3 * colWidth, statY, "TIME IN TRANSIT", playtimeStr, paint);

        // Divider
        paint.setStrokeWidth(1f);
        paint.setColor(0x1FFFFFFF);
        canvas.drawLine(70, 435, width - 70, 435, paint);

        // 7. Active Vehicle & Equipment Details (Clean text)
        String activeVehicleId = saves.text("deliver3d_vehicle", "bike");
        com.kazisayed.deliverit3d.model.VehicleSpec activeSpec = com.kazisayed.deliverit3d.model.VehicleSpec.findById(activeVehicleId);
        String vName = activeSpec != null ? activeSpec.getName().toUpperCase(Locale.ROOT) : "FIXIE MESSENGER";
        String vType = activeSpec != null ? activeSpec.getType().toUpperCase(Locale.ROOT) : "BICYCLE";
        int vSpeed = activeSpec != null ? (int) activeSpec.getBaseSpeedKmh() : 45;

        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextSize(13f);
        paint.setLetterSpacing(0.10f);
        paint.setColor(0xFFCBD5E1);
        canvas.drawText("ACTIVE VEHICLE: " + vName + " (" + vType + ")  |  BASE SPEED: " + vSpeed + " KM/H", 70, 475, paint);

        // 8. Footer (Minimal, discreet)
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        paint.setTextSize(11f);
        paint.setLetterSpacing(0.14f);
        paint.setColor(0xFF475569);
        canvas.drawText("NIGHT RIDER 3D // DISPATCH ENGINE v2.0 // OFFICIAL DISPATCH RECORD", 70, height - 70, paint);

        return bitmap;
    }

    private static void drawStatColumn(Canvas canvas, float x, float y, String label, String value, Paint paint) {
        // Label: Muted slate, bold condensed, tracked
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
        paint.setTextSize(11.5f);
        paint.setLetterSpacing(0.12f);
        paint.setColor(0xFF94A3B8);
        canvas.drawText(label, x, y, paint);

        // Value: Bold white, large and readable
        paint.setTextSize(26f);
        paint.setLetterSpacing(0.06f);
        paint.setColor(0xFFFFFFFF);
        canvas.drawText(value, x, y + 42, paint);
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

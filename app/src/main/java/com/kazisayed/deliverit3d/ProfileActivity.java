package com.kazisayed.deliverit3d;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.kazisayed.deliverit3d.databinding.ActivityProfileBinding;
import java.io.InputStream;
import java.util.Locale;

/**
 * ProfileActivity: Courier profile screen displaying live statistics,
 * RTDB-backed total playtime, and customizable profile picture
 * with runtime storage/media permission checks and 5 default avatars fallback.
 */
public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    openGalleryPicker();
                } else {
                    Toast.makeText(this, "Photo access permission is required to select a profile picture.", Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleImageSelected);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UserManager user = UserManager.get();

        refreshAvatarDisplay();

        // Display user details
        binding.tvProfileName.setText(user.getUsername());
        String uid = user.getUid();
        binding.tvProfileUid.setText("ID: #" + (uid.length() > 8 ? uid.substring(0, 8) : uid));

        binding.tvProfileCash.setText("$" + user.getCash());
        binding.tvProfilePoints.setText(String.format(Locale.US, "%,d pts", user.getPoints()));

        // Total playtime backed by Firebase Realtime Database
        long totalSecs = user.getTotalPlaytime();
        long mins = totalSecs / 60;
        long secs = totalSecs % 60;
        binding.tvProfilePlaytime.setText(String.format(Locale.US, "%dm %ds", mins, secs));

        // Profile Picture Click Handlers
        binding.layoutAvatarContainer.setOnClickListener(v -> showPhotoOptionsDialog());
        binding.btnChangePhoto.setOnClickListener(v -> showPhotoOptionsDialog());

        binding.btnShareScore.setOnClickListener(v -> ScoreCardGenerator.shareScoreCard(this));
        binding.btnBackToGame.setOnClickListener(v -> finish());
    }

    private void refreshAvatarDisplay() {
        UserManager user = UserManager.get();
        if (user.hasCustomProfilePic()) {
            Bitmap customBmp = user.getProfilePicBitmap();
            if (customBmp != null) {
                Bitmap circular = UserManager.getCircularBitmap(customBmp);
                binding.ivAvatar.setImageBitmap(circular);
                binding.tvAvatarType.setText("CUSTOM PHOTO");
                return;
            }
        }

        // Fallback to one of the 5 default avatars
        int avatarRes = UserManager.getDefaultAvatarDrawable(user.getAvatarId());
        binding.ivAvatar.setImageResource(avatarRes);
        binding.tvAvatarType.setText("DEFAULT AVATAR #" + user.getAvatarId());
    }

    private void showPhotoOptionsDialog() {
        UserManager user = UserManager.get();
        CharSequence[] options;
        if (user.hasCustomProfilePic()) {
            options = new CharSequence[]{"Choose New Photo from Gallery", "Select Default Avatar (1-5)", "Remove Custom Photo", "Cancel"};
        } else {
            options = new CharSequence[]{"Choose Photo from Gallery", "Select Default Avatar (1-5)", "Cancel"};
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Profile Picture");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                checkPermissionAndPick();
            } else if (which == 1) {
                showDefaultAvatarsPicker();
            } else if (which == 2 && user.hasCustomProfilePic()) {
                user.removeProfilePic(this);
                refreshAvatarDisplay();
                Toast.makeText(this, "Reverted to default avatar.", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });
        builder.show();
    }

    private void checkPermissionAndPick() {
        String permission = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            openGalleryPicker();
        } else {
            requestPermissionLauncher.launch(permission);
        }
    }

    private void openGalleryPicker() {
        pickImageLauncher.launch("image/*");
    }

    private void handleImageSelected(Uri uri) {
        if (uri == null) return;
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (is != null) is.close();

            if (bitmap != null) {
                UserManager.get().setProfilePic(this, bitmap);
                refreshAvatarDisplay();
                Toast.makeText(this, "Profile picture updated successfully!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Unable to read image.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error loading image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showDefaultAvatarsPicker() {
        CharSequence[] avatarChoices = new CharSequence[]{
                "Avatar 1 (Urban Messenger)",
                "Avatar 2 (Night Courier)",
                "Avatar 3 (Metro Rider)",
                "Avatar 4 (Cyber Courier)",
                "Avatar 5 (District Veteran)"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Choose Default Avatar");
        builder.setItems(avatarChoices, (dialog, which) -> {
            int selectedId = which + 1; // 1 to 5
            UserManager.get().removeProfilePic(this); // Clears custom pic to use default avatar
            UserManager.get().setAvatarId(this, selectedId);
            refreshAvatarDisplay();
            Toast.makeText(this, "Avatar updated to #" + selectedId, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        builder.show();
    }
}

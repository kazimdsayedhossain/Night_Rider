package com.kazisayed.deliverit3d;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.kazisayed.deliverit3d.databinding.ActivityProfileBinding;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * ProfileActivity: Courier ID card.
 *
 * Content Provider usage: Android Photo Picker (PickVisualMedia) reads from
 * MediaStore.Images — the device's shared media content provider.
 * Selected image is compressed and uploaded to Firebase Storage at portraits/{uid}.jpg.
 * The download URL is saved to Firestore users/{uid}.portraitUrl.
 */
public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private FirebaseUser user;
    private FirebaseFirestore db;

    // Photo picker — uses MediaStore content provider under the hood
    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) uploadPortrait(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        user = FirebaseAuth.getInstance().getCurrentUser();
        db = FirebaseFirestore.getInstance();

        if (user == null) { finish(); return; }

        // Display name
        String name = user.isAnonymous() ? "Guest Courier" :
                (user.getDisplayName() != null ? user.getDisplayName() : "Courier");
        binding.tvProfileName.setText(name);

        // Load stats from Firestore
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(this, (DocumentSnapshot doc) -> {
                    if (!doc.exists()) return;
                    Long cash = doc.getLong("cash");
                    Long level = doc.getLong("level");
                    binding.tvProfileCash.setText((cash != null ? cash : 0) + " coins");
                    binding.tvProfileLevel.setText("Level " + (level != null ? level : 1));
                });

        binding.btnChangePhoto.setOnClickListener(v ->
                photoPicker.launch(new PickVisualMediaRequest.Builder()
                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                        .build()));
    }

    /**
     * Upload selected portrait to Firebase Storage.
     * Reads bytes from the MediaStore URI (Content Provider), uploads to Storage,
     * then saves the download URL to Firestore.
     */
    private void uploadPortrait(Uri uri) {
        binding.progressUpload.setVisibility(View.VISIBLE);
        binding.btnChangePhoto.setEnabled(false);

        new Thread(() -> {
            byte[] bytes = readBytes(uri);
            if (bytes == null) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Could not read image", Toast.LENGTH_SHORT).show();
                    binding.progressUpload.setVisibility(View.GONE);
                    binding.btnChangePhoto.setEnabled(true);
                });
                return;
            }

            StorageReference ref = FirebaseStorage.getInstance()
                    .getReference("portraits/" + user.getUid() + ".jpg");
            ref.putBytes(bytes)
                    .continueWithTask(task -> ref.getDownloadUrl())
                    .addOnSuccessListener(this, downloadUri -> {
                        // Save URL to Firestore
                        db.collection("users").document(user.getUid())
                                .update("portraitUrl", downloadUri.toString());

                        // Update portrait ImageView
                        binding.ivPortrait.setImageURI(uri);
                        binding.progressUpload.setVisibility(View.GONE);
                        binding.btnChangePhoto.setEnabled(true);
                        Toast.makeText(this, "Portrait updated!", Toast.LENGTH_SHORT).show();
                    })
                    .addOnFailureListener(this, e -> {
                        Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        binding.progressUpload.setVisibility(View.GONE);
                        binding.btnChangePhoto.setEnabled(true);
                    });
        }).start();
    }

    private byte[] readBytes(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            int read;
            while ((read = in.read(tmp)) != -1) buf.write(tmp, 0, read);
            return buf.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}

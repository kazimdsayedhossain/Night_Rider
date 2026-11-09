package com.kazisayed.deliverit3d;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.kazisayed.deliverit3d.databinding.ActivityProfileBinding;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        UserManager user = UserManager.get();

        // Assign permanent cartoon avatar
        binding.ivAvatar.setImageResource(UserManager.getAvatarDrawable(user.getAvatarId()));

        // Display user details
        binding.tvProfileName.setText(user.getUsername());
        String uid = user.getUid();
        binding.tvProfileUid.setText("ID: #" + (uid.length() > 8 ? uid.substring(0, 8) : uid));

        binding.tvProfileCash.setText("$" + user.getCash());
        binding.tvProfilePoints.setText(String.format(Locale.US, "%,d pts", user.getPoints()));

        long totalSecs = user.getTotalPlaytime();
        long mins = totalSecs / 60;
        long secs = totalSecs % 60;
        binding.tvProfilePlaytime.setText(String.format(Locale.US, "%dm %ds", mins, secs));

        binding.btnShareScore.setOnClickListener(v -> ScoreCardGenerator.shareScoreCard(this));
        binding.btnBackToGame.setOnClickListener(v -> finish());
    }
}

package com.kazisayed.deliverit3d;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AlphaAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.AnimationSet;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.kazisayed.deliverit3d.databinding.ActivitySplashBinding;

/**
 * SplashActivity: boot logo animation.
 * If the user is already signed in (Firebase Auth persists), skips to MainMenuActivity.
 * Otherwise routes to AuthActivity after the animation.
 */
public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Animate logo: scale up + fade in
        AnimationSet anim = new AnimationSet(true);
        ScaleAnimation scale = new ScaleAnimation(0.6f, 1f, 0.6f, 1f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f);
        scale.setDuration(600);
        AlphaAnimation fade = new AlphaAnimation(0f, 1f);
        fade.setDuration(600);
        anim.addAnimation(scale);
        anim.addAnimation(fade);
        binding.ivLogo.startAnimation(anim);
        binding.tvTitle.startAnimation(fade);

        // After 1.5s, check auth state and route accordingly
        new Handler(Looper.getMainLooper()).postDelayed(this::navigate, 1500);
    }

    private void navigate() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        Class<?> dest = (user != null) ? MainMenuActivity.class : AuthActivity.class;
        startActivity(new Intent(this, dest));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}

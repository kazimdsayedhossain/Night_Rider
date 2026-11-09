package com.kazisayed.deliverit3d;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.kazisayed.deliverit3d.databinding.ActivityAuthBinding;

public class AuthActivity extends AppCompatActivity {

    private static final String TAG = "AuthActivity";
    private ActivityAuthBinding binding;
    private FirebaseAuth auth;
    private GoogleSignInClient googleClient;

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                try {
                    GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(result.getData())
                            .getResult(ApiException.class);
                    if (account != null && account.getIdToken() != null) {
                        String name = account.getDisplayName();
                        if (name == null || name.trim().isEmpty()) {
                            name = account.getGivenName();
                        }
                        if (name == null || name.trim().isEmpty()) {
                            if (account.getEmail() != null && account.getEmail().contains("@")) {
                                name = account.getEmail().split("@")[0];
                            }
                        }
                        Log.i(TAG, "Google sign-in token obtained for: " + account.getEmail() + " (" + name + ")");
                        firebaseAuthWithGoogle(account.getIdToken(), name);
                    } else {
                        showError("Google account did not return a valid ID token");
                        setLoading(false);
                    }
                } catch (ApiException e) {
                    Log.e(TAG, "Google sign-in ApiException code: " + e.getStatusCode(), e);
                    showError("Google sign-in (" + e.getStatusCode() + "): " + e.getMessage());
                    setLoading(false);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xFF05080C));
        hideSystemUi();

        super.onCreate(savedInstanceState);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        auth = FirebaseAuth.getInstance();

        // Configure Google Sign-In Client
        String webClientId = getString(R.string.default_web_client_id);
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();
        googleClient = GoogleSignIn.getClient(this, gso);

        // Auto-skip ONLY if user is already authenticated with a real account (Google or Email)
        FirebaseUser currentUser = auth.getCurrentUser();
        boolean forceAuth = getIntent().getBooleanExtra("force_auth", false);
        if (currentUser != null && !currentUser.isAnonymous() && !forceAuth) {
            Log.i(TAG, "Existing authenticated user found: " + currentUser.getEmail() + " (" + currentUser.getDisplayName() + ")");
            UserManager.get().loadOrCreateProfile(this, currentUser, currentUser.getDisplayName(), mgr -> navigateToGame());
            return;
        }

        // 1. Google Sign-In
        binding.btnGoogleSignIn.setOnClickListener(v -> {
            setLoading(true);
            googleClient.signOut().addOnCompleteListener(task -> {
                Intent signInIntent = googleClient.getSignInIntent();
                googleSignInLauncher.launch(signInIntent);
            });
        });

        // 2. Open Right-Side Email & Password Section
        binding.btnToggleEmail.setOnClickListener(v -> toggleEmailSection(true));
        binding.btnContentClose.setOnClickListener(v -> toggleEmailSection(false));

        // 3. Email & Password Sign In
        binding.btnEmailSignIn.setOnClickListener(v -> {
            String name = getName();
            String email = getEmail();
            String password = getPassword();
            if (validate(email, password)) {
                setLoading(true);
                auth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener(r -> onAuthSuccess(name))
                        .addOnFailureListener(e -> {
                            showError("Sign-in failed: " + e.getMessage());
                            setLoading(false);
                        });
            }
        });

        // 4. Email & Password Register (Name is MUST)
        binding.btnEmailSignUp.setOnClickListener(v -> {
            String name = getName();
            String email = getEmail();
            String password = getPassword();
            if (TextUtils.isEmpty(name)) {
                binding.etName.setError("Courier Name / Call-Sign is required");
                binding.etName.requestFocus();
                return;
            }
            if (validate(email, password)) {
                setLoading(true);
                auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener(r -> {
                            FirebaseUser user = auth.getCurrentUser();
                            if (user != null) {
                                UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                        .setDisplayName(name)
                                        .build();
                                user.updateProfile(profileUpdates)
                                        .addOnCompleteListener(t -> onAuthSuccess(name));
                            } else {
                                onAuthSuccess(name);
                            }
                        })
                        .addOnFailureListener(e -> {
                            showError("Registration failed: " + e.getMessage());
                            setLoading(false);
                        });
            }
        });

        // 5. Guest Access (Falls back to local guest session if Firebase Anonymous Auth is disabled)
        binding.btnGuest.setOnClickListener(v -> {
            setLoading(true);
            auth.signInAnonymously()
                    .addOnSuccessListener(r -> onAuthSuccess("Guest Rider"))
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Firebase anonymous sign-in disabled in console, using offline guest session: " + e.getMessage());
                        UserManager.get().loadOrCreateProfile(this, null, "Guest Rider", mgr -> navigateToGame());
                    });
        });
    }

    private void toggleEmailSection(boolean show) {
        if (show) {
            binding.tvTitleMain.setText("AUTHENTICATION");
            binding.layoutRightContent.setVisibility(View.VISIBLE);
            binding.layoutRightContent.setAlpha(0f);
            binding.layoutRightContent.setTranslationX(-40f);
            binding.layoutRightContent.animate().alpha(1f).translationX(0f).setDuration(180).start();
        } else {
            binding.tvTitleMain.setText("NIGHT RIDER");
            binding.layoutRightContent.animate().alpha(0f).translationX(-40f).setDuration(150).withEndAction(() -> {
                binding.layoutRightContent.setVisibility(View.GONE);
            }).start();
        }
    }

    private void hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    public void onBackPressed() {
        if (binding.layoutRightContent.getVisibility() == View.VISIBLE) {
            toggleEmailSection(false);
            return;
        }
        super.onBackPressed();
    }

    private String getName() {
        return binding.etName.getText() != null ? binding.etName.getText().toString().trim() : "";
    }

    private String getEmail() {
        return binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
    }

    private String getPassword() {
        return binding.etPassword.getText() != null ? binding.etPassword.getText().toString().trim() : "";
    }

    private boolean validate(String email, String password) {
        if (TextUtils.isEmpty(email) || !email.contains("@")) {
            binding.etEmail.setError("Enter a valid email address");
            return false;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            binding.etPassword.setError("Password must be at least 6 characters");
            return false;
        }
        return true;
    }

    private void firebaseAuthWithGoogle(String idToken, String preferredName) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnSuccessListener(r -> {
                    Log.i(TAG, "Firebase authenticated successfully with Google ID token, preferred name: " + preferredName);
                    FirebaseUser user = auth.getCurrentUser();
                    if (user != null && preferredName != null && !preferredName.trim().isEmpty()) {
                        UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                .setDisplayName(preferredName)
                                .build();
                        user.updateProfile(profileUpdates);
                    }
                    onAuthSuccess(preferredName);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Firebase auth with Google failed", e);
                    showError("Firebase Google auth failed: " + e.getMessage());
                    setLoading(false);
                });
    }

    private void onAuthSuccess(String preferredName) {
        FirebaseUser user = auth.getCurrentUser();
        Log.i(TAG, "Auth success, syncing profile for UID: " + (user != null ? user.getUid() : "null") + ", name: " + preferredName);
        UserManager.get().loadOrCreateProfile(this, user, preferredName, mgr -> navigateToGame());
    }

    private void navigateToGame() {
        Intent intent = new Intent(this, GameActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void setLoading(boolean loading) {
        binding.progressAuth.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnGoogleSignIn.setEnabled(!loading);
        binding.btnToggleEmail.setEnabled(!loading);
        binding.btnEmailSignIn.setEnabled(!loading);
        binding.btnEmailSignUp.setEnabled(!loading);
        binding.btnGuest.setEnabled(!loading);
    }

    private void showError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
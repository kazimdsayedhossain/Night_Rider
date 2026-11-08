package com.kazisayed.deliverit3d;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.kazisayed.deliverit3d.databinding.ActivityAuthBinding;

public class AuthActivity extends AppCompatActivity {

    private ActivityAuthBinding binding;
    private FirebaseAuth auth;
    private GoogleSignInClient googleClient;

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                try {
                    GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(result.getData())
                            .getResult(ApiException.class);
                    if (account != null && account.getIdToken() != null) {
                        firebaseAuthWithGoogle(account.getIdToken());
                    } else {
                        showError("Google account has no valid ID token");
                        setLoading(false);
                    }
                } catch (ApiException e) {
                    showError("Google sign-in error (" + e.getStatusCode() + "): " + e.getMessage());
                    setLoading(false);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();

        // Configure Google Sign-In
        String webClientId = getString(R.string.default_web_client_id);
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();
        googleClient = GoogleSignIn.getClient(this, gso);

        // Email & Password: Sign In
        binding.btnEmailSignIn.setOnClickListener(v -> {
            String email = getEmail();
            String password = getPassword();
            if (validate(email, password)) {
                setLoading(true);
                auth.signInWithEmailAndPassword(email, password)
                        .addOnSuccessListener(r -> goToMainMenu())
                        .addOnFailureListener(e -> {
                            showError("Sign-in failed: " + e.getMessage());
                            setLoading(false);
                        });
            }
        });

        // Email & Password: Register
        binding.btnEmailSignUp.setOnClickListener(v -> {
            String email = getEmail();
            String password = getPassword();
            if (validate(email, password)) {
                setLoading(true);
                auth.createUserWithEmailAndPassword(email, password)
                        .addOnSuccessListener(r -> goToMainMenu())
                        .addOnFailureListener(e -> {
                            showError("Registration failed: " + e.getMessage());
                            setLoading(false);
                        });
            }
        });

        // Google Sign-In
        binding.btnGoogleSignIn.setOnClickListener(v -> {
            setLoading(true);
            googleSignInLauncher.launch(googleClient.getSignInIntent());
        });

        // Guest mode
        binding.btnGuest.setOnClickListener(v -> {
            setLoading(true);
            auth.signInAnonymously()
                    .addOnSuccessListener(r -> goToMainMenu())
                    .addOnFailureListener(e -> {
                        showError("Guest sign-in failed: " + e.getMessage());
                        setLoading(false);
                    });
        });
    }

    private String getEmail() {
        return binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
    }

    private String getPassword() {
        return binding.etPassword.getText() != null ? binding.etPassword.getText().toString().trim() : "";
    }

    private boolean validate(String email, String password) {
        if (TextUtils.isEmpty(email) || !email.contains("@")) {
            binding.etEmail.setError("Enter a valid email");
            return false;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            binding.etPassword.setError("Password must be at least 6 characters");
            return false;
        }
        return true;
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnSuccessListener(r -> goToMainMenu())
                .addOnFailureListener(e -> {
                    showError("Firebase auth with Google failed: " + e.getMessage());
                    setLoading(false);
                });
    }

    private void goToMainMenu() {
        startActivity(new Intent(this, MainMenuActivity.class));
        finish();
    }

    private void setLoading(boolean loading) {
        binding.progressAuth.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnEmailSignIn.setEnabled(!loading);
        binding.btnEmailSignUp.setEnabled(!loading);
        binding.btnGoogleSignIn.setEnabled(!loading);
        binding.btnGuest.setEnabled(!loading);
    }

    private void showError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}

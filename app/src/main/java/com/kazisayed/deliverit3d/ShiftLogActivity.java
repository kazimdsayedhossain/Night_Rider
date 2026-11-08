package com.kazisayed.deliverit3d;

import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.kazisayed.deliverit3d.adapter.ShiftLogAdapter;
import com.kazisayed.deliverit3d.databinding.ActivityShiftLogBinding;
import com.kazisayed.deliverit3d.model.ShiftRecord;
import java.util.ArrayList;
import java.util.List;

/**
 * ShiftLogActivity: personal delivery history from Firestore.
 *
 * Reads: users/{uid}/shifts - sorted by date descending.
 * Uses RecyclerView + ShiftLogAdapter + ShiftLogViewHolder.
 */
public class ShiftLogActivity extends AppCompatActivity {

    private ActivityShiftLogBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityShiftLogBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }

        List<ShiftRecord> shifts = new ArrayList<>();
        ShiftLogAdapter adapter = new ShiftLogAdapter(shifts);
        binding.rvShiftLog.setLayoutManager(new LinearLayoutManager(this));
        binding.rvShiftLog.setAdapter(adapter);

        FirebaseFirestore.getInstance()
                .collection("users").document(user.getUid())
                .collection("shifts")
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(this, snapshot -> {
                    for (QueryDocumentSnapshot doc : snapshot) {
                        ShiftRecord r = doc.toObject(ShiftRecord.class);
                        shifts.add(r);
                    }
                    adapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> { /* offline: list stays empty */ });
    }
}

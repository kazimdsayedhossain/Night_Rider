package com.kazisayed.deliverit3d;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.kazisayed.deliverit3d.adapter.VehicleAdapter;
import com.kazisayed.deliverit3d.databinding.ActivityGarageBinding;
import com.kazisayed.deliverit3d.model.VehicleSpec;
import java.util.ArrayList;
import java.util.List;

/**
 * GarageActivity: vehicle showroom.
 *
 * Uses View Binding + RecyclerView + VehicleAdapter / VehicleViewHolder.
 * Equipping a vehicle saves the choice to Firestore (online) and LevelDB (offline).
 */
public class GarageActivity extends AppCompatActivity implements VehicleAdapter.OnEquipListener {

    private ActivityGarageBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGarageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        List<VehicleSpec> vehicles = VehicleSpec.all();
        String equipped = SaveStore.getEquippedVehicle(getApplicationContext());

        VehicleAdapter adapter = new VehicleAdapter(vehicles, equipped, this);
        binding.rvVehicles.setLayoutManager(new LinearLayoutManager(this));
        binding.rvVehicles.setAdapter(adapter);
    }

    @Override
    public void onEquip(VehicleSpec vehicle) {
        SaveStore.setEquippedVehicle(getApplicationContext(), vehicle.getId());
        // Also push to Firestore if online
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(user.getUid())
                    .update("equippedVehicle", vehicle.getId());
        }
    }
}

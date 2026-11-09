package com.kazisayed.deliverit3d;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.kazisayed.deliverit3d.adapter.VehicleAdapter;
import com.kazisayed.deliverit3d.databinding.ActivityGarageBinding;
import com.kazisayed.deliverit3d.databinding.DialogVehicleUpgradeBinding;
import com.kazisayed.deliverit3d.model.VehicleSpec;
import java.util.List;
import java.util.Locale;

/**
 * GarageActivity: vehicle showroom with strict cash buying validation
 * and vehicle-specific upgrade tuning for speed and order queue capacity.
 */
public class GarageActivity extends AppCompatActivity implements VehicleAdapter.OnVehicleActionListener {

    private ActivityGarageBinding binding;
    private VehicleAdapter adapter;
    private List<VehicleSpec> vehicles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGarageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        vehicles = VehicleSpec.all();
        String equipped = SaveStore.getEquippedVehicle(getApplicationContext());

        updateWallet();

        adapter = new VehicleAdapter(vehicles, equipped, this);
        binding.rvVehicles.setLayoutManager(new LinearLayoutManager(this));
        binding.rvVehicles.setAdapter(adapter);

        binding.btnBackGarage.setOnClickListener(v -> finish());
    }

    private void updateWallet() {
        int cash = SaveStore.getCash(getApplicationContext());
        binding.tvGarageWallet.setText(String.format(Locale.US, "WALLET: $%,d", cash));
    }

    @Override
    public void onEquip(VehicleSpec vehicle) {
        SaveStore.setEquippedVehicle(getApplicationContext(), vehicle.getId());
        adapter.setEquippedId(vehicle.getId());
        adapter.notifyDataSetChanged();

        // Push to Firestore if logged in
        com.google.firebase.auth.FirebaseUser user =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(user.getUid())
                    .update("equippedVehicle", vehicle.getId());
        }
        Toast.makeText(this, "Equipped " + vehicle.getName(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBuy(VehicleSpec vehicle) {
        int cash = SaveStore.getCash(getApplicationContext());
        if (cash < vehicle.getPrice()) {
            Toast.makeText(this, String.format(Locale.US, "Insufficient Cash! Need $%,d (Wallet: $%,d)", vehicle.getPrice(), cash), Toast.LENGTH_SHORT).show();
            return;
        }

        boolean success = SaveStore.buyVehicle(getApplicationContext(), vehicle.getId(), vehicle.getPrice());
        if (success) {
            SaveStore.setEquippedVehicle(getApplicationContext(), vehicle.getId());
            adapter.setEquippedId(vehicle.getId());
            updateWallet();
            adapter.notifyDataSetChanged();

            com.google.firebase.auth.FirebaseUser user =
                    com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users").document(user.getUid())
                        .update("equippedVehicle", vehicle.getId());
            }

            Toast.makeText(this, "Purchased and Equipped " + vehicle.getName() + "!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onUpgrade(VehicleSpec vehicle) {
        showUpgradeDialog(vehicle);
    }

    private void showUpgradeDialog(VehicleSpec vehicle) {
        DialogVehicleUpgradeBinding dialogBinding = DialogVehicleUpgradeBinding.inflate(
                LayoutInflater.from(this));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogBinding.getRoot())
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        refreshDialogViews(dialogBinding, vehicle);

        dialogBinding.btnDialogClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void refreshDialogViews(DialogVehicleUpgradeBinding dialogBinding, VehicleSpec vehicle) {
        int cash = SaveStore.getCash(getApplicationContext());
        dialogBinding.tvDialogVehicleName.setText(vehicle.getName() + " [" + vehicle.getType() + "]");
        dialogBinding.tvDialogWallet.setText(String.format(Locale.US, "WALLET: $%,d", cash));

        // Speed Part Tuning
        int speedTier = SaveStore.getSpeedUpgradeLevel(getApplicationContext(), vehicle.getId());
        dialogBinding.tvSpeedPartName.setText(vehicle.getSpeedPartName());
        dialogBinding.tvSpeedPartDesc.setText(vehicle.getSpeedPartDesc());
        dialogBinding.tvSpeedTierLabel.setText("TIER " + speedTier + " / 3");

        if (speedTier == 0) {
            dialogBinding.tvSpeedStatDiff.setText("Current: Stock -> Next: +12% Speed, +15% Accel");
        } else if (speedTier == 1) {
            dialogBinding.tvSpeedStatDiff.setText("Current: +12% Speed, +15% Accel -> Next: +24% Speed, +30% Accel");
        } else if (speedTier == 2) {
            dialogBinding.tvSpeedStatDiff.setText("Current: +24% Speed, +30% Accel -> Next: +36% Speed, +45% Accel");
        } else {
            dialogBinding.tvSpeedStatDiff.setText("Current: +36% Speed, +45% Accel [MAXIMUM TIER]");
        }

        int speedCost = VehicleSpec.getSpeedUpgradeCost(vehicle.getId(), speedTier);
        if (speedCost > 0) {
            dialogBinding.btnDialogUpgradeSpeed.setText(String.format(Locale.US, "UPGRADE FOR $%,d", speedCost));
            dialogBinding.btnDialogUpgradeSpeed.setEnabled(true);
            dialogBinding.btnDialogUpgradeSpeed.setOnClickListener(v -> {
                int curCash = SaveStore.getCash(getApplicationContext());
                if (curCash < speedCost) {
                    Toast.makeText(this, String.format(Locale.US, "Need $%,d for this upgrade! (Wallet: $%,d)", speedCost, curCash), Toast.LENGTH_SHORT).show();
                    return;
                }
                SaveStore.upgradeVehicleSpeed(getApplicationContext(), vehicle.getId(), speedCost);
                updateWallet();
                refreshDialogViews(dialogBinding, vehicle);
                adapter.notifyDataSetChanged();
                Toast.makeText(this, "Upgraded " + vehicle.getSpeedPartName() + " to Tier " + (speedTier + 1) + "!", Toast.LENGTH_SHORT).show();
            });
        } else {
            dialogBinding.btnDialogUpgradeSpeed.setText("MAX TIER REACHED");
            dialogBinding.btnDialogUpgradeSpeed.setEnabled(false);
            dialogBinding.btnDialogUpgradeSpeed.setOnClickListener(null);
        }

        // Cargo Capacity Tuning
        int capTier = SaveStore.getCapacityUpgradeLevel(getApplicationContext(), vehicle.getId());
        dialogBinding.tvCapPartName.setText(vehicle.getCapacityPartName());
        dialogBinding.tvCapPartDesc.setText(vehicle.getCapacityPartDesc());
        dialogBinding.tvCapTierLabel.setText("TIER " + capTier + " / 3");

        int curCap = VehicleSpec.getCapacityForTier(vehicle.getId(), capTier);
        if (capTier < 3) {
            int nextCap = VehicleSpec.getCapacityForTier(vehicle.getId(), capTier + 1);
            dialogBinding.tvCapStatDiff.setText("Current: " + curCap + " Orders -> Next: " + nextCap + " Orders (+" + vehicle.getCapacityPerTier() + " Slots)");
        } else {
            dialogBinding.tvCapStatDiff.setText("Current: " + curCap + " Orders [MAXIMUM CAPACITY]");
        }

        int capCost = VehicleSpec.getCapacityUpgradeCost(vehicle.getId(), capTier);
        if (capCost > 0) {
            dialogBinding.btnDialogUpgradeCap.setText(String.format(Locale.US, "UPGRADE FOR $%,d", capCost));
            dialogBinding.btnDialogUpgradeCap.setEnabled(true);
            dialogBinding.btnDialogUpgradeCap.setOnClickListener(v -> {
                int curCash = SaveStore.getCash(getApplicationContext());
                if (curCash < capCost) {
                    Toast.makeText(this, String.format(Locale.US, "Need $%,d for this upgrade! (Wallet: $%,d)", capCost, curCash), Toast.LENGTH_SHORT).show();
                    return;
                }
                SaveStore.upgradeVehicleCapacity(getApplicationContext(), vehicle.getId(), capCost);
                updateWallet();
                refreshDialogViews(dialogBinding, vehicle);
                adapter.notifyDataSetChanged();
                Toast.makeText(this, "Upgraded " + vehicle.getCapacityPartName() + " to Tier " + (capTier + 1) + "!", Toast.LENGTH_SHORT).show();
            });
        } else {
            dialogBinding.btnDialogUpgradeCap.setText("MAX TIER REACHED");
            dialogBinding.btnDialogUpgradeCap.setEnabled(false);
            dialogBinding.btnDialogUpgradeCap.setOnClickListener(null);
        }

        // Utility / Handling Tuning
        int utilTier = SaveStore.getUtilityUpgradeLevel(getApplicationContext(), vehicle.getId());
        dialogBinding.tvUtilPartName.setText(vehicle.getUtilityPartName());
        dialogBinding.tvUtilPartDesc.setText(vehicle.getUtilityPartDesc());
        dialogBinding.tvUtilTierLabel.setText("TIER " + utilTier + " / 3");

        if (vehicle.getUtilityPart() != null) {
            if (utilTier < 3) {
                dialogBinding.tvUtilStatDiff.setText("Current: " + vehicle.getUtilityPart().getBenefit(utilTier)
                        + " -> Next: " + vehicle.getUtilityPart().getNextBenefit(utilTier));
            } else {
                dialogBinding.tvUtilStatDiff.setText("Current: " + vehicle.getUtilityPart().getBenefit(utilTier) + " [MAXIMUM TIER]");
            }
        }

        int utilCost = VehicleSpec.getUtilityUpgradeCost(vehicle.getId(), utilTier);
        if (utilCost > 0) {
            dialogBinding.btnDialogUpgradeUtil.setText(String.format(Locale.US, "UPGRADE FOR $%,d", utilCost));
            dialogBinding.btnDialogUpgradeUtil.setEnabled(true);
            dialogBinding.btnDialogUpgradeUtil.setOnClickListener(v -> {
                int curCash = SaveStore.getCash(getApplicationContext());
                if (curCash < utilCost) {
                    Toast.makeText(this, String.format(Locale.US, "Need $%,d for this upgrade! (Wallet: $%,d)", utilCost, curCash), Toast.LENGTH_SHORT).show();
                    return;
                }
                SaveStore.upgradeVehicleUtility(getApplicationContext(), vehicle.getId(), utilCost);
                updateWallet();
                refreshDialogViews(dialogBinding, vehicle);
                adapter.notifyDataSetChanged();
                Toast.makeText(this, "Upgraded " + vehicle.getUtilityPartName() + " to Tier " + (utilTier + 1) + "!", Toast.LENGTH_SHORT).show();
            });
        } else {
            dialogBinding.btnDialogUpgradeUtil.setText("MAX TIER REACHED");
            dialogBinding.btnDialogUpgradeUtil.setEnabled(false);
            dialogBinding.btnDialogUpgradeUtil.setOnClickListener(null);
        }
    }
}

package com.kazisayed.deliverit3d;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.kazisayed.deliverit3d.adapter.VehicleAdapter;
import com.kazisayed.deliverit3d.databinding.ActivityGarageBinding;
import com.kazisayed.deliverit3d.databinding.DialogUpgradePopUpBinding;
import com.kazisayed.deliverit3d.model.VehicleSpec;
import java.util.List;
import java.util.Locale;

/**
 * GarageActivity:
 * - Lists available vehicles (bikes and cycles) on the left.
 * - When clicking any purchased vehicle, opens accessories section in the entire right window.
 * - Clicking buy on any accessory triggers a themed modal pop-up with a darkened background,
 *   explaining what it improves, with Buy and Cancel buttons.
 * - Zero ASCII icons throughout.
 */
public class GarageActivity extends AppCompatActivity implements VehicleAdapter.OnVehicleActionListener {

    private ActivityGarageBinding binding;
    private VehicleAdapter adapter;
    private List<VehicleSpec> vehicles;
    private VehicleSpec currentSelectedVehicle;

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

        // Back button inside accessories section returns to default garage right panel
        binding.btnAccBack.setOnClickListener(v -> closeAccessoriesSection());

        // Auto-select equipped vehicle to show its accessories initially if owned
        VehicleSpec equippedSpec = VehicleSpec.findById(equipped);
        if (equippedSpec != null) {
            onVehicleSelected(equippedSpec);
        }
    }

    private void updateWallet() {
        int cash = SaveStore.getCash(getApplicationContext());
        String walletText = String.format(Locale.US, "WALLET: $%,d", cash);
        binding.tvGarageWallet.setText(walletText);
        binding.tvAccWallet.setText(walletText);
    }

    private void closeAccessoriesSection() {
        currentSelectedVehicle = null;
        binding.layoutAccessoriesSection.setVisibility(View.GONE);
        binding.layoutGarageDefault.setVisibility(View.VISIBLE);
        adapter.setSelectedVehicleId(null);
        adapter.notifyDataSetChanged();
    }

    @Override
    public void onVehicleSelected(VehicleSpec vehicle) {
        currentSelectedVehicle = vehicle;
        binding.layoutGarageDefault.setVisibility(View.GONE);
        binding.layoutAccessoriesSection.setVisibility(View.VISIBLE);

        refreshAccessoriesSection(vehicle);
    }

    private void refreshAccessoriesSection(VehicleSpec vehicle) {
        if (vehicle == null) return;

        updateWallet();

        binding.tvAccVehicleName.setText(vehicle.getName().toUpperCase(Locale.ROOT));

        String equippedId = SaveStore.getEquippedVehicle(getApplicationContext());
        boolean isEquipped = vehicle.getId().equals(equippedId);

        if (isEquipped) {
            binding.btnAccEquip.setText("EQUIPPED");
            binding.btnAccEquip.setEnabled(false);
            binding.btnAccEquip.setAlpha(0.6f);
        } else {
            binding.btnAccEquip.setText("EQUIP VEHICLE");
            binding.btnAccEquip.setEnabled(true);
            binding.btnAccEquip.setAlpha(1.0f);
            binding.btnAccEquip.setOnClickListener(v -> onEquip(vehicle));
        }

        // Accessory 1: Speed Part
        int speedTier = SaveStore.getSpeedUpgradeLevel(getApplicationContext(), vehicle.getId());
        binding.tvAccSpeedName.setText(vehicle.getSpeedPartName().toUpperCase(Locale.ROOT));
        binding.tvAccSpeedLevel.setText("TIER " + speedTier + "/3");
        String nextSpeedBenefit = vehicle.getSpeedPart() != null
                ? vehicle.getSpeedPart().getNextBenefit(speedTier)
                : "+12% Speed Boost";
        binding.tvAccSpeedBenefit.setText(speedTier >= 3 ? "MAX TIER REACHED" : ("NEXT: " + nextSpeedBenefit));

        int speedCost = VehicleSpec.getSpeedUpgradeCost(vehicle.getId(), speedTier);
        if (speedCost > 0) {
            binding.btnAccSpeedBuy.setText(String.format(Locale.US, "BUY ($%,d)", speedCost));
            binding.btnAccSpeedBuy.setEnabled(true);
            binding.btnAccSpeedBuy.setAlpha(1.0f);
            binding.btnAccSpeedBuy.setOnClickListener(v -> showUpgradePopup(vehicle, 0));
        } else {
            binding.btnAccSpeedBuy.setText("MAX TIER");
            binding.btnAccSpeedBuy.setEnabled(false);
            binding.btnAccSpeedBuy.setAlpha(0.5f);
            binding.btnAccSpeedBuy.setOnClickListener(null);
        }

        // Accessory 2: Cargo Part
        int capTier = SaveStore.getCapacityUpgradeLevel(getApplicationContext(), vehicle.getId());
        binding.tvAccCargoName.setText(vehicle.getCapacityPartName().toUpperCase(Locale.ROOT));
        binding.tvAccCargoLevel.setText("TIER " + capTier + "/3");
        String nextCapBenefit = vehicle.getCapacityPart() != null
                ? vehicle.getCapacityPart().getNextBenefit(capTier)
                : "+2 Cargo Slots";
        binding.tvAccCargoBenefit.setText(capTier >= 3 ? "MAX TIER REACHED" : ("NEXT: " + nextCapBenefit));

        int capCost = VehicleSpec.getCapacityUpgradeCost(vehicle.getId(), capTier);
        if (capCost > 0) {
            binding.btnAccCargoBuy.setText(String.format(Locale.US, "BUY ($%,d)", capCost));
            binding.btnAccCargoBuy.setEnabled(true);
            binding.btnAccCargoBuy.setAlpha(1.0f);
            binding.btnAccCargoBuy.setOnClickListener(v -> showUpgradePopup(vehicle, 1));
        } else {
            binding.btnAccCargoBuy.setText("MAX TIER");
            binding.btnAccCargoBuy.setEnabled(false);
            binding.btnAccCargoBuy.setAlpha(0.5f);
            binding.btnAccCargoBuy.setOnClickListener(null);
        }

        // Accessory 3: Utility Part
        int utilTier = SaveStore.getUtilityUpgradeLevel(getApplicationContext(), vehicle.getId());
        binding.tvAccUtilName.setText(vehicle.getUtilityPartName().toUpperCase(Locale.ROOT));
        binding.tvAccUtilLevel.setText("TIER " + utilTier + "/3");
        String nextUtilBenefit = vehicle.getUtilityPart() != null
                ? vehicle.getUtilityPart().getNextBenefit(utilTier)
                : "+15% Wet Grip";
        binding.tvAccUtilBenefit.setText(utilTier >= 3 ? "MAX TIER REACHED" : ("NEXT: " + nextUtilBenefit));

        int utilCost = VehicleSpec.getUtilityUpgradeCost(vehicle.getId(), utilTier);
        if (utilCost > 0) {
            binding.btnAccUtilBuy.setText(String.format(Locale.US, "BUY ($%,d)", utilCost));
            binding.btnAccUtilBuy.setEnabled(true);
            binding.btnAccUtilBuy.setAlpha(1.0f);
            binding.btnAccUtilBuy.setOnClickListener(v -> showUpgradePopup(vehicle, 2));
        } else {
            binding.btnAccUtilBuy.setText("MAX TIER");
            binding.btnAccUtilBuy.setEnabled(false);
            binding.btnAccUtilBuy.setAlpha(0.5f);
            binding.btnAccUtilBuy.setOnClickListener(null);
        }
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
        refreshAccessoriesSection(vehicle);
        Toast.makeText(this, "Equipped " + vehicle.getName(), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBuyVehicle(VehicleSpec vehicle) {
        int cash = SaveStore.getCash(getApplicationContext());
        if (cash < vehicle.getPrice()) {
            Toast.makeText(this, String.format(Locale.US, "Insufficient Cash: Need $%,d (Have $%,d)", vehicle.getPrice(), cash), Toast.LENGTH_SHORT).show();
            return;
        }

        boolean success = SaveStore.buyVehicle(getApplicationContext(), vehicle.getId(), vehicle.getPrice());
        if (success) {
            SaveStore.setEquippedVehicle(getApplicationContext(), vehicle.getId());
            adapter.setEquippedId(vehicle.getId());
            adapter.setSelectedVehicleId(vehicle.getId());
            updateWallet();
            adapter.notifyDataSetChanged();

            com.google.firebase.auth.FirebaseUser user =
                    com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users").document(user.getUid())
                        .update("equippedVehicle", vehicle.getId());
            }

            Toast.makeText(this, "Purchased " + vehicle.getName() + "!", Toast.LENGTH_SHORT).show();
            onVehicleSelected(vehicle);
        }
    }

    private void showUpgradePopup(VehicleSpec vehicle, int partType) {
        DialogUpgradePopUpBinding popupBinding = DialogUpgradePopUpBinding.inflate(
                LayoutInflater.from(this));

        int curTier = partType == 0 ? SaveStore.getSpeedUpgradeLevel(getApplicationContext(), vehicle.getId())
                    : partType == 1 ? SaveStore.getCapacityUpgradeLevel(getApplicationContext(), vehicle.getId())
                    : SaveStore.getUtilityUpgradeLevel(getApplicationContext(), vehicle.getId());

        int cost = partType == 0 ? VehicleSpec.getSpeedUpgradeCost(vehicle.getId(), curTier)
                 : partType == 1 ? VehicleSpec.getCapacityUpgradeCost(vehicle.getId(), curTier)
                 : VehicleSpec.getUtilityUpgradeCost(vehicle.getId(), curTier);

        String partName = partType == 0 ? vehicle.getSpeedPartName()
                        : partType == 1 ? vehicle.getCapacityPartName()
                        : vehicle.getUtilityPartName();

        String improvement = partType == 0 ? (vehicle.getSpeedPart() != null ? vehicle.getSpeedPart().getNextBenefit(curTier) : "+12% Speed Boost")
                           : partType == 1 ? (vehicle.getCapacityPart() != null ? vehicle.getCapacityPart().getNextBenefit(curTier) : "+2 Cargo Capacity")
                           : (vehicle.getUtilityPart() != null ? vehicle.getUtilityPart().getNextBenefit(curTier) : "Enhanced Handling");

        int currentCash = SaveStore.getCash(getApplicationContext());

        popupBinding.tvPopupTag.setText("UPGRADE ACCESSORY // " + vehicle.getName().toUpperCase(Locale.ROOT));
        popupBinding.tvPopupPartName.setText(partName.toUpperCase(Locale.ROOT));
        popupBinding.tvPopupTier.setText("TIER " + curTier + " / 3 TO TIER " + (curTier + 1) + " / 3");
        popupBinding.tvPopupBenefit.setText(improvement);
        popupBinding.tvPopupCostWallet.setText(String.format(Locale.US, "PRICE: $%,d  |  WALLET: $%,d", cost, currentCash));
        popupBinding.btnPopupBuy.setText(String.format(Locale.US, "BUY ($%,d)", cost));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(popupBinding.getRoot())
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.85f); // 85% darkened background!
        }

        popupBinding.btnPopupCancel.setOnClickListener(v -> dialog.dismiss());

        popupBinding.btnPopupBuy.setOnClickListener(v -> {
            int latestCash = SaveStore.getCash(getApplicationContext());
            if (latestCash < cost) {
                Toast.makeText(this, String.format(Locale.US, "Insufficient cash: Need $%,d (Have $%,d)", cost, latestCash), Toast.LENGTH_SHORT).show();
                return;
            }

            boolean upgraded = false;
            if (partType == 0) {
                upgraded = SaveStore.upgradeVehicleSpeed(getApplicationContext(), vehicle.getId(), cost);
            } else if (partType == 1) {
                upgraded = SaveStore.upgradeVehicleCapacity(getApplicationContext(), vehicle.getId(), cost);
            } else {
                upgraded = SaveStore.upgradeVehicleUtility(getApplicationContext(), vehicle.getId(), cost);
            }

            if (upgraded) {
                // Push update to Firestore
                com.google.firebase.auth.FirebaseUser user =
                        com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                if (user != null) {
                    String fieldName = partType == 0 ? "upgrade_speed_" + vehicle.getId()
                                     : partType == 1 ? "upgrade_cap_" + vehicle.getId()
                                     : "upgrade_util_" + vehicle.getId();
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("users").document(user.getUid())
                            .update(fieldName, curTier + 1, "cash", latestCash - cost);
                }

                updateWallet();
                refreshAccessoriesSection(vehicle);
                adapter.notifyDataSetChanged();
                dialog.dismiss();
                Toast.makeText(this, "Upgraded " + partName + " to Tier " + (curTier + 1) + "!", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }
}

package com.kazisayed.deliverit3d.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.kazisayed.deliverit3d.SaveStore;
import com.kazisayed.deliverit3d.databinding.ItemVehicleBinding;
import com.kazisayed.deliverit3d.model.VehicleSpec;
import java.util.List;
import java.util.Locale;

public class VehicleAdapter extends RecyclerView.Adapter<VehicleAdapter.VehicleViewHolder> {

    public interface OnVehicleActionListener {
        void onEquip(VehicleSpec vehicle);
        void onBuy(VehicleSpec vehicle);
        void onUpgrade(VehicleSpec vehicle);
    }

    private final List<VehicleSpec> vehicles;
    private String equippedId;
    private final OnVehicleActionListener listener;

    public VehicleAdapter(List<VehicleSpec> vehicles, String equippedId, OnVehicleActionListener listener) {
        this.vehicles = vehicles;
        this.equippedId = equippedId != null ? equippedId : "bike";
        this.listener = listener;
    }

    public void setEquippedId(String id) {
        this.equippedId = id;
    }

    @NonNull
    @Override
    public VehicleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemVehicleBinding binding = ItemVehicleBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new VehicleViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VehicleViewHolder holder, int position) {
        VehicleSpec v = vehicles.get(position);
        Context ctx = holder.itemView.getContext();

        boolean isOwned = SaveStore.isVehicleOwned(ctx, v.getId());
        boolean isEquipped = v.getId().equals(equippedId);

        int speedTier = SaveStore.getSpeedUpgradeLevel(ctx, v.getId());
        int capTier = SaveStore.getCapacityUpgradeLevel(ctx, v.getId());

        holder.binding.tvVehicleName.setText(v.getName());
        holder.binding.tvVehicleType.setText(v.getType().toUpperCase(Locale.ROOT));
        holder.binding.tvVehicleStats.setText(v.getStats());

        if (isEquipped) {
            holder.binding.tvVehiclePrice.setText("EQUIPPED");
            holder.binding.tvVehiclePrice.setTextColor(0xFF10B981);
            holder.binding.tvVehiclePrice.setBackgroundColor(0x2210B981);
        } else if (isOwned) {
            holder.binding.tvVehiclePrice.setText("OWNED");
            holder.binding.tvVehiclePrice.setTextColor(0xFF94A3B8);
            holder.binding.tvVehiclePrice.setBackgroundColor(0x2294A3B8);
        } else {
            holder.binding.tvVehiclePrice.setText("$" + v.getPrice());
            holder.binding.tvVehiclePrice.setTextColor(0xFF38BDF8);
            holder.binding.tvVehiclePrice.setBackgroundColor(0x2238BDF8);
        }

        holder.binding.tvSpeedBadge.setText("⚡ " + v.getSpeedPartName() + ": LV " + speedTier + "/3");
        holder.binding.tvCargoBadge.setText("📦 " + v.getCapacityPartName() + ": LV " + capTier + "/3 (" + VehicleSpec.getCapacityForTier(v.getId(), capTier) + " SLOTS)");

        if (isOwned) {
            holder.binding.btnUpgrade.setEnabled(true);
            holder.binding.btnUpgrade.setText("UPGRADE");
            holder.binding.btnUpgrade.setBackgroundTintList(ColorStateList.valueOf(0xFF243247));
            holder.binding.btnUpgrade.setTextColor(0xFFE2E8F0);
            holder.binding.btnUpgrade.setOnClickListener(view -> {
                if (listener != null) listener.onUpgrade(v);
            });
        } else {
            holder.binding.btnUpgrade.setEnabled(false);
            holder.binding.btnUpgrade.setText("LOCKED");
            holder.binding.btnUpgrade.setBackgroundTintList(ColorStateList.valueOf(0xFF161E28));
            holder.binding.btnUpgrade.setTextColor(0xFF64748B);
            holder.binding.btnUpgrade.setOnClickListener(null);
        }

        if (isEquipped) {
            holder.binding.btnEquip.setText("EQUIPPED");
            holder.binding.btnEquip.setEnabled(false);
            holder.binding.btnEquip.setBackgroundTintList(ColorStateList.valueOf(0xFF1B5E20));
            holder.binding.btnEquip.setTextColor(0xFFCBD5E1);
            holder.binding.btnEquip.setOnClickListener(null);
        } else if (isOwned) {
            holder.binding.btnEquip.setText("EQUIP");
            holder.binding.btnEquip.setEnabled(true);
            holder.binding.btnEquip.setBackgroundTintList(ColorStateList.valueOf(0xFF0F766E));
            holder.binding.btnEquip.setTextColor(0xFFFFFFFF);
            holder.binding.btnEquip.setOnClickListener(view -> {
                equippedId = v.getId();
                notifyDataSetChanged();
                if (listener != null) listener.onEquip(v);
            });
        } else {
            holder.binding.btnEquip.setText("BUY $" + v.getPrice());
            holder.binding.btnEquip.setEnabled(true);
            holder.binding.btnEquip.setBackgroundTintList(ColorStateList.valueOf(0xFFB45309));
            holder.binding.btnEquip.setTextColor(0xFFFFFFFF);
            holder.binding.btnEquip.setOnClickListener(view -> {
                if (listener != null) listener.onBuy(v);
            });
        }
    }

    @Override
    public int getItemCount() {
        return vehicles != null ? vehicles.size() : 0;
    }

    public static class VehicleViewHolder extends RecyclerView.ViewHolder {
        final ItemVehicleBinding binding;
        public VehicleViewHolder(ItemVehicleBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

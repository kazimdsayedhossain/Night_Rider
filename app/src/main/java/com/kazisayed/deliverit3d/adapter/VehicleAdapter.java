package com.kazisayed.deliverit3d.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.kazisayed.deliverit3d.databinding.ItemVehicleBinding;
import com.kazisayed.deliverit3d.model.VehicleSpec;
import java.util.List;

public class VehicleAdapter extends RecyclerView.Adapter<VehicleAdapter.VehicleViewHolder> {

    public interface OnEquipListener {
        void onEquip(VehicleSpec vehicle);
    }

    private final List<VehicleSpec> vehicles;
    private String equippedId;
    private final OnEquipListener listener;

    public VehicleAdapter(List<VehicleSpec> vehicles, String equippedId, OnEquipListener listener) {
        this.vehicles = vehicles;
        this.equippedId = equippedId != null ? equippedId : "bike";
        this.listener = listener;
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
        holder.binding.tvVehicleName.setText(v.getName());
        holder.binding.tvVehicleType.setText(v.getType());
        holder.binding.tvVehicleStats.setText(v.getStats());

        boolean isEquipped = v.getId().equals(equippedId);
        if (isEquipped) {
            holder.binding.btnEquip.setText("Equipped");
            holder.binding.btnEquip.setEnabled(false);
            holder.binding.btnEquip.setBackgroundColor(Color.parseColor("#1B5E20"));
        } else {
            holder.binding.btnEquip.setText("Equip");
            holder.binding.btnEquip.setEnabled(true);
            holder.binding.btnEquip.setBackgroundColor(Color.parseColor("#222228"));
        }

        holder.binding.btnEquip.setOnClickListener(view -> {
            equippedId = v.getId();
            notifyDataSetChanged();
            if (listener != null) listener.onEquip(v);
        });
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

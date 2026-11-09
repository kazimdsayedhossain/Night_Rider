package com.kazisayed.deliverit3d.adapter;

import android.content.Context;
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
        void onVehicleSelected(VehicleSpec vehicle);
        void onBuyVehicle(VehicleSpec vehicle);
        void onEquip(VehicleSpec vehicle);
    }

    private final List<VehicleSpec> vehicles;
    private String equippedId;
    private String selectedVehicleId;
    private final OnVehicleActionListener listener;

    public VehicleAdapter(List<VehicleSpec> vehicles, String equippedId, OnVehicleActionListener listener) {
        this.vehicles = vehicles;
        this.equippedId = equippedId != null ? equippedId : "bike";
        this.selectedVehicleId = this.equippedId;
        this.listener = listener;
    }

    public void setEquippedId(String id) {
        this.equippedId = id;
    }

    public void setSelectedVehicleId(String id) {
        this.selectedVehicleId = id;
    }

    public String getSelectedVehicleId() {
        return selectedVehicleId;
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

        boolean isOwned = "bike".equals(v.getId()) || SaveStore.isVehicleOwned(ctx, v.getId());
        boolean isEquipped = v.getId().equals(equippedId);
        boolean isSelected = v.getId().equals(selectedVehicleId);

        holder.binding.tvVehicleName.setText(v.getName().toUpperCase(Locale.ROOT));
        holder.binding.tvVehicleType.setText(v.getType().toUpperCase(Locale.ROOT));
        holder.binding.tvVehicleStats.setText(v.getStats().toUpperCase(Locale.ROOT));

        // Highlight selected vehicle card in list
        if (isSelected) {
            holder.binding.cardVehicle.setCardBackgroundColor(0xFF1E293B);
        } else {
            holder.binding.cardVehicle.setCardBackgroundColor(0xFF121720);
        }

        // Status badge: Purchased, Equipped, or Buy ($...)
        if (isEquipped) {
            holder.binding.tvVehicleStatus.setText("PURCHASED [EQUIPPED]");
            holder.binding.tvVehicleStatus.setTextColor(0xFF10B981);
            holder.binding.tvVehicleStatus.setBackgroundColor(0x2210B981);
        } else if (isOwned) {
            holder.binding.tvVehicleStatus.setText("PURCHASED");
            holder.binding.tvVehicleStatus.setTextColor(0xFF38BDF8);
            holder.binding.tvVehicleStatus.setBackgroundColor(0x2238BDF8);
        } else {
            holder.binding.tvVehicleStatus.setText("BUY ($" + String.format(Locale.US, "%,d", v.getPrice()) + ")");
            holder.binding.tvVehicleStatus.setTextColor(0xFFF59E0B);
            holder.binding.tvVehicleStatus.setBackgroundColor(0x22F59E0B);
        }

        // Card Click Handler
        holder.itemView.setOnClickListener(view -> {
            if (isOwned) {
                selectedVehicleId = v.getId();
                notifyDataSetChanged();
                if (listener != null) listener.onVehicleSelected(v);
            } else {
                if (listener != null) listener.onBuyVehicle(v);
            }
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

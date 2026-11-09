package com.kazisayed.deliverit3d.adapter;

import android.content.Context;
import android.graphics.Typeface;
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

        // Vehicle Name
        holder.binding.tvVehicleName.setText(v.getName().toUpperCase(Locale.ROOT));
        holder.binding.tvVehicleName.setTypeface(Typeface.create("sans-serif-condensed", isSelected || isEquipped ? Typeface.BOLD : Typeface.NORMAL));
        holder.binding.tvVehicleName.setTextColor(isSelected || isEquipped ? 0xFFFFFFFF : 0xFFCBD5E1);

        // Vehicle Type and Stats (clean text, no colored pill boxes)
        holder.binding.tvVehicleType.setText(v.getType().toUpperCase(Locale.ROOT));
        holder.binding.tvVehicleStats.setText(v.getStats().toUpperCase(Locale.ROOT));

        // Status text (clean text, no colored boxes, just bold text if necessary)
        if (isEquipped) {
            holder.binding.tvVehicleStatus.setText("PURCHASED [EQUIPPED]");
            holder.binding.tvVehicleStatus.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            holder.binding.tvVehicleStatus.setTextColor(0xFFFFFFFF);
        } else if (isOwned) {
            holder.binding.tvVehicleStatus.setText("PURCHASED");
            holder.binding.tvVehicleStatus.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
            holder.binding.tvVehicleStatus.setTextColor(0xFFCBD5E1);
        } else {
            holder.binding.tvVehicleStatus.setText("BUY ($" + String.format(Locale.US, "%,d", v.getPrice()) + ")");
            holder.binding.tvVehicleStatus.setTypeface(Typeface.create("sans-serif-condensed", Typeface.BOLD));
            holder.binding.tvVehicleStatus.setTextColor(0xFFFFFFFF);
        }

        // Row background: transparent, clean text list matching rest of game UI
        holder.binding.layoutVehicleRow.setBackgroundColor(isSelected ? 0x24FFFFFF : 0x00000000);

        // Click Handler
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

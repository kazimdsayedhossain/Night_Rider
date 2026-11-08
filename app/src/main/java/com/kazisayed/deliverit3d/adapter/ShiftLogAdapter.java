package com.kazisayed.deliverit3d.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.kazisayed.deliverit3d.databinding.ItemShiftLogBinding;
import com.kazisayed.deliverit3d.model.ShiftRecord;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ShiftLogAdapter extends RecyclerView.Adapter<ShiftLogAdapter.ShiftLogViewHolder> {

    private final List<ShiftRecord> shifts;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.US);

    public ShiftLogAdapter(List<ShiftRecord> shifts) {
        this.shifts = shifts;
    }

    @NonNull
    @Override
    public ShiftLogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemShiftLogBinding binding = ItemShiftLogBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ShiftLogViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ShiftLogViewHolder holder, int position) {
        ShiftRecord record = shifts.get(position);
        holder.binding.tvShiftRoute.setText(record.getRoute());
        if (record.getDate() != null) {
            holder.binding.tvShiftDate.setText(dateFormat.format(record.getDate()));
        } else {
            holder.binding.tvShiftDate.setText("Recently completed");
        }
        holder.binding.tvShiftCash.setText(String.format(Locale.US, "+%d", record.getCash()));

        int stars = record.getStars();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stars; i++) sb.append("★");
        for (int i = stars; i < 3; i++) sb.append("☆");
        holder.binding.tvShiftStars.setText(sb.toString());
    }

    @Override
    public int getItemCount() {
        return shifts != null ? shifts.size() : 0;
    }

    public static class ShiftLogViewHolder extends RecyclerView.ViewHolder {
        final ItemShiftLogBinding binding;
        public ShiftLogViewHolder(ItemShiftLogBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

package com.kazisayed.deliverit3d.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.kazisayed.deliverit3d.databinding.ItemLeaderboardBinding;
import com.kazisayed.deliverit3d.model.LeaderboardEntry;
import java.util.List;
import java.util.Locale;

public class LeaderboardAdapter extends RecyclerView.Adapter<LeaderboardAdapter.LeaderboardViewHolder> {

    private final List<LeaderboardEntry> entries;

    public LeaderboardAdapter(List<LeaderboardEntry> entries) {
        this.entries = entries;
    }

    @NonNull
    @Override
    public LeaderboardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLeaderboardBinding binding = ItemLeaderboardBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new LeaderboardViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull LeaderboardViewHolder holder, int position) {
        LeaderboardEntry entry = entries.get(position);
        holder.binding.tvRank.setText(String.format(Locale.US, "#%d", entry.getRank()));
        holder.binding.tvCourierName.setText(entry.getDisplayName());
        holder.binding.tvDistrict.setText(entry.getDistrict());
        holder.binding.tvScore.setText(String.format(Locale.US, "%d pts", entry.getScore()));
    }

    @Override
    public int getItemCount() {
        return entries != null ? entries.size() : 0;
    }

    public static class LeaderboardViewHolder extends RecyclerView.ViewHolder {
        final ItemLeaderboardBinding binding;
        public LeaderboardViewHolder(ItemLeaderboardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

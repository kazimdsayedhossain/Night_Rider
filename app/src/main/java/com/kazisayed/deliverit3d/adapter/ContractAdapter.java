package com.kazisayed.deliverit3d.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.kazisayed.deliverit3d.databinding.ItemContractBinding;
import com.kazisayed.deliverit3d.model.Contract;
import java.util.List;
import java.util.Locale;

public class ContractAdapter extends RecyclerView.Adapter<ContractAdapter.ContractViewHolder> {

    public interface OnAcceptListener {
        void onAccept(Contract contract);
    }

    private final List<Contract> contracts;
    private final OnAcceptListener listener;

    public ContractAdapter(List<Contract> contracts, OnAcceptListener listener) {
        this.contracts = contracts;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ContractViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemContractBinding binding = ItemContractBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ContractViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ContractViewHolder holder, int position) {
        Contract c = contracts.get(position);
        holder.binding.tvRoute.setText(c.getRoute());
        holder.binding.tvPayout.setText(String.format(Locale.US, "+%d coins", c.getPayout()));
        holder.binding.tvMeta.setText(String.format(Locale.US, "%s | %ds limit | Tier %d",
                c.getCargo(), c.getTimeLimitSeconds(), c.getDifficulty()));

        holder.binding.btnAccept.setOnClickListener(v -> {
            if (listener != null) listener.onAccept(c);
        });
    }

    @Override
    public int getItemCount() {
        return contracts != null ? contracts.size() : 0;
    }

    public static class ContractViewHolder extends RecyclerView.ViewHolder {
        final ItemContractBinding binding;
        public ContractViewHolder(ItemContractBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}

package com.kazisayed.deliverit3d;

import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.kazisayed.deliverit3d.adapter.ContractAdapter;
import com.kazisayed.deliverit3d.databinding.ActivityContractBoardBinding;
import com.kazisayed.deliverit3d.model.Contract;
import java.util.ArrayList;
import java.util.List;

/**
 * ContractBoardActivity: RecyclerView list of delivery contracts.
 *
 * Online: fetches contracts from Firestore "contracts" collection.
 * Offline: serves cached list from LevelDB (last known contracts).
 *
 * Tapping "Accept" on a contract starts GameActivity with the contract
 * data passed as a ContractParcel via explicit Intent.
 */
public class ContractBoardActivity extends AppCompatActivity implements ContractAdapter.OnAcceptListener {

    private ActivityContractBoardBinding binding;
    private ContractAdapter adapter;
    private final List<Contract> contracts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityContractBoardBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        adapter = new ContractAdapter(contracts, this);
        binding.rvContracts.setLayoutManager(new LinearLayoutManager(this));
        binding.rvContracts.setAdapter(adapter);

        if (isOnline()) {
            loadFromFirestore();
        } else {
            binding.tvOfflineNote.setVisibility(View.VISIBLE);
            loadFromCache();
        }
    }

    private void loadFromFirestore() {
        binding.progressContracts.setVisibility(View.VISIBLE);
        FirebaseFirestore.getInstance().collection("contracts")
                .get()
                .addOnSuccessListener(this, snapshot -> {
                    binding.progressContracts.setVisibility(View.GONE);
                    contracts.clear();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        Contract c = doc.toObject(Contract.class);
                        c.setId(doc.getId());
                        contracts.add(c);
                    }
                    if (contracts.isEmpty()) {
                        contracts.addAll(Contract.defaults()); // fallback seed contracts
                    }
                    adapter.notifyDataSetChanged();
                    binding.tvEmpty.setVisibility(contracts.isEmpty() ? View.VISIBLE : View.GONE);
                    // Cache the contracts locally for offline use
                    SaveStore.cacheContracts(getApplicationContext(), contracts);
                })
                .addOnFailureListener(e -> {
                    binding.progressContracts.setVisibility(View.GONE);
                    binding.tvOfflineNote.setVisibility(View.VISIBLE);
                    loadFromCache();
                });
    }

    private void loadFromCache() {
        List<Contract> cached = SaveStore.getCachedContracts(getApplicationContext());
        contracts.clear();
        if (cached.isEmpty()) {
            contracts.addAll(Contract.defaults());
        } else {
            contracts.addAll(cached);
        }
        adapter.notifyDataSetChanged();
        binding.tvEmpty.setVisibility(contracts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onAccept(Contract contract) {
        // Pass contract to GameActivity via explicit Intent
        Intent intent = new Intent(this, GameActivity.class);
        intent.putExtra(GameActivity.EXTRA_CONTRACT_ROUTE, contract.getRoute());
        intent.putExtra(GameActivity.EXTRA_CONTRACT_PAYOUT, contract.getPayout());
        intent.putExtra(GameActivity.EXTRA_CONTRACT_TIME, contract.getTimeLimitSeconds());
        intent.putExtra(GameActivity.EXTRA_CONTRACT_CARGO, contract.getCargo());
        startActivity(intent);
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }
}

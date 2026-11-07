package com.foodrush.app.ui.restaurant;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.recyclerview.widget.LinearLayoutManager;

import com.foodrush.app.R;
import com.foodrush.app.adapter.RestaurantAdapter;
import com.foodrush.app.data.AuthRepository;
import com.foodrush.app.data.Cloud;
import com.foodrush.app.data.FirebaseSync;
import com.foodrush.app.data.MenuRepository;
import com.foodrush.app.data.Session;
import com.foodrush.app.databinding.ActivityClaimBinding;
import com.foodrush.app.model.Restaurant;
import com.foodrush.app.ui.BaseActivity;
import com.foodrush.app.ui.LoginActivity;
import com.foodrush.app.util.States;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * First stop for a new restaurant account: take over one of the listed restaurants nobody runs yet,
 * or add a brand new one.
 */
public class ClaimRestaurantActivity extends BaseActivity {

    private static final String TAG = "ClaimRestaurant";

    private ActivityClaimBinding binding;
    private Session session;
    private RestaurantAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityClaimBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        session = new Session(this);

        adapter = new RestaurantAdapter(this::confirmClaim, false);
        binding.claimList.setLayoutManager(new LinearLayoutManager(this));
        binding.claimList.setAdapter(adapter);
        binding.claimNew.setOnClickListener(v -> startActivity(new Intent(this, RestaurantProfileActivity.class)
                .putExtra(RestaurantProfileActivity.EXTRA_CREATE, true)));
        binding.claimLogout.setOnClickListener(v -> {
            new AuthRepository(this).logout();
            startActivity(new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (session.restaurantId() != null) {
            // Created one on the profile screen.
            startActivity(new Intent(this, KitchenActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            return;
        }
        load();
    }

    private void load() {
        States.loading(binding.claimState);
        MenuRepository.get(this).unclaimedRestaurants(new MenuRepository.Listener<List<Restaurant>>() {
            @Override
            public void onResult(List<Restaurant> result) {
                adapter.submit(result, new HashMap<>());
                if (result.isEmpty()) {
                    States.empty(binding.claimState, R.string.claim_none);
                } else {
                    States.hide(binding.claimState);
                }
            }

            @Override
            public void onError() {
                States.error(binding.claimState, ClaimRestaurantActivity.this::load);
            }
        });
    }

    private void confirmClaim(Restaurant restaurant) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.claim_confirm_title, restaurant.name))
                .setMessage(getString(R.string.claim_confirm_text, restaurant.areaLabel()))
                .setPositiveButton(R.string.claim_confirm, (d, w) -> claim(restaurant))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** A transaction, so two accounts can't take the same restaurant at once. */
    private void claim(Restaurant restaurant) {
        String uid = session.uid();
        DocumentReference ref = Cloud.db().collection(Cloud.RESTAURANTS).document(restaurant.id);
        Cloud.db().runTransaction(tx -> {
            DocumentSnapshot doc = tx.get(ref);
            String owner = doc.getString("ownerUid");
            if (!doc.exists() || owner != null && !owner.isEmpty()) {
                return false;
            }
            Map<String, Object> update = new HashMap<>();
            update.put("ownerUid", uid);
            tx.update(ref, update);
            return true;
        }).addOnSuccessListener(ok -> {
            if (!Boolean.TRUE.equals(ok)) {
                showMessage(getString(R.string.claim_taken));
                load();
                return;
            }
            restaurant.ownerUid = uid;
            session.setRestaurantId(restaurant.id);
            FirebaseSync.saveUser(this, uid, session.name(), session.email(), session.phone(), session.role(),
                    restaurant.id);
            startActivity(new Intent(this, KitchenActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        }).addOnFailureListener(e -> {
            Log.w(TAG, "claim failed", e);
            showMessage(getString(R.string.save_failed));
        });
    }
}

package com.example.map4;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

public class AdminActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private DatabaseReference databaseReference;
    private Map<String, Marker> userMarkers = new HashMap<>(); // Store user markers
    private Button btnFindRoute;
    private double selectedUserLat = 0.0, selectedUserLng = 0.0; // Stores selected user's location


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second); // Ensure this layout contains btnRoute

        btnFindRoute = findViewById(R.id.btnFindRoute);
        btnFindRoute.setVisibility(View.GONE); // Hide button initially



        // Initialize Firebase database reference
        databaseReference = FirebaseDatabase.getInstance().getReference("user_alerts");

        // Load the Google Map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Fetch FCM token for push notifications
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w("FCM", "Fetching FCM token failed", task.getException());
                        return;
                    }
                    String token = task.getResult();
                    Log.d("FCM", "Admin FCM Token: " + token);
                });

        // Set Route Button Click Listener
        btnFindRoute.setOnClickListener(view -> {
            if (selectedUserLat == 0.0 || selectedUserLng == 0.0) {
                Toast.makeText(this, "No user selected!", Toast.LENGTH_SHORT).show();
            } else {
                Intent intent = new Intent(AdminActivity.this, SecondActivity.class);
                intent.putExtra("userLat", selectedUserLat);
                intent.putExtra("userLng", selectedUserLng);
                startActivity(intent);
            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        listenForUserAlerts();

        // Set marker click listener to update selected user
        mMap.setOnMarkerClickListener(marker -> {
            for (Map.Entry<String, Marker> entry : userMarkers.entrySet()) {
                if (entry.getValue().equals(marker)) {
                    LatLng position = marker.getPosition();
                    selectedUserLat = position.latitude;
                    selectedUserLng = position.longitude;

                    Toast.makeText(this, "User Selected: " + entry.getKey(), Toast.LENGTH_SHORT).show();
                    btnFindRoute.setVisibility(View.VISIBLE); // Show Route button
                    return true;
                }
            }
            return false;
        });
    }

    private void listenForUserAlerts() {
        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (mMap == null) return;

                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String userId = userSnapshot.getKey();
                    UserLocation location = userSnapshot.getValue(UserLocation.class);

                    if (location != null) {
                        LatLng userPosition = new LatLng(location.getLatitude(), location.getLongitude());

                        if (userMarkers.containsKey(userId)) {
                            userMarkers.get(userId).setPosition(userPosition);
                        } else {
                            Marker marker = mMap.addMarker(new MarkerOptions()
                                    .position(userPosition)
                                    .title("User Alert: " + userId));
                            userMarkers.put(userId, marker);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "Failed to load user locations", error.toException());
            }
        });
    }


}
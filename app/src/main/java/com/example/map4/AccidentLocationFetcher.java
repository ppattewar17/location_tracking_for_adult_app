package com.example.map4;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.HashMap;
import java.util.Map;

public class AccidentLocationFetcher {
    private final DatabaseReference accidentRef;
    private final GoogleMap mMap;
    private final Map<String, Marker> accidentMarkers = new HashMap<>();

    public AccidentLocationFetcher(GoogleMap googleMap) {
        this.mMap = googleMap;
        this.accidentRef = FirebaseDatabase.getInstance().getReference("accident_location");
        fetchAccidentLocations();
    }

    private void fetchAccidentLocations() {
        accidentRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d("Firebase", "Accident data received: " + snapshot.getValue());

                if (mMap == null) {
                    Log.e("Firebase", "Map is not initialized.");
                    return;
                }

                for (DataSnapshot data : snapshot.getChildren()) {
                    String accidentId = data.getKey();
                    UserLocation location = data.getValue(UserLocation.class);

                    if (location != null) {
                        LatLng position = new LatLng(location.getLatitude(), location.getLongitude());

                        if (accidentMarkers.containsKey(accidentId)) {
                            // Update existing marker position
                            accidentMarkers.get(accidentId).setPosition(position);
                        } else {
                            // Create a new marker
                            Marker marker = mMap.addMarker(new MarkerOptions()
                                    .position(position)
                                    .title("Accident Location: " + accidentId));
                            accidentMarkers.put(accidentId, marker);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "Failed to fetch accident locations", error.toException());
            }
        });
    }
}

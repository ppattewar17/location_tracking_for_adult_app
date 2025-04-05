package com.example.map4;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DataSnapshot;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int LOCATION_PERMISSION_CODE = 1;
    private GoogleMap mMap;
    private SearchView mapSearchView;
    private Button locationButton;
    private Location currentLocation;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private Marker clickedMarker;
    private LocationCallback locationCallback;
    private DatabaseReference databaseReference;
    private Marker CurrentMarker;
    private Map<String, Marker> ambulanceMarkers = new HashMap<>();

    private Map<String, Marker> userMarkers = new HashMap<>();


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        mapSearchView = findViewById(R.id.search_view);
        locationButton = findViewById(R.id.btn_get_location);
        Button move = findViewById(R.id.btn_track);

        try {
            FirebaseApp.initializeApp(this);
            databaseReference = FirebaseDatabase.getInstance().getReference("ambulence_current_location");

            if (databaseReference == null) {
                Log.e("Firebase", "Database reference is null after initialization!");
                Toast.makeText(this, "Firebase initialization failed!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("Firebase", "Exception during Firebase initialization", e);
            Toast.makeText(this, "Firebase initialization failed!", Toast.LENGTH_SHORT).show();
        }

        move.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SecondActivity.class);
            startActivity(intent);
        });

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        // Check if GPS is enabled
        if (!isGPSEnabled()) {
            showGPSAlert();
        } else {
            requestLocationUpdates();
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        mapSearchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchLocation(query);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });


        locationButton.setOnClickListener(v -> getLastLocation());

    }

    private boolean isGPSEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    private void showGPSAlert() {
        new AlertDialog.Builder(this)
                .setTitle("Enable GPS")
                .setMessage("GPS is required for this feature. Enable it in settings.")
                .setPositiveButton("Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private Marker currentMarker; // Store marker reference

    private void requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_CODE);
            return;
        }

        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(5000); // Update every 5 seconds
        locationRequest.setFastestInterval(2000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) return;

                for (Location location : locationResult.getLocations()) {
                    currentLocation = location;
                    updateMapWithLocation();
                    updateLocationToFirebase(location);
                }
            }
        };

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    private void updateLocationToFirebase(Location location) {
        String ambulanceId = getAmbulanceId();
        if (databaseReference != null) {
            DatabaseReference ambulanceRef = databaseReference.child(ambulanceId);

            // Read the current isFree value from Firebase
            ambulanceRef.child("isFree").get().addOnCompleteListener(task -> {
                boolean isFree;
                if (task.isSuccessful() && task.getResult().exists()) {
                    isFree = task.getResult().getValue(Boolean.class); // Keep existing value
                } else {
                    isFree = checkIfAmbulanceIsFree(); // First time setting
                }

                // Update location without modifying isFree if it already exists
                Map<String, Object> updateMap = new HashMap<>();
                updateMap.put("latitude", location.getLatitude());
                updateMap.put("longitude", location.getLongitude());

                if (!task.getResult().exists()) { // Only set isFree if it's the first time
                    updateMap.put("isFree", isFree);
                }

                ambulanceRef.updateChildren(updateMap)
                        .addOnSuccessListener(aVoid -> Log.d("Firebase", "Location updated"))
                        .addOnFailureListener(e -> Log.e("Firebase", "Failed to update location", e));
            });
        }
    }

    private boolean checkIfAmbulanceIsFree() {
        // Add conditions to determine if the ambulance is free or occupied
        return true;  // Example: Always return true for now
    }

    private void listenForAmbulanceLocations() {
        if (databaseReference == null) {
            Log.e("Firebase", "Database reference is null. Exiting listener.");
            return;
        }

        databaseReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (mMap == null) {
                    Log.e("Firebase", "Map is not initialized yet.");
                    return;
                }

                for (DataSnapshot data : snapshot.getChildren()) {
                    String ambulanceId = data.getKey();
                    UserLocation location = data.getValue(UserLocation.class);

                    if (location != null) {
                        LatLng position = new LatLng(location.getLatitude(), location.getLongitude());

                        if (ambulanceMarkers.containsKey(ambulanceId)) {
                            ambulanceMarkers.get(ambulanceId).setPosition(position);
                        } else {
                            Marker marker = mMap.addMarker(new MarkerOptions().position(position)
                                    .title("Ambulance: " + ambulanceId));
                            ambulanceMarkers.put(ambulanceId, marker);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "Failed to get ambulance locations", error.toException());
            }
        });
    }


    private String getAmbulanceId() {
        return "ambulance_" + Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    private void getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_CODE);
            return;
        }

        Task<Location> task = fusedLocationProviderClient.getLastLocation();
        task.addOnSuccessListener(location -> {
            if (location != null) {
                currentLocation = location;
                updateMapWithLocation();
            } else {
                Toast.makeText(MainActivity.this, "Unable to get location", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Log.e("LocationError", "Failed to get last location", e);
        });
    }


    private boolean isSearching = false; // Add this flag
    private boolean isCameraManuallyMoved = false;

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        listenForAmbulanceLocations();
        listenForUserAlerts();
        new AccidentLocationFetcher(mMap);

        mMap.setOnCameraMoveStartedListener(reason -> {
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                isCameraManuallyMoved = true;
            }
        });

        mMap.setOnMapClickListener(latLng -> {
            if (mMap == null) return;

            runOnUiThread(() -> {
                if (clickedMarker != null) {
                    clickedMarker.remove();
                }
                clickedMarker = mMap.addMarker(new MarkerOptions()
                        .position(latLng)
                        .title("Selected Location"));

                Toast.makeText(MainActivity.this,
                        "Clicked Location: " + latLng.latitude + ", " + latLng.longitude,
                        Toast.LENGTH_SHORT).show();
            });
        });
        updateMapWithLocation();
    }

    private void updateMapWithLocation() {
        if (mMap != null && currentLocation != null && !isSearching) {
            LatLng userLocation = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());

            // Preserve the zoom level
            float currentZoom = mMap.getCameraPosition().zoom;
            if (currentZoom < 10) currentZoom = 15;

            if (currentMarker == null) {
                // Create marker only if it doesn’t exist
                currentMarker = mMap.addMarker(new MarkerOptions()
                        .position(userLocation)
                        .title("My Location"));
            } else {
                // Move marker to new position
                currentMarker.setPosition(userLocation);
            }

            // Move camera ONLY if user has NOT manually moved it
            if (!isCameraManuallyMoved) {
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, currentZoom));
            }
        }
    }


    private void searchLocation(String locationName) {
        isSearching = true;
        try {
            List<android.location.Address> addresses = new android.location.Geocoder(this, Locale.getDefault())
                    .getFromLocationName(locationName, 1);
            if (addresses != null && !addresses.isEmpty()) {
                android.location.Address address = addresses.get(0);
                LatLng searchedLocation = new LatLng(address.getLatitude(), address.getLongitude());

                // Update currentLocation to prevent resetting to the previous location
                currentLocation = new Location("");
                currentLocation.setLatitude(address.getLatitude());
                currentLocation.setLongitude(address.getLongitude());

                if (mMap != null) {
                    mMap.clear();
                    mMap.addMarker(new MarkerOptions().position(searchedLocation).title(locationName));
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(searchedLocation, 12));
                }
            } else {
                Toast.makeText(this, "Location not found!", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Error finding location!", Toast.LENGTH_SHORT).show();
        }
        locationButton.setOnClickListener(v -> {
            isSearching = false;
            getLastLocation();
        });

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastLocation();
            } else {
                Toast.makeText(this, "Location permission denied. Enable it in settings.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void listenForUserAlerts() {
        DatabaseReference alertRef = FirebaseDatabase.getInstance().getReference("user_alerts");

        alertRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Log.d("Firebase", "Data received from Firebase: " + snapshot.getValue());

                if (mMap == null) {
                    Log.e("Firebase", "Map is not initialized yet.");
                    return;
                }

                for (DataSnapshot data : snapshot.getChildren()) {
                    String userId = data.getKey();
                    UserLocation location = data.getValue(UserLocation.class);

                    if (location != null) {
                        LatLng position = new LatLng(location.getLatitude(), location.getLongitude());
                        Log.d("Firebase", "User " + userId + " location: " + position.toString());

                        if (userMarkers.containsKey(userId)) {
                            userMarkers.get(userId).setPosition(position);
                        } else {
                            Marker marker = mMap.addMarker(new MarkerOptions()
                                    .position(position)
                                    .title("User Alert: " + userId)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
                            userMarkers.put(userId, marker);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("Firebase", "Failed to get user alerts", error.toException());
            }
        });
    }
}

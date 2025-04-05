package com.example.map4;

import static android.content.Intent.ACTION_VIEW;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

public class SecondActivity extends AppCompatActivity {

    private Button getDirection;
    private FusedLocationProviderClient fusedLocationClient;
    private double userLat, userLng; // User's accident location

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

        getDirection = findViewById(R.id.btnFindRoute);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Get User's Accident Location from Intent
        userLat = getIntent().getDoubleExtra("userLat", 0.0);
        userLng = getIntent().getDoubleExtra("userLng", 0.0);

        getDirection.setOnClickListener(view -> {
            if (userLat == 0.0 || userLng == 0.0) {
                Toast.makeText(this, "Invalid user location!", Toast.LENGTH_SHORT).show();
            } else {
                getAdminLocation();
            }
        });
    }

    private void getAdminLocation() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        double adminLat = location.getLatitude();
                        double adminLng = location.getLongitude();

                        // Open Google Maps with navigation from admin to user
                        openGoogleMaps(adminLat, adminLng, userLat, userLng);
                    } else {
                        Toast.makeText(this, "Failed to get admin location!", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void openGoogleMaps(double fromLat, double fromLng, double toLat, double toLng) {
        try {
            Uri uri = Uri.parse("google.navigation:q=" + toLat + "," + toLng + "&mode=d");
            Intent intent = new Intent(ACTION_VIEW, uri);
            intent.setPackage("com.google.android.apps.maps");
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Uri uri = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.maps");
            Intent intent = new Intent(ACTION_VIEW, uri);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }
}

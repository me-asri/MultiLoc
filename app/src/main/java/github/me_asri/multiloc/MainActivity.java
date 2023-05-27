package github.me_asri.multiloc;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.core.location.LocationManagerCompat;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.MinimapOverlay;

import java.util.function.Consumer;

import github.me_asri.multiloc.databinding.ActivityMainBinding;
import github.me_asri.multiloc.location.BTSLocation;
import github.me_asri.multiloc.location.IPLocation;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getName();
    private static final String SHARED_PREF_OSMDROID = TAG + ".osmdroid_pref";
    private static final OnlineTileSourceBase MAP_TILE_SOURCE = TileSourceFactory.MAPNIK;

    private ActivityMainBinding mBinding;

    // TODO: replace ProgressDialog with ProgressBar
    private ProgressDialog mProgressDialog;

    private String mSelectedProvider;

    private LocationManager mLocationManager;

    private IMapController mMapController;
    private Marker mMapMarker;

    private final IPLocation mIPLocation = new IPLocation();
    private final BTSLocation mBTSLocation = new BTSLocation();

    private final ActivityResultLauncher<String[]> multiPermRequest = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        View root = mBinding.getRoot();
        setContentView(root);

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setTitle(R.string.app_name);
        mProgressDialog.setMessage(getText(R.string.progress_dialog_message));

        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // osmdroid configuration
        Configuration.getInstance().load(this, getSharedPreferences(SHARED_PREF_OSMDROID, 0));
        // osmdroid map source
        mBinding.map.setTileSource(MAP_TILE_SOURCE);

        // Allow horizontal map repetition
        mBinding.map.setHorizontalMapRepetitionEnabled(true);
        // Prevent vertical map repetition
        mBinding.map.setVerticalMapRepetitionEnabled(false);
        // Limit vertical scrolling
        mBinding.map.setScrollableAreaLimitLatitude(MapView.getTileSystem().getMaxLatitude(), MapView.getTileSystem().getMinLatitude(), 0);

        mMapController = mBinding.map.getController();
        mMapController.setZoom(2.5);

        mMapMarker = new Marker(mBinding.map);

        DisplayMetrics dm = getResources().getDisplayMetrics();

        MinimapOverlay minimap = new MinimapOverlay(this, mBinding.map.getTileRequestCompleteHandler());
        minimap.setTileSource(MAP_TILE_SOURCE);
        minimap.setWidth(dm.widthPixels / 5);
        minimap.setHeight(dm.heightPixels / 5);
        mBinding.map.getOverlays().add(minimap);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_main, menu);

        MenuItem locateItem = menu.findItem(R.id.locateItem);
        locateItem.setOnMenuItemClickListener(this::onLocateItemClick);

        AppCompatSpinner mProviderSpinner = (AppCompatSpinner) menu.findItem(R.id.locationProviderItem).getActionView();
        mProviderSpinner.setOnItemSelectedListener(new OnProviderItemSelectedListener());

        return true;
    }

    private boolean onLocateItemClick(MenuItem item) {
        switch (mSelectedProvider) {
            case "IP":
                useIPLocation();
                break;

            case "BTS":
                useBTSLocation();
                break;

            case "GPS":
                useGPSLocation();
                break;

            case "WiFi":
                useWiFiLocation();
                break;

            default:
                throw new RuntimeException("Unhandled selection");
        }

        return true;
    }

    private void displayPointOnMap(double lat, double lon) {
        GeoPoint geoPoint = new GeoPoint(lat, lon);
        // Zoom in
        mMapController.setZoom(18.5);

        // Center map to the specified location
        mMapController.setCenter(geoPoint);

        // Remove previous marker
        mMapMarker.closeInfoWindow();
        mMapMarker.remove(mBinding.map);

        // Set marker position
        mMapMarker.setPosition(geoPoint);
        mMapMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        // Set info window text
        mMapMarker.setTitle("Lat: " + lat + " - Lon: " + lon);

        // Add marker
        mBinding.map.getOverlays().add(mMapMarker);
    }

    private void displayPoint(double lat, double lon) {
        mBinding.locText.setVisibility(View.VISIBLE);
        mBinding.locText.setText(getString(R.string.text_location, lat, lon));

        displayPointOnMap(lat, lon);
    }

    private void displayPoint(double lat, double lon, double alt, double speed, boolean mock) {
        mBinding.locText.setVisibility(View.VISIBLE);
        mBinding.locText.setText(getString(R.string.text_location_extra, lat, lon, alt, speed,
                (mock) ? "true" : "false"));

        displayPointOnMap(lat, lon);
    }

    private void useIPLocation() {
        mProgressDialog.show();

        mIPLocation.getLocation(null, (r, t) -> {
            mProgressDialog.dismiss();

            if (t != null) {
                Log.e(TAG, "onIPButtonClick: ", t);
                Toast.makeText(MainActivity.this, "Exception occurred: " + t.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
            if (r == null) {
                Toast.makeText(MainActivity.this, "Received empty response", Toast.LENGTH_LONG).show();
                return;
            }

            displayPoint(r.lat, r.lon);
        });
    }

    private void useBTSLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Fine location permission required", Toast.LENGTH_SHORT).show();
            requestLocationPermission();
            return;
        }

        if (!LocationManagerCompat.isLocationEnabled(mLocationManager)) {
            Toast.makeText(this, "Location not enabled", Toast.LENGTH_LONG).show();
            return;
        }

        mProgressDialog.show();

        mBTSLocation.getLocation(this, null, (r, t) -> {
            mProgressDialog.dismiss();

            if (t != null) {
                Log.e(TAG, "onBTSLocation: ", t);

                Toast.makeText(MainActivity.this, "Exception occurred: " + t, Toast.LENGTH_LONG).show();
                return;
            }
            if (r == null) {
                Toast.makeText(MainActivity.this, "Received empty response", Toast.LENGTH_LONG).show();
                return;
            }

            displayPoint(r.lat, r.lon);
        });
    }

    private void useAndroidLocation(String locationProvider) {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show();
            requestLocationPermission();

            return;
        }

        if (!LocationManagerCompat.isLocationEnabled(mLocationManager)) {
            Toast.makeText(this, "Location not enabled", Toast.LENGTH_LONG).show();
            return;
        }

        mProgressDialog.show();

        Consumer<Location> locationCallback = l -> {
            mProgressDialog.dismiss();

            if (l == null) {
                Toast.makeText(this, "Failed to determine location", Toast.LENGTH_LONG).show();
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                displayPoint(l.getLatitude(), l.getLongitude(), l.getAltitude(), l.getSpeed(), l.isMock());
            } else {
                displayPoint(l.getLatitude(), l.getLongitude(), l.getAltitude(), l.getSpeed(), l.isFromMockProvider());
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mLocationManager.getCurrentLocation(locationProvider, null, getMainExecutor(), locationCallback);
        } else {
            mLocationManager.requestSingleUpdate(locationProvider, new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location location) {
                    locationCallback.accept(location);
                }

                @Override
                public void onProviderEnabled(@NonNull String provider) {
                }

                @Override
                public void onProviderDisabled(@NonNull String provider) {
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                }
            }, null);
        }
    }

    private void useGPSLocation() {
        useAndroidLocation(LocationManager.GPS_PROVIDER);
    }

    private void useWiFiLocation() {
        useAndroidLocation(LocationManager.NETWORK_PROVIDER);
    }

    private void requestLocationPermission() {
        multiPermRequest.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private class OnProviderItemSelectedListener implements AdapterView.OnItemSelectedListener {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            mSelectedProvider = (String) parent.getSelectedItem();

            if ((mSelectedProvider.equals("GPS") || mSelectedProvider.equals("WiFi")) && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestLocationPermission();
            } else if (mSelectedProvider.equals("BTS") && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestLocationPermission();
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }
}
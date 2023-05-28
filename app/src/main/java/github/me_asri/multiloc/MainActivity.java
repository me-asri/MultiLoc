package github.me_asri.multiloc;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
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

import com.google.gson.JsonSyntaxException;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.MinimapOverlay;

import java.net.SocketTimeoutException;

import github.me_asri.multiloc.databinding.ActivityMainBinding;
import github.me_asri.multiloc.location.BTSLocation;
import github.me_asri.multiloc.location.IPLocation;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getName();
    private static final String SHARED_PREF_OSMDROID = TAG + ".osmdroid_pref";
    private static final OnlineTileSourceBase MAP_TILE_SOURCE = TileSourceFactory.MAPNIK;
    private static final long LOCATION_TIMEOUT_MILLIS = 7000;

    private ActivityMainBinding mBinding;

    // TODO: replace ProgressDialog with ProgressBar
    private ProgressDialog mProgressDialog;

    private String mSelectedProvider;

    private LocationManager mLocationManager;

    private IMapController mMapController;
    private Marker mMapMarker;

    private final IPLocation mIPLocation = new IPLocation(LOCATION_TIMEOUT_MILLIS);
    private final BTSLocation mBTSLocation = new BTSLocation(LOCATION_TIMEOUT_MILLIS);

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
        mMapController.setZoom(17.5);

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

    private void displayPoint(double lat, double lon, double alt, double speed, boolean mock) {
        mBinding.locText.setVisibility(View.VISIBLE);
        mBinding.locText.setText(getString(R.string.text_location_android, lat, lon, alt, speed,
                getString((mock) ? R.string.text_yes : R.string.text_no)));

        displayPointOnMap(lat, lon);
    }

    private void displayPoint(double lat, double lon, String isp, String as) {
        mBinding.locText.setVisibility(View.VISIBLE);
        mBinding.locText.setText(getString(R.string.text_location_ip, lat, lon, isp, as));

        displayPointOnMap(lat, lon);
    }

    private void displayPoint(double lat, double lon, String mcc, String mnc, int tac, long ci) {
        mBinding.locText.setVisibility(View.VISIBLE);
        mBinding.locText.setText(getString(R.string.text_location_bts, lat, lon, mcc, mnc, tac, ci));

        displayPointOnMap(lat, lon);
    }

    private void useIPLocation() {
        mProgressDialog.show();

        mIPLocation.getLocation(null, (r, t) -> {
            mProgressDialog.dismiss();

            if (t != null) {
                Log.e(TAG, "onIPButtonClick: ", t);

                if (t instanceof JsonSyntaxException) {
                    Toast.makeText(MainActivity.this, "Received invalid response", Toast.LENGTH_LONG).show();
                } else if (t instanceof SocketTimeoutException) {
                    Toast.makeText(MainActivity.this, "Connection timed-out", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
                }
                return;
            }
            if (r == null) {
                Toast.makeText(MainActivity.this, "Received empty response", Toast.LENGTH_LONG).show();
                return;
            }

            displayPoint(r.lat, r.lon, r.isp, r.as);
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

                if (t instanceof JsonSyntaxException) {
                    Toast.makeText(MainActivity.this, "Received invalid response", Toast.LENGTH_LONG).show();
                } else if (t instanceof SocketTimeoutException) {
                    Toast.makeText(MainActivity.this, "Connection timed-out", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MainActivity.this, t.getMessage(), Toast.LENGTH_LONG).show();
                }
                return;
            }
            if (r == null) {
                Toast.makeText(MainActivity.this, "Received empty response", Toast.LENGTH_LONG).show();
                return;
            }

            displayPoint(r.lat, r.lon, r.mcc, r.mnc, r.tac, r.ci);
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

        Handler handler = new Handler(getMainLooper());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            CancellationSignal locationCancelSignal = new CancellationSignal();
            Runnable timeoutRunnable = () -> {
                locationCancelSignal.cancel();

                Toast.makeText(this, "Operation timed-out", Toast.LENGTH_LONG).show();
                mProgressDialog.dismiss();
            };

            mLocationManager.getCurrentLocation(locationProvider, locationCancelSignal, getMainExecutor(), l -> {
                handler.removeCallbacks(timeoutRunnable);

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
            });
            handler.postDelayed(timeoutRunnable, LOCATION_TIMEOUT_MILLIS);
        } else {
            CancellationSignal timeoutCancelSignal = new CancellationSignal();

            LocationListener locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location l) {
                    timeoutCancelSignal.cancel();

                    mProgressDialog.dismiss();

                    displayPoint(l.getLatitude(), l.getLongitude(), l.getAltitude(), l.getSpeed(), l.isFromMockProvider());
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
            };

            mLocationManager.requestSingleUpdate(locationProvider, locationListener, null);

            Runnable timeoutRunnable = () -> {
                mLocationManager.removeUpdates(locationListener);

                Toast.makeText(this, "Operation timed-out", Toast.LENGTH_LONG).show();
                mProgressDialog.dismiss();
            };
            handler.postDelayed(timeoutRunnable, LOCATION_TIMEOUT_MILLIS);
            timeoutCancelSignal.setOnCancelListener(() -> handler.removeCallbacks(timeoutRunnable));
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
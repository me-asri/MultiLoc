package github.me_asri.multiloc;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatSpinner;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.function.Consumer;

import github.me_asri.multiloc.databinding.ActivityMainBinding;
import github.me_asri.multiloc.location.BTSLocation;
import github.me_asri.multiloc.location.IPLocation;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getName();
    private static final String SHARED_PREF_OSMDROID = TAG + ".osmdroid_pref";

    private ActivityMainBinding mBinding;

    private String mSelectedProvider;

    private LocationManager mLocationManager;

    private IMapController mMapController;

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

        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // osmdroid configuration
        Configuration.getInstance().load(this, getSharedPreferences(SHARED_PREF_OSMDROID, 0));
        // osmdroid map source
        mBinding.map.setTileSource(TileSourceFactory.MAPNIK);

        // Allow horizontal map repetition
        mBinding.map.setHorizontalMapRepetitionEnabled(true);
        // Prevent vertical map repetition
        mBinding.map.setVerticalMapRepetitionEnabled(false);
        // Limit vertical scrolling
        mBinding.map.setScrollableAreaLimitLatitude(MapView.getTileSystem().getMaxLatitude(), MapView.getTileSystem().getMinLatitude(), 0);

        mMapController = mBinding.map.getController();
        mMapController.setZoom(2.5);
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

            default:
                throw new RuntimeException("Unhandled selection");
        }

        return true;
    }

    private void showPointOnMap(double lat, double lon) {
        mMapController.setCenter(new GeoPoint(lat, lon));
        mMapController.setZoom(12.0);

        Marker marker = new Marker(mBinding.map);
        marker.setPosition(new GeoPoint(lat, lon));
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mBinding.map.getOverlays().add(marker);

    }

    private void useIPLocation() {
        mIPLocation.getLocation((r, t) -> {
            if (t != null) {
                Log.e(TAG, "onIPButtonClick: ", t);
                Toast.makeText(MainActivity.this, "Exception occurred: " + t.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
            if (r == null) {
                Toast.makeText(MainActivity.this, "Received empty response", Toast.LENGTH_LONG).show();
                return;
            }

            mBinding.locText.setText(getString(R.string.text_location, r.lat, r.lon));
            showPointOnMap(r.lat, r.lon);
        });
    }

    private void useBTSLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Fine location permission required", Toast.LENGTH_SHORT).show();
            requestLocationPermission();

            return;
        }

        mBTSLocation.getLocation(this, null, (r, t) -> {
            if (t != null) {
                Log.e(TAG, "onIPButtonClick: ", t);
                Toast.makeText(MainActivity.this, "Exception occurred: " + t.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                return;
            }
            if (r == null) {
                Toast.makeText(MainActivity.this, "Received empty response", Toast.LENGTH_LONG).show();
                return;
            }

            mBinding.locText.setText(getString(R.string.text_location, r.lat, r.lon));
            showPointOnMap(r.lat, r.lon);
        });
    }

    private void useGPSLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show();
            requestLocationPermission();

            return;
        }

        Consumer<Location> locationCallback = l -> {
            mBinding.locText.setText(getString(R.string.text_location, l.getLatitude(), l.getLongitude()));
            showPointOnMap(l.getLatitude(), l.getLongitude());
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mLocationManager.getCurrentLocation(LocationManager.GPS_PROVIDER, null, getMainExecutor(), locationCallback);
        } else {
            mLocationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationCallback::accept, null);
        }
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

            if (mSelectedProvider.equals("GPS") && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
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
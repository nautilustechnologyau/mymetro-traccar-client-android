package org.traccar.client;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static org.traccar.client.MainFragment.KEY_STATUS;
import static org.traccar.client.oba.util.PermissionUtils.LOCATION_PERMISSIONS;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.preference.PreferenceManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.traccar.client.app.Application;
import org.traccar.client.databinding.ActivityMainBinding;
import org.traccar.client.oba.io.ObaAnalytics;
import org.traccar.client.oba.region.ObaRegionsTask;
import org.traccar.client.oba.util.LocationUtils;
import org.traccar.client.oba.util.PermissionUtils;
import org.traccar.client.oba.util.PreferenceUtils;
import org.traccar.client.oba.util.UIUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class HomeActivity extends AppCompatActivity implements ObaRegionsTask.Callback {

    private static final String TAG = HomeActivity.class.getSimpleName();

    private static final long ALARM_MANAGER_INTERVAL = 15000;

    //One week, in milliseconds
    private static final long REGION_UPDATE_THRESHOLD = 1000 * 60 * 60 * 24 * 7;

    private static final String CHECK_REGION_VER = "checkRegionVer";

    private static boolean mServiceStarted = false;

    private static boolean mStartClicked = false;

    private ActivityMainBinding mBinding;

    private SharedPreferences mSharedPreferences;

    private FloatingActionButton mBtnFabStartStop;

    private AlarmManager mAlarmManager;

    private PendingIntent mAlarmIntent;

    private boolean mRequestingPermissions = false;

    private static final String INITIAL_STARTUP = "initialStartup";

    boolean mInitialStartup = true;

    private FirebaseAnalytics mFirebaseAnalytics;

    /**
     * GoogleApiClient being used for Location Services
     */
    protected GoogleApiClient mGoogleApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        if (BuildConfig.HIDDEN_APP && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            removeLauncherIcon();
        }

        setupFabButton();

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mSharedPreferences.edit().putBoolean(KEY_STATUS, false).commit();
        mInitialStartup = Application.getPrefs().getBoolean(INITIAL_STARTUP, true);

        setupAlarmManager();

        setupStartStopButtonState();

        setupStatusPreference();

        setupGooglePlayServices();

        if (!mInitialStartup || PermissionUtils.hasGrantedAtLeastOnePermission(this, LOCATION_PERMISSIONS)) {
            // It's not the first startup or if the user has already granted location permissions (Android L and lower), then check the region status
            // Otherwise, wait for a permission callback from the BaseMapFragment before checking the region status
            checkRegionStatus();
        }
    }

    // Register the permissions callback, which handles the user's response to the
    // system permissions dialog. Save the return value, an instance of
    // ActivityResultLauncher, as an instance variable.
    private ActivityResultLauncher<String> locationRequestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
        if (mStartClicked) {
            startTrackingService(false, isGranted);
        }
    });

    private ActivityResultLauncher<String> backgroundLocationRequestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
        if (isGranted) {
            Log.d(TAG, "Background location permission granted.");
        } else {
            Log.d(TAG, "Background location permission denied!");
        }
    });

    @Override
    public void onStart() {
        super.onStart();
        requestPermissionsIfRequired(true, false);
        if (mRequestingPermissions) {
            mRequestingPermissions = new BatteryOptimizationHelper().requestException(this);
        }

        if (mGoogleApiClient != null && !mGoogleApiClient.isConnected()) {
            mGoogleApiClient.connect();
        }
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        Boolean isTalkBackEnabled = am.isTouchExplorationEnabled();
        ObaAnalytics.setAccessibility(mFirebaseAnalytics, isTalkBackEnabled);
    }

    @Override
    public void onStop() {
        // Tear down GoogleApiClient
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        setupStartStopButtonState();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private void setupAlarmManager() {
        mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent originalIntent = new Intent(this, AutostartReceiver.class);
        originalIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

        int flags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;
        } else {
            flags = PendingIntent.FLAG_UPDATE_CURRENT;
        }
        mAlarmIntent = PendingIntent.getBroadcast(this, 0, originalIntent, flags);
    }
    private void onClickFAB() {
        if (isTrackingOn()) {
            stopTrackingService();
        } else {
            startTrackingService(true, false);
        }

        ((MainApplication)getApplication()).handleRatingFlow(this);

        setupStartStopButtonState();

        setupStatusPreference();
    }

    private void setupStatusPreference() {
        if (isTrackingOn()) {
            mSharedPreferences.edit().putBoolean(KEY_STATUS, true).apply();
        } else {
            mSharedPreferences.edit().putBoolean(KEY_STATUS, false).apply();
        }
    }

    private static boolean isTrackingOn() {
        return mServiceStarted;
    }

    private void setupFabButton() {
        BottomNavigationView navView = findViewById(R.id.nav_view);
        navView.setBackground(null);
        navView.getMenu().getItem(2).setEnabled(false);

        mBtnFabStartStop = findViewById(R.id.fab);
        mBtnFabStartStop.setOnClickListener(v -> {
            onClickFAB();
        });

        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home,
                R.id.navigation_settings,
                R.id.navigation_notifications,
                R.id.navigation_info).build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(mBinding.navView, navController);
    }
    private void setupStartStopButtonState() {
        if (isTrackingOn()) {
            mBtnFabStartStop.setImageResource(R.drawable.ic_stop);
        } else {
            mBtnFabStartStop.setImageResource(R.drawable.ic_start);
        }
    }

    private boolean requestPermissionsIfRequired(boolean checkPermission, boolean initialPermission) {
        boolean permission = initialPermission;

        if (checkPermission) {
            if (ContextCompat.checkSelfPermission(this,
                    ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                permission = true;
            }
            if (!permission) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    locationRequestPermissionLauncher.launch(ACCESS_FINE_LOCATION);
                }
            }
        }

        return permission;
    }

    private void startTrackingService(boolean checkPermission, boolean initialPermission) {
        mStartClicked = true;

        boolean permission = requestPermissionsIfRequired(checkPermission, initialPermission);

        if (permission) {
            ContextCompat.startForegroundService(this, new Intent(this, TrackingService.class));
            mServiceStarted = true;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                mAlarmManager.setInexactRepeating(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        ALARM_MANAGER_INTERVAL, ALARM_MANAGER_INTERVAL, mAlarmIntent
                );
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && ContextCompat.checkSelfPermission(this,
                    ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                mRequestingPermissions = true;
                showBackgroundLocationDialog(this);
            } else {
                mRequestingPermissions = new BatteryOptimizationHelper().requestException(this);
            }
        }
    }

    private void stopTrackingService() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            mAlarmManager.cancel(mAlarmIntent);
        }
        stopService(new Intent(this, TrackingService.class));
        mServiceStarted = false;
    }

    private void showBackgroundLocationDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        CharSequence option;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            option = context.getPackageManager().getBackgroundPermissionOptionLabel();
        } else {
            option = context.getString(R.string.request_background_option);
        }
        builder.setMessage(context.getString(R.string.request_background, option));
        builder.setPositiveButton(android.R.string.ok,(dialog, which) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    backgroundLocationRequestPermissionLauncher.launch(ACCESS_BACKGROUND_LOCATION);
                }
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void removeLauncherIcon() {
        String className = HomeActivity.class.getCanonicalName().replace(".HomeActivity", ".Launcher");
        ComponentName componentName = new ComponentName(getPackageName(), className);
        PackageManager packageManager = getPackageManager();
        if (packageManager.getComponentEnabledSetting(componentName) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
            );
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setMessage(getString(R.string.hidden_alert));
            builder.setPositiveButton(android.R.string.ok, null);
            builder.show();
        }
    }

    @Override
    public void onRegionTaskFinished(boolean currentRegionChanged) {
        // Show "What's New" (which might need refreshed Regions API contents)
        //boolean update = autoShowWhatsNew();

        // Redraw nav drawer if the region changed, or if we just installed a new version
        //if (currentRegionChanged || update) {
        //    redrawNavigationDrawerFragment();
        //}

        // If region changed and was auto-selected, show user what region we're using
        if (currentRegionChanged
                && Application.getPrefs()
                .getBoolean(getString(R.string.preference_key_auto_select_region), true)
                && Application.get().getCurrentRegion() != null
                && UIUtils.canManageDialog(this)) {
            Toast.makeText(getApplicationContext(),
                    getString(R.string.region_region_found,
                            Application.get().getCurrentRegion().getName()),
                    Toast.LENGTH_LONG
            ).show();
        }
        // updateLayersFab();
    }

    private void setupGooglePlayServices() {
        // Init Google Play Services as early as possible in the Fragment lifecycle to give it time
        GoogleApiAvailability api = GoogleApiAvailability.getInstance();
        if (api.isGooglePlayServicesAvailable(this)
                == ConnectionResult.SUCCESS) {
            mGoogleApiClient = LocationUtils.getGoogleApiClientWithCallbacks(this);
            mGoogleApiClient.connect();
        }
    }

    /**
     * Checks region status, which can potentially including forcing a reload of region
     * info from the server.  Also includes auto-selection of closest region.
     */
    private void checkRegionStatus() {
        //First check for custom API URL set by user via Preferences, since if that is set we don't need region info from the REST API
        if (!TextUtils.isEmpty(Application.get().getCustomApiUrl())) {
            return;
        }

        // Check if region is hard-coded for this build flavor
        /*if (BuildConfig.USE_FIXED_REGION) {
            ObaRegion r = RegionUtils.getRegionFromBuildFlavor();
            // Set the hard-coded region
            RegionUtils.saveToProvider(this, Collections.singletonList(r));
            Application.get().setCurrentRegion(r);
            // Disable any region auto-selection in preferences
            PreferenceUtils
                    .saveBoolean(getString(R.string.preference_key_auto_select_region), false);
            return;
        }*/

        boolean forceReload = false;
        boolean showProgressDialog = true;

        //If we don't have region info selected, or if enough time has passed since last region info update,
        //force contacting the server again
        if (Application.get().getCurrentRegion() == null ||
                new Date().getTime() - Application.get().getLastRegionUpdateDate()
                        > REGION_UPDATE_THRESHOLD) {
            forceReload = true;
            Log.d(TAG,
                    "Region info has expired (or does not exist), forcing a reload from the server...");
        }

        if (Application.get().getCurrentRegion() != null) {
            //We already have region info locally, so just check current region status quietly in the background
            showProgressDialog = false;
        }

        try {
            PackageInfo appInfo = getPackageManager().getPackageInfo(getPackageName(),
                    PackageManager.GET_META_DATA);
            SharedPreferences settings = Application.getPrefs();
            final int oldVer = settings.getInt(CHECK_REGION_VER, 0);
            final int newVer = appInfo.versionCode;

            if (oldVer < newVer) {
                forceReload = true;
            }
            PreferenceUtils.saveInt(CHECK_REGION_VER, appInfo.versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            // Do nothing
        }

        //Check region status, possibly forcing a reload from server and checking proximity to current region
        List<ObaRegionsTask.Callback> callbacks = new ArrayList<>();
        //callbacks.add(mMapFragment);
        callbacks.add(this);
        ObaRegionsTask task = new ObaRegionsTask(this, callbacks, forceReload, showProgressDialog);
        task.execute();
    }

}
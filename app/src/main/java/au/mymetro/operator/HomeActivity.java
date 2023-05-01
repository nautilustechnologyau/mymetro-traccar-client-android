package au.mymetro.operator;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static au.mymetro.operator.MainFragment.KEY_STATUS;
import static au.mymetro.operator.MainFragment.KEY_URL;
import static au.mymetro.operator.oba.util.PermissionUtils.BACKGROUND_LOCATION_PERMISSION_REQUEST;
import static au.mymetro.operator.oba.util.PermissionUtils.LOCATION_PERMISSIONS;

import android.Manifest;
import android.app.AlarmManager;
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
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.preference.PreferenceManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import au.mymetro.operator.app.Application;
import au.mymetro.operator.databinding.ActivityMainBinding;
import au.mymetro.operator.oba.io.ObaAnalytics;
import au.mymetro.operator.oba.io.ObaContext;
import au.mymetro.operator.oba.io.elements.ObaStop;
import au.mymetro.operator.oba.map.MapParams;
import au.mymetro.operator.oba.map.googlemapsv2.BaseMapFragment;
import au.mymetro.operator.oba.region.ObaRegionsTask;
import au.mymetro.operator.oba.travelbehavior.constants.TravelBehaviorConstants;
import au.mymetro.operator.oba.ui.ArrivalsListFragment;
import au.mymetro.operator.oba.util.LocationUtils;
import au.mymetro.operator.oba.util.PermissionUtils;
import au.mymetro.operator.oba.util.PreferenceUtils;
import au.mymetro.operator.oba.util.UIUtils;
import au.mymetro.operator.ui.home.HomeViewModel;

public class HomeActivity extends AppCompatActivity implements ObaRegionsTask.Callback, BaseMapFragment.OnLocationPermissionResultListener {

    public interface SlidingPanelController {

        /**
         * Sets the height of the sliding panel in pixels
         *
         * @param heightInPixels height of panel in pixels
         */
        void setPanelHeightPixels(int heightInPixels);

        /**
         * Returns the current height of the sliding panel in pixels, or -1 if the panel isn't yet
         * initialized
         *
         * @return the current height of the sliding panel in pixels, or -1 if the panel isn't yet
         * initialized
         */
        int getPanelHeightPixels();
    }

    private static final String TAG = HomeActivity.class.getSimpleName();

    private static final long ALARM_MANAGER_INTERVAL = 15000;

    //One week, in milliseconds
    private static final long REGION_UPDATE_THRESHOLD = 1000 * 60 * 60 * 24 * 7;

    public static final int BATTERY_OPTIMIZATIONS_PERMISSION_REQUEST = 111;

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

    private ActivityResultLauncher<String> travelBehaviorPermissionsLauncher;

    private ArrivalsListFragment mArrivalsListFragment;

    //Map Fragment
    private BaseMapFragment mMapFragment;

    private HomeViewModel homeViewModel;

    /**
     * GoogleApiClient being used for Location Services
     */
    protected GoogleApiClient mGoogleApiClient;

    /**
     * Starts the MapActivity in "RouteMode", which shows stops along a route,
     * and does not get new stops when the user pans the map.
     *
     * @param context The context of the activity.
     * @param routeId The route to show.
     */
    public static void start(Context context, String routeId) {
        context.startActivity(makeIntent(context, routeId));
    }

    /**
     * Starts the MapActivity with a particular stop focused with the center of
     * the map at a particular point.
     *
     * @param context The context of the activity.
     * @param stop    The stop to focus on.
     */
    public static void start(Context context, ObaStop stop) {
        context.startActivity(makeIntent(context, stop));
    }

    /**
     * Starts the MapActivity with a particular stop focused with the center of
     * the map at a particular point.
     *
     * @param context The context of the activity.
     * @param focusId The stop to focus.
     * @param lat     The latitude of the map center.
     * @param lon     The longitude of the map center.
     */
    public static void start(Context context,
                             String focusId,
                             double lat,
                             double lon) {
        context.startActivity(makeIntent(context, focusId, lat, lon));
    }

    /**
     * Returns an intent that starts the MapActivity in "RouteMode", which shows
     * stops along a route, and does not get new stops when the user pans the
     * map.
     *
     * @param context The context of the activity.
     * @param routeId The route to show.
     */
    public static Intent makeIntent(Context context, String routeId) {
        Intent myIntent = new Intent(context, HomeActivity.class);
        myIntent.putExtra(MapParams.MODE, MapParams.MODE_ROUTE);
        myIntent.putExtra(MapParams.ZOOM_TO_ROUTE, true);
        myIntent.putExtra(MapParams.ROUTE_ID, routeId);
        return myIntent;
    }

    /**
     * Returns an intent that will start the MapActivity with a particular stop
     * focused with the center of the map at a particular point.
     *
     * @param context The context of the activity.
     * @param focusId The stop to focus.
     * @param lat     The latitude of the map center.
     * @param lon     The longitude of the map center.
     */
    public static Intent makeIntent(Context context,
                                    String focusId,
                                    double lat,
                                    double lon) {
        Intent myIntent = new Intent(context, HomeActivity.class);
        myIntent.putExtra(MapParams.STOP_ID, focusId);
        myIntent.putExtra(MapParams.CENTER_LAT, lat);
        myIntent.putExtra(MapParams.CENTER_LON, lon);
        return myIntent;
    }

    /**
     * Returns an intent that will start the MapActivity with a particular stop
     * focused with the center of the map at a particular point.
     *
     * @param context The context of the activity.
     * @param stop    The stop to focus on.
     */
    public static Intent makeIntent(Context context, ObaStop stop) {
        Intent myIntent = new Intent(context, HomeActivity.class);
        myIntent.putExtra(MapParams.STOP_ID, stop.getId());
        myIntent.putExtra(MapParams.STOP_NAME, stop.getName());
        myIntent.putExtra(MapParams.STOP_CODE, stop.getStopCode());
        myIntent.putExtra(MapParams.CENTER_LAT, stop.getLatitude());
        myIntent.putExtra(MapParams.CENTER_LON, stop.getLongitude());
        return myIntent;
    }

    public ArrivalsListFragment getArrivalsListFragment() {
        return mArrivalsListFragment;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        if (BuildConfig.HIDDEN_APP && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            removeLauncherIcon();
        }

        setupNavigation();

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mSharedPreferences.edit().putBoolean(KEY_STATUS, false).commit();
        mInitialStartup = Application.getPrefs().getBoolean(INITIAL_STARTUP, true);

        ObaContext.setApiKey(Application.getPrefs().getString(getString(R.string.preference_key_oba_api_key), null));

        setupAlarmManager();

        setupStartStopButtonState();

        setupStatusPreference();

        setupGooglePlayServices();

        // To enable checkBatteryOptimizations, also uncomment the
        // REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission in AndroidManifest.xml
        // See https://github.com/OneBusAway/onebusaway-android/pull/988#discussion_r299950506
        // checkBatteryOptimizations();

        //setupPermissions(this);

        BaseMapFragment.setOnLocationPermissionResultListener(this);

        //new TravelBehaviorManager(this, getApplicationContext()).
        //        registerTravelBehaviorParticipant();

        //if (Application.get().getCurrentRegion() != null) {
        //    PreferenceUtils.saveString(getString(R.string.preference_key_region),
        //            Application.get().getCurrentRegion().getName());
        //}

        if (!mInitialStartup || PermissionUtils.hasGrantedAtLeastOnePermission(this, LOCATION_PERMISSIONS)) {
            // It's not the first startup or if the user has already granted location permissions (Android L and lower), then check the region status
            // Otherwise, wait for a permission callback from the BaseMapFragment before checking the region status
            checkRegionStatus();
        }

        /*if (!mInitialStartup && !isApiKeyValid()) {
            UIUtils.showObaApiKeyInputDialog(this);
        }*/

        //setupMapFragment(savedInstanceState);

        // setupLocationHelper(savedInstanceState);

        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        homeViewModel.getTripId().observe(this, this::onTripChange);
        homeViewModel.getServiceStatus().observe(this, this::onServiceStatus);
    }

    // Register the permissions callback, which handles the user's response to the
    // system permissions dialog. Save the return value, an instance of
    // ActivityResultLauncher, as an instance variable.
    private ActivityResultLauncher<String> locationRequestPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
        if (mInitialStartup) {
            // It's not the first startup or if the user has already granted location permissions (Android L and lower), then check the region status
            // Otherwise, wait for a permission callback from the BaseMapFragment before checking the region status
            // mInitialStartup = true;
            mInitialStartup = false;
            PreferenceUtils.saveBoolean(INITIAL_STARTUP, false);
            checkRegionStatus();
            return;
        }
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
        // requestPermissionsIfRequired(true, false);
        //if (mRequestingPermissions) {
        //    mRequestingPermissions = new BatteryOptimizationHelper().requestException(this);
        //}

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
            if (!isApiKeyValid()) {
                showApiKeyDialog(this);
                //UIUtils.popupSnackbarForApiKey(this);
                return;
            }

            String tripId = homeViewModel.getTripId().getValue();
            if (TextUtils.isEmpty(tripId)) {
                Snackbar snackbar = Snackbar.make(findViewById(R.id.home_layout),
                                R.string.msg_trip_not_selected,
                                Snackbar.LENGTH_INDEFINITE);
                snackbar.setAction("DISMISS", v -> {

                });
                snackbar.show();
                return;
            }
            startTrackingService(true, false);
        }

        ((Application) getApplication()).handleRatingFlow(this);

        setupStartStopButtonState();

        setupStatusPreference();
    }

    private void onTripChange(String tripId) {
        Log.d(TAG, "Trip selection changed: " + tripId);
        /*if (TextUtils.isEmpty(tripId)) {
            mBtnFabStartStop.setEnabled(false);
        } else {
            mBtnFabStartStop.setEnabled(true);
        }*/
    }

    private void onServiceStatus(Boolean status) {
        if (!status) {
            onClickFAB();
        }
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

    private void setupNavigation() {
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
            Intent intent = new Intent(this, TrackingService.class);
            Bundle bundle = new Bundle();
            // bundle.putString();
            bundle.putString("tripId", homeViewModel.getTripId().getValue());
            bundle.putString("routeId", homeViewModel.getRouteId().getValue());
            bundle.putString("blockId", homeViewModel.getBlockId().getValue());
            intent.putExtras(bundle);

            Log.d(TAG, "Starting tracking service with tripId=" + homeViewModel.getTripId().getValue());
            ContextCompat.startForegroundService(this, intent);
            homeViewModel.setMapMode(MapParams.MODE_ROUTE);
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
        homeViewModel.setMapMode(MapParams.MODE_STOP);
        mServiceStarted = false;
    }

    private void showBackgroundLocationDialog(Context context) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        CharSequence option;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            option = context.getPackageManager().getBackgroundPermissionOptionLabel();
        } else {
            option = context.getString(R.string.request_background_option);
        }
        builder.setMessage(context.getString(R.string.request_background, option));
        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    backgroundLocationRequestPermissionLauncher.launch(ACCESS_BACKGROUND_LOCATION);
                }
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    private void showApiKeyDialog(Context context) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setMessage(context.getString(R.string.oba_api_key_invalid));
        builder.setPositiveButton(android.R.string.ok, null);
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
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
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

        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentByTag(BaseMapFragment.TAG);
        if (fragment != null) {
            mMapFragment = (BaseMapFragment) fragment;
            mMapFragment.setMyLocation();
        }

        if (!currentRegionChanged && Application.get().getCurrentRegion() != null && !isApiKeyValid()) {
            UIUtils.showObaApiKeyInputDialog(this);
        }
        //    PreferenceUtils.saveString(getString(R.string.preference_key_region),
        //            Application.get().getCurrentRegion().getName());
        //}
        // If region changed and was auto-selected, show user what region we're using
        if (currentRegionChanged
                && Application.getPrefs().getBoolean(getString(R.string.preference_key_auto_select_region), true)
                && Application.get().getCurrentRegion() != null
                && UIUtils.canManageDialog(this)) {
            Toast.makeText(getApplicationContext(),
                    getString(R.string.region_region_found,
                            Application.get().getCurrentRegion().getName()),
                    Toast.LENGTH_LONG
            ).show();

            UIUtils.showObaApiKeyInputDialog(this);

            PreferenceUtils.saveString(KEY_URL, Application.get().getCurrentRegion().getTraccarBaseUrl());


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

    private boolean isApiKeyValid() {
        String apiKey = PreferenceUtils.getString(getString(R.string.preference_key_oba_api_key));
        return !TextUtils.isEmpty(apiKey);
    }

    /**
     * Setup permissions that are only requested if the user joins the travel behavior study. This
     * method must be called from #onCreate().
     * <p>
     * A call to #requestPhysicalActivityPermission() invokes the permission request, and should only
     * be called in the case when the user opts into the study.
     *
     * @param activity
     */
    private void setupPermissions(AppCompatActivity activity) {
        travelBehaviorPermissionsLauncher =
                registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        // User opt-ed into study and granted physical activity tracking - now request background location permissions (when targeting Android 11 we can't request both simultaneously)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            activity.requestPermissions(TravelBehaviorConstants.BACKGROUND_LOCATION_PERMISSION, BACKGROUND_LOCATION_PERMISSION_REQUEST);
                        }
                    }
                });
    }

    /**
     * Requests physical activity permissions, and then subsequently background location
     * permissions (based on the initialization in #setupPermissions() if the user grants physical
     * activity permissions. This method should only be called after the user opts into the travel behavior study.
     */
    public void requestPhysicalActivityPermission() {
        if (travelBehaviorPermissionsLauncher != null) {
            travelBehaviorPermissionsLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION);
        }
    }

    private void showIgnoreBatteryOptimizationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setMessage(R.string.application_ignoring_battery_opt_message)
                .setTitle(R.string.application_ignoring_battery_opt_title)
                .setIcon(R.drawable.ic_alert_warning)
                .setCancelable(false)
                .setPositiveButton(R.string.travel_behavior_dialog_yes,
                        (dialog, which) -> {
                            if (PermissionUtils.hasGrantedAllPermissions(this, new String[]{Manifest.
                                    permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS})) {
                                UIUtils.openBatteryIgnoreIntent(this);
                            } else {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    requestPermissions(new String[]{Manifest.
                                                    permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS},
                                            BATTERY_OPTIMIZATIONS_PERMISSION_REQUEST);
                                }
                            }
                            PreferenceUtils.saveBoolean(getString(R.string.not_request_battery_optimizations_key),
                                    true);
                        })
                .setNegativeButton(R.string.travel_behavior_dialog_no,
                        (dialog, which) -> {
                            PreferenceUtils.saveBoolean(getString(R.string.not_request_battery_optimizations_key),
                                    true);
                        })
                .create().show();
    }

    private void checkBatteryOptimizations() {
        Boolean ignoringBatteryOptimizations = Application.isIgnoringBatteryOptimizations(getApplicationContext());
        if (ignoringBatteryOptimizations != null && !ignoringBatteryOptimizations) {
            showIgnoreBatteryOptimizationDialog();
        }
    }

    @Override
    public void onLocationPermissionResult(int grantResult) {
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            if (mInitialStartup) {
                // It's not the first startup or if the user has already granted location permissions (Android L and lower), then check the region status
                // Otherwise, wait for a permission callback from the BaseMapFragment before checking the region status
                // mInitialStartup = true;
                mInitialStartup = false;
                PreferenceUtils.saveBoolean(INITIAL_STARTUP, false);
                checkRegionStatus();
            }
        }
    }

}
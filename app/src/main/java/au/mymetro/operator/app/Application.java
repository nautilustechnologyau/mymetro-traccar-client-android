package au.mymetro.operator.app;

import static com.google.android.gms.location.LocationServices.getFusedLocationProviderClient;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.GeomagneticField;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.multidex.MultiDexApplication;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;

import java.security.MessageDigest;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import au.mymetro.operator.R;
import au.mymetro.operator.oba.io.ObaAnalytics;
import au.mymetro.operator.oba.io.ObaApi;
import au.mymetro.operator.oba.io.elements.ObaRegion;
import au.mymetro.operator.oba.provider.ObaContract;
import au.mymetro.operator.oba.util.LocationUtils;
import au.mymetro.operator.oba.util.PreferenceUtils;

public class Application extends MultiDexApplication {
    private static final String TAG = "Application";
    public static final String PRIMARY_CHANNEL = "default";
    public static final String APP_UID = "app_uid";

    public static final String CHANNEL_TRIP_PLAN_UPDATES_ID = "trip_plan_updates";
    public static final String CHANNEL_DESTINATION_ALERT_ID = "destination_alerts";
    public static final String CHANNEL_ARRIVAL_REMINDERS_ID = "arrival_reminders";

    private SharedPreferences mPrefs;
    private static Application mApp;

    /**
     * We centralize location tracking in the Application class to allow all objects to make
     * use of the last known location that we've seen.  This is more reliable than using the
     * getLastKnownLocation() method of the location providers, and allows us to track both
     * Location
     * API v1 and fused provider.  It allows us to avoid strange behavior like animating a map view
     * change when opening a new Activity, even when the previous Activity had a current location.
     */
    private static Location mLastKnownLocation = null;

    // Magnetic declination is based on location, so track this centrally too.
    static GeomagneticField mGeomagneticField = null;

    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    public void onCreate() {
        super.onCreate();
        System.setProperty("http.keepAliveDuration", String.valueOf(30 * 60 * 1000));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerChannel();
        }

        mApp = this;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        initOba();
        initObaRegion();
        reportAnalytics();

    }

    /**
     * Per http://developer.android.com/reference/android/app/Application.html#onTerminate(),
     * this code is only executed in emulated process environments - it will never be called
     * on a production Android device.
     */
    @Override
    public void onTerminate() {
        super.onTerminate();
        mApp = null;
    }

    //
    // Public helpers
    //
    public static Application get() {
        return mApp;
    }

    public static SharedPreferences getPrefs() {
        return get().mPrefs;
    }


    /**
     * Returns the last known location that the application has seen, or null if we haven't seen a
     * location yet.  When trying to get a most recent location in one shot, this method should
     * always be called.
     *
     * @param cxt    The Context being used, or null if one isn't available
     * @param client The GoogleApiClient being used to obtain fused provider updates, or null if
     *               one
     *               isn't available
     * @return the last known location that the application has seen, or null if we haven't seen a
     * location yet
     */
    public static synchronized Location getLastKnownLocation(Context cxt, GoogleApiClient client) {
        if (mLastKnownLocation == null) {
            // Try to get a last known location from the location providers
            try {
                mLastKnownLocation = getLocation2(cxt, client);
            } catch (SecurityException e) {
                Log.e(TAG, "User may have denied location permission - " + e);
            }
        }
        // Pass back last known saved location, hopefully from past location listener updates
        return mLastKnownLocation;
    }


    /**
     * Sets the last known location observed by the application via an instance of LocationHelper
     *
     * @param l a location received by a LocationHelper instance
     */
    public static synchronized void setLastKnownLocation(Location l) {
        // If the new location is better than the old one, save it
        if (LocationUtils.compareLocations(l, mLastKnownLocation)) {
            if (mLastKnownLocation == null) {
                mLastKnownLocation = new Location("Last known location");
            }
            mLastKnownLocation.set(l);
            mGeomagneticField = new GeomagneticField(
                    (float) l.getLatitude(),
                    (float) l.getLongitude(),
                    (float) l.getAltitude(),
                    System.currentTimeMillis());
            // Log.d(TAG, "Newest best location: " + mLastKnownLocation.toString());
        }
    }

    /**
     * Returns the declination of the horizontal component of the magnetic field from true north,
     * in
     * degrees (i.e. positive means the magnetic field is rotated east that much from true north).
     *
     * @return declination of the horizontal component of the magnetic field from true north, in
     * degrees (i.e. positive means the magnetic field is rotated east that much from true north),
     * or null if its not available
     */
    public static Float getMagneticDeclination() {
        if (mGeomagneticField != null) {
            return mGeomagneticField.getDeclination();
        } else {
            return null;
        }
    }

    /**
     * We need to provide the API for a location used to disambiguate stop IDs in case of
     * collision,
     * or to provide multiple results in the case multiple agencies. But we really don't need it to
     * be very accurate.
     * <p/>
     * Note that the GoogleApiClient must already have been initialized and connected prior to
     * calling
     * this method, since GoogleApiClient.connect() is asynchronous and doesn't connect before it
     * returns,
     * which requires additional initialization time (prior to calling this method)
     *
     * @param client an initialized and connected GoogleApiClient, or null if Google Play Services
     *               isn't available
     * @return a recent location, considering both Google Play Services (if available) and the
     * Android Location API
     */
    private static Location getLocation(Context cxt, GoogleApiClient client) {
        Location last = getLocation2(cxt, client);
        if (last != null) {
            return last;
        } else {
            return LocationUtils.getDefaultSearchCenter();
        }
    }

    /**
     * Returns a location, considering both Google Play Services (if available) and the Android
     * Location API
     * <p/>
     * Note that the GoogleApiClient must already have been initialized and connected prior to
     * calling
     * this method, since GoogleApiClient.connect() is asynchronous and doesn't connect before it
     * returns,
     * which requires additional initialization time (prior to calling this method)
     *
     * @param client an initialized and connected GoogleApiClient, or null if Google Play Services
     *               isn't available
     * @return a recent location, considering both Google Play Services (if available) and the
     * Android Location API
     * @throws SecurityException if the user has remove location permissions
     */
    @SuppressLint("MissingPermission")
    private static Location getLocation2(Context cxt, GoogleApiClient client)
            throws SecurityException {
        GoogleApiAvailability api = GoogleApiAvailability.getInstance();
        Location playServices = null;
        if (client != null &&
                cxt != null &&
                api.isGooglePlayServicesAvailable(cxt)
                        == ConnectionResult.SUCCESS
                && client.isConnected()) {
            FusedLocationProviderClient fusedClient = getFusedLocationProviderClient(cxt);
            Task<Location> task = fusedClient.getLastLocation();
            if (task.isComplete()) {
                playServices = task.getResult();
                Log.d(TAG, "Got location from Google Play Services, testing against API v1...");
            }
        }
        Location apiV1 = getLocationApiV1(cxt);

        if (LocationUtils.compareLocationsByTime(playServices, apiV1)) {
            Log.d(TAG, "Using location from Google Play Services");
            return playServices;
        } else {
            Log.d(TAG, "Using location from Location API v1");
            return apiV1;
        }
    }

    @SuppressLint("MissingPermission")
    private static Location getLocationApiV1(Context cxt) {
        if (cxt == null) {
            return null;
        }
        LocationManager mgr = (LocationManager) cxt.getSystemService(Context.LOCATION_SERVICE);
        List<String> providers = mgr.getProviders(true);
        Location last = null;
        for (Iterator<String> i = providers.iterator(); i.hasNext(); ) {
            Location loc = null;
            try {
                loc = mgr.getLastKnownLocation(i.next());
            }  catch (SecurityException e) {
                Log.w(TAG, "User may have denied location permission - " + e);
            }
            // If this provider has a last location, and either:
            // 1. We don't have a last location,
            // 2. Our last location is older than this location.
            if (LocationUtils.compareLocationsByTime(loc, last)) {
                last = loc;
            }
        }
        return last;
    }

    //
    // Helper to get/set the regions
    //
    public synchronized ObaRegion getCurrentRegion() {
        return ObaApi.getDefaultContext().getRegion();
    }

    public synchronized void setCurrentRegion(ObaRegion region) {
        setCurrentRegion(region, true);
    }

    public synchronized void setCurrentRegion(ObaRegion region, boolean regionChanged) {
        if (region != null) {
            // First set it in preferences, then set it in OBA.
            ObaApi.getDefaultContext().setRegion(region);
            PreferenceUtils.saveLong(mPrefs, getString(R.string.preference_key_region), region.getId());
            if (regionChanged) {
                PreferenceUtils.saveString(mPrefs, getString(R.string.preference_key_oba_api_key), null);
            }
            //We're using a region, so clear the custom API URL preference
            setCustomApiUrl(null);
            if (regionChanged && region.getOtpBaseUrl() != null) {
                setCustomOtpApiUrl(null);
                setUseOldOtpApiUrlVersion(false);
                setCustomTraccarUrl(null);
            }
        } else {
            //User must have just entered a custom API URL via Preferences, so clear the region info
            ObaApi.getDefaultContext().setRegion(null);
            PreferenceUtils.saveLong(mPrefs, getString(R.string.preference_key_region), -1);
        }
    }

    /**
     * Gets the date at which the region information was last updated, in the number of
     * milliseconds
     * since January 1, 1970, 00:00:00 GMT
     * Default value is 0 if the region info has never been updated.
     *
     * @return the date at which the region information was last updated, in the number of
     * milliseconds since January 1, 1970, 00:00:00 GMT.  Default value is 0 if the region info has
     * never been updated.
     */
    public long getLastRegionUpdateDate() {
        SharedPreferences preferences = getPrefs();
        return preferences.getLong(getString(R.string.preference_key_last_region_update), 0);
    }

    /**
     * Sets the date at which the region information was last updated
     *
     * @param date the date at which the region information was last updated, in the number of
     *             milliseconds since January 1, 1970, 00:00:00 GMT
     */
    public void setLastRegionUpdateDate(long date) {
        PreferenceUtils
                .saveLong(mPrefs, getString(R.string.preference_key_last_region_update), date);
    }

    /**
     * Returns the custom URL if the user has set a custom API URL manually via Preferences, or
     * null
     * if it has not been set
     *
     * @return the custom URL if the user has set a custom API URL manually via Preferences, or null
     * if it has not been set
     */
    public String getCustomApiUrl() {
        SharedPreferences preferences = getPrefs();
        return preferences.getString(getString(R.string.preference_key_oba_api_url), null);
    }

    /**
     * Sets the custom URL used to reach a OBA REST API server that is not available via the
     * Regions
     * REST API
     *
     * @param url the custom URL
     */
    public void setCustomApiUrl(String url) {
        PreferenceUtils.saveString(getString(R.string.preference_key_oba_api_url), url);
    }

    /**
     * Returns the custom OTP URL if the user has set a custom API URL manually via Preferences, or
     * null
     * if it has not been set
     *
     * @return the custom URL if the user has set a custom API URL manually via Preferences, or null
     * if it has not been set
     */
    public String getCustomOtpApiUrl() {
        SharedPreferences preferences = getPrefs();
        return preferences.getString(getString(R.string.preference_key_otp_api_url), null);
    }

    /**
     * Sets the custom OTP URL used to reach a OBA REST API server that is not available via the
     * Regions
     * REST API
     *
     * @param url the custom URL
     */
    public void setCustomOtpApiUrl(String url) {
        PreferenceUtils.saveString(getString(R.string.preference_key_otp_api_url), url);
    }

    /**
     * Returns the custom Traccar URL if the user has set a custom API URL manually via Preferences, or
     * null
     * if it has not been set
     *
     * @return the custom URL if the user has set a custom API URL manually via Preferences, or null
     * if it has not been set
     */
    public String getCustomTraccarUrl() {
        SharedPreferences preferences = getPrefs();
        return preferences.getString(getString(R.string.preference_key_traccar_url), null);
    }

    /**
     * Sets the custom Traccar URL used to reach a OBA REST API server that is not available via the
     * Regions
     * REST API
     *
     * @param url the custom URL
     */
    public void setCustomTraccarUrl(String url) {
        PreferenceUtils.saveString(getString(R.string.preference_key_traccar_url), url);
    }

    /**
     * @return true if the OTP url version is old, or false  if it has not been set
     */
    public boolean getUseOldOtpApiUrlVersion() {
        SharedPreferences preferences = getPrefs();
        return preferences.getBoolean(getString(R.string.preference_key_otp_api_url_version), false);
    }

    /**
     * Sets the OTP Api url version
     *
     * @param useOldOtpApiUrlVersion indicates that if otp url structure belongs to older version
     */
    public void setUseOldOtpApiUrlVersion(boolean useOldOtpApiUrlVersion) {
        PreferenceUtils.saveBoolean(getString(R.string.preference_key_otp_api_url_version),
                useOldOtpApiUrlVersion);
    }

    private static final String HEXES = "0123456789abcdef";

    public static String getHex(byte[] raw) {
        final StringBuilder hex = new StringBuilder(2 * raw.length);
        for (byte b : raw) {
            hex.append(HEXES.charAt((b & 0xF0) >> 4))
                    .append(HEXES.charAt((b & 0x0F)));
        }
        return hex.toString();
    }

    private String getAppUid() {
        return UUID.randomUUID().toString();
    }

    private void initOba() {
        String uuid = mPrefs.getString(APP_UID, null);
        if (uuid == null) {
            // Generate one and save that.
            uuid = getAppUid();
            PreferenceUtils.saveString(APP_UID, uuid);
        }

        // Get the current app version.
        PackageManager pm = getPackageManager();
        PackageInfo appInfo = null;
        try {
            appInfo = pm.getPackageInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            // Do nothing, perhaps we'll get to show it again? Or never.
            return;
        }
        ObaApi.getDefaultContext().setAppInfo(appInfo.versionCode, uuid);
    }

    private void initObaRegion() {
        // Read the region preference, look it up in the DB, then set the region.
        long id = mPrefs.getLong(getString(R.string.preference_key_region), -1);
        if (id < 0) {
            Log.d(TAG, "Regions preference ID is less than 0, returning...");
            return;
        }

        ObaRegion region = ObaContract.Regions.get(this, (int) id);
        if (region == null) {
            Log.d(TAG, "Regions preference is null, returning...");
            return;
        }


        ObaApi.getDefaultContext().setRegion(region);
    }

    @SuppressLint("MissingPermission")
    private void reportAnalytics() {
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        if (getCustomApiUrl() == null && getCurrentRegion() != null) {
            ObaAnalytics.setRegion(mFirebaseAnalytics, getCurrentRegion().getName());
        } else if (Application.get().getCustomApiUrl() != null) {
            String customUrl = null;
            MessageDigest digest = null;
            try {
                digest = MessageDigest.getInstance("SHA-1");
                digest.update(getCustomApiUrl().getBytes());
                customUrl = getString(R.string.analytics_label_custom_url) +
                        ": " + getHex(digest.digest());
            } catch (Exception e) {
                customUrl = Application.get().getString(R.string.analytics_label_custom_url);
            }
            ObaAnalytics.setRegion(mFirebaseAnalytics, customUrl);
        }
        Boolean experimentalRegions = getPrefs().getBoolean(getString(R.string.preference_key_experimental_regions),
                Boolean.FALSE);
        Boolean autoRegion = getPrefs().getBoolean(getString(R.string.preference_key_auto_select_region),
                true);
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void registerChannel() {
        NotificationChannel channel = new NotificationChannel(
                PRIMARY_CHANNEL, getString(R.string.channel_default), NotificationManager.IMPORTANCE_LOW
        );
        channel.setLightColor(Color.BLUE);
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }

    public void handleRatingFlow(Activity activity) {}

    /**
     * Method to check whether bikeshare layer is enabled or not.
     *
     * @return true if the bikeshare layer is an option that can be toggled on/off
     */
    public static boolean isBikeshareEnabled() {
        // Bike layer is enabled if either the current region
        // supports it or a custom otp url is set. The custom otp url is used to make the testing
        // process easier
        return ((Application.get().getCurrentRegion() != null
                && Application.get().getCurrentRegion().getSupportsOtpBikeshare())
                || !TextUtils.isEmpty(Application.get().getCustomOtpApiUrl()));
    }

    public static Boolean isIgnoringBatteryOptimizations(Context applicationContext) {
        PowerManager pm = (PowerManager) applicationContext.getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                pm.isIgnoringBatteryOptimizations(applicationContext.getPackageName())) {
            return true;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null;
        }

        return false;
    }
}

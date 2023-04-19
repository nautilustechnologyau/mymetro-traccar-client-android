package org.traccar.client;

import static android.Manifest.permission.ACCESS_BACKGROUND_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;

import static org.traccar.client.MainFragment.KEY_STATUS;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.preference.PreferenceManager;

import org.traccar.client.databinding.ActivityMainBinding;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = HomeActivity.class.getSimpleName();

    private static final long ALARM_MANAGER_INTERVAL = 15000;

    private static boolean mServiceStarted = false;

    private static boolean mStartClicked = false;
    private ActivityMainBinding mBinding;

    private SharedPreferences mSharedPreferences;

    private FloatingActionButton mBtnFabStartStop;

    private AlarmManager mAlarmManager;

    private PendingIntent mAlarmIntent;

    private boolean mRequestingPermissions = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        if (BuildConfig.HIDDEN_APP && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            removeLauncherIcon();
        }

        setupFabButton();

        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mSharedPreferences.edit().putBoolean(KEY_STATUS, false).commit();

        setupAlarmManager();

        setupStartStopButtonState();

        setupStatusPreference();
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

}
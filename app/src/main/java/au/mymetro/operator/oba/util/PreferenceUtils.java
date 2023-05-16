/*
 * Copyright 2023 Nautilus Technology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.mymetro.operator.oba.util;

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;

import au.mymetro.operator.R;
import au.mymetro.operator.app.Application;
import au.mymetro.operator.oba.map.MapParams;

/**
 * A class containing utility methods related to preferences
 */
public class PreferenceUtils {

    @TargetApi(9)
    public static void saveString(SharedPreferences prefs, String key, String value) {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putString(key, value);
        edit.apply();
    }

    public static void saveString(String key, String value) {
        saveString(Application.getPrefs(), key, value);
    }

    @TargetApi(9)
    public static void saveInt(SharedPreferences prefs, String key, int value) {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putInt(key, value);
        edit.apply();
    }

    public static void saveInt(String key, int value) {
        saveInt(Application.getPrefs(), key, value);
    }

    @TargetApi(9)
    public static void saveLong(SharedPreferences prefs, String key, long value) {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putLong(key, value);
        edit.apply();
    }

    public static void saveLong(String key, long value) {
        saveLong(Application.getPrefs(), key, value);
    }

    @TargetApi(9)
    public static void saveBoolean(SharedPreferences prefs, String key, boolean value) {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putBoolean(key, value);
        edit.apply();
    }

    public static void saveBoolean(String key, boolean value) {
        saveBoolean(Application.getPrefs(), key, value);
    }

    @TargetApi(9)
    public static void saveFloat(SharedPreferences prefs, String key, float value) {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putFloat(key, value);
        edit.apply();
    }

    public static void saveFloat(String key, float value) {
        saveFloat(Application.getPrefs(), key, value);
    }

    @TargetApi(9)
    public static void saveDouble(SharedPreferences prefs, String key, double value) {
        SharedPreferences.Editor edit = prefs.edit();
        edit.putLong(key, Double.doubleToRawLongBits(value));
        edit.apply();
    }

    @TargetApi(9)
    public static void saveDouble(String key, double value) {
        saveDouble(Application.getPrefs(), key, value);
    }

    /**
     * Gets a double for the provided key from preferences, or the default value if the preference
     * doesn't currently have a value
     *
     * @param key          key for the preference
     * @param defaultValue the default value to return if the key doesn't have a value
     * @return a double from preferences, or the default value if it doesn't exist
     */
    public static Double getDouble(String key, double defaultValue) {
        if (!Application.getPrefs().contains(key)) {
            return defaultValue;
        }
        return Double.longBitsToDouble(Application.getPrefs().getLong(key, 0));
    }

    public static String getString(String key) {
        return Application.getPrefs().getString(key, null);
    }

    public static long getLong(String key, long defaultValue) {
        return Application.getPrefs().getLong(key, defaultValue);
    }

    public static float getFloat(String key, float defaultValue) {
        return Application.getPrefs().getFloat(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        return Application.getPrefs().getInt(key, defaultValue);
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        return Application.getPrefs().getBoolean(key, defaultValue);
    }

    /**
     * Returns true if the user has previously indicated that they don't want to be prompted to provide
     * location permissions. Note that this means they haven't actually be prompted with the
     * system permission dialog.
     */
    public static boolean userDeniedLocationPermission() {
        Resources r = Application.get().getResources();
        return getBoolean(r.getString(R.string.preferences_key_user_denied_location_permissions), false);
    }

    /**
     * Retrieves the map view location and zoom level from a preference and stores it in the
     * provided bundle, if a valid lat/long and zoom level has been previously saved to prefs
     *
     * @param b bundle to store the map view center and zoom level in
     */
    public static void maybeRestoreMapViewToBundle(Bundle b) {
        Double lat = PreferenceUtils.getDouble(MapParams.CENTER_LAT, 0.0d);
        Double lon = PreferenceUtils.getDouble(MapParams.CENTER_LON, 0.0d);
        float zoom = PreferenceUtils.getFloat(MapParams.ZOOM, MapParams.DEFAULT_ZOOM);
        if (lat != 0.0 && lon != 0.0 && zoom != 0.0) {
            b.putDouble(MapParams.CENTER_LAT, lat);
            b.putDouble(MapParams.CENTER_LON, lon);
            b.putFloat(MapParams.ZOOM, zoom);
        }
    }

    /**
     * Saves provided MapView center location and zoom level to preferences
     *
     * @param lat  latitude of map center
     * @param lon  longitude of map center
     * @param zoom zoom level of map
     */
    public static void saveMapViewToPreferences(double lat, double lon, float zoom) {
        saveDouble(MapParams.CENTER_LAT, lat);
        saveDouble(MapParams.CENTER_LON, lon);
        saveFloat(MapParams.ZOOM, zoom);
    }

    /**
     * Set value to true if the user has previously indicated that they don't want to be prompted to provide
     * location permissions, or false if they have indicated that they want to be prompted with
     * the system permission dialog.
     */
    public static void setUserDeniedLocationPermissions(boolean value) {
        Resources r = Application.get().getResources();
        saveBoolean(r.getString(R.string.preferences_key_user_denied_location_permissions), value);
    }

}

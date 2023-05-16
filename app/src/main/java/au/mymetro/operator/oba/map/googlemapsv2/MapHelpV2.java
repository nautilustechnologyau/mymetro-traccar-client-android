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
package au.mymetro.operator.oba.map.googlemapsv2;

import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

import au.mymetro.operator.oba.io.elements.ObaRegion;
import au.mymetro.operator.oba.io.elements.ObaTripStatus;
import au.mymetro.operator.oba.io.request.ObaTripDetailsResponse;

/**
 * Utilities to help process data for Android Maps API v1
 */
public class MapHelpV2 {

    public static final String TAG = "MapHelpV2";

    /**
     * Converts a latitude/longitude to a LatLng.
     *
     * @param lat The latitude.
     * @param lon The longitude.
     * @return A LatLng representing this latitude/longitude.
     */
    public static final LatLng makeLatLng(double lat, double lon) {
        return new LatLng(lat, lon);
    }

    /**
     * Converts a Location to a LatLng.
     *
     * @param l Location to convert
     * @return A LatLng representing this LatLng.
     */
    public static final LatLng makeLatLng(Location l) {
        if (l == null) {
            return null;
        }
        return makeLatLng(l.getLatitude(), l.getLongitude());
    }

    /**
     * Converts a LatLng to a Location.
     *
     * @param latLng LatLng to convert
     * @return A Location representing this LatLng.
     */
    public static final Location makeLocation(LatLng latLng) {
        if (latLng == null) {
            return null;
        }
        Location l = new Location("FromLatLng");
        l.setLatitude(latLng.latitude);
        l.setLongitude(latLng.longitude);
        return l;
    }

    /**
     * Returns the bounds for the entire region.
     *
     * @return LatLngBounds for the region
     */
    public static LatLngBounds getRegionBounds(ObaRegion region) {
        if (region == null) {
            throw new IllegalArgumentException("Region is null");
        }
        double latMin = 90;
        double latMax = -90;
        double lonMin = 180;
        double lonMax = -180;

        // This is fairly simplistic
        for (ObaRegion.Bounds bound : region.getBounds()) {
            // Get the top bound
            double lat = bound.getLat();
            double latSpanHalf = bound.getLatSpan() / 2.0;
            double lat1 = lat - latSpanHalf;
            double lat2 = lat + latSpanHalf;
            if (lat1 < latMin) {
                latMin = lat1;
            }
            if (lat2 > latMax) {
                latMax = lat2;
            }

            double lon = bound.getLon();
            double lonSpanHalf = bound.getLonSpan() / 2.0;
            double lon1 = lon - lonSpanHalf;
            double lon2 = lon + lonSpanHalf;
            if (lon1 < lonMin) {
                lonMin = lon1;
            }
            if (lon2 > lonMax) {
                lonMax = lon2;
            }
        }

        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(MapHelpV2.makeLatLng(latMin, lonMin));
        builder.include(MapHelpV2.makeLatLng(latMax, lonMax));

        return builder.build();
    }

    /**
     * Returns true if Android Maps V2 is installed, false if it is not
     */
    public static boolean isMapsInstalled(Context context) {
        return ProprietaryMapHelpV2.isMapsInstalled(context);
    }

    /**
     * Prompts the user to install Android Maps V2
     */
    public static void promptUserInstallMaps(final Context context) {
        ProprietaryMapHelpV2.promptUserInstallMaps(context);
    }

    /**
     * Gets the location of the vehicle closest to the provided location running the provided
     * routes
     *
     * @param response response containing list of trips with vehicle locations
     * @param loc      location
     * @return the closest vehicle location to the given location, or null if a closest vehicle
     * couldn't be found
     */
    public static LatLng getClosestVehicle(ObaTripDetailsResponse response, Location loc) {
        if (loc == null) {
            return null;
        }
        float minDist = Float.MAX_VALUE;
        ObaTripStatus closestVehicle = null;
        Location closestVehicleLocation = null;
        Float distToVehicle;

        //for (ObaTripDetails detail : response.getTrips()) {
            Location vehicleLocation;
            ObaTripStatus status = response.getStatus();
            if (status == null) {
                return null;
            }

            if (status.getLastKnownLocation() != null) {
                // Use last actual position
                vehicleLocation = status.getLastKnownLocation();
            } else if (status.getPosition() != null) {
                // Use last interpolated position
                vehicleLocation = status.getPosition();
            } else {
                // No vehicle location - continue to next trip
                return null;
            }
            distToVehicle = vehicleLocation.distanceTo(loc);

            if (distToVehicle < minDist) {
                closestVehicleLocation = vehicleLocation;
                closestVehicle = status;
                minDist = distToVehicle;
            }
        //}

        if (closestVehicleLocation == null) {
            return null;
        }

        Log.d(TAG, "Closest vehicle is vehicleId=" + closestVehicle.getVehicleId() + ", routeId="
                + ", tripId=" +
                closestVehicle.getActiveTripId() + " at " + closestVehicleLocation.getLatitude()
                + ","
                + closestVehicleLocation.getLongitude());

        return makeLatLng(closestVehicleLocation);
    }
}

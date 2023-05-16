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
package au.mymetro.operator.oba.map;

import android.app.Activity;
import android.location.Location;
import android.os.Bundle;
import android.view.View;

import java.util.List;

import au.mymetro.operator.oba.io.elements.ObaReferences;
import au.mymetro.operator.oba.io.elements.ObaRoute;
import au.mymetro.operator.oba.io.elements.ObaShape;
import au.mymetro.operator.oba.io.elements.ObaStop;
import au.mymetro.operator.oba.io.request.ObaStopsForLocationResponse;
import au.mymetro.operator.oba.io.request.ObaStopsForRouteResponse;
import au.mymetro.operator.oba.io.request.ObaTripDetailsResponse;
import au.mymetro.operator.oba.io.request.ObaTripsForRouteResponse;

public interface MapModeController {

    /**
     * The percentage of the map that the bottom sliding overlay will cover when expanded,
     * from 0 to 1
     */
    public static final float OVERLAY_PERCENTAGE = 0.5f;

    /**
     * Controllers should make every attempt to communicate through
     * the Callback interface rather than accessing the MapFragment
     * directly, even if it means duplicating some functionality,
     * just to keep the separation between them clean.
     *
     * @author paulw
     */
    interface Callback {

        // Used by the controller to tell the Fragment what to do.
        Activity getActivity();

        View getView();

        // Can't use a LoaderManager with a SherlockMapActivity
        //LoaderManager getLoaderManager();

        void showProgress(boolean show);

        String getMapMode();

        MapModeController setMapMode(String mode, Bundle args);

        ObaMapView getMapView();

        void showStops(List<ObaStop> stops, ObaReferences refs);

        //void showBikeStations(List<BikeRentalStation> bikeStations);

        //void clearBikeStations();

        boolean setMyLocation(boolean useDefaultZoom, boolean animateToLocation);

        void notifyOutOfRange();

        // Zooms to the region bounds, if a region has been set
        void zoomToRegion();

        Location getSouthWest();

        Location getNorthEast();
    }

    /**
     * Interface used to abstract the ObaMapView class, to allow multiple implementations
     * (e.g., Google Maps API v1, v2)
     *
     * @author barbeau
     */
    interface ObaMapView {

        // Sets the current zoom level of the map
        void setZoom(float zoomLevel);

        // Returns the current center-point position of the map
        Location getMapCenterAsLocation();

        // Sets the map center, taking into account whether the overlay is expanded
        void setMapCenter(Location location, boolean animateToLocation, boolean overlayExpanded);

        // The current latitude span (from the top edge to the bottom edge of the map) in decimal degrees
        double getLatitudeSpanInDecDegrees();

        // The current longitude span (from the left edge to the right edge of the map) in decimal degrees
        double getLongitudeSpanInDecDegrees();

        // Returns the current zoom level of the map.
        float getZoomLevelAsFloat();

        // Set lines to be shown on the map view
        void setRouteOverlay(int lineOverlayColor, ObaShape[] shapes);

        void setRouteOverlay(int lineOverlayColor, ObaShape[] shapes, boolean clear);

        /**
         * Updates markers for the provided routeIds from the status info from the given
         * ObaTripsForRouteResponse
         *
         * @param response response that contains the real-time status info
         */
        void updateVehicles(ObaTripDetailsResponse response);

        // Remove the vehicles from the map
        void removeVehicleOverlay();

        // Zoom to line overlay of route
        void zoomToRoute();

        // Zoom to line overlay of itinerary
        void zoomToItinerary();

        /**
         * Zoom to include the closest vehicle from the response within the map view
         */
        void zoomIncludeClosestVehicle(ObaTripDetailsResponse response);

        // Post invalidate
        void postInvalidate();

        // Removes the route from the map
        void removeRouteOverlay();

        /**
         * Clears any stop markers from the map
         *
         * @param clearFocusedStop true to clear the currently focused stop, false to leave it on
         *                         map
         */
        void removeStopOverlay(boolean clearFocusedStop);

        // Returns true if the map is capable of watching itself, false if it needs an external watcher
        boolean canWatchMapChanges();

        /**
         * Sets focus to a particular stop, or pass in null for the stop to clear the focus
         *
         * @param stop   ObaStop to focus on, or null to clear the focus
         * @param routes a list of all route display names that serve this stop, or null to clear
         *               the focus
         */
        void setFocusStop(ObaStop stop, List<ObaRoute> routes);

        /**
         * Adds a generic marker to the map and returns the ID associated with that marker, which
         * can
         * be used to remove the marker via removeMarker()
         *
         * @param location Location at which the marker should be added
         * @param hue      The hue (color) of the marker. Value must be greater or equal to 0 and
         *                 less than 360, or null if the default color should be used.
         * @return the ID associated with the marker that was just added, or -1 if the addition
         * failed
         */
        int addMarker(Location location, Float hue);

        // Allow removal of markers, using the ID per marker generated by addMarker()
        void removeMarker(int markerId);

        /**
         * Define a visible region on the map, to signal to the map that portions of the map around
         * the edges may be obscured, by setting padding on each of the four edges of the map.
         *
         * @param left   the number of pixels of padding to be added on the left of the map, or null
         *               if the existing padding should be used
         * @param top    the number of pixels of padding to be added on the top of the map, or null
         *               if the existing padding should be used
         * @param right  the number of pixels of padding to be added on the right of the map, or null
         *               if the existing padding should be used
         * @param bottom the number of pixels of padding to be added on the bottom of the map, or null
         *               if the existing padding should be used
         */
        void setPadding(Integer left, Integer top, Integer right, Integer bottom);

        void setupVehicleOverlay();
    }

    String getMode();

    void setState(Bundle args);

    void destroy();

    void onPause();

    /**
     * This is called when fm.beginTransaction().hide() or fm.beginTransaction().show() is called
     *
     * @param hidden True if the fragment is now hidden, false if it is not visible.
     */
    void onHidden(boolean hidden);

    void onResume();

    void onSaveInstanceState(Bundle outState);

    void onViewStateRestored(Bundle savedInstanceState);

    /**
     * Called when we have the user's location,
     * or when they explicitly said to go to their location.
     */
    void onLocation();

    /**
     * Called when we don't know the user's location.
     */
    void onNoLocation();

    /**
     * For maps that can watch themselves for changes in zoom/center, this is after a change
     */
    void notifyMapChanged();

    interface RoutesDataReceivedListener {
        void onRoutesDataReceived(ObaStopsForRouteResponse response);
    }

    interface VehicleDataReceivedListener {
        void onVehicleDataReceived(ObaTripsForRouteResponse response);
    }

    interface TripDetailsDataReceivedListener {
        void onTripDetailsDataReceived(ObaTripDetailsResponse response);
    }

    interface StopDataReceivedListener {
        void onStopDataReceived(ObaStopsForLocationResponse response);
    }

    void setRoutesDataReceivedListener(RoutesDataReceivedListener listener);

    void setVehicleDataReceivedListener(VehicleDataReceivedListener listener);

    void setTripDetailsDataReceivedListener(TripDetailsDataReceivedListener listener);

    void setStopDataReceivedListener(StopDataReceivedListener listener);
}

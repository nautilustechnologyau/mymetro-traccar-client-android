/*
 * Copyright (C) 2011-2014 Paul Watts (paulcwatts@gmail.com),
 * University of South Florida (sjbarbeau@gmail.com), and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.mymetro.operator.oba.map;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.loader.app.LoaderManager;
import androidx.loader.content.AsyncTaskLoader;
import androidx.loader.content.Loader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import au.mymetro.operator.R;
import au.mymetro.operator.app.Application;
import au.mymetro.operator.oba.io.ObaApi;
import au.mymetro.operator.oba.io.elements.ObaRoute;
import au.mymetro.operator.oba.io.elements.ObaShape;
import au.mymetro.operator.oba.io.elements.ObaStop;
import au.mymetro.operator.oba.io.elements.ObaStopGroup;
import au.mymetro.operator.oba.io.elements.ObaStopGrouping;
import au.mymetro.operator.oba.io.elements.ObaTrip;
import au.mymetro.operator.oba.io.request.ObaStopsForRouteRequest;
import au.mymetro.operator.oba.io.request.ObaStopsForRouteResponse;
import au.mymetro.operator.oba.io.request.ObaTripDetailsRequest;
import au.mymetro.operator.oba.io.request.ObaTripDetailsResponse;
import au.mymetro.operator.oba.io.request.ObaTripsForRouteRequest;
import au.mymetro.operator.oba.io.request.ObaTripsForRouteResponse;
import au.mymetro.operator.oba.map.googlemapsv2.BaseMapFragment;
import au.mymetro.operator.oba.util.LocationUtils;
import au.mymetro.operator.oba.util.UIUtils;

public class RouteMapController implements MapModeController {

    private static final String TAG = "RouteMapController";

    private static final int ROUTES_LOADER = 5677;

    private static final int VEHICLES_LOADER = 5678;

    private final Callback mFragment;

    private String mRouteId;

    private String mTripId;

    private boolean mShowStop;

    private boolean mZoomToRoute;

    private boolean mZoomIncludeClosestVehicle;

    private int mLineOverlayColor;

    private RoutePopup mRoutePopup;

    private int mShortAnimationDuration;

    // In lieu of using an actual LoaderManager, which isn't
    // available in SherlockMapActivity
    private Loader<ObaStopsForRouteResponse> mRouteLoader;

    private RouteLoaderListener mRouteLoaderListener;

    private Loader<ObaTripsForRouteResponse> mVehiclesLoader;

    private VehicleLoaderListener mVehicleLoaderListener;

    private Loader<ObaTripDetailsResponse> mTripDetailsLoader;

    private TripDetailsLoaderListener mTripDetailsLoaderListener;

    private long mLastUpdatedTimeVehicles;

    private long mLastUpdatedTimeTripDetails;

    private ObaStopsForRouteResponse mStopsForRoutesResponse;

    private ObaTripDetailsResponse mTripDetailsResponse;

    private boolean mStopsPopulated = false;

    private RoutesDataReceivedListener mRoutesDataReceivedListener;

    private TripDetailsDataReceivedListener mTripDetailsDataReceivedListener;

    private VehicleDataReceivedListener mVehicleDataReceivedListener;

    public RouteMapController(Callback callback) {
        mFragment = callback;
        mLineOverlayColor = mFragment.getActivity()
                .getResources()
                .getColor(R.color.route_line_color_default);
        mShortAnimationDuration = mFragment.getActivity().getResources().getInteger(
                android.R.integer.config_shortAnimTime);
        mRoutePopup = new RoutePopup();
        mRouteLoaderListener = new RouteLoaderListener();
        mVehicleLoaderListener = new VehicleLoaderListener();
        mTripDetailsLoaderListener = new TripDetailsLoaderListener();
    }

    @Override
    public void setState(Bundle args) {
        if (args == null) {
            throw new IllegalArgumentException("args cannot be null");
        }
        String routeId = args.getString(MapParams.ROUTE_ID);
        String tripId = args.getString(MapParams.TRIP_ID);

        mShowStop = args.getBoolean(MapParams.SHOW_STOP, true);
        if (!mShowStop) {
            mFragment.getMapView().removeStopOverlay(true);
        }

        // If the previous map zoom isn't the default, then zoom to that level as a start
        float mapZoom = args.getFloat(MapParams.ZOOM, MapParams.DEFAULT_ZOOM);
        if (mapZoom != MapParams.DEFAULT_ZOOM) {
            mFragment.getMapView().setZoom(mapZoom);
        }

        mZoomToRoute = args.getBoolean(MapParams.ZOOM_TO_ROUTE, false);
        mZoomIncludeClosestVehicle = args
                .getBoolean(MapParams.ZOOM_INCLUDE_CLOSEST_VEHICLE, false);
        if (!routeId.equals(mRouteId)) {
            if (mRouteId != null) {
                clearCurrentState();
            }

            // Set up the new route
            mRouteId = routeId;
            mTripId = tripId;
            mRoutePopup.showLoading();
            mFragment.showProgress(true);

            //mFragment.getLoaderManager().restartLoader(ROUTES_LOADER, null, this);
            mRouteLoader = mRouteLoaderListener.onCreateLoader(ROUTES_LOADER, null);
            mRouteLoader.registerListener(0, mRouteLoaderListener);
            mRouteLoader.startLoading();

            mVehiclesLoader = mVehicleLoaderListener.onCreateLoader(VEHICLES_LOADER, null);
            mVehiclesLoader.registerListener(0, mVehicleLoaderListener);
            //mVehiclesLoader.startLoading();

            mTripDetailsLoader = mTripDetailsLoaderListener.onCreateLoader(VEHICLES_LOADER, null);
            mTripDetailsLoader.registerListener(0, mTripDetailsLoaderListener);
            //mTripDetailsLoader.startLoading();
        } else {
            // We are returning to the route view with the route already set, so show the header
            mRoutePopup.show();
        }
    }

    /**
     * Clears the current state of the controller, so a new route can be loaded
     */
    private void clearCurrentState() {
        // Stop loaders and refresh handler
        mRouteLoader.stopLoading();
        mRouteLoader.reset();
        mVehiclesLoader.stopLoading();
        mVehiclesLoader.reset();
        mTripDetailsLoader.reset();
        mVehicleRefreshHandler.removeCallbacks(mVehicleRefresh);
        mTripDetailsRefreshHandler.removeCallbacks(mTripDetailsRefresh);

        // Clear the existing route and vehicle overlays
        mFragment.getMapView().removeRouteOverlay();
        mFragment.getMapView().removeVehicleOverlay();

        // Clear the existing stop icons, but leave the currently focused stop
        mFragment.getMapView().removeStopOverlay(false);
    }

    @Override
    public String getMode() {
        return MapParams.MODE_ROUTE;
    }

    @Override
    public void destroy() {
        mRoutePopup.hide();
        mFragment.getMapView().removeRouteOverlay();
        mVehicleRefreshHandler.removeCallbacks(mVehicleRefresh);
        mTripDetailsRefreshHandler.removeCallbacks(mTripDetailsRefresh);
        mFragment.getMapView().removeVehicleOverlay();
    }

    @Override
    public void onPause() {
        mVehicleRefreshHandler.removeCallbacks(mVehicleRefresh);
        mTripDetailsRefreshHandler.removeCallbacks(mTripDetailsRefresh);
    }

    /**
     * This is called when fm.beginTransaction().hide() or fm.beginTransaction().show() is called
     *
     * @param hidden True if the fragment is now hidden, false if it is not visible.
     */
    @Override
    public void onHidden(boolean hidden) {
        // If the fragment is no longer visible, hide the route header - otherwise, show it
        if (hidden) {
            mRoutePopup.hide();
        } else {
            mRoutePopup.show();
        }
    }

    @Override
    public void onResume() {
        // scheduleVehicleRefresh();
        scheduleTripDetailsRefresh();
    }

    private void scheduleVehicleRefresh() {
        // Make sure we schedule a future update for vehicles
        mVehicleRefreshHandler.removeCallbacks(mVehicleRefresh);

        if (mLastUpdatedTimeVehicles == 0) {
            // We haven't loaded any vehicles yet - schedule the refresh for the full period and defer
            // to the loader to reschedule when load is complete
            mVehicleRefreshHandler.postDelayed(mVehicleRefresh, VEHICLE_REFRESH_PERIOD);
            return;
        }

        long elapsedTimeMillis = TimeUnit.NANOSECONDS.toMillis(UIUtils.getCurrentTimeForComparison()
                - mLastUpdatedTimeVehicles);
        long refreshPeriod;
        if (elapsedTimeMillis > VEHICLE_REFRESH_PERIOD) {
            // Schedule an immediate update, if we're past the normal period after a load
            refreshPeriod = 100;
        } else {
            // Schedule an update so a total of VEHICLE_REFRESH_PERIOD has elapsed since the last update
            refreshPeriod = VEHICLE_REFRESH_PERIOD - elapsedTimeMillis;
        }
        mVehicleRefreshHandler.postDelayed(mVehicleRefresh, refreshPeriod);
    }

    private void scheduleTripDetailsRefresh() {
        // Make sure we schedule a future update for vehicles
        mTripDetailsRefreshHandler.removeCallbacks(mVehicleRefresh);

        if (mLastUpdatedTimeTripDetails == 0) {
            // We haven't loaded any vehicles yet - schedule the refresh for the full period and defer
            // to the loader to reschedule when load is complete
            mTripDetailsRefreshHandler.postDelayed(mTripDetailsRefresh, TRIP_REFRESH_PERIOD);
            return;
        }

        long elapsedTimeMillis = TimeUnit.NANOSECONDS.toMillis(UIUtils.getCurrentTimeForComparison()
                - mLastUpdatedTimeTripDetails);
        long refreshPeriod;
        if (elapsedTimeMillis > TRIP_REFRESH_PERIOD) {
            // Schedule an immediate update, if we're past the normal period after a load
            refreshPeriod = 100;
        } else {
            // Schedule an update so a total of VEHICLE_REFRESH_PERIOD has elapsed since the last update
            refreshPeriod = TRIP_REFRESH_PERIOD - elapsedTimeMillis;
        }
        mTripDetailsRefreshHandler.postDelayed(mTripDetailsRefresh, refreshPeriod);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(MapParams.ROUTE_ID, mRouteId);
        outState.putBoolean(MapParams.ZOOM_TO_ROUTE, mZoomToRoute);
        outState.putBoolean(MapParams.ZOOM_INCLUDE_CLOSEST_VEHICLE, mZoomIncludeClosestVehicle);
        outState.putBoolean(MapParams.SHOW_STOP, true);
        outState.putString(MapParams.TRIP_ID, mTripId);

        Location centerLocation = mFragment.getMapView().getMapCenterAsLocation();
        outState.putDouble(MapParams.CENTER_LAT, centerLocation.getLatitude());
        outState.putDouble(MapParams.CENTER_LON, centerLocation.getLongitude());
        outState.putFloat(MapParams.ZOOM, mFragment.getMapView().getZoomLevelAsFloat());
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return;
        }

        String stopId = savedInstanceState.getString(MapParams.STOP_ID);
        if (stopId == null) {
            // If there is no focused stop then restore the map state otherwise
            // let the BaseMapFragment to handle map state with focused stop

            float mapZoom = savedInstanceState.getFloat(MapParams.ZOOM, MapParams.DEFAULT_ZOOM);
            if (mapZoom != MapParams.DEFAULT_ZOOM) {
                mFragment.getMapView().setZoom(mapZoom);
            }

            double lat = savedInstanceState.getDouble(MapParams.CENTER_LAT);
            double lon = savedInstanceState.getDouble(MapParams.CENTER_LON);
            if (lat != 0.0d && lon != 0.0d) {
                Location location = LocationUtils.makeLocation(lat, lon);
                mFragment.getMapView().setMapCenter(location, false, false);
            }
        }
    }

    @Override
    public void onLocation() {
        // Don't care
    }

    @Override
    public void onNoLocation() {
        // Don't care
    }

    @Override
    public void notifyMapChanged() {
        // Don't care
    }

    @Override
    public void setRoutesDataReceivedListener(RoutesDataReceivedListener listener) {
        mRoutesDataReceivedListener = listener;
    }

    @Override
    public void setVehicleDataReceivedListener(VehicleDataReceivedListener listener) {
        mVehicleDataReceivedListener = listener;
    }

    @Override
    public void setTripDetailsDataReceivedListener(TripDetailsDataReceivedListener listener) {
        mTripDetailsDataReceivedListener = listener;
    }

    @Override
    public void setStopDataReceivedListener(StopDataReceivedListener listener) {
    }

    //
    // Map popup
    //
    private class RoutePopup {

        private final Activity mActivity;

        private final View mView;

        private final TextView mRouteShortName;

        private final TextView mRouteLongName;

        private final TextView mAgencyName;

        private final ProgressBar mProgressBar;

        // Prevents completely hiding vehicle markers at top of route
        private int VEHICLE_MARKER_PADDING;

        RoutePopup() {
            mActivity = mFragment.getActivity();
            float paddingDp =
                    mActivity.getResources().getDimension(R.dimen.map_route_vehicle_markers_padding)
                            / mActivity.getResources().getDisplayMetrics().density;
            VEHICLE_MARKER_PADDING = UIUtils.dpToPixels(mActivity, paddingDp);
            mView = mActivity.findViewById(R.id.route_info);
            mFragment.getMapView()
                    .setPadding(null, mView.getHeight() + VEHICLE_MARKER_PADDING, null, null);
            mRouteShortName = (TextView) mView.findViewById(R.id.short_name);
            mRouteLongName = (TextView) mView.findViewById(R.id.long_name);
            mAgencyName = (TextView) mView.findViewById(R.id.agency);
            mProgressBar = (ProgressBar) mView.findViewById(R.id.route_info_loading_spinner);

            // Make sure the cancel button is shown
            View cancel = mView.findViewById(R.id.cancel_route_mode);
            cancel.setVisibility(View.VISIBLE);
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ObaMapView obaMapView = mFragment.getMapView();
                    // We want to preserve the current zoom and center.
                    Bundle bundle = new Bundle();
                    bundle.putBoolean(MapParams.DO_N0T_CENTER_ON_LOCATION, true);
                    bundle.putFloat(MapParams.ZOOM, obaMapView.getZoomLevelAsFloat());
                    Location point = obaMapView.getMapCenterAsLocation();
                    bundle.putDouble(MapParams.CENTER_LAT, point.getLatitude());
                    bundle.putDouble(MapParams.CENTER_LON, point.getLongitude());
                    mFragment.setMapMode(MapParams.MODE_STOP, bundle);
                }
            });
        }

        void showLoading() {
            mFragment.getMapView()
                    .setPadding(null, mView.getHeight() + VEHICLE_MARKER_PADDING, null, null);
            UIUtils.hideViewWithoutAnimation(mRouteShortName);
            UIUtils.hideViewWithoutAnimation(mRouteLongName);
            UIUtils.showViewWithoutAnimation(mView);
            UIUtils.showViewWithoutAnimation(mProgressBar);
        }

        /**
         * Show the route header and populate it with the provided information
         * @param route route information to show in the header
         * @param agencyName agency name to show in the header
         */
        void show(ObaRoute route, String agencyName) {
            mRouteShortName.setText(UIUtils.formatDisplayText(UIUtils.getRouteDisplayName(route)));
            mRouteLongName.setText(UIUtils.formatDisplayText(UIUtils.getRouteDescription(route)));
            mAgencyName.setText(agencyName);
            show();
        }

        /**
         * Show the route header with the existing route information
         */
        void show() {
            UIUtils.hideViewWithAnimation(mProgressBar, mShortAnimationDuration);
            UIUtils.showViewWithAnimation(mRouteShortName, mShortAnimationDuration);
            UIUtils.showViewWithAnimation(mRouteLongName, mShortAnimationDuration);
            UIUtils.showViewWithAnimation(mView, mShortAnimationDuration);
            mFragment.getMapView()
                    .setPadding(null, mView.getHeight() + VEHICLE_MARKER_PADDING, null, null);
        }

        void hide() {
            mFragment.getMapView().setPadding(null, 0, null, null);
            UIUtils.hideViewWithAnimation(mView, mShortAnimationDuration);
        }
    }

    private static final long VEHICLE_REFRESH_PERIOD = TimeUnit.SECONDS.toMillis(15);

    private static final long TRIP_REFRESH_PERIOD = TimeUnit.SECONDS.toMillis(15);

    private final Handler mVehicleRefreshHandler = new Handler();

    private final Handler mTripDetailsRefreshHandler = new Handler();

    private final Runnable mVehicleRefresh = new Runnable() {
        public void run() {
            refreshVehicles();
        }
    };

    private final Runnable mTripDetailsRefresh = new Runnable() {
        public void run() {
            refreshTripDetails();
        }
    };

    /**
     * Refresh vehicle data from the OBA server
     */
    private void refreshVehicles() {
        if (mVehiclesLoader != null) {
            mVehiclesLoader.onContentChanged();
        }
    }

    /**
     * Refresh vehicle data from the OBA server
     */
    private void refreshTripDetails() {
        if (mTripDetailsLoader != null) {
            mTripDetailsLoader.onContentChanged();
        }
    }

    //
    // Loaders
    //

    private static class RoutesLoader extends AsyncTaskLoader<ObaStopsForRouteResponse> {

        private final String mRouteId;

        public RoutesLoader(Context context, String routeId) {
            super(context);
            mRouteId = routeId;
        }

        @Override
        public ObaStopsForRouteResponse loadInBackground() {
            if (Application.get().getCurrentRegion() == null &&
                    TextUtils.isEmpty(Application.get().getCustomApiUrl())) {
                //We don't have region info or manually entered API to know what server to contact
                Log.d(TAG, "Trying to load stops for route from server " +
                            "without OBA REST API endpoint, aborting...");
                return null;
            }
            //Make OBA REST API call to the server and return result
            return new ObaStopsForRouteRequest.Builder(getContext(), mRouteId)
                    .setIncludeShapes(true)
                    .build()
                    .call();
        }

        @Override
        public void deliverResult(ObaStopsForRouteResponse data) {
            //mResponse = data;
            super.deliverResult(data);
        }

        @Override
        public void onStartLoading() {
            forceLoad();
        }
    }

    class RouteLoaderListener implements LoaderManager.LoaderCallbacks<ObaStopsForRouteResponse>,
            Loader.OnLoadCompleteListener<ObaStopsForRouteResponse> {

        @Override
        public Loader<ObaStopsForRouteResponse> onCreateLoader(int id,
                Bundle args) {
            return new RoutesLoader(mFragment.getActivity(), mRouteId);
        }

        @Override
        public void onLoadFinished(Loader<ObaStopsForRouteResponse> loader,
                ObaStopsForRouteResponse response) {

            if (response == null || response.getCode() != ObaApi.OBA_OK) {
                BaseMapFragment.showMapError(response);
                return;
            }

            mStopsForRoutesResponse = response;

            ObaMapView obaMapView = mFragment.getMapView();

            ObaRoute route = response.getRoute(response.getRouteId());

            mRoutePopup.show(route, response.getAgency(route.getAgencyId()).getName());

            mFragment.getMapView().removeVehicleOverlay();
            mFragment.getMapView().removeRouteOverlay();
            //mFragment.getMapView().removeStopOverlay(true);


            if (route.getColor() != null) {
                mLineOverlayColor = route.getColor();
            }

            obaMapView.setRouteOverlay(mLineOverlayColor, response.getShapes());

            // Set the stops for this route
            List<ObaStop> stops = response.getStops();

            //mFragment.showStops(stops, response);
            //mFragment.getMapView().removeStopOverlay(true);

            mFragment.showProgress(true);

            if (mZoomToRoute) {
                obaMapView.zoomToRoute();
                mZoomToRoute = false;
            }

            //
            // wait to zoom till we have the right response
            obaMapView.postInvalidate();

            if (mRoutesDataReceivedListener != null) {
                mRoutesDataReceivedListener.onRoutesDataReceived(response);
            }

            mTripDetailsLoader.startLoading();

            // mVehiclesLoader.startLoading();
        }

        @Override
        public void onLoaderReset(Loader<ObaStopsForRouteResponse> loader) {
            mFragment.getMapView().removeRouteOverlay();
            mFragment.getMapView().removeVehicleOverlay();
        }

        @Override
        public void onLoadComplete(Loader<ObaStopsForRouteResponse> loader,
                ObaStopsForRouteResponse response) {
            onLoadFinished(loader, response);
        }
    }

    private static class VehiclesLoader extends AsyncTaskLoader<ObaTripsForRouteResponse> {

        private final String mRouteId;

        public VehiclesLoader(Context context, String routeId) {
            super(context);
            mRouteId = routeId;
        }

        @Override
        public ObaTripsForRouteResponse loadInBackground() {
            if (Application.get().getCurrentRegion() == null &&
                    TextUtils.isEmpty(Application.get().getCustomApiUrl())) {
                //We don't have region info or manually entered API to know what server to contact
                Log.d(TAG, "Trying to load trips (vehicles) for route from server " +
                        "without OBA REST API endpoint, aborting...");
                return null;
            }
            //Make OBA REST API call to the server and return result
            return new ObaTripsForRouteRequest.Builder(getContext(), mRouteId)
                    .setIncludeStatus(true)
                    .build()
                    .call();
        }

        @Override
        public void deliverResult(ObaTripsForRouteResponse data) {
            super.deliverResult(data);
        }

        @Override
        public void onStartLoading() {
            forceLoad();
        }
    }

    class VehicleLoaderListener implements LoaderManager.LoaderCallbacks<ObaTripsForRouteResponse>,
            Loader.OnLoadCompleteListener<ObaTripsForRouteResponse> {

        HashSet<String> routes = new HashSet<>(1);

        @Override
        public Loader<ObaTripsForRouteResponse> onCreateLoader(int id,
                Bundle args) {
            return new VehiclesLoader(mFragment.getActivity(), mRouteId);
        }

        @Override
        public void onLoadFinished(Loader<ObaTripsForRouteResponse> loader,
                ObaTripsForRouteResponse response) {

            if (response == null || response.getCode() != ObaApi.OBA_OK) {
                BaseMapFragment.showMapError(response);
                return;
            }

            ObaMapView obaMapView = mFragment.getMapView();

            /*routes.clear();
            routes.add(mRouteId);

            if (mTripId != null && mStopsForRoutesResponse != null && mShowStop) {
                ObaTripDetails trip = null;
                for (ObaTripDetails tripDetails : response.getTrips()) {
                    if (mTripId.equals(tripDetails.getId())) {
                        trip = tripDetails;
                        break;
                    }
                }

                if (trip != null) {
                    String stopId = trip.getStatus().getNextStop();
                    String[] stopIds = {stopId};
                    List<ObaStop> stops = response.getStops(stopIds);
                    if (stops != null) {
                        mFragment.showStops(stops, response);
                    }
                }
            }*/

            ///////

            // ObaMapView obaMapView = mFragment.getMapView();

            if (mStopsForRoutesResponse != null) {
                ObaRoute route = mStopsForRoutesResponse.getRoute(mRouteId);

                if (route.getColor() != null) {
                    mLineOverlayColor = route.getColor();
                }

                ObaShape[] shapes = null;
                String[] stopIds = null;
                if (mTripId != null) {
                    ObaTrip trip = response.getTrip(mTripId);
                    if (trip != null) {
                        ObaStopGrouping[] stopGroupings = mStopsForRoutesResponse.getStopGroupings();
                        if (stopGroupings != null && stopGroupings.length > 0) {
                            for (ObaStopGrouping stopGrouping : stopGroupings) {
                                if (shapes != null || stopIds != null) {
                                    break;
                                }
                                ObaStopGroup[] stopGroups = stopGrouping.getStopGroups();
                                if (stopGroups != null && stopGroups.length > 0) {
                                    for (int i = 0; i < stopGroups.length; i++) {
                                        ObaStopGroup stopGroup = stopGroups[i];
                                        if (String.valueOf(trip.getDirectionId()).equals(stopGroup.getId())) {
                                            shapes = stopGroup.getShapes();
                                            stopIds = stopGroup.getStopIds();
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (shapes != null) {
                        obaMapView.setRouteOverlay(mLineOverlayColor, shapes);
                    } else {
                        obaMapView.setRouteOverlay(mLineOverlayColor, mStopsForRoutesResponse.getShapes());
                    }
                } else {
                    obaMapView.setRouteOverlay(mLineOverlayColor, mStopsForRoutesResponse.getShapes());
                }

                // Set the stops for this route

                List<ObaStop> stops = new ArrayList<>();

                if (stopIds != null) {
                    for (String stopId : stopIds) {
                        ObaStop stop = mStopsForRoutesResponse.getStop(stopId);
                        if (stop != null) {
                            stops.add(stop);
                        }
                    }
                } else {
                    stops = mStopsForRoutesResponse.getStops();
                }

                mFragment.showStops(stops, response);

                mFragment.showProgress(true);

                if (mZoomToRoute) {
                    obaMapView.zoomToRoute();
                    mZoomToRoute = false;
                }
                //
                // wait to zoom till we have the right response
                obaMapView.postInvalidate();
            }

            mVehicleRefreshHandler.removeCallbacks(mVehicleRefresh);

            //////

            // obaMapView.updateVehicles(routes, response, mTripId);

            /*if (mZoomIncludeClosestVehicle) {
                // obaMapView.zoomIncludeClosestVehicle(routes, response);
                mZoomIncludeClosestVehicle = false;
            }

            mLastUpdatedTimeVehicles = UIUtils.getCurrentTimeForComparison();

            // Clear any pending refreshes
            mVehicleRefreshHandler.removeCallbacks(mVehicleRefresh);

            // Post an update
            // mVehicleRefreshHandler.postDelayed(mVehicleRefresh, VEHICLE_REFRESH_PERIOD);

            if (mOnVehicleDataReceivedListener != null) {
                mOnVehicleDataReceivedListener.onVehicleDataReceived(response);
            }*/
        }

        @Override
        public void onLoaderReset(Loader<ObaTripsForRouteResponse> loader) {
            mFragment.getMapView().removeVehicleOverlay();
            mFragment.getMapView().removeRouteOverlay();
        }

        @Override
        public void onLoadComplete(Loader<ObaTripsForRouteResponse> loader,
                ObaTripsForRouteResponse response) {
            onLoadFinished(loader, response);
        }
    }

    private static class TripDetailsLoader extends AsyncTaskLoader<ObaTripDetailsResponse> {

        private final String mTripId;

        public TripDetailsLoader(Context context, String tripId) {
            super(context);
            mTripId = tripId;
        }

        @Override
        public ObaTripDetailsResponse loadInBackground() {
            if (Application.get().getCurrentRegion() == null &&
                    TextUtils.isEmpty(Application.get().getCustomApiUrl())) {
                //We don't have region info or manually entered API to know what server to contact
                Log.d(TAG, "Trying to load stops for route from server " +
                        "without OBA REST API endpoint, aborting...");
                return null;
            }
            //Make OBA REST API call to the server and return result
            return new ObaTripDetailsRequest.Builder(getContext(), mTripId)
                    .setIncludeStatus(true)
                    .setIncludeTrip(true)
                    .setIncludeSchedule(true)
                    .build()
                    .call();
        }

        @Override
        public void deliverResult(ObaTripDetailsResponse data) {
            //mResponse = data;
            super.deliverResult(data);
        }

        @Override
        public void onStartLoading() {
            forceLoad();
        }
    }

    class TripDetailsLoaderListener implements LoaderManager.LoaderCallbacks<ObaTripDetailsResponse>,
            Loader.OnLoadCompleteListener<ObaTripDetailsResponse> {

        @Override
        public Loader<ObaTripDetailsResponse> onCreateLoader(int id,
                                                             Bundle args) {
            return new TripDetailsLoader(mFragment.getActivity(), mTripId);
        }

        @Override
        public void onLoadFinished(Loader<ObaTripDetailsResponse> loader,
                                   ObaTripDetailsResponse response) {

            mTripDetailsResponse = response;

            if (response == null || response.getCode() != ObaApi.OBA_OK) {
                BaseMapFragment.showMapError(response);
                return;
            }

            if (!mStopsPopulated) {
                mFragment.getMapView().removeStopOverlay(true);
                mFragment.showStops(response.getStops(), response);
                mStopsPopulated = true;
            }

            ObaMapView obaMapView = mFragment.getMapView();
            obaMapView.updateVehicles(null);
            //obaMapView.setupVehicleOverlay();

            if (mZoomIncludeClosestVehicle) {
                obaMapView.zoomIncludeClosestVehicle(response);
                mZoomIncludeClosestVehicle = false;
            }

            mLastUpdatedTimeTripDetails = UIUtils.getCurrentTimeForComparison();

            // Clear any pending refreshes
            mTripDetailsRefreshHandler.removeCallbacks(mTripDetailsRefresh);


            if (mTripDetailsDataReceivedListener != null) {
                mTripDetailsDataReceivedListener.onTripDetailsDataReceived(response);
            }

            // Post an update
            // mTripDetailsRefreshHandler.postDelayed(mTripDetailsRefresh, TRIP_REFRESH_PERIOD);

        }

        @Override
        public void onLoaderReset(Loader<ObaTripDetailsResponse> loader) {
            mFragment.getMapView().removeRouteOverlay();
            mFragment.getMapView().removeVehicleOverlay();
        }

        @Override
        public void onLoadComplete(Loader<ObaTripDetailsResponse> loader,
                                   ObaTripDetailsResponse response) {
            onLoadFinished(loader, response);
        }
    }

}
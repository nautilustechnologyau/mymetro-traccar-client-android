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

package au.mymetro.operator.ui.home;

import static au.mymetro.operator.oba.util.PermissionUtils.LOCATION_PERMISSIONS;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.location.Location;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.onebusaway.transit_data_federation.impl.shapes.PointAndIndex;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import au.mymetro.operator.R;
import au.mymetro.operator.TripStatusUtils;
import au.mymetro.operator.app.Application;
import au.mymetro.operator.databinding.FragmentHomeBinding;
import au.mymetro.operator.oba.io.elements.ObaArrivalInfo;
import au.mymetro.operator.oba.io.elements.ObaRegion;
import au.mymetro.operator.oba.io.elements.ObaRoute;
import au.mymetro.operator.oba.io.elements.ObaStop;
import au.mymetro.operator.oba.io.elements.ObaTrip;
import au.mymetro.operator.oba.io.elements.ObaTripSchedule;
import au.mymetro.operator.oba.io.elements.ObaTripStatus;
import au.mymetro.operator.oba.io.request.ObaArrivalInfoResponse;
import au.mymetro.operator.oba.io.request.ObaStopsForLocationResponse;
import au.mymetro.operator.oba.io.request.ObaStopsForRouteResponse;
import au.mymetro.operator.oba.io.request.ObaTripDetailsResponse;
import au.mymetro.operator.oba.map.MapModeController;
import au.mymetro.operator.oba.map.MapParams;
import au.mymetro.operator.oba.map.googlemapsv2.BaseMapFragment;
import au.mymetro.operator.oba.ui.ArrivalInfo;
import au.mymetro.operator.oba.ui.SimpleArrivalListFragment;
import au.mymetro.operator.oba.ui.TripDetailsActivity;
import au.mymetro.operator.oba.ui.TripDetailsListFragment;
import au.mymetro.operator.oba.util.ArrivalInfoUtils;
import au.mymetro.operator.oba.util.LocationUtils;
import au.mymetro.operator.oba.util.PermissionUtils;
import au.mymetro.operator.oba.util.UIUtils;

public class HomeFragment extends Fragment
        implements BaseMapFragment.OnFocusChangedListener,
        BaseMapFragment.LocationChangedListener,
        SimpleArrivalListFragment.Callback,
        MapModeController.RoutesDataReceivedListener,
        MapModeController.TripDetailsDataReceivedListener,
        MapModeController.StopDataReceivedListener,
        BaseMapFragment.MapInitCompletedListener,
        TripStatusUtils.TripStatusComputedListener,
        TripStatusUtils.CloseToStopListener {

    public static String TAG = "HomeFragment";

    private FragmentHomeBinding binding;

    private BaseMapFragment mMapFragment;

    private ProgressDialog mProgressDialog;

    private String mCurrentMapMode = MapParams.MODE_STOP;

    private HomeViewModel homeViewModel;

    private Bundle mSavedInstanceState;

    private TripStatusUtils mTripStatusUtil;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        mSavedInstanceState = savedInstanceState;
        binding = FragmentHomeBinding.inflate(inflater, container, false);

        binding.tripScheduleView.setOnClickListener(this::onTripScheduleViewClicked);

        View root = binding.getRoot();

        homeViewModel = new ViewModelProvider(requireActivity()).get(HomeViewModel.class);
        homeViewModel.getMapMode().observe(getViewLifecycleOwner(), this::onMapModeChange);
        homeViewModel.getStop().observe(getViewLifecycleOwner(), this::onStopChange);

        mMapFragment = null;

        if (PermissionUtils.hasGrantedAtLeastOnePermission(requireActivity(), LOCATION_PERMISSIONS)) {
            setupMapFragment();
        }

        updateStopInfoHeaders(homeViewModel.getArrivalInfo().getValue());
        selectStopTrip();
        return root;
    }

    @Override
    public void onStart() {
        super.onStart();
        updateFragmentHeader();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        mMapFragment = null;
    }

    @Override
    public void onStopFocusChanged(ObaStop stop, HashMap<String, ObaRoute> routes, Location location) {
        // if we are in trip mode, ignore this change
        if (!MapParams.MODE_STOP.equalsIgnoreCase(homeViewModel.getMapMode().getValue())) {
            return;
        }

        homeViewModel.setStop(stop);
        homeViewModel.setArrivalInfo(null);
        homeViewModel.setTripId(null);
    }

    @Override
    public void onArrivalDataReceived(ObaArrivalInfoResponse response) {
        if (mProgressDialog != null) {
            mProgressDialog.hide();
        }
        if (!UIUtils.canManageDialog(getActivity())) {
            return;
        }

        if (response == null || response.getArrivalInfo() == null || response.getArrivalInfo().length < 1) {
            /*Snackbar snackbar = Snackbar.make(requireActivity().findViewById(R.id.home_layout),
                    R.string.ri_no_trip,
                    Snackbar.LENGTH_INDEFINITE);
            snackbar.setAction("DISMISS", v -> {});
            snackbar.show();*/
            Toast.makeText(requireActivity(), R.string.ri_no_trip, Toast.LENGTH_SHORT).show();
        }
    }

    private void onTripScheduleViewClicked(View view) {
        if (!MapParams.MODE_ROUTE.equals(homeViewModel.getMapMode().getValue())) {
            return;
        }

        TripDetailsActivity.start(requireActivity(),
                homeViewModel.getTripId().getValue(),
                TripDetailsListFragment.SCROLL_MODE_VEHICLE);
    }


    @Override
    public void onArrivalItemClicked(ObaArrivalInfo obaArrivalInfo, ArrivalInfo stopInfo, String agencyName, String blockId, View view) {
        ObaArrivalInfo prevArrivalInfo = homeViewModel.getArrivalInfo().getValue();

        // check if same trip is clicked again
        // if same trip is clicked, we will deselect it
        if (prevArrivalInfo != null && obaArrivalInfo != null) {
            if (prevArrivalInfo.getTripId().equals(obaArrivalInfo.getTripId())) {
                unselectTrip(view);
                return;
            }
        }

        ObaTripStatus tripStatus = obaArrivalInfo.getTripStatus();
        boolean showAlertDialog = false;
        String alertHeader = "Are you sure you want to select the trip?";
        String alertItems = "";

        if (stopInfo.getEta() > 5) {
            showAlertDialog = true;
            alertItems = alertItems + "- " + getActivity().getString(R.string.msg_trip_too_early, stopInfo.getEta()) + "\n";
        }

        if (UIUtils.isLocationRealtime(tripStatus)) {
            showAlertDialog = true;
            alertItems = alertItems + "- " + getActivity().getString(R.string.msg_trip_ongoing) + "\n";
        }

        if (showAlertDialog) {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
            builder.setTitle(R.string.msg_trip_title);
            builder.setIcon(R.drawable.ic_alert);
            builder.setMessage(alertItems + "\n" + alertHeader);
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    selectTrip(obaArrivalInfo, agencyName, blockId, view);
                }
            });
            builder.setNegativeButton(android.R.string.no, null);
            builder.show();
        } else {
            selectTrip(obaArrivalInfo, agencyName, blockId, view);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (!MapParams.MODE_ROUTE.equals(homeViewModel.getMapMode().getValue())) {
            return;
        }

        if (location == null) {
            return;
        }

        updateNextStopInfo(location);
    }

    @Override
    public void onRoutesDataReceived(ObaStopsForRouteResponse response) {
    }

    @Override
    public void onStopDataReceived(ObaStopsForLocationResponse response) {
        List<ObaStop> stops = Arrays.asList(response.getStops());

        ObaStop stop = homeViewModel.getStop().getValue();
        if (stop != null && mMapFragment != null && mMapFragment.getFocusStop() == null) {
            mMapFragment.doFocusChange(stop);
        }
    }

    @Override
    public void onTripDetailsDataReceived(ObaTripDetailsResponse response) {
        Log.d(TAG, "Trip details data received: " + response);

        if (!MapParams.MODE_ROUTE.equals(homeViewModel.getMapMode().getValue())) {
            return;
        }
        homeViewModel.setResponse(response);

        if (response == null) {
            endTrip();
            return;
        }

        ObaTripStatus tripStatus = response.getStatus();
        if (tripStatus == null) {
            return;
        }

        ObaTripSchedule schedule = response.getSchedule();
        if (schedule == null) {
            endTrip();
            return;
        }

        ObaTripSchedule.StopTime[] stopTimes = schedule.getStopTimes();
        if (stopTimes == null || stopTimes.length < 1) {
            endTrip();
            return;
        }

        mTripStatusUtil = new TripStatusUtils(homeViewModel.getStop().getValue(), response, this, this);
    }

    void updateNextStopLocation() {
        if (mTripStatusUtil == null) {
            return;
        }

        if (mTripStatusUtil.hasTripEnded()) {
            endTrip();
            return;
        }

        ObaTrip trip = mTripStatusUtil.getResponse().getTrip(homeViewModel.getTripId().getValue());
        if (trip == null) {
            return;
        }

        ObaRoute route = mTripStatusUtil.getResponse().getRoute(homeViewModel.getRouteId().getValue());
        if (route == null) {
            return;
        }

        ObaStop nextStop = mTripStatusUtil.getNextStop();

        ObaTripStatus tripStatus = mTripStatusUtil.getTripStatus();
        long deviation = tripStatus.getScheduleDeviation();
        long date = tripStatus.getServiceDate();
        String timeScheduled = DateUtils.formatDateTime(getActivity(),
                date + mTripStatusUtil.getNextStopTime().getArrivalTime() * 1000,
                DateUtils.FORMAT_SHOW_TIME |
                        DateUtils.FORMAT_NO_NOON |
                        DateUtils.FORMAT_NO_MIDNIGHT);

        String timeEstimated = DateUtils.formatDateTime(getActivity(),
                date + deviation * 1000 + mTripStatusUtil.getNextStopTime().getArrivalTime() * 1000,
                DateUtils.FORMAT_SHOW_TIME |
                        DateUtils.FORMAT_NO_NOON |
                        DateUtils.FORMAT_NO_MIDNIGHT);

        binding.tripInfoRoute.setText(UIUtils.getRouteDisplayName(route));
        binding.tripInfoHeadsign.setText(trip.getHeadsign());
        binding.tripInfoNextStopName.setText(nextStop.getName());
        binding.tripInfoNextStopDirection.setText(UIUtils.getStopDirectionText(nextStop.getDirection()));
        binding.tripInfoArrivalTimeScheduled.setText(timeScheduled);
        binding.tripInfoArrivalTimeEstimated.setText(timeEstimated);
        binding.tripInfoNextStopDistance.setText(mTripStatusUtil.getDistanceToNextStop() + " m");

        String speed = String.format(Locale.getDefault(), "%d", (int)mTripStatusUtil.getSpeed());
        binding.tripInfoSpeed.setText(speed);

        int speedProgress = (int)((mTripStatusUtil.getSpeed() / 100) * 100);
        binding.tripInfoSpeedProgress.setProgress(speedProgress);

        // compute stop progress
        double currentStopDistance = mTripStatusUtil.getCurrentStopTime().getDistanceAlongTrip();
        double nextStopDistance = mTripStatusUtil.getNextStopTime().getDistanceAlongTrip();
        double distanceBetweenStop = nextStopDistance - currentStopDistance;
        double distanceTravelled = mTripStatusUtil.getDistanceToNextStop();
        if (distanceBetweenStop > 0 && distanceTravelled > 0) {
            int stopProgress = (int) (100 - (distanceTravelled / distanceBetweenStop) * 100);
            Log.d(TAG, "Current Stop distance along trip: " + currentStopDistance);
            Log.d(TAG, "Next Stop distance along trip: " + nextStopDistance);
            Log.d(TAG, "Distance between stops: " + distanceBetweenStop);
            Log.d(TAG, "Distance travelled: " + distanceTravelled);
            Log.d(TAG, "Stop progress: " + stopProgress);
            binding.tripInfoNextStopDistanceProgress.setProgress(stopProgress);
        }

        // compute trip progress
        double totalDistance = mTripStatusUtil.getTotalTripLength();
        double travelledDistance = mTripStatusUtil.getDistanceTravelled();
        int tripProgress = (int)((travelledDistance / totalDistance) * 100);
        binding.tripInfoTripTravelDistanceProgress.setProgress(tripProgress);
        binding.tripInfoTravelledDistance.setText(String.format(Locale.getDefault(), "%d %%", tripProgress));

        updateStatusView(tripStatus);
    }

    void updateNextStopInfo(Location l) {
        if (l == null) {
            return;
        }

        if (mTripStatusUtil == null) {
            return;
        }

        mTripStatusUtil.computeTripStatus(l);

        //updateNextStopLocation();
    }

    void updateStatusView(ObaTripStatus status) {
        boolean isRealtime = UIUtils.isLocationRealtime(status);

        int statusColor;

        if (isRealtime) {
            long deviationMin = TimeUnit.SECONDS.toMinutes(status.getScheduleDeviation());
            String statusString = ArrivalInfoUtils.computeArrivalLabelFromDelay(getResources(), deviationMin);
            //statusColor = ArrivalInfoUtils.computeColorFromDeviation(deviationMin);
            binding.tripInfoArrivalStatus.setText(statusString.replace("min ", "min\n"));
            //binding.tripInfoArrivalStatus.setTextColor(statusColor);
        } else {
            // Scheduled info
            //statusColor = getResources().getColor(R.color.stop_info_scheduled_time);
            binding.tripInfoArrivalStatus.setText(R.string.stop_info_scheduled);
            //binding.tripInfoArrivalStatus.setTextColor(statusColor);
        }
    }

    @Override
    public void onMapInitCompleted(MapModeController controller) {
        if (controller != null && mMapFragment != null) {
            if (MapParams.MODE_STOP.equals(homeViewModel.getMapMode().getValue())) {
                controller.setStopDataReceivedListener(this);
            }
        }
    }

    private void onStopChange(ObaStop stop) {
        removeArrivalFragment();
        if (stop != null && MapParams.MODE_STOP.equals(homeViewModel.getMapMode().getValue())) {
            showArrivalListFragment(stop);
        }

        updateStopInfoHeaders(null);
    }

    private void onMapModeChange(String mode) {
        //String prevMapMode = homeViewModel.getMapMode().getValue();
        Log.d(TAG, "Map mode changed from " + mCurrentMapMode + " to " + mode);

        if (mode == null) {
            return;
        }

        // ignore if we are in same mode mode
        if (mode.equals(mCurrentMapMode)) {
            return;
        }

        mCurrentMapMode = mode;

        if (MapParams.MODE_STOP.equals(mode)) {
            // we are in stop mode, notify main activity to disable the start button
            homeViewModel.setTripId(null);
        }

        updateStopInfoHeaders(homeViewModel.getArrivalInfo().getValue());

        // remove arrival fragment (if any)
        removeArrivalFragment();

        ObaArrivalInfo arrivalInfo = homeViewModel.getArrivalInfo().getValue();
        Bundle bundle = createMapBundle();
        if (MapParams.MODE_ROUTE.equals(mode) && arrivalInfo != null) {
            MapModeController controller = mMapFragment.setMapMode(MapParams.MODE_ROUTE, bundle);
            if (controller != null) {
                controller.setRoutesDataReceivedListener(this);
                controller.setTripDetailsDataReceivedListener(this);
            }

            // turn off screen timeout
            requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            MapModeController controller = mMapFragment.setMapMode(MapParams.MODE_STOP, bundle);
            if (controller != null) {
                controller.setStopDataReceivedListener(this);
            }
            // turn back screen timeout
            requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

    }

    public void updateFragmentHeader() {
        String subtitle = getString(R.string.title_no_region);
        ObaRegion region = Application.get().getCurrentRegion();
        if (region != null) {
            subtitle = region.getName();
        }
        ((AppCompatActivity) getActivity()).getSupportActionBar().setSubtitle(subtitle);
    }

    private void updateStopInfoHeaders(ObaArrivalInfo arrivalInfo) {
        ObaStop stop = homeViewModel.getStop().getValue();
        String mapMode = homeViewModel.getMapMode().getValue();


        String routeName = "-:-";
        if (arrivalInfo != null) {
            routeName = UIUtils.getRouteDisplayName(arrivalInfo);
        }

        String nextStopName = "-:-";
        if (stop != null) {
            nextStopName = stop.getName();
        }

        if (mapMode == null || mapMode.equals(MapParams.MODE_STOP)) {
            binding.selectStopHeader.setVisibility(View.VISIBLE);
            binding.tripInfoHeader.setVisibility(View.GONE);

            if (stop == null) {
                binding.selectStopInfoText.setText(R.string.report_dialog_stop_header);
            } else {
                binding.selectStopInfoText.setText(nextStopName);
            }

            if (arrivalInfo == null) {
                binding.selectTripInfoText.setText(R.string.report_dialog_no_trip);
            } else {
                String arrivalHeader = routeName;
                arrivalHeader = arrivalHeader.concat(" - ").concat(arrivalInfo.getHeadsign());
                binding.selectTripInfoText.setText(arrivalHeader);
            }
        } else {
            binding.selectStopHeader.setVisibility(View.GONE);
            binding.tripInfoHeader.setVisibility(View.VISIBLE);
        }
    }

    private Bundle createMapBundle() {
        String mapMode = homeViewModel.getMapMode().getValue();
        ObaArrivalInfo arrivalInfo = homeViewModel.getArrivalInfo().getValue();
        ObaStop stop = homeViewModel.getStop().getValue();
        Bundle bundle = new Bundle();
        if (MapParams.MODE_ROUTE.equals(mapMode) && arrivalInfo != null) {
            bundle.putBoolean(MapParams.ZOOM_TO_ROUTE, false);
            bundle.putBoolean(MapParams.ZOOM_INCLUDE_CLOSEST_VEHICLE, false);
            bundle.putString(MapParams.ROUTE_ID, arrivalInfo.getRouteId());
            bundle.putString(MapParams.TRIP_ID, arrivalInfo.getTripId());
            bundle.putBoolean(MapParams.SHOW_STOP, true);
            bundle.putString(MapParams.MODE, MapParams.MODE_ROUTE);
        } else if (MapParams.MODE_STOP.equals(mapMode) && stop != null) {
            bundle.putString(MapParams.MODE, MapParams.MODE_STOP);
            bundle.putString(MapParams.STOP_ID, stop.getId());
        } else {
            bundle.putString(MapParams.MODE, MapParams.MODE_STOP);
        }

        return bundle;
    }

    /**
     * Setting up the BaseMapFragment
     * BaseMapFragment was used to implement a map.
     */
    public void setupMapFragment() {
        FragmentManager fm = getActivity().getSupportFragmentManager();
        Fragment fragment = fm.findFragmentByTag(BaseMapFragment.TAG);

        if (fragment != null) {
            mMapFragment = (BaseMapFragment) fragment;
            mMapFragment.setOnFocusChangeListener(this);
        }

        if (mMapFragment == null) {
            mMapFragment = BaseMapFragment.newInstance(createMapBundle());
            mMapFragment.setArguments(createMapBundle());
            mMapFragment.setMapInitCompletedListener(this);
            mMapFragment.setLocationChangedListener(this);

            fm.beginTransaction().replace(R.id.map_view, mMapFragment).commit();
        }
        fm.beginTransaction().show(mMapFragment).commit();

        setupLocationHelper(mSavedInstanceState);

        mMapFragment.setMyLocation();

        // Register listener for map focus callbacks
        mMapFragment.setOnFocusChangeListener(this);

    }

    public void setMyLocationEnabled() {
        if (mMapFragment != null) {
            mMapFragment.setMyLocationEnabled();
        }
    }

    public void refreshMapData() {
        if (mMapFragment != null) {
            mMapFragment.refreshData();
        }
    }

    private void setupLocationHelper(Bundle savedInstanceState) {
        double lat;
        double lon;
        if (savedInstanceState == null) {
            lat = getActivity().getIntent().getDoubleExtra(MapParams.CENTER_LAT, 0);
            lon = getActivity().getIntent().getDoubleExtra(MapParams.CENTER_LON, 0);
        } else {
            lat = savedInstanceState.getDouble(MapParams.CENTER_LAT, 0);
            lon = savedInstanceState.getDouble(MapParams.CENTER_LON, 0);
        }

        Location mapCenterLocation = LocationUtils.makeLocation(lat, lon);
        // mIssueLocationHelper = new IssueLocationHelper(mapCenterLocation, this);

        // Set map center location
        mMapFragment.setMapCenter(mapCenterLocation, true, false);
    }

    void selectStopTrip() {
        removeArrivalFragment();

        if (!MapParams.MODE_STOP.equals(homeViewModel.getMapMode().getValue())) {
            return;
        }

        ObaStop stop = homeViewModel.getStop().getValue();

        if (mMapFragment != null && stop != null) {
            showArrivalListFragment(stop);
        }
    }

    private void showArrivalListFragment(ObaStop obaStop) {
        if (mProgressDialog == null) {
            mProgressDialog = new ProgressDialog(requireActivity());
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setIndeterminate(true);
        }
        mProgressDialog.show();

        binding.bottomSheet.setVisibility(View.VISIBLE);

        LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
        View v = layoutInflater.inflate(R.layout.arrivals_list_header, null);
        v.setVisibility(View.GONE);

        SimpleArrivalListFragment.show(
                (AppCompatActivity) getActivity(),
                R.id.arrival_info,
                obaStop,
                homeViewModel.getArrivalInfo().getValue(),
                this);
    }

    private void selectTrip(ObaArrivalInfo obaArrivalInfo, String agencyName, String blockId, View view) {
        // if we are not in stop mode, ignore
        if (!MapParams.MODE_STOP.equalsIgnoreCase(homeViewModel.getMapMode().getValue())) {
            return;
        }

        if (obaArrivalInfo == null) {
            homeViewModel.setBlockId(null);
            homeViewModel.setRouteId(null);
            homeViewModel.setArrivalInfo(null);
            homeViewModel.setTripId(null);
            return;
        }

        homeViewModel.setBlockId(blockId);
        homeViewModel.setRouteId(obaArrivalInfo.getRouteId());
        homeViewModel.setArrivalInfo(obaArrivalInfo);
        homeViewModel.setTripId(obaArrivalInfo.getTripId());

        updateStopInfoHeaders(obaArrivalInfo);

        FragmentManager manager = getActivity().getSupportFragmentManager();
        SimpleArrivalListFragment fragment = (SimpleArrivalListFragment) manager.findFragmentByTag(SimpleArrivalListFragment.TAG);

        if (fragment != null) {
            fragment.setInfoVisibility(View.GONE);
        }

        if (view != null) {
            view.setBackgroundColor(getResources().getColor(R.color.theme_accent));
        }
    }

    private void unselectTrip(View view) {
        // if we are not in stop mode, ignore
        if (!MapParams.MODE_STOP.equalsIgnoreCase(homeViewModel.getMapMode().getValue())) {
            return;
        }

        homeViewModel.setBlockId(null);
        homeViewModel.setRouteId(null);
        homeViewModel.setArrivalInfo(null);
        homeViewModel.setTripId(null);

        updateStopInfoHeaders(null);

        if (view != null) {
            view.setBackgroundColor(getResources().getColor(R.color.stop_info_arrival_list_background));
        }
    }

    protected void removeArrivalFragment() {
        binding.bottomSheet.setVisibility(View.GONE);
        FragmentManager manager = getActivity().getSupportFragmentManager();
        Fragment fragment = manager.findFragmentByTag(SimpleArrivalListFragment.TAG);

        if (fragment != null) {
            FragmentTransaction trans = manager.beginTransaction();
            trans.remove(fragment);
            trans.commit();
            manager.popBackStack();
        }
    }

    private void endTrip() {
        homeViewModel.setResponse(null);
        homeViewModel.setServiceStatus(false);
        homeViewModel.setTripId(null);
        homeViewModel.setArrivalInfo(null);
        homeViewModel.setStop(null);
        homeViewModel.setRouteId(null);
        homeViewModel.setBlockId(null);
        if (mTripStatusUtil != null) {
            mTripStatusUtil.destroy();
            mTripStatusUtil = null;
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getActivity());
        builder.setTitle(R.string.msg_trip_end_title);
        builder.setIcon(R.drawable.ic_alert);
        builder.setMessage(R.string.msg_trip_end_message);
        builder.setPositiveButton(R.string.ok, null);
        builder.show();
    }

    @Override
    public void onTripStatusComputed(TripStatusUtils tripStatus) {
        if (binding != null) {
            updateNextStopLocation();
        }
    }

    @Override
    public void onCloseToStop(ObaStop stop, ObaTripSchedule.StopTime stopTime, PointAndIndex pointAndIndex) {
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(requireActivity(), notification);
            r.play();
        } catch (Exception e) {
            Log.d(TAG, "Error playing notification", e);
        }
    }
}
package au.mymetro.operator.ui.home;

import static au.mymetro.operator.oba.util.PermissionUtils.LOCATION_PERMISSIONS;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.onebusaway.transit_data_federation.impl.shapes.PointAndIndex;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
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
import au.mymetro.operator.oba.io.elements.ObaTripDetails;
import au.mymetro.operator.oba.io.elements.ObaTripSchedule;
import au.mymetro.operator.oba.io.elements.ObaTripStatus;
import au.mymetro.operator.oba.io.elements.OccupancyState;
import au.mymetro.operator.oba.io.elements.Status;
import au.mymetro.operator.oba.io.request.ObaArrivalInfoResponse;
import au.mymetro.operator.oba.io.request.ObaStopsForLocationResponse;
import au.mymetro.operator.oba.io.request.ObaStopsForRouteResponse;
import au.mymetro.operator.oba.io.request.ObaTripDetailsResponse;
import au.mymetro.operator.oba.io.request.ObaTripsForRouteResponse;
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
        MapModeController.VehicleDataReceivedListener,
        MapModeController.TripDetailsDataReceivedListener,
        MapModeController.StopDataReceivedListener,
        BaseMapFragment.MapInitCompletedListener, TripStatusUtils.TripStatusComputedListener, TripStatusUtils.CloseToStopListener {

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

        if (stopInfo.getEta() > 3) {
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

        /*double speed = 0.0f;
        if (location == null) {
            // try to use location from trip status
            ObaTripDetailsResponse response = homeViewModel.getResponse().getValue();
            if (response == null) {
                return;
            } else {
                location = response.getStatus().getLastKnownLocation();
                if (location != null) {
                    speed = location.getSpeed();
                } else {
                    return;
                }
            }
        }*/

        if (location == null) {
            return;
        }

        //binding.tripInfoSpeed.setText(String.format("%f kmh", speed));
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
            // endTrip();
            return;
        }

        /*if (tripStatus.getStatus() != null && tripStatus.getStatus() == Status.CANCELED) {
            endTrip();
            return;
        }*/

        String activeTripId = tripStatus.getActiveTripId();
        String currentTripId = response.getId();
        /*if (TextUtils.isEmpty(activeTripId) || !activeTripId.equals(currentTripId)) {
            endTrip();
            return;
        }*/

        ObaTrip trip = response.getTrip(homeViewModel.getTripId().getValue());
        /*if (trip == null) {
            endTrip();
            return;
        }*/

        ObaRoute route = response.getRoute(homeViewModel.getRouteId().getValue());
        /*if (route == null) {
            endTrip();
            return;
        }*/

        ObaTripSchedule schedule = response.getSchedule();
        if (schedule == null) {
            endTrip();
            return;
        }

        /*if (TextUtils.isEmpty(tripStatus.getNextStop())) {
            endTrip();
            return;
        }*/

        /*ObaStop nextStop = response.getStop(tripStatus.getNextStop());
        if (nextStop == null) {
            endTrip();
            return;
        }*/

        ObaTripSchedule.StopTime[] stopTimes = schedule.getStopTimes();
        if (stopTimes == null || stopTimes.length < 1) {
            endTrip();
            return;
        }

        /*ObaTripSchedule.StopTime nextStopTime = null;
        for (ObaTripSchedule.StopTime stopTime : stopTimes) {
            if (stopTime.getStopId().equals(tripStatus.getNextStop())) {
                nextStopTime = stopTime;
                break;
            }
        }*/

        /*if (nextStopTime == null) {
            endTrip();
            return;
        }*/

        mTripStatusUtil = new TripStatusUtils(homeViewModel.getStop().getValue(), response, this, this);
        //updateNextStopLocation();
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
        String time = DateUtils.formatDateTime(getActivity(),
                date + mTripStatusUtil.getNextStopTime().getArrivalTime() * 1000
                        + deviation * 1000,
                DateUtils.FORMAT_SHOW_TIME |
                        DateUtils.FORMAT_NO_NOON |
                        DateUtils.FORMAT_NO_MIDNIGHT);

        binding.tripInfoRoute.setText(UIUtils.getRouteDisplayName(route));
        binding.tripInfoHeadsign.setText(trip.getHeadsign());
        binding.tripInfoNextStop.setText(nextStop.getName());
        binding.tripInfoNextStopDirection.setText(UIUtils.getStopDirectionText(nextStop.getDirection()));
        binding.tripInfoArrivalTime.setText(time);
        binding.tripInfoNextStopDistance.setText(mTripStatusUtil.getDistanceToNextStop() + "m");

        String speed = String.format(Locale.getDefault(), "%d km/h", (int)mTripStatusUtil.getSpeed());
        Log.d(TAG, "Speed: " + speed);
        binding.tripInfoSpeed.setText(speed);
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

        TextView statusView = binding.status.status3;
        statusView.setBackgroundResource(R.drawable.round_corners_style_b_status);
        GradientDrawable d = (GradientDrawable) statusView.getBackground();

        // Set padding on status view
        int pSides = UIUtils.dpToPixels(requireActivity(), 5);
        int pTopBottom = UIUtils.dpToPixels(requireActivity(), 2);

        int statusColor;

        if (isRealtime) {
            long deviationMin = TimeUnit.SECONDS.toMinutes(status.getScheduleDeviation());
            String statusString = ArrivalInfoUtils.computeArrivalLabelFromDelay(getResources(), deviationMin);
            statusView.setText(statusString);
            statusColor = ArrivalInfoUtils.computeColorFromDeviation(deviationMin);
            d.setColor(getResources().getColor(statusColor));
            statusView.setPadding(pSides, pTopBottom, pSides, pTopBottom);
        } else {
            // Scheduled info
            statusView.setText(R.string.stop_info_scheduled);
            statusColor = R.color.stop_info_scheduled_time;
            d.setColor(getResources().getColor(statusColor));
            statusView.setPadding(pSides, pTopBottom, pSides, pTopBottom);
        }
    }

    @Override
    public void onVehicleDataReceived(ObaTripsForRouteResponse response) {
        /*Log.d(TAG, "Vehicle data received: " + response);
        if (response == null) {
            return;
        }

        if (MapParams.MODE_ROUTE.equals(homeViewModel.getMapMode().getValue())) {
            binding.tripInfoHeader.setVisibility(View.VISIBLE);

            ObaTripDetails tripDetails = null;
            for (ObaTripDetails td : response.getTrips()) {
                if (td.getId().equals(homeViewModel.getTripId().getValue())) {
                    tripDetails = td;
                    break;
                }
            }

            if (tripDetails == null) {
                // notify main activity that the trip has ended
                homeViewModel.setServiceStatus(false);
                homeViewModel.setTripId(null);
                homeViewModel.setArrivalInfo(null);
                homeViewModel.setStop(null);
                homeViewModel.setRouteId(null);
                homeViewModel.setBlockId(null);

                Snackbar snackbar = Snackbar.make(requireActivity().findViewById(R.id.home_layout),
                        R.string.trip_ended,
                        Snackbar.LENGTH_INDEFINITE);
                snackbar.setAction("DISMISS", v -> {});
                snackbar.show();
                return;
            }

            ObaTrip trip = response.getTrip(homeViewModel.getTripId().getValue());
            if (trip == null) {
                return;
            }

            ObaRoute route = response.getRoute(homeViewModel.getRouteId().getValue());
            if (route == null) {
                return;
            }

            ObaTripStatus tripStatus = tripDetails.getStatus();
            if (tripStatus == null) {
                return;
            }

            ObaStop nextStop = response.getStop(tripDetails.getStatus().getNextStop());

            if (nextStop == null) {
                // notify trip end
                //binding.tripInfoNextStop.setText(nextStop.getName());
                //binding.tripInfoNextStopDirection.setText(UIUtils.getStopDirectionText(nextStop.getDirection()));
            } else {
                long etaMin = tripStatus.getNextStopTimeOffset() / 60;
                long etaSec = tripStatus.getNextStopTimeOffset() % 60;
                binding.tripInfoRoute.setText(UIUtils.getRouteDisplayName(route));
                binding.tripInfoHeadsign.setText(trip.getHeadsign());
                binding.tripInfoNextStop.setText(nextStop.getName());
                binding.tripInfoNextStopDirection.setText(UIUtils.getStopDirectionText(nextStop.getDirection()));
                binding.tripInfoEtaMin.setText(String.valueOf(etaMin));
                binding.tripInfoEtaSec.setText(String.valueOf(etaSec));
            }
        }*/
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
        removeFragmentByTag(SimpleArrivalListFragment.TAG);
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
        removeFragmentByTag(SimpleArrivalListFragment.TAG);

        ObaArrivalInfo arrivalInfo = homeViewModel.getArrivalInfo().getValue();
        Bundle bundle = createMapBundle();
        if (MapParams.MODE_ROUTE.equals(mode) && arrivalInfo != null) {
            MapModeController controller = mMapFragment.setMapMode(MapParams.MODE_ROUTE, bundle);
            if (controller != null) {
                controller.setRoutesDataReceivedListener(this);
                controller.setVehicleDataReceivedListener(this);
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

        String headsign = "-:-";
        String eta = "-:-";
        String min = "-:-";
        if (arrivalInfo != null) {
            headsign = arrivalInfo.getHeadsign();

            ArrivalInfo stopInfo = ArrivalInfoUtils.convertObaArrivalInfo(requireActivity(), arrivalInfo, true);

            if (stopInfo.getEta() == 0) {
                eta = getString(R.string.stop_info_eta_now);
                min = "";
            } else {
                eta = String.valueOf(stopInfo.getEta());
                min = "min";
            }
        }

        String nextStopName = "-:-";
        String direction = "-:-";
        if (stop != null) {
            nextStopName = stop.getName();
            direction = stop.getDirection();
        }

        if (mapMode == null || mapMode.equals(MapParams.MODE_STOP)) {
            binding.selectStopHeader.setVisibility(View.VISIBLE);
            binding.tripInfoHeader.setVisibility(View.GONE);
            // binding.tripInfoFabsPanel.setVisibility(View.GONE);

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
            //binding.tripInfoFabsPanel.setVisibility(View.VISIBLE);

            /*binding.tripInfoRoute.setText(routeName);
            binding.tripInfoHeadsign.setText(headsign);
            binding.tripInfoNextStop.setText(nextStopName);
            binding.tripInfoNextStopDirection.setText(UIUtils.getStopDirectionText(direction));
            binding.tripInfoEta.setText(eta);
            binding.tripInfoEtaMin.setText(min);*/
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
            //mMapFragment.setMyLocation();

            // Register listener for map focus callbacks
            mMapFragment.setOnFocusChangeListener(this);

            fm.beginTransaction().replace(R.id.map_view, mMapFragment).commit();
        }
        fm.beginTransaction().show(mMapFragment).commit();

        setupLocationHelper(mSavedInstanceState);
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
        removeFragmentByTag(SimpleArrivalListFragment.TAG);

        if (!MapParams.MODE_STOP.equals(homeViewModel.getMapMode().getValue())) {
            return;
        }

        ObaStop stop = homeViewModel.getStop().getValue();

        /*if (mMapFragment != null && stop != null) {
            mMapFragment.doFocusChange(stop);
        }*/
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

    protected void removeFragmentByTag(String tag) {
        FragmentManager manager = getActivity().getSupportFragmentManager();
        Fragment fragment = manager.findFragmentByTag(tag);

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

        Snackbar snackbar = Snackbar.make(requireActivity().findViewById(R.id.home_layout),
                R.string.trip_ended,
                Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction("DISMISS", v -> {});
        snackbar.show();
    }

    @Override
    public void onTripStatusComputed(TripStatusUtils tripStatus) {
        updateNextStopLocation();
    }

    @Override
    public void onCloseToStop(ObaStop stop, ObaTripSchedule.StopTime stopTime, PointAndIndex pointAndIndex) {
        try {
            Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone r = RingtoneManager.getRingtone(requireActivity(), notification);
            r.play();
        } catch (Exception e) {
            e.printStackTrace();
        }
        //Log.d("onCloseToStop", "pointAndIndex.distanceFromTarget: " + pointAndIndex.distanceFromTarget + ", pointAndIndex.point: " + pointAndIndex.point);
    }
}
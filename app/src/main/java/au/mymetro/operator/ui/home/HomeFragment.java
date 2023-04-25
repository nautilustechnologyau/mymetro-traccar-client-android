package au.mymetro.operator.ui.home;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import java.util.HashMap;

import au.mymetro.operator.R;
import au.mymetro.operator.databinding.FragmentHomeBinding;
import au.mymetro.operator.oba.io.elements.ObaArrivalInfo;
import au.mymetro.operator.oba.io.elements.ObaRoute;
import au.mymetro.operator.oba.io.elements.ObaStop;
import au.mymetro.operator.oba.map.MapParams;
import au.mymetro.operator.oba.map.googlemapsv2.BaseMapFragment;
import au.mymetro.operator.oba.ui.SimpleArrivalListFragment;
import au.mymetro.operator.oba.util.LocationUtils;
import au.mymetro.operator.oba.util.UIUtils;

public class HomeFragment extends Fragment implements BaseMapFragment.OnFocusChangedListener, SimpleArrivalListFragment.Callback {

    public static String TAG = "HomeFragment";

    private FragmentHomeBinding binding;

    private BaseMapFragment mMapFragment;

    /**
     * Selected arrival information for trip problem reporting
     */
    private ObaArrivalInfo mArrivalInfo;

    /**
     * Block ID for the trip in mArrivalInfo
     */
    private String mBlockId;

    /**
     * Agency name for trip problem reporting
     */
    private String mAgencyName;

    private ObaStop mStop;

    private HashMap<String, ObaRoute> mRoutes;

    private Location mLocation;

    private boolean mShowArrivalListFragment = false;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        HomeViewModel homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // final TextView textView = binding.textHome;
        // homeViewModel.getText().observe(getViewLifecycleOwner(), textView::setText);

        root.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Layout height: " + root.getHeight());
                Log.d(TAG, "Layout width: " + root.getWidth());

                binding.riFrameMapView.setLayoutParams(
                        new RelativeLayout.LayoutParams(
                                root.getWidth(),
                                root.getHeight() - 20
                        )

                );
            }
        });

        setupMapFragment(savedInstanceState);
        setupLocationHelper(savedInstanceState);
        updateInfoHeaders();
        selectStopTrip();

        return root;
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
    public void onFocusChanged(ObaStop stop, HashMap<String, ObaRoute> routes, Location location) {
        mStop = stop;
        mArrivalInfo = null;
        mRoutes = routes;
        mLocation = location;

        if (stop != null) {
            showArrivalListFragment(mStop);
        } else if (location != null) {
            removeFragmentByTag(SimpleArrivalListFragment.TAG);
        }

        updateInfoHeaders();
    }

    /**
     * Setting up the BaseMapFragment
     * BaseMapFragment was used to implement a map.
     */
    private void setupMapFragment(Bundle bundle) {
        FragmentManager fm = getActivity().getSupportFragmentManager();
        Fragment fragment = fm.findFragmentByTag(BaseMapFragment.TAG);
        if (fragment != null) {
            mMapFragment = (BaseMapFragment) fragment;
            mMapFragment.setOnFocusChangeListener(this);
        }
        if (mMapFragment == null) {
            mMapFragment = BaseMapFragment.newInstance();
            mMapFragment.setArguments(bundle);
            // Register listener for map focus callbacks
            mMapFragment.setOnFocusChangeListener(this);

            fm.beginTransaction()
                    .replace(R.id.ri_frame_map_view, mMapFragment)
                    .commit();

            //fm.beginTransaction().add(R.id.ri_frame_map_view, mMapFragment,
            //        BaseMapFragment.TAG).commit();
        }
        fm.beginTransaction().show(mMapFragment).commit();
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
        if (mMapFragment != null && mStop != null) {
            showArrivalListFragment(mStop);
        }
    }

    private void updateInfoHeaders() {
        if (mStop == null) {
            binding.riInfoText.setText(R.string.report_dialog_stop_header);
        } else {
            binding.riInfoText.setText(mStop.getName());
        }

        if (mArrivalInfo == null) {
            binding.riBusStopText.setText(R.string.report_dialog_no_trip);
        } else {
            binding.riBusStopText.setText(UIUtils.getRouteDisplayName(mArrivalInfo) + " - " + mArrivalInfo.getHeadsign());
        }
    }

    private void showArrivalListFragment(ObaStop obaStop) {
        LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
        View v = layoutInflater.inflate(R.layout.arrivals_list_header, null);
        v.setVisibility(View.GONE);

        mShowArrivalListFragment = true;

        SimpleArrivalListFragment.show((AppCompatActivity) getActivity(), R.id.ri_report_stop_problem, obaStop, mArrivalInfo, this);
    }

    @Override
    public void onArrivalItemClicked(ObaArrivalInfo obaArrivalInfo, String agencyName, String blockId, View view) {
        mShowArrivalListFragment = false;

        if (UIUtils.isLocationRealtime(obaArrivalInfo.getTripStatus())) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(), R.style.CustomAlertDialog);
            builder.setMessage(getActivity().getString(R.string.msg_trip_ongoing));
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

    private void selectTrip(ObaArrivalInfo obaArrivalInfo, String agencyName, String blockId, View view) {
        mAgencyName = agencyName;
        mBlockId = blockId;
        mArrivalInfo = obaArrivalInfo;

        updateInfoHeaders();

        FragmentManager manager = getActivity().getSupportFragmentManager();
        SimpleArrivalListFragment fragment = (SimpleArrivalListFragment) manager.findFragmentByTag(SimpleArrivalListFragment.TAG);

        if (fragment != null) {
            fragment.setInfoVisibility(View.GONE);
        }

        if (view != null) {
            view.setBackgroundColor(getResources().getColor(R.color.theme_accent));
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
}
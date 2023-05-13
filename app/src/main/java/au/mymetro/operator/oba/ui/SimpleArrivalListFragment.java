/*
 * Copyright (C) 2012-2015 Paul Watts (paulcwatts@gmail.com), University of South Florida,
 * Benjamin Du (bendu@me.com), and individual contributors.
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

package au.mymetro.operator.oba.ui;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import java.util.ArrayList;

import au.mymetro.operator.R;
import au.mymetro.operator.oba.io.ObaApi;
import au.mymetro.operator.oba.io.elements.ObaArrivalInfo;
import au.mymetro.operator.oba.io.elements.ObaReferences;
import au.mymetro.operator.oba.io.elements.ObaStop;
import au.mymetro.operator.oba.io.elements.ObaTrip;
import au.mymetro.operator.oba.io.elements.OccupancyState;
import au.mymetro.operator.oba.io.request.ObaArrivalInfoResponse;
import au.mymetro.operator.oba.map.MapParams;
import au.mymetro.operator.oba.provider.ObaContract;
import au.mymetro.operator.oba.util.ArrivalInfoUtils;
import au.mymetro.operator.oba.util.FragmentUtils;
import au.mymetro.operator.oba.util.UIUtils;

public class SimpleArrivalListFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<ObaArrivalInfoResponse> {

    public interface Callback {
        void onArrivalItemClicked(ObaArrivalInfo obaArrivalInfo, ArrivalInfo stopInfo, String agencyName, String blockId, View view);
        void onArrivalDataReceived(ObaArrivalInfoResponse response);
    }

    private ObaStop mObaStop;

    private ObaArrivalInfo mSelectedArrivalInfo;

    private String mBundleObaStopId;

    private Callback mCallback;

    public static final String TAG = "SimpArrivalListFragment";

    private static int ARRIVALS_LIST_LOADER = 3;

    private static int MAX_TRIP_DISPLAYED = 5;

    public static void show(AppCompatActivity activity, Integer containerViewId,
                            ObaStop stop, ObaArrivalInfo selectedObaArrivalInfo, Callback callback) {
        FragmentManager fm = activity.getSupportFragmentManager();

        SimpleArrivalListFragment fragment = new SimpleArrivalListFragment();
        fragment.setObaStop(stop);
        fragment.setSelectedArrivalInfo(selectedObaArrivalInfo);

        Intent intent = new Intent(activity, SimpleArrivalListFragment.class);
        intent.setData(Uri.withAppendedPath(ObaContract.Stops.CONTENT_URI, stop.getId()));
        fragment.setArguments(FragmentUtils.getIntentArgs(intent));
        fragment.setCallback(callback);

        try {
            FragmentTransaction ft = fm.beginTransaction();
            ft.replace(containerViewId, fragment, TAG);
            ft.addToBackStack(null);
            ft.commit();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Cannot show SimpleArrivalListFragment after onSaveInstanceState has been called");
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mObaStop != null) {
            outState.putString(MapParams.STOP_ID, mObaStop.getId());
        } else if (mBundleObaStopId != null) {
            outState.putString(MapParams.STOP_ID, mBundleObaStopId);
        }
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            mBundleObaStopId = savedInstanceState.getString(MapParams.STOP_ID);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.simple_arrival_list, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ((ImageView) getActivity().findViewById(R.id.arrival_list_action_info)).setColorFilter(
                getResources().getColor(R.color.material_gray));
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        LoaderManager mgr = LoaderManager.getInstance(this);

        mgr.initLoader(ARRIVALS_LIST_LOADER, getArguments(), this).forceLoad();
    }

    public void setObaStop(ObaStop obaStop) {
        mObaStop = obaStop;
    }

    public void setSelectedArrivalInfo(ObaArrivalInfo arrivalInfo) {
        mSelectedArrivalInfo = arrivalInfo;
    }

    @Override
    public void onResume() {
        super.onResume();

        getActivity().getSupportLoaderManager().restartLoader(ARRIVALS_LIST_LOADER, getArguments(), this).
                forceLoad();
    }

    @Override
    public Loader<ObaArrivalInfoResponse> onCreateLoader(int id, Bundle args) {
        String stopId;
        if (mObaStop == null) {
            stopId = mBundleObaStopId;
        } else {
            stopId = mObaStop.getId();
        }
        return new ArrivalsListLoader(getActivity(), stopId);
    }

    @Override
    public void onLoadFinished(Loader<ObaArrivalInfoResponse> loader, ObaArrivalInfoResponse data) {
        ObaArrivalInfo[] info;
        if (data.getCode() == ObaApi.OBA_OK) {
            info = data.getArrivalInfo();
            if (info.length > 0) {
                loadArrivalList(info, data.getRefs(), data.getCurrentTime());
            } else {
                clearViews();
            }
        }

        if (mCallback != null) {
            mCallback.onArrivalDataReceived(data);
        }
    }

    private void clearViews() {
        LinearLayout contentLayout = (LinearLayout) getActivity().
                findViewById(R.id.simple_arrival_content);
        if (contentLayout == null) {
            contentLayout.removeAllViews();
        }

        //String text = getResources().getString(R.string.ri_no_trip);
        //((TextView)getActivity().findViewById(R.id.simple_arrival_info_text)).setText(text);
    }

    public void setInfoVisibility(int visibility) {
        getActivity().findViewById(R.id.simple_arrival_info_frame).setVisibility(visibility);
    }

    private void loadArrivalList(ObaArrivalInfo[] info, final ObaReferences refs, long currentTime) {
        LinearLayout contentLayout = (LinearLayout) getActivity().
                findViewById(R.id.simple_arrival_content);
        if (contentLayout == null) {
            return;
        }
        contentLayout.removeAllViews();

        if (info == null || refs == null) {
            return;
        }

        ArrayList<ArrivalInfo> arrivalInfos = ArrivalInfoUtils.convertObaArrivalInfo(getActivity(),
                info, new ArrayList<String>(), currentTime, false);

        int count = 0;
        for (ArrivalInfo stopInfo : arrivalInfos) {
            count++;
            if (count > MAX_TRIP_DISPLAYED) {
                break;
            }


            final ObaArrivalInfo arrivalInfo = stopInfo.getInfo();

            LayoutInflater inflater = LayoutInflater.from(getActivity());
            LinearLayout view = (LinearLayout) inflater.inflate(
                    R.layout.arrivals_list_item, null, false);

            if (mSelectedArrivalInfo != null && mSelectedArrivalInfo.getTripId().equalsIgnoreCase(arrivalInfo.getTripId())) {
                view.setBackgroundColor(getResources().getColor(R.color.theme_accent));
                // setInfoVisibility(View.GONE);
            }

            TextView route = (TextView) view.findViewById(R.id.route);
            TextView destination = (TextView) view.findViewById(R.id.destination);
            TextView time = (TextView) view.findViewById(R.id.time);
            TextView status = (TextView) view.findViewById(R.id.status);
            TextView etaView = (TextView) view.findViewById(R.id.eta);
            TextView minView = (TextView) view.findViewById(R.id.eta_min);
            ViewGroup realtimeView = (ViewGroup) view.findViewById(R.id.eta_realtime_indicator);
            ViewGroup occupancyView = view.findViewById(R.id.occupancy);

            view.findViewById(R.id.more_horizontal).setVisibility(View.INVISIBLE);
            view.findViewById(R.id.route_favorite).setVisibility(View.INVISIBLE);

            String routeShortName = arrivalInfo.getShortName();
            route.setText(routeShortName.trim());
            UIUtils.maybeShrinkRouteName(getActivity(), route, routeShortName.trim());

            destination.setText(UIUtils.formatDisplayText(arrivalInfo.getHeadsign()));
            status.setText(stopInfo.getStatusText());

            long eta = stopInfo.getEta();
            if (eta == 0) {
                etaView.setText(R.string.stop_info_eta_now);
                minView.setVisibility(View.GONE);
            } else {
                etaView.setText(String.valueOf(eta));
                minView.setVisibility(View.VISIBLE);
            }

            status.setBackgroundResource(R.drawable.round_corners_style_b_status);
            GradientDrawable d = (GradientDrawable) status.getBackground();

            Integer colorCode = stopInfo.getColor();
            int color = getActivity().getResources().getColor(colorCode);
            if (stopInfo.getPredicted()) {
                // Show real-time indicator
                UIUtils.setRealtimeIndicatorColorByResourceCode(realtimeView, colorCode,
                        android.R.color.transparent);
                realtimeView.setVisibility(View.VISIBLE);
            } else {
                realtimeView.setVisibility(View.INVISIBLE);
            }

            etaView.setTextColor(color);
            minView.setTextColor(color);
            d.setColor(color);

            // Set padding on status view
            int pSides = UIUtils.dpToPixels(getActivity(), 5);
            int pTopBottom = UIUtils.dpToPixels(getActivity(), 2);
            status.setPadding(pSides, pTopBottom, pSides, pTopBottom);

            time.setText(DateUtils.formatDateTime(getActivity(),
                    stopInfo.getDisplayTime(),
                    DateUtils.FORMAT_SHOW_TIME |
                            DateUtils.FORMAT_NO_NOON |
                            DateUtils.FORMAT_NO_MIDNIGHT
            ));

            // Occupancy
            if (stopInfo.getPredictedOccupancy() != null) {
                // Predicted occupancy data
                UIUtils.setOccupancyVisibilityAndColor(occupancyView, stopInfo.getPredictedOccupancy(), OccupancyState.PREDICTED);
                UIUtils.setOccupancyContentDescription(occupancyView, stopInfo.getPredictedOccupancy(), OccupancyState.PREDICTED);
            } else {
                // Historical occupancy data
                UIUtils.setOccupancyVisibilityAndColor(occupancyView, stopInfo.getHistoricalOccupancy(), OccupancyState.HISTORICAL);
                UIUtils.setOccupancyContentDescription(occupancyView, stopInfo.getHistoricalOccupancy(), OccupancyState.HISTORICAL);
            }

            View reminder = view.findViewById(R.id.reminder);
            reminder.setVisibility(View.GONE);

            contentLayout.addView(view);

            view.setOnClickListener(view1 -> {
                String agencyName = findAgencyNameByRouteId(refs, arrivalInfo.getRouteId());
                String blockId = findBlockIdByTripId(refs, arrivalInfo.getTripId());

                if (mCallback != null) {
                    mCallback.onArrivalItemClicked(arrivalInfo, stopInfo, agencyName, blockId, view);
                }
            });
        }
    }

    private String findAgencyNameByRouteId(ObaReferences refs, String routeId) {
        String agencyId = refs.getRoute(routeId).getAgencyId();
        return refs.getAgency(agencyId).getId();
    }

    private String findBlockIdByTripId(ObaReferences refs, String tripId) {
        ObaTrip trip = refs.getTrip(tripId);
        return trip.getBlockId();
    }

    @Override
    public void onLoaderReset(Loader<ObaArrivalInfoResponse> loader) {
    }

    @SuppressWarnings("unused")
    private ArrivalsListLoader getArrivalsLoader() {
        // If the Fragment hasn't been attached to an Activity yet, return null
        if (!isAdded()) {
            return null;
        }
        Loader<ObaArrivalInfoResponse> l =
                getLoaderManager().getLoader(ARRIVALS_LIST_LOADER);
        return (ArrivalsListLoader) l;
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }
}
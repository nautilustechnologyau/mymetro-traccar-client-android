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

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import au.mymetro.operator.oba.io.elements.ObaArrivalInfo;
import au.mymetro.operator.oba.io.elements.ObaRegion;
import au.mymetro.operator.oba.io.elements.ObaStop;
import au.mymetro.operator.oba.io.request.ObaTripDetailsResponse;
import au.mymetro.operator.oba.map.MapParams;

public class HomeViewModel extends ViewModel {

    private final MutableLiveData<Boolean> mServiceStatus;
    private final MutableLiveData<String> mMapMode;
    private final MutableLiveData<String> mTripId;
    private final MutableLiveData<String> mRouteId;
    private final MutableLiveData<String> mBlockId;
    private final MutableLiveData<ObaArrivalInfo> mArrivalInfo;
    private final MutableLiveData<ObaStop> mStop;
    private final MutableLiveData<ObaTripDetailsResponse> mResponse;
    private final MutableLiveData<ObaRegion> mRegionChanged;

    public HomeViewModel() {
        mServiceStatus = new MutableLiveData<>();
        mMapMode = new MutableLiveData<>();
        mTripId = new MutableLiveData<>();
        mRouteId = new MutableLiveData<>();
        mBlockId = new MutableLiveData<>();
        mArrivalInfo = new MutableLiveData<>();
        mStop = new MutableLiveData<>();
        mResponse = new MutableLiveData<>();
        mRegionChanged = new MutableLiveData<>();
        mMapMode.setValue(MapParams.MODE_STOP);
    }

    public LiveData<Boolean> getServiceStatus() {
        return mServiceStatus;
    }

    public void setServiceStatus(Boolean serviceStarted) {
        mServiceStatus.setValue(serviceStarted);
    }

    public MutableLiveData<String> getMapMode() {
        return mMapMode;
    }

    public void setMapMode(String mode) {
        if (mMapMode.getValue() == null && mode == null) {
            return;
        }

        if (mMapMode.getValue() != null && mMapMode.getValue().equals(mode)) {
            return;
        }

        if (mode != null && mode.equals(mMapMode.getValue())) {
            return;
        }

        mMapMode.setValue(mode);
    }

    public void setTripId(String tripId) {
        if (mTripId.getValue() == null && tripId == null) {
            return;
        }

        if (mTripId.getValue() != null && mTripId.getValue().equals(tripId)) {
            return;
        }

        if (tripId != null && tripId.equals(mTripId.getValue())) {
            return;
        }

        mTripId.setValue(tripId);
    }

    public MutableLiveData<String> getTripId() {
        return mTripId;
    }

    public void setRouteId(String routeId) {
        mRouteId.setValue(routeId);
    }

    public MutableLiveData<String> getRouteId() {
        return mRouteId;
    }

    public MutableLiveData<ObaTripDetailsResponse> getResponse() {
        return mResponse;
    }

    public void setResponse(ObaTripDetailsResponse response) {
        mResponse.setValue(response);
    }

    public void setBlockId(String blockId) {
        mBlockId.setValue(blockId);
    }

    public MutableLiveData<String> getBlockId() {
        return mBlockId;
    }

    public MutableLiveData<ObaArrivalInfo> getArrivalInfo() {
        return mArrivalInfo;
    }

    public void setArrivalInfo(ObaArrivalInfo arrivalInfo) {
        mArrivalInfo.setValue(arrivalInfo);
    }

    public MutableLiveData<ObaStop> getStop() {
        return mStop;
    }

    public void setStop(ObaStop stop) {
        if (mStop.getValue() == null && stop == null) {
            return;
        }

        if (mStop.getValue() != null && mStop.getValue() == stop) {
            return;
        }

        if (stop != null && stop == mStop.getValue()) {
            return;
        }

        if (stop != null && mStop.getValue() != null
                && mStop.getValue().getId().equals(stop.getId())) {
            return;
        }

        mStop.setValue(stop);
    }

    public MutableLiveData<ObaRegion> getRegionChanged() {
        return mRegionChanged;
    }
    public void setRegionChanged(ObaRegion changed) {
        mRegionChanged.setValue(changed);
    }
}
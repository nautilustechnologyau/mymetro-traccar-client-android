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
package au.mymetro.operator.oba.travelbehavior.model;

import android.location.Location;

import org.opentripplanner.api.model.TripPlan;

public class TripPlanData {
    public TravelBehaviorInfo.LocationInfo locationInfo;

    public TripPlan tripPlan;

    public String url;

    public Long regionId;

    public Long localElapsedRealtimeNanos;

    public Long localSystemCurrMillis;

    public Long otpServerTimestamp;

    public TripPlanData() {
    }

    public TripPlanData(TripPlan tripPlan, String url, Long regionId ,
                        Long localElapsedRealtimeNanos, Long localSystemCurrMillis,
                        Long otpServerTimestamp) {
        this.tripPlan = tripPlan;
        this.url = url;
        this.regionId = regionId;
        this.localElapsedRealtimeNanos = localElapsedRealtimeNanos;
        this.localSystemCurrMillis = localSystemCurrMillis;
        this.otpServerTimestamp = otpServerTimestamp;
    }

    public Long getLocalElapsedRealtimeNanos() {
        return localElapsedRealtimeNanos;
    }

    public Long getLocalSystemCurrMillis() {
        return localSystemCurrMillis;
    }

    public Long getOtpServerTimestamp() {
        return otpServerTimestamp;
    }

    public void setLocation(Location location) {
        locationInfo = new TravelBehaviorInfo.LocationInfo(location);
    }
}

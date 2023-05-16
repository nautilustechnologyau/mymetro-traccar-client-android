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
package au.mymetro.operator.oba.io.request;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import au.mymetro.operator.oba.io.elements.ObaReferences;
import au.mymetro.operator.oba.io.elements.ObaReferencesElement;
import au.mymetro.operator.oba.io.elements.ObaStop;
import au.mymetro.operator.oba.io.elements.ObaTripDetails;
import au.mymetro.operator.oba.io.elements.ObaTripDetailsElement;
import au.mymetro.operator.oba.io.elements.ObaTripSchedule;
import au.mymetro.operator.oba.io.elements.ObaTripStatus;

/**
 * Response object for ObaStopRequest requests.
 *
 * @author Paul Watts (paulcwatts@gmail.com)
 */
public final class ObaTripDetailsResponse extends ObaResponseWithRefs
        implements ObaTripDetails {

    private static final class Data {

        private static final Data EMPTY_OBJECT = new Data();

        private final ObaReferencesElement references = ObaReferencesElement.EMPTY_OBJECT;

        private final ObaTripDetailsElement entry = ObaTripDetailsElement.EMPTY_OBJECT;
    }

    private final Data data;

    private ObaTripDetailsResponse() {
        data = Data.EMPTY_OBJECT;
    }

    @Override
    public String getId() {
        return data.entry.getId();
    }

    @Override
    public ObaTripSchedule getSchedule() {
        return data.entry.getSchedule();
    }

    @Override
    public ObaTripStatus getStatus() {
        return data.entry.getStatus();
    }

    @Override
    public ObaReferences getRefs() {
        return data.references;
    }

    public List<ObaStop> getStops() {
        List<ObaStop> stops = new ArrayList<>();
        ObaTripSchedule schedule = getSchedule();
        if (schedule == null) {
            return stops;
        }

        ObaTripSchedule.StopTime[] stopTimes = schedule.getStopTimes();
        if (stopTimes == null) {
            return stops;
        }

        for (ObaTripSchedule.StopTime stopTime : stopTimes) {
            stops.add(getStop(stopTime.getStopId()));
        }

        return stops;
    }

}

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
package au.mymetro.operator.oba.ui;

import android.content.ContentQueryMap;
import android.content.Context;

import java.util.ArrayList;

import au.mymetro.operator.oba.io.elements.ObaArrivalInfo;
import au.mymetro.operator.oba.util.ArrayAdapter;

/**
 * Base adapter class for the various styles of arrivals lists
 *
 * @author barbeau
 */
public abstract class ArrivalsListAdapterBase<T> extends ArrayAdapter<T> {

    protected ContentQueryMap mTripsForStop;

    public ArrivalsListAdapterBase(Context context, int layout) {
        super(context, layout);
    }

    public void setTripsForStop(ContentQueryMap tripsForStop) {
        mTripsForStop = tripsForStop;
        notifyDataSetChanged();
    }

    abstract public void setData(ObaArrivalInfo[] arrivals, ArrayList<String> routesFilter, long currentTime);
}

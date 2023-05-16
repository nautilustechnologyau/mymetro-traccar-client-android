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
package au.mymetro.operator.oba.region;

import android.content.Context;

import androidx.loader.content.AsyncTaskLoader;

import java.util.ArrayList;

import au.mymetro.operator.oba.io.elements.ObaRegion;
import au.mymetro.operator.oba.util.RegionUtils;

public class ObaRegionsLoader extends AsyncTaskLoader<ArrayList<ObaRegion>> {
    //private static final String TAG = "ObaRegionsLoader";

    private Context mContext;

    private ArrayList<ObaRegion> mResults;

    private final boolean mForceReload;

    public ObaRegionsLoader(Context context) {
        super(context);
        this.mContext = context;
        mForceReload = false;
    }

    /**
     * @param context The context.
     * @param force   Forces loading the regions from the remote repository.
     */
    public ObaRegionsLoader(Context context, boolean force) {
        super(context);
        this.mContext = context;
        mForceReload = force;
    }

    @Override
    protected void onStartLoading() {
        if (mResults != null) {
            deliverResult(mResults);
        } else {
            forceLoad();
        }
    }

    @Override
    public ArrayList<ObaRegion> loadInBackground() {
        return RegionUtils.getRegions(mContext, mForceReload);
    }
}

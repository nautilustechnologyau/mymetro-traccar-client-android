/*
 * Copyright (C) 2010-2017 Paul Watts (paulcwatts@gmail.com),
 * University of South  Florida (sjbarbeau@gmail.com), Microsoft Corporation
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

package org.traccar.client.oba.util;

import android.app.Activity;
import android.content.Context;
import android.os.Build;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import org.traccar.client.HomeActivity;

/**
 * A class containing utility methods related to the user interface
 */
public final class UIUtils {

    private static final String TAG = "UIHelp";

    public static void setupActionBar(AppCompatActivity activity) {
        ActionBar bar = activity.getSupportActionBar();
        bar.setIcon(android.R.color.transparent);
        bar.setDisplayShowTitleEnabled(true);

        // HomeActivity is the root for all other activities
        if (!(activity instanceof HomeActivity)) {
            bar.setDisplayHomeAsUpEnabled(true);
        }
    }

    /**
     * Returns true if the activity is still active and dialogs can be managed (i.e., displayed
     * or dismissed), or false if it is
     * not
     *
     * @param activity Activity to check for displaying/dismissing a dialog
     * @return true if the activity is still active and dialogs can be managed, or false if it is
     * not
     */
    public static boolean canManageDialog(Activity activity) {
        if (activity == null) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return !activity.isFinishing() && !activity.isDestroyed();
        } else {
            return !activity.isFinishing();
        }
    }

    /**
     * Returns true if the context is an Activity and is still active and dialogs can be managed
     * (i.e., displayed or dismissed) OR the context is not an Activity, or false if the Activity
     * is
     * no longer active.
     *
     * NOTE: We really shouldn't display dialogs from a Service - a notification is a better way
     * to communicate with the user.
     *
     * @param context Context to check for displaying/dismissing a dialog
     * @return true if the context is an Activity and is still active and dialogs can be managed
     * (i.e., displayed or dismissed) OR the context is not an Activity, or false if the Activity
     * is
     * no longer active
     */
    public static boolean canManageDialog(Context context) {
        if (context == null) {
            return false;
        }

        if (context instanceof Activity) {
            return canManageDialog((Activity) context);
        } else {
            // We really shouldn't be displaying dialogs from a Service, but if for some reason we
            // need to do this, we don't have any way of checking whether its possible
            return true;
        }
    }
}

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
package au.mymetro.operator.oba.tripservice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import au.mymetro.operator.oba.util.ReminderUtils;

/**
 * Responsible for receiving Intents from the Android platform related to scheduled reminders.
 *
 * TODO - We should be extending the support library version of WakefulBroadcastReceiver and
 * starting the service using startWakefulService().  See #493 for details.
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Received alarm");
        ReminderUtils.startReminderService(context, intent, TAG);
    }
}

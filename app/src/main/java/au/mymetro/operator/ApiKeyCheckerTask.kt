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

package au.mymetro.operator

import android.app.Activity
import android.os.AsyncTask
import au.mymetro.operator.oba.util.PreferenceUtils
import au.mymetro.operator.oba.util.RegionUtils

internal class ApiKeyCheckerTask : AsyncTask<Any, Integer, Boolean>() {
    private lateinit var context: Activity
    private lateinit var apiKey: String
    private lateinit var listener: ApiKeyCheckerTaskListener

    override fun onPostExecute(valid: Boolean?) {
        if (valid == false) {
            PreferenceUtils.saveString(context.getString(R.string.preference_key_oba_api_key), null)
            PreferenceUtils.saveString(PreferencesFragment.KEY_DEVICE, null)
        } else {
            PreferenceUtils.saveString(context.getString(R.string.preference_key_oba_api_key), apiKey)
            PreferenceUtils.saveString(PreferencesFragment.KEY_DEVICE, apiKey)
        }

        if (listener != null) {
            listener.onApiCheckerTaskComplete(valid)
        }
    }

    override fun doInBackground(vararg params: Any?): Boolean? {
        context = params[0] as Activity
        listener = params[1] as ApiKeyCheckerTaskListener
        apiKey = params[2] as String
        return RegionUtils.isApiKeyValid(context, apiKey)
    }

    interface ApiKeyCheckerTaskListener {
        fun onApiCheckerTaskComplete(valid: Boolean?)
    }
}

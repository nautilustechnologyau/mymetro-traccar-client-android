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

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.webkit.URLUtil
import android.widget.EditText
import android.widget.Toast
import androidx.preference.*
import au.mymetro.operator.ApiKeyCheckerTask.ApiKeyCheckerTaskListener
import au.mymetro.operator.app.Application
import au.mymetro.operator.oba.io.ObaAnalytics
import au.mymetro.operator.oba.region.ObaRegionsTask
import au.mymetro.operator.oba.ui.NavHelp
import au.mymetro.operator.oba.ui.RegionsActivity
import au.mymetro.operator.oba.util.UIUtils
import com.google.firebase.analytics.FirebaseAnalytics
import java.util.*

class PreferencesFragment : PreferenceFragmentCompat(),
        OnSharedPreferenceChangeListener,
        ObaRegionsTask.Callback,
        ApiKeyCheckerTaskListener {

    private lateinit var sharedPreferences: SharedPreferences
    private var mAutoSelectInitialValue: Boolean = true
    private lateinit var mFirebaseAnalytics: FirebaseAnalytics

    @SuppressLint("UnspecifiedImmutableFlag")
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        setPreferencesFromResource(R.xml.preferences, rootKey)
        initPreferences()

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(context!!)

        findPreference<Preference>(KEY_DEVICE)?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            newValue != null && newValue != ""
        }
        findPreference<Preference>(KEY_URL)?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            newValue != null && validateServerURL(newValue.toString())
        }
        findPreference<Preference>(KEY_INTERVAL)?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            try {
                newValue != null && (newValue as String).toInt() > 0
            } catch (e: NumberFormatException) {
                Log.w(TAG, e)
                false
            }
        }

        val apiKeyPreference: EditTextPreference? = findPreference(getString(R.string.preference_key_oba_api_key))
        apiKeyPreference?.setOnBindEditTextListener {editText ->
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        apiKeyPreference?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            ApiKeyCheckerTask().execute(activity, activity, newValue as String?)
            true
        }

        val numberValidationListener = Preference.OnPreferenceChangeListener { _, newValue ->
            try {
                newValue != null && (newValue as String).toInt() >= 0
            } catch (e: NumberFormatException) {
                Log.w(TAG, e)
                false
            }
        }

        val regionClickListener = Preference.OnPreferenceClickListener { _ ->
            RegionsActivity.start(activity)
            true
        }

        findPreference<Preference>(KEY_DISTANCE)?.onPreferenceChangeListener = numberValidationListener
        findPreference<Preference>(KEY_ANGLE)?.onPreferenceChangeListener = numberValidationListener

        findPreference<Preference>(getString(R.string.preference_key_region))?.onPreferenceClickListener = regionClickListener
        findPreference<Preference>(getString(R.string.preference_key_auto_select_region))?.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener {
                    preference, newValue ->
                    findPreference<Preference>(getString(R.string.preference_key_region))?.isEnabled = !(newValue as Boolean)
                    if (newValue as Boolean) {
                        val callbacks: MutableList<ObaRegionsTask.Callback> = ArrayList()
                        callbacks.add(this)
                        val task = ObaRegionsTask(activity, callbacks, false, true)
                        task.execute()
                    }
                    true
                }

        if (sharedPreferences.getBoolean(KEY_STATUS, false)) {
            //startTrackingService(checkPermission = true, initialPermission = false)
            setPreferencesEnabled(false)
        }
    }

    class NumericEditTextPreferenceDialogFragment : EditTextPreferenceDialogFragmentCompat() {

        override fun onBindDialogView(view: View) {
            val editText = view.findViewById<EditText>(android.R.id.edit)
            editText.inputType = InputType.TYPE_CLASS_NUMBER
            super.onBindDialogView(view)
        }

        companion object {
            fun newInstance(key: String?): NumericEditTextPreferenceDialogFragment {
                val fragment = NumericEditTextPreferenceDialogFragment()
                val bundle = Bundle()
                bundle.putString(ARG_KEY, key)
                fragment.arguments = bundle
                return fragment
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (listOf(KEY_INTERVAL, KEY_DISTANCE, KEY_ANGLE).contains(preference.key)) {
            val f: EditTextPreferenceDialogFragmentCompat =
                    NumericEditTextPreferenceDialogFragment.newInstance(preference.key)
            f.setTargetFragment(this, 0)
            f.show(requireFragmentManager(), "androidx.preference.PreferenceFragment.DIALOG")
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onResume() {
        super.onResume()
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        changePreferenceSummary(getString(R.string.preference_key_region))
    }

    override fun onPause() {
        super.onPause()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        val currentValue = findPreference<Preference>(getString(R.string.preference_key_auto_select_region))?.isEnabled ?: true
        if (currentValue and !mAutoSelectInitialValue) {
            NavHelp.goHome(activity, false)
        }
        super.onDestroy()
    }

    private fun setPreferencesEnabled(enabled: Boolean) {
        findPreference<Preference>(KEY_DEVICE)?.isEnabled = enabled
        findPreference<Preference>(KEY_URL)?.isEnabled = enabled
        findPreference<Preference>(KEY_INTERVAL)?.isEnabled = enabled
        findPreference<Preference>(KEY_DISTANCE)?.isEnabled = enabled
        findPreference<Preference>(KEY_ANGLE)?.isEnabled = enabled
        findPreference<Preference>(KEY_ACCURACY)?.isEnabled = enabled
        findPreference<Preference>(KEY_BUFFER)?.isEnabled = enabled
        findPreference<Preference>(KEY_WAKELOCK)?.isEnabled = enabled
        findPreference<Preference>(getString(R.string.preference_key_auto_select_region))?.isEnabled = enabled

        if (enabled) {
            findPreference<Preference>(getString(R.string.preference_key_region))?.isEnabled = !sharedPreferences.getBoolean(getString(R.string.preference_key_auto_select_region), true);
        } else {
            findPreference<Preference>(getString(R.string.preference_key_region))?.isEnabled = false
        }
        findPreference<Preference>(getString(R.string.preference_key_oba_api_key))?.isEnabled = enabled
        findPreference<Preference>(getString(R.string.preference_key_auto_select_region))?.isEnabled = enabled
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        if (key == KEY_STATUS) {
            if (sharedPreferences.getBoolean(KEY_STATUS, false)) {
                setPreferencesEnabled(false)
            } else {
                setPreferencesEnabled(true)
            }
        } else if (key == KEY_DEVICE) {
            findPreference<Preference>(KEY_DEVICE)?.summary = sharedPreferences.getString(KEY_DEVICE, null)
        } else if (key == getString(R.string.preference_key_auto_select_region)) {
            val autoSelect = findPreference<Preference>(key)?.isEnabled
            if (autoSelect!!) {
                ObaAnalytics.reportUiEvent(mFirebaseAnalytics, getString(R.string.analytics_label_button_press_auto), null)
            } else {
                ObaAnalytics.reportUiEvent(mFirebaseAnalytics, getString(R.string.analytics_label_button_press_manual), null)
            }
        }
    }

    private fun initPreferences() {
        PreferenceManager.setDefaultValues(requireActivity(), R.xml.preferences, false)
        if (!sharedPreferences.contains(KEY_DEVICE)) {
            val id = (Random().nextInt(900000) + 100000).toString()
            sharedPreferences.edit().putString(KEY_DEVICE, id).apply()
            findPreference<EditTextPreference>(KEY_DEVICE)?.text = id
        }

        findPreference<Preference>(KEY_DEVICE)?.summary = sharedPreferences.getString(KEY_DEVICE, null)

        mAutoSelectInitialValue = findPreference<Preference>(getString(R.string.preference_key_auto_select_region))?.isEnabled ?: true
        findPreference<Preference>(getString(R.string.preference_key_region))?.isEnabled = !sharedPreferences.getBoolean(getString(R.string.preference_key_auto_select_region), true)

        // disable and hide the status preference
        val preference = findPreference<Preference>(KEY_STATUS)
        preference?.isEnabled = false
        preference?.isVisible = false
    }

    private fun validateServerURL(userUrl: String): Boolean {
        val port = Uri.parse(userUrl).port
        if (
            URLUtil.isValidUrl(userUrl) &&
            (port == -1 || port in 1..65535) &&
            (URLUtil.isHttpUrl(userUrl) || URLUtil.isHttpsUrl(userUrl))
        ) {
            return true
        }
        Toast.makeText(activity, R.string.error_msg_invalid_url, Toast.LENGTH_LONG).show()
        return false
    }

    /**
     * Changes the summary of a preference based on a given preference key
     *
     * @param preferenceKey preference key that triggers a change in summary
     */
    private fun changePreferenceSummary(preferenceKey: String) {
        // Change the current region summary and server API URL summary
        if (preferenceKey.equals(getString(R.string.preference_key_region), ignoreCase = true)) {
            if (Application.get().currentRegion != null) {
                findPreference<Preference>(getString(R.string.preference_key_region))?.summary = Application.get().currentRegion.name
            }
        }
    }

    companion object {
        private val TAG = PreferencesFragment::class.java.simpleName
        const val KEY_DEVICE = "vehicle_id"
        const val KEY_URL = "url"
        const val KEY_INTERVAL = "interval"
        const val KEY_DISTANCE = "distance"
        const val KEY_ANGLE = "angle"
        const val KEY_ACCURACY = "accuracy"
        const val KEY_STATUS = "status"
        const val KEY_BUFFER = "buffer"
        const val KEY_WAKELOCK = "wakelock"
    }

    override fun onRegionTaskFinished(currentRegionChanged: Boolean) {
        activity?.setProgressBarIndeterminateVisibility(false)

        if (currentRegionChanged) {
            // If region was auto-selected, show user the region we're using
            if (Application.getPrefs()
                            .getBoolean(getString(R.string.preference_key_auto_select_region), true)
                    && Application.get().currentRegion != null) {
                Toast.makeText(activity,
                        getString(R.string.region_region_found,
                                Application.get().currentRegion.name),
                        Toast.LENGTH_LONG
                ).show()
            }

            // Update the preference summary to show the newly selected region
            changePreferenceSummary(getString(R.string.preference_key_region))

            UIUtils.showObaApiKeyInputDialog(activity, this)
        }
    }

    override fun onApiCheckerTaskComplete(valid: Boolean?) {
        if (java.lang.Boolean.FALSE == valid) {
            UIUtils.popupSnackbarForApiKey(activity, this)
        }
    }
}
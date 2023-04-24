package au.mymetro.operator

import android.content.Context
import android.os.AsyncTask
import android.widget.Toast
import au.mymetro.operator.app.Application
import au.mymetro.operator.oba.util.PreferenceUtils
import au.mymetro.operator.oba.util.RegionUtils

internal class ApiKeyCheckerTask : AsyncTask<Any, Integer, Boolean>() {
    private lateinit var context: Context
    private lateinit var apiKey: String

    override fun onPostExecute(valid: Boolean?) {
        if (valid == false) {
            Toast.makeText(context, "Invalid API Key", Toast.LENGTH_LONG).show()
            //toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 0)
            //toast.show()
            PreferenceUtils.saveString(context.getString(R.string.preference_key_oba_api_key), null)
            PreferenceUtils.saveString(MainFragment.KEY_DEVICE, null)
        } else {
            PreferenceUtils.saveString(context.getString(R.string.preference_key_oba_api_key), apiKey)
            PreferenceUtils.saveString(MainFragment.KEY_DEVICE, apiKey)
        }
    }

    override fun doInBackground(vararg params: Any?): Boolean? {
        context = params[0] as Context
        apiKey = params[1] as String
        return RegionUtils.isApiKeyValid(context, apiKey)
    }
}

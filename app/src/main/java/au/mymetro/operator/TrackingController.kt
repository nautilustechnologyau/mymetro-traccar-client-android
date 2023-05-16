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

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.PreferenceManager
import au.mymetro.operator.DatabaseHelper.DatabaseHandler
import au.mymetro.operator.NetworkManager.NetworkHandler
import au.mymetro.operator.PositionProvider.PositionListener
import au.mymetro.operator.ProtocolFormatter.formatRequest
import au.mymetro.operator.RequestManager.RequestHandler
import au.mymetro.operator.RequestManager.sendRequestAsync
import au.mymetro.operator.ui.notifications.NotificationsFragment

class TrackingController(private val context: Context,
                         private val tripId: String,
                         private val routeId: String,
                         private val blockId: String) : PositionListener, NetworkHandler {

    private val handler = Handler(Looper.getMainLooper())
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val positionProvider = PositionProviderFactory.create(context, this)
    private val databaseHelper = DatabaseHelper(context)
    private val networkManager = NetworkManager(context, this)

    private val url: String = preferences.getString(PreferencesFragment.KEY_URL, context.getString(R.string.settings_url_default_value))!!
    private val buffer: Boolean = preferences.getBoolean(PreferencesFragment.KEY_BUFFER, true)

    private var isOnline = networkManager.isOnline
    private var isWaiting = false

    fun start() {
        if (isOnline) {
            read()
        }
        try {
            positionProvider.startUpdates()
        } catch (e: SecurityException) {
            Log.w(TAG, e)
        }
        networkManager.start()
    }

    fun stop() {
        networkManager.stop()
        try {
            positionProvider.stopUpdates()
        } catch (e: SecurityException) {
            Log.w(TAG, e)
        }
        handler.removeCallbacksAndMessages(null)
    }

    override fun onPositionUpdate(position: Position) {
        //StatusActivity.addMessage(context.getString(R.string.status_location_update))
        NotificationsFragment.addMessage(context.getString(R.string.status_location_update))
        position.tripId = tripId
        position.routeId = routeId
        position.blockId = blockId
        if (buffer) {
            write(position)
        } else {
            send(position)
        }
    }

    override fun onPositionError(error: Throwable) {}
    override fun onNetworkUpdate(isOnline: Boolean) {
        val message = if (isOnline) R.string.status_network_online else R.string.status_network_offline
        //StatusActivity.addMessage(context.getString(message))
        NotificationsFragment.addMessage(context.getString(message))
        if (!this.isOnline && isOnline) {
            read()
        }
        this.isOnline = isOnline
    }

    //
    // State transition examples:
    //
    // write -> read -> send -> delete -> read
    //
    // read -> send -> retry -> read -> send
    //

    private fun log(action: String, position: Position?) {
        var formattedAction: String = action
        if (position != null) {
            formattedAction +=
                    " (id:" + position.id +
                    " time:" + position.time.time / 1000 +
                    " lat:" + position.latitude +
                    " lon:" + position.longitude + ")"
        }
        Log.d(TAG, formattedAction)
    }

    private fun write(position: Position) {
        log("write", position)
        databaseHelper.insertPositionAsync(position, object : DatabaseHandler<Unit?> {
            override fun onComplete(success: Boolean, result: Unit?) {
                if (success) {
                    if (isOnline && isWaiting) {
                        read()
                        isWaiting = false
                    }
                }
            }
        })
    }

    private fun read() {
        log("read", null)
        databaseHelper.selectPositionAsync(object : DatabaseHandler<Position?> {
            override fun onComplete(success: Boolean, result: Position?) {
                if (success) {
                    if (result != null) {
                        if (result.deviceId == preferences.getString(PreferencesFragment.KEY_DEVICE, null)) {
                            send(result)
                        } else {
                            delete(result)
                        }
                    } else {
                        isWaiting = true
                    }
                } else {
                    retry()
                }
            }
        })
    }

    private fun delete(position: Position) {
        log("delete", position)
        databaseHelper.deletePositionAsync(position.id, object : DatabaseHandler<Unit?> {
            override fun onComplete(success: Boolean, result: Unit?) {
                if (success) {
                    read()
                } else {
                    retry()
                }
            }
        })
    }

    private fun send(position: Position) {
        log("send", position)
        val request = formatRequest(url, position)
        sendRequestAsync(request, object : RequestHandler {
            override fun onComplete(success: Boolean) {
                if (success) {
                    if (buffer) {
                        delete(position)
                    }
                } else {
                    StatusActivity.addMessage(context.getString(R.string.status_send_fail))
                    NotificationsFragment.addMessage(context.getString(R.string.status_send_fail))
                    if (buffer) {
                        retry()
                    }
                }
            }
        })
    }

    private fun retry() {
        log("retry", null)
        handler.postDelayed({
            if (isOnline) {
                read()
            }
        }, RETRY_DELAY.toLong())
    }

    companion object {
        private val TAG = TrackingController::class.java.simpleName
        private const val RETRY_DELAY = 30 * 1000
    }

}

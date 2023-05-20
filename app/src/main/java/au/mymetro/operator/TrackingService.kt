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

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import au.mymetro.operator.ui.notifications.NotificationsFragment

class TrackingService : Service() {

    private var wakeLock: WakeLock? = null
    private var trackingController: TrackingController? = null
    private var tripId: String = ""
    private var routeId: String = ""
    private var blockId: String = ""


    class HideNotificationService : Service() {
        override fun onBind(intent: Intent): IBinder? {
            return null
        }

        override fun onCreate() {
            startForeground(NOTIFICATION_ID, createNotification(this))
            stopForeground(true)
        }

        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
    }

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        startForeground(NOTIFICATION_ID, createNotification(this))
        Log.i(TAG, "service create")
        sendBroadcast(Intent(ACTION_STARTED))
        //StatusActivity.addMessage(getString(R.string.status_service_create))
        NotificationsFragment.addMessage(getString(R.string.status_service_create))

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PreferencesFragment.KEY_WAKELOCK, true)) {
                val powerManager = getSystemService(POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, javaClass.name)
                wakeLock?.acquire()
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, Intent(this, HideNotificationService::class.java))
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    // @TargetApi(Build.VERSION_CODES.ECLAIR)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        tripId = intent?.extras?.getString("tripId", "")!!
        Log.d(TAG, "Starting tracking service with tripId: " + tripId)
        routeId = intent.extras?.getString("routeId", "")!!
        Log.d(TAG, "Starting tracking service with routeId: " + routeId)
        blockId = intent.extras?.getString("blockId", "")!!
        Log.d(TAG, "Starting tracking service with blockId: " + blockId)
        WakefulBroadcastReceiver.completeWakefulIntent(intent)

        if (trackingController == null) {
            trackingController = TrackingController(this, tripId, routeId, blockId)
            trackingController?.start()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        stopForeground(true)
        Log.i(TAG, "service destroy")
        sendBroadcast(Intent(ACTION_STOPPED))
        //StatusActivity.addMessage(getString(R.string.status_service_destroy))
        NotificationsFragment.addMessage(getString(R.string.status_service_destroy))
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        trackingController?.stop()
    }

    companion object {

        const val ACTION_STARTED = "au.mymetro.operator.action.SERVICE_STARTED"
        const val ACTION_STOPPED = "au.mymetro.operator.action.SERVICE_STOPPED"
        private val TAG = TrackingService::class.java.simpleName
        private const val NOTIFICATION_ID = 1

        @SuppressLint("UnspecifiedImmutableFlag")
        private fun createNotification(context: Context): Notification {
            val builder = NotificationCompat.Builder(context, MainApplication.PRIMARY_CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
            val intent = Intent(context, HomeActivity::class.java)
            builder
                .setContentTitle(context.getString(R.string.settings_status_on_summary))
                .setTicker(context.getString(R.string.settings_status_on_summary))
                .color = ContextCompat.getColor(context, R.color.primary_dark)
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            builder.setContentIntent(PendingIntent.getActivity(context, 0, intent, flags))
            return builder.build()
        }
    }
}

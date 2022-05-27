package com.hms.advancedlocationlibrary.services

import android.app.Activity
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.hms.advancedlocationlibrary.AdvancedLocation.Companion.mLocationDatabase
import com.hms.advancedlocationlibrary.AdvancedLocation.Companion.mLocationManager
import com.hms.advancedlocationlibrary.AdvancedLocation.Companion.mPermissionManager
import com.hms.advancedlocationlibrary.database.dto.LocationDto
import com.hms.advancedlocationlibrary.data.model.holders.TaskData
import com.hms.advancedlocationlibrary.utils.Constants.FROM_ACTIVITY
import com.hms.advancedlocationlibrary.utils.Constants.LOG_PREFIX
import com.hms.advancedlocationlibrary.utils.Constants.TASK_REQUEST
import com.hms.advancedlocationlibrary.utils.NotificationUtils
import com.hms.advancedlocationlibrary.utils.Utils

/**
 *  This service handles obtaining and saving of only location data.
 *  Displays a notification to user to inform that location is being shared.
 */
internal class LocationService : Service() {

    companion object {
        private const val TAG = "${LOG_PREFIX}LocationSharingService"

        private const val ONGOING_NOTIFICATION_ID = 418
        const val INTENT_REQUEST_CODE = 419
    }

    private var mBinder: Binder? = null

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind()")
        return mBinder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand()")
        handleIntent(intent)
        return START_REDELIVER_INTENT
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy()")
        super.onDestroy()
    }

    /**
     *  If location permissions are granted by user, starts location updates and saves retrieved
     *  location data to Cloud DB.
     */
    private fun startLocationSharing(taskData: TaskData) {
        try {
            mPermissionManager.doIfLocationPermitted {
                mLocationManager.startLocationUpdates(taskData) { result ->
                    Log.d(TAG, "LocationResultListener --> onResult: $result")

                    mLocationDatabase?.insertLocation(
                        LocationDto(
                            latitude = result.latitude,
                            longitude = result.longitude,
                            currentTime = Utils.getCurrentTime()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "startLocationSharing --> Error: ", e)
        }
    }

    /**
     *  Displays notification and starts Foreground service.
     */
    private fun start(activityClass: Class<out Activity>?) {
        startForeground(
            ONGOING_NOTIFICATION_ID,
            NotificationUtils.getForegroundServiceNotification(applicationContext, activityClass)
        )
    }

    /**
     *  Handles incoming intent. Extracts activity and taskRequest from intent and starts location
     *  updates.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent != null) {
            val activityName = intent.getStringExtra(FROM_ACTIVITY)

            if (activityName != null) {
                val activityClass = Class.forName(activityName).asSubclass(Activity::class.java)
                start(activityClass)
            } else {
                start(null)
            }

            val taskRequest = intent.getParcelableExtra<TaskData>(TASK_REQUEST)

            if (taskRequest != null) {
                startLocationSharing(taskRequest)
            } else {
                Log.w(TAG, "onStartCommand --> Task request is null.")
            }
        } else {
            Log.w(TAG, "onStartCommand --> Intent is null.")
        }
    }

    /**
     *  Clear DB values written before
     */
    private fun clearDB(){
        mLocationDatabase?.clearDB()
    }
}
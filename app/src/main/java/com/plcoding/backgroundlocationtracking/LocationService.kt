package com.plcoding.backgroundlocationtracking

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.provider.Settings
import android.content.Intent
import android.os.Build

import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationServices
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.database.database
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch

import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.lang.Math.cos
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.sqrt

class LocationService: Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationClient: LocationClient
    var ID = "";
    val database = Firebase.database
     var myref = database.getReference();
     var long =0.0;
    var lat = 0.0;
    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        locationClient = DefaultLocationClient(
            applicationContext,
            LocationServices.getFusedLocationProviderClient(applicationContext)
        )
        ID = Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID)
        myref = database.getReference("Users").child(ID)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> stop()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun start() {

        Firebase.database.getReference("Users").child(ID).child("Status").setValue("Active")
        val current = LocalDateTime.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val formatted = current.format(formatter)
        var cnt = 0;
        var start   =0;
        val notification = NotificationCompat.Builder(this, "location")
            .setContentTitle("Tracking location...")
            .setContentText("Location: null")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setOngoing(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        locationClient
            .getLocationUpdates(10000L)
            .catch { e -> e.printStackTrace() }
            .onEach { location ->
                if(start==0) {
                    myref.child("History").child(formatted).child(cnt.toString()).child("Latitude")
                        .setValue(location.latitude.toString())
                    myref.child("History").child(formatted).child(cnt.toString()).child("Longitude")
                        .setValue(location.longitude.toString())
                    start++;
                    long = location.longitude;
                    lat = location.latitude

                    cnt++;

                }
                else
                {
                    if(haversine(long, lat, location.longitude,location.latitude)>20 && location.accuracy<100 ) {

                        Log.d("Distance","Distance is Greater than 5m")

                        myref.child("History").child(formatted).child(cnt.toString())
                            .child("Latitude")
                            .setValue(location.latitude.toString())
                        myref.child("History").child(formatted).child(cnt.toString())
                            .child("Longitude")
                            .setValue(location.longitude.toString())
                        long = location.longitude;
                        lat = location.latitude
                        cnt++;
                    }
                    else
                    {
                        Log.d("Distance","Distance is Less than 5m")
                    }
                }
                myref.child("Live").child("Longitude").setValue(location.longitude.toString())
                myref.child("Live").child("Latitude").setValue(location.latitude.toString())

                Log.d("Location",location.latitude.toString())
                Log.d("Location",location.longitude.toString())
                Log.d("Location",ID)

                val lat = location.latitude.toString()
                val long = location.longitude.toString()
                val updatedNotification = notification.setContentText(
                    "Location: ($lat, $long)"
                )
                notificationManager.notify(1, updatedNotification.build())
            }
            .launchIn(serviceScope)

        startForeground(1, notification.build())
    }

    private fun stop() {
        val ID = Settings.Secure.getString(this.contentResolver, Settings.Secure.ANDROID_ID)
        Firebase.database.getReference("Users").child(ID).child("Status").setValue("Inactive")

        println("Turning oFF")
        Log.d("Turning oFF",ID)
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }
    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371e3 // Radius of the Earth in meters
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLat = Math.toRadians(lat2 - lat1)
        val deltaLon = Math.toRadians(lon2 - lon1)

        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLon / 2) * sin(deltaLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c // Distance in meters
    }
}
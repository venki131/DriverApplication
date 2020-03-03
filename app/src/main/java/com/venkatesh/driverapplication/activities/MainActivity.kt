package com.venkatesh.driverapplication.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker.PERMISSION_DENIED
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import com.google.android.gms.location.*
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.PolylineOptions
import com.venkatesh.driverapplication.R
import com.venkatesh.driverapplication.helpers.FirebaseHelper
import com.venkatesh.driverapplication.helpers.GoogleMapHelper
import com.venkatesh.driverapplication.helpers.MarkerAnimationHelper
import com.venkatesh.driverapplication.helpers.UiHelper
import com.venkatesh.driverapplication.interfaces.IPositiveNegativeListener
import com.venkatesh.driverapplication.interfaces.LatLngInterpolator
import com.venkatesh.driverapplication.models.Driver
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_ACCESS_FINE_LOCATION = 100
    }

    private val TAG = "MainActivity"
    private lateinit var googleMap: GoogleMap
    private lateinit var locationProviderClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var locationFlag = true
    private var driverOnlineFlag = true
    private var currentPositionMarker: Marker? = null
    private val googleMapHelper =
        GoogleMapHelper()
    private val firebaseHelper =
        FirebaseHelper("007")
    private val markerAnimationHelper =
        MarkerAnimationHelper()
    private val uiHelper = UiHelper()

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val mapFragment: SupportMapFragment =
            supportFragmentManager.findFragmentById(R.id.supportMap) as SupportMapFragment
        mapFragment.getMapAsync { googleMap = it }
        createLocationCallback()
        locationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = uiHelper.getLocationRequest()
        if (!uiHelper.isPlayServicesAvailable(this)) {
            Toast.makeText(this, "Play services not installed", Toast.LENGTH_LONG).show()
            finish()
        } else requestLocationUpdate()

        val driverStatusTextView = findViewById<TextView>(R.id.driverStatusTextView)
        driverStatusSwitch.setOnCheckedChangeListener { _, b ->
            driverOnlineFlag = b
            if (driverOnlineFlag) driverStatusTextView.text = "Driver Online"
            else {
                driverStatusTextView.text = "Driver Offline"
                firebaseHelper.deleteDriver()
            }
        }
    }

    /**
     * get current location and update in Firebase RealTime Database if driverOnlineFlag is true
     */
    private fun createLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                super.onLocationResult(locationResult)
                if (locationResult!!.lastLocation == null) return
                val latLng = LatLng(
                    locationResult.lastLocation.latitude,
                    locationResult.lastLocation.longitude
                )
                Log.e(TAG, latLng.latitude.toString() + " " + latLng.longitude.toString())
                if (locationFlag) {
                    locationFlag = false
                    animateCamera(latLng)
                }

                if (driverOnlineFlag) firebaseHelper.updateDriver(
                    Driver(
                        lat = latLng.latitude,
                        lng = latLng.longitude
                    )
                )
                showOrAnimateMarker(latLng)
                val destination = LatLng(12.934533, 77.626579)
                drawPolyline(latLng, destination)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdate() {
        if (!uiHelper.isLocationPermissionEnabled(this)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_ACCESS_FINE_LOCATION
            )
            return
        }

        if (uiHelper.isLocationProviderEnabled(this))
            uiHelper.showPositiveDialogWithListener(
                this,
                resources.getString(R.string.need_location),
                resources.getString(R.string.location_content),
                object :
                    IPositiveNegativeListener {
                    override fun onPositive() {
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    }
                },
                "Turn On",
                false
            )
        locationProviderClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.myLooper()
        )
    }

    /**
     * animate to current latLng
     * addMarker is current latLng is null
     */
    private fun showOrAnimateMarker(latLng: LatLng) {
        if (currentPositionMarker == null)
            currentPositionMarker =
                googleMap.addMarker(googleMapHelper.getDriverMarkerOption(latLng))
        else
            markerAnimationHelper.animateMarkerToGB(
                currentPositionMarker!!,
                latLng,
                LatLngInterpolator.Spherical()
            )
    }

    private fun drawPolyline(source: LatLng, destination: LatLng) {
        var polyLineOption = PolylineOptions()
        polyLineOption.color(Color.BLACK)
        polyLineOption.width(3F)
        polyLineOption.geodesic(true)
        polyLineOption.add(source)
        polyLineOption.add(destination)

        /*
        *****Enable billing to use Directions API*****

        var strOrigin = "origin" + source.latitude + "," + source.longitude
        var strDestination = "destination" + destination.latitude + "," + destination.longitude
        val sensor = "sensor=false"
        val mode = "mode=driving"

        val parameters = "$strOrigin&$strDestination&$sensor&$mode"
        val key = "&key=AIzaSyD9L_crcezrfhgNZIBIUNlfOnDAxmnVcIE"
        val url = "https://maps.googleapis.com/maps/api/directions/json?$parameters$key"
        Log.d(TAG, url)*/
        googleMap.addPolyline(polyLineOption)
    }

    /**
     * animate GoogleMap to driver current location
     */
    private fun animateCamera(latLng: LatLng) {
        googleMap.animateCamera(googleMapHelper.buildCameraUpdate(latLng))
    }

    /**
     * permission check
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_ACCESS_FINE_LOCATION) {
            if (grantResults[0] == PERMISSION_DENIED) {
                Toast.makeText(this, "Location Permission Denied!", Toast.LENGTH_LONG).show()
                finish()
            } else if (grantResults[0] == PERMISSION_GRANTED) requestLocationUpdate()
        }
    }
}

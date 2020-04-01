package com.akristic.simplelastquake

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.akristic.simplelastquake.databinding.EarthquakeActivityBinding
import com.google.android.gms.location.*
import com.google.android.material.chip.Chip
import kotlinx.android.synthetic.main.earthquake_activity.*
import java.util.*

class EarthquakeActivity : AppCompatActivity() {
    val PERMISSION_ID = 42
    lateinit var mFusedLocationClient: FusedLocationProviderClient

    lateinit var viewModel: EarthquakeViewModel
    private var location: Location? = null

    /** Adapter for the list of earthquakes  */
    private var mAdapter: EarthquakeAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.earthquake_activity)
        val viewModelFactory = ViewModelProvider.AndroidViewModelFactory(application)
        viewModel = ViewModelProvider(this, viewModelFactory).get(EarthquakeViewModel::class.java)


        val binding: EarthquakeActivityBinding =
            DataBindingUtil.setContentView(this, R.layout.earthquake_activity)
        //binding.usersListViewModel = viewModel
        // Find a reference to the {@link ListView} in the layout

        binding.list.emptyView = binding.emptyView

        // Create a new adapter that takes an empty list of earthquakes as input
        mAdapter = EarthquakeAdapter(this, ArrayList<Earthquake>())

        // Set the adapter on the {@link ListView}
        // so the list can be populated in the user interface
        binding.list.adapter = mAdapter
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        // Set an item click listener on the ListView, which sends an intent to a web browser
        // to open a website with more information about the selected earthquake.
        binding.list.onItemClickListener =
            OnItemClickListener { adapterView, view, position, l -> // Find the current earthquake that was clicked on
                val currentEarthquake: Earthquake? = mAdapter!!.getItem(position)

                // Convert the String URL into a URI object (to pass into the Intent constructor)
                val earthquakeUri = Uri.parse(currentEarthquake?.url)

                // Create a new intent to view the earthquake URI
                val websiteIntent = Intent(Intent.ACTION_VIEW, earthquakeUri)

                // Send the intent to launch a new activity
                startActivity(websiteIntent)
            }


        viewModel.earthquakes.observe(this, androidx.lifecycle.Observer {
            binding.loadingIndicator.visibility = View.GONE
            // Set empty state text to display "No earthquakes found."
            binding.emptyView.setText(R.string.no_earthquakes)

            // Clear the adapter of previous earthquake data
            mAdapter?.clear()

            // If there is a valid list of {@link Earthquake}s, then add them to the adapter's
            // data set. This will trigger the ListView to update.
            if (it != null && it.isNotEmpty()) {
                mAdapter?.addAll(it)
            }

        })
        viewModel.loading.observe(this, androidx.lifecycle.Observer {
            if(it == true){
                binding.loadingIndicator.visibility=View.VISIBLE
            }
        })

        viewModel.requestQuery.observe(this, androidx.lifecycle.Observer {
            viewModel.getEarthquakes()
        })
        //creating chips from regions in earthquake list
        val chipGroup = binding.filterList
        val inflater = LayoutInflater.from(chipGroup.context)
        val children: MutableList<Chip> = mutableListOf()
        for (i in 1..9) {
            val chip = inflater.inflate(R.layout.filter_chip, chipGroup, false) as Chip
            chip.text = "Mag > $i"
            chip.tag = "$i"
            chip.setOnCheckedChangeListener { button, isChecked ->
                viewModel.onFilterChanged(button.tag as String, isChecked)
            }
            children.add(chip)
        }
        chipGroup.removeAllViews()
        for (chip in children) {
            chipGroup.addView(chip)
        }

        val chipGroupNearMe = binding.nearMe
        val inflaterNear = LayoutInflater.from(chipGroupNearMe.context)
        val childrenNear: MutableList<Chip> = mutableListOf()

        //near me
        val chipNearMe =
            inflaterNear.inflate(R.layout.location_chip, chipGroupNearMe, false) as Chip
        chipNearMe.text = "Near Me"
        chipNearMe.tag = "LOCATION"
        chipNearMe.setOnCheckedChangeListener { button, isChecked ->
            getLastLocation()
            val currentLocation = location
            if (currentLocation != null) {
                viewModel.onLocationChanged(
                    button.tag as String,
                    currentLocation.latitude.toString(),
                    currentLocation.longitude.toString(),
                    isChecked
                )
            }
        }
        childrenNear.add(chipNearMe)
        //chip europe
        val chipEurope =
            inflaterNear.inflate(R.layout.location_chip, chipGroupNearMe, false) as Chip
        chipEurope.text = "EMSC Data"
        chipEurope.tag = "EUROPE"
        chipEurope.setOnCheckedChangeListener { button, isChecked ->
                viewModel.onEMSCDataChanged(button.tag as String, isChecked)
            }

        childrenNear.add(chipEurope)

        //chip europe
        val chipAmerica =
            inflaterNear.inflate(R.layout.location_chip, chipGroupNearMe, false) as Chip
        chipAmerica.text = "USGS Data"
        chipAmerica.tag = "AMERICA"
        chipAmerica.setOnCheckedChangeListener { button, isChecked ->
            viewModel.onUSGSDataChanged(button.tag as String, isChecked)
        }
        childrenNear.add(chipAmerica)

        //clear chips if exist
        chipGroupNearMe.removeAllViews()
        //add created chips
        for (chip in childrenNear) {
            chipGroupNearMe.addView(chip)
        }
        //get location on startup
        getLastLocation()
    }

    private fun checkPermissions(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return false
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
            PERMISSION_ID
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_ID) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                getLastLocation()
            }
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocation() {
        if (checkPermissions()) {
            if (isLocationEnabled()) {

                mFusedLocationClient.lastLocation.addOnCompleteListener(this) { task ->
                    val locationCurrent = task.result
                    if (locationCurrent == null) {
                        requestNewLocationData()
                    } else {
                        location = locationCurrent
                    }
                }
            } else {
                Toast.makeText(this, "Turn on location", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
        } else {
            requestPermissions()
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {
        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_NO_POWER
        mLocationRequest.interval = 0
        mLocationRequest.fastestInterval = 0
        mLocationRequest.numUpdates = 1

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            Toast.makeText(
                applicationContext,
                "lat: " + mLastLocation.latitude.toString() + "\nlon: " + mLastLocation.longitude.toString(),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}


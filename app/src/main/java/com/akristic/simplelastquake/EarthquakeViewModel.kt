package com.akristic.simplelastquake

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*

class EarthquakeViewModel(application: Application) : AndroidViewModel(application) {
    private var filter = FilterHolder()

    companion object {
        private const val USGS_REQUEST_URL =
            "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&orderby=time&limit=100"
        private const val QUERY_MAIN =
            "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson"
        private const val QUERY_ORDER_TIME = "&orderby=time"
        private const val QUERY_LIMIT_100 = "&limit=100"
        private const val QUERY_MIN_MAG = "&minmagnitude="
        private const val QUERY_MAXRADIUS = "&maxradius=5"
        private const val EMSC_QUERY_URL =
            "https://www.seismicportal.eu/fdsnws/event/1/query?format=json&limit=100&orderby=time"
    }

    private var loadDataJob: Job = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + loadDataJob)
    val _earthquakes = MutableLiveData<List<Earthquake?>>()
    val earthquakes: LiveData<List<Earthquake?>>
        get() = _earthquakes
    val _requestQuery = MutableLiveData<String>()
    val requestQuery: LiveData<String>
        get() = _requestQuery

    /* So we can add it to query
    "&minmagnitude=3" example of string
     */
    val _magnitude = MutableLiveData<String>()
    val magnitude: LiveData<String>
        get() = _magnitude

    // langitude and longitude saved in string ready to be added to query
    //"&latitude=41.81587777&longitude=16.15888888" looks like this
    val _location = MutableLiveData<String>()
    val location: LiveData<String>
        get() = _location


    //for showing data loader and hiding it
    val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean>
        get() = _loading

    //location switch
    val _near_me_is_on = MutableLiveData<Boolean>()
    val near_me_is_on: LiveData<Boolean>
        get() = _near_me_is_on

    /*
    server America USGS = 1
    server Europe EMSC = 2 //default
     */
    val _serverName = MutableLiveData<Int>()
    val serverName: LiveData<Int>
        get() = _serverName


    init {
        _requestQuery.value = USGS_REQUEST_URL
        _magnitude.value = ""
        _location.value = ""
        _serverName.value = 2 //default is set Europe
        _near_me_is_on.value = false
        setQuery()
        getEarthquakes()
    }

    fun getEarthquakes() {
        uiScope.launch {
            _loading.value = true
            _earthquakes.value = getEarthquakesFromInternet()
            _loading.value = false
        }
    }

    private suspend fun getEarthquakesFromInternet(): List<Earthquake>? {
        return withContext(Dispatchers.IO) {
            QueryUtils.fetchEarthquakeData(
                requestQuery.value ?: USGS_REQUEST_URL,
                serverName.value ?: 1
            )
        }
    }

    fun onFilterChanged(filter: String, isChecked: Boolean) {
        if (this.filter.update(filter, isChecked)) {
            if (isChecked) {
                _magnitude.value = QUERY_MIN_MAG + filter
                if (serverName.value == 1) {
                    _requestQuery.value =
                        QUERY_MAIN + QUERY_ORDER_TIME + QUERY_LIMIT_100 + magnitude.value + location.value
                } else if (serverName.value == 2) {
                    _requestQuery.value =
                        EMSC_QUERY_URL + magnitude.value + location.value
                }

                getEarthquakes()
            } else {
                _magnitude.value = ""
                if (serverName.value == 1) {
                    _requestQuery.value =
                        QUERY_MAIN + QUERY_ORDER_TIME + QUERY_LIMIT_100 + location.value
                } else if (serverName.value == 2) {
                    _requestQuery.value =
                        EMSC_QUERY_URL + location.value
                }

                getEarthquakes()
            }
        }
    }

    fun onEMSCDataChanged() {
        _serverName.value = 2
        setQuery()
        getEarthquakes()
    }

    fun onUSGSDataChanged() {
        _serverName.value = 1
        setQuery()
        getEarthquakes()
    }

    fun onLocationChanged(lat: String, lon: String) {
        _near_me_is_on.value = !near_me_is_on.value!!
        if (near_me_is_on.value!!) {
            _location.value = "&latitude=" + lat + "&longitude=" + lon + QUERY_MAXRADIUS
        } else {
            _location.value = ""
        }
        setQuery()
        getEarthquakes()
    }

    private fun setQuery(){
        if (serverName.value == 1) {
            _requestQuery.value =
                QUERY_MAIN + QUERY_ORDER_TIME + QUERY_LIMIT_100 + magnitude.value + location.value
        } else if (serverName.value == 2) {
            _requestQuery.value =
                EMSC_QUERY_URL + magnitude.value + location.value
        }
    }
    private class FilterHolder {
        var currentValue: String? = null
            private set

        fun update(changedFilter: String, isChecked: Boolean): Boolean {
            if (isChecked) {
                currentValue = changedFilter
                return true
            } else if (currentValue == changedFilter) {
                currentValue = null
                return true
            }
            return false
        }
    }
}
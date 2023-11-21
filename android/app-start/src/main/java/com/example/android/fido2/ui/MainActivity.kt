/*
 * Copyright 2021 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.fido2.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.example.android.fido2.R
import com.example.android.fido2.repository.SignInState
import com.example.android.fido2.ui.auth.AuthFragment
import com.example.android.fido2.ui.home.HomeFragment
import com.example.android.fido2.ui.username.UsernameFragment
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.fido.Fido
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task

import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    /*--------------WiFi Scanning----------------*/
    private val apSsid: String = "esp8266 AP"

    private var wifiStat: Boolean = false
    private val wifiManager: WifiManager get() = this.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var resultList = ArrayList<ScanResult>()
    private var ssidList = ArrayList<String>()

    private val PERMISSIONS_REQUEST_CODE = 1

    private val wifiScanReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            if (success) {
                scanSuccess()
            } else {
                // scan failure handling
                scanFailure()
            }
        }
    }

    @SuppressLint("NewApi")
    private fun scanSuccess() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSIONS_REQUEST_CODE
            );
        }

        Log.d("Scan", "Scan Success, adding results to list")
        resultList = wifiManager.getScanResults() as ArrayList<ScanResult>

        for (ssid in resultList){
            if (ssid.wifiSsid.toString().isNotEmpty()) {
                var ssidString = ssid.wifiSsid.toString()
                ssidString = ssidString.replace(("[^\\w\\d ]").toRegex(), "")
                ssidList.add(ssidString)
            }
        }
    }

    @SuppressLint("NewApi")
    private fun scanFailure() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSIONS_REQUEST_CODE)
        }

        Log.d("Scan", "Scan Failure, using old results instead")
        resultList = wifiManager.getScanResults() as ArrayList<ScanResult>

        for (ssid in resultList){
            if (ssid.wifiSsid.toString().isNotEmpty()){
                var ssidString = ssid.wifiSsid.toString()
                ssidString = ssidString.replace(("[^\\w\\d ]").toRegex(), "")
                ssidList.add(ssidString)
            }
        }
    }
    /*--------------WiFi Scanning----------------*/


    /*--------------WiFi Connection--------------*/
    private val apSuggestion = WifiNetworkSuggestion.Builder()
    private val apSpecifier = WifiNetworkSpecifier.Builder()
    private val apRequest = NetworkRequest.Builder()
    private val connectivityManager: ConnectivityManager get() = this.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager


    private val suggestionPostConnectionReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!intent.action.equals(WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION)) {
                Log.d("Connection", "WifiManager != ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION")
                return
            }
            Log.d("Connection", "connectByWifiNetworkSuggestion: onReceive: ")
            // do post connect processing here
        }
    }

    private fun addNetwork() {
        apSuggestion.setSsid(apSsid)
        apSpecifier.setSsid(apSsid)
        apRequest.addTransportType(NetworkCapabilities.TRANSPORT_WIFI).setNetworkSpecifier(apSpecifier.build())

        Log.d("Connection", "addNetwork")
        val suggestionsList = listOf(apSuggestion.build())
        val resultValue = wifiManager.addNetworkSuggestions(suggestionsList)
        val resultKey = when (resultValue) {
            WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS -> "STATUS_NETWORK_SUGGESTIONS_SUCCESS"
            WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_INTERNAL -> "STATUS_NETWORK_SUGGESTIONS_ERROR_INTERNAL"
            WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_APP_DISALLOWED -> "STATUS_NETWORK_SUGGESTIONS_ERROR_APP_DISALLOWED"
            WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE -> "STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_DUPLICATE"
            WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_EXCEEDS_MAX_PER_APP -> "STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_EXCEEDS_MAX_PER_APP"
            WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_REMOVE_INVALID -> "STATUS_NETWORK_SUGGESTIONS_ERROR_REMOVE_INVALID"
            WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_NOT_ALLOWED -> "STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_NOT_ALLOWED"
            WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID -> "STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID"
            else -> ""
        }
        Log.d("Connection", "Connect: result: $resultValue: $resultKey")
        Toast.makeText(this, "result: $resultValue: $resultKey", Toast.LENGTH_SHORT).show()

        Log.d("Connection", "Register receiver")
        registerReceiver(suggestionPostConnectionReceiver, IntentFilter(WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION))
    }

    private fun connect(){
        Log.d("Connection", "networkCallback")
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                connectivityManager.bindProcessToNetwork(network)
                addNetwork()
            }
        }
        networkCallback.let {
            Log.d("Connection", "requestNetwork")
            connectivityManager.requestNetwork(apRequest.build(), it)
        }
    }
    /*--------------WiFi Connection--------------*/
    private val REQUEST_CHECK_SETTINGS = 0x1
    private var gps: Boolean = false

    private fun checkLocationSetting()
    {
        val locationRequest = LocationRequest.create()
        locationRequest.apply {
            priority=LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 10000
        }

        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
        builder.setAlwaysShow(true)

        val result: Task<LocationSettingsResponse> = LocationServices.getSettingsClient(applicationContext)
            .checkLocationSettings(builder.build())

        result.addOnCompleteListener {
            try{
                val response: LocationSettingsResponse = it.getResult(ApiException::class.java)
                Log.d("GPS", "checkSetting: GPS On")
                gps = true
            }catch(e:ApiException){

                when(e.statusCode){
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED ->{
                        val resolvableApiException = e as ResolvableApiException
                        resolvableApiException.startResolutionForResult(this@MainActivity, REQUEST_CHECK_SETTINGS)
                        Log.d("GPS", "checkSetting: RESOLUTION_REQUIRED")
                    }

                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                        Toast.makeText(this@MainActivity, "GPS services unavailable", Toast.LENGTH_SHORT).show()
                        Log.d("GPS", "No GPS on device")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        setSupportActionBar(findViewById(R.id.toolbar))

        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) != PackageManager.PERMISSION_GRANTED ||
            checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.ACCESS_NETWORK_STATE),
                PERMISSIONS_REQUEST_CODE)
        }else{
            //Check GPS services
            if(!gps) {
                checkLocationSetting()
            }
            //Check Wi-Fi services
            if(!wifiManager.isWifiEnabled) {
                Toast.makeText(this, "Error, please turn on Wifi services and open the app again", Toast.LENGTH_LONG).show()
                val panelIntent = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                startActivityForResult(panelIntent, 0)
            }else{
                val intentFilter = IntentFilter()
                intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                this.registerReceiver(wifiScanReceiver, intentFilter)

                Log.d("Scan", "Starting scan")

                resultList.clear()
                ssidList.clear()
                val success = wifiManager.startScan()

                if (!success) {
                    Log.d("Scan", "Start scan failed")
                    scanFailure()
                }else{
                    Log.d("Scan", "Start scan success")
                    scanSuccess()
                }

                if (wifiStat) {
                    Log.d("Scan", "Success, $apSsid found")
                    //main
                    lifecycleScope.launchWhenStarted {
                        viewModel.signInState.collect { state ->
                            when (state) {
                                is SignInState.SignedOut -> {
                                    showFragment(UsernameFragment::class.java) { UsernameFragment() }
                                }
                                is SignInState.SigningIn -> {
                                    showFragment(AuthFragment::class.java) { AuthFragment() }
                                }
                                is SignInState.SignInError -> {
                                    Toast.makeText(this@MainActivity, state.error, Toast.LENGTH_LONG).show()
                                    // return to username prompt
                                    showFragment(UsernameFragment::class.java) { UsernameFragment() }
                                }
                                is SignInState.SignedIn -> {
                                    connect()
                                    showFragment(HomeFragment::class.java) { HomeFragment() }
                                }
                            }
                        }
                    }
                }else{
                    //setContentView(R.layout.wifi_result)
                    Log.d("Scan", "Failed, $apSsid not found")
                    Toast.makeText(this, "Failed, $apSsid not found", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setFido2ApiClient(Fido.getFido2ApiClient(this))
    }

    override fun onPause() {
        super.onPause()
        viewModel.setFido2ApiClient(null)
    }

    private fun showFragment(clazz: Class<out Fragment>, create: () -> Fragment) {
        val manager = supportFragmentManager
        if (!clazz.isInstance(manager.findFragmentById(R.id.container))) {
            manager.commit {
                replace(R.id.container, create())
            }
        }
    }

}

package dev.prince.mapboxsample

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.app.ActivityCompat
import androidx.core.app.ComponentActivity
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.tasks.await

class LocationService {

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context, activity: ComponentActivity, onLocationServicesEnabled: () -> Unit): Location {
        if (!context.hasPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        ) {
            throw LocationServiceException.MissingPermissionException()
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled || !isNetworkEnabled) {
            requestLocationServices(activity) { enabled ->
                if (enabled) {
                    onLocationServicesEnabled()
                } else {
                    throw LocationServiceException.LocationDisabledException()
                }
            }
        }

        val locationProvider = LocationServices.getFusedLocationProviderClient(context)
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        return try {
            val location = locationProvider.getCurrentLocation(request, null).await()
            location
        } catch (e: Exception) {
            throw LocationServiceException.UnknownException(e)
        }
    }

    fun Context.hasPermissions(vararg permissions: String) =
        permissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    sealed class LocationServiceException : Exception() {
        class MissingPermissionException : LocationServiceException()
        class LocationDisabledException : LocationServiceException()
        class NoNetworkEnabledException : LocationServiceException()
        class UnknownException(val exception: Exception) : LocationServiceException()
    }

    private fun requestLocationServices(activity: androidx.core.app.ComponentActivity, onResult: (Boolean) -> Unit) {
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        val client: SettingsClient = LocationServices.getSettingsClient(activity)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener {
            onResult(true)
        }

        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try {
                    exception.startResolutionForResult(activity, REQUEST_CHECK_SETTINGS)
                } catch (sendEx: IntentSender.SendIntentException) {
                    onResult(false)
                }
            } else {
                onResult(false)
            }
        }
    }

    companion object {
        const val REQUEST_CHECK_SETTINGS = 1001
    }
}

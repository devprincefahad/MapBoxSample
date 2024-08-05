package dev.prince.mapboxsample

import android.content.Context
import android.util.Log
import androidx.core.app.ComponentActivity
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.MapboxDirections
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.core.constants.Constants.PRECISION_6
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MapViewModel(
    private val context: Context,
    private val locationService: LocationService
) : ViewModel() {

    private val _userLocation = MutableStateFlow<Point?>(null)
    val userLocation: StateFlow<Point?> = _userLocation.asStateFlow()

    private val _showRoute = MutableStateFlow(false)
    val showRoute: StateFlow<Boolean> = _showRoute.asStateFlow()

    private val _routeGeometry = MutableStateFlow<List<Point>>(emptyList())
    val routeGeometry: StateFlow<List<Point>> = _routeGeometry.asStateFlow()

    private var mapboxMap: MapboxMap? = null
    private var pointAnnotationManager: PointAnnotationManager? = null
    private var polylineAnnotationManager: PolylineAnnotationManager? = null

    fun setMapboxMap(map: MapboxMap) {
        mapboxMap = map
    }

    fun setAnnotationManagers(point: PointAnnotationManager, polyline: PolylineAnnotationManager) {
        pointAnnotationManager = point
        polylineAnnotationManager = polyline
    }

    fun updateUserLocation(location: Point) {
        _userLocation.value = location
        updateMapLocation(location)
        updateLocationMarkers(location)
    }

    fun setShowRoute(show: Boolean) {
        _showRoute.value = show
        if (show) {
            createRoute()
        }
    }

    fun clearRoute() {
        _showRoute.value = false
        _routeGeometry.value = emptyList()
        polylineAnnotationManager?.deleteAll()
    }

    fun getUserLocation(activity: ComponentActivity, onLocationUpdated: (Point) -> Unit) {
        viewModelScope.launch {
            try {
                locationService.getCurrentLocation(context, activity) {
                    getUserLocation(activity, onLocationUpdated)
                }.let { location ->
                    val point = Point.fromLngLat(location.longitude, location.latitude)
                    onLocationUpdated(point)
                }
            } catch (e: Exception) {
                handleLocationError(e)
            }
        }
    }

    private fun updateMapLocation(location: Point) {
        mapboxMap?.flyTo(
            CameraOptions.Builder()
                .center(location)
                .zoom(15.0)
                .build(),
            MapAnimationOptions.Builder()
                .duration(1000)
                .build()
        )
    }

    private fun updateLocationMarkers(userLocation: Point) {
        pointAnnotationManager?.let { manager ->
            manager.deleteAll()

            // User location marker
            val userLocationBitmap = context.getDrawable(R.drawable.user_location)?.toBitmap()
            userLocationBitmap?.let { bitmap ->
                val userLocationOptions = PointAnnotationOptions()
                    .withPoint(userLocation)
                    .withIconImage(bitmap)
                manager.create(userLocationOptions)
            }

            // Destination marker
            val destinationBitmap = context.getDrawable(R.drawable.placeholder)?.toBitmap()
            destinationBitmap?.let { bitmap ->
                val destinationOptions = PointAnnotationOptions()
                    .withPoint(DESTINATION)
                    .withIconImage(bitmap)
                manager.create(destinationOptions)
            }
        }
    }

    private fun createRoute() {
        val origin = _userLocation.value ?: return
        MapboxDirections.builder()
            .origin(origin)
            .destination(DESTINATION)
            .overview(DirectionsCriteria.OVERVIEW_FULL)
            .profile(DirectionsCriteria.PROFILE_DRIVING)
            .accessToken(context.getString(R.string.mapbox_access_token))
            .build()
            .enqueueCall(object : Callback<DirectionsResponse> {
                override fun onResponse(
                    call: Call<DirectionsResponse>,
                    response: Response<DirectionsResponse>
                ) {
                    response.body()?.routes()?.firstOrNull()?.geometry()?.let { geometry ->
                        val routeGeometry = LineString.fromPolyline(geometry, PRECISION_6).coordinates()
                        _routeGeometry.value = routeGeometry
                        drawRouteOnMap(routeGeometry)
                    }
                }

                override fun onFailure(call: Call<DirectionsResponse>, t: Throwable) {
                    // Handle error
                }
            })
    }

    fun drawRouteOnMap(routeGeometry: List<Point>) {
        polylineAnnotationManager?.let { manager ->
            manager.deleteAll()
            val polylineAnnotationOptions = PolylineAnnotationOptions()
                .withPoints(routeGeometry)
                .withLineColor("#0062ff")
                .withLineWidth(5.0)
            manager.create(polylineAnnotationOptions)
        }
    }

    private fun handleLocationError(e: Exception) {
        val message = when (e) {
            is LocationService.LocationServiceException.LocationDisabledException -> "Please enable GPS"
            is LocationService.LocationServiceException.MissingPermissionException -> "Location permission is required"
            is LocationService.LocationServiceException.NoNetworkEnabledException -> "Please enable network"
            else -> "An unknown error occurred"
        }
        // You might want to use a LiveData or StateFlow to communicate this error to the UI
        // For simplicity, we'll just log it here
        Log.e("MapViewModel", message)
    }

    companion object {
        // delhi
        private val DESTINATION = Point.fromLngLat(77.2882359, 28.5351112)
    }
}
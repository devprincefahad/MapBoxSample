package dev.prince.mapboxsample

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.MapboxDirections
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.core.constants.Constants.PRECISION_6
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
import dev.prince.mapboxsample.ui.theme.MapBoxSampleTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MapViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MapViewModel(applicationContext, LocationService()) as T
            }
        })[MapViewModel::class.java]

        setContent {
            MapBoxSampleTheme {
                MapScreen(viewModel)
            }
        }
    }
}

@Composable
fun MapScreen(viewModel: MapViewModel) {
    val context = LocalContext.current
    val activity = context as ComponentActivity

    val userLocation by viewModel.userLocation.collectAsState()
    val showRoute by viewModel.showRoute.collectAsState()
    val routeGeometry by viewModel.routeGeometry.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            requestLocationServices(activity) { enabled ->
                if (enabled) {
                    viewModel.getUserLocation(activity) { newLocation ->
                        viewModel.updateUserLocation(newLocation)
                    }
                } else {
                    Toast.makeText(
                        context,
                        "Location services are required for this app",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (context.hasLocationPermission()) {
            requestLocationServices(activity) { enabled ->
                if (enabled) {
                    viewModel.getUserLocation(activity) { newLocation ->
                        viewModel.updateUserLocation(newLocation)
                    }
                } else {
                    Toast.makeText(
                        context,
                        "Location services are required for this app",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.weight(1f)
        ) {
            AndroidView(
                factory = { context ->
                    MapView(context).also { mapView ->
                        val map = mapView.getMapboxMap().apply {
                            loadStyleUri(Style.MAPBOX_STREETS)
                        }
                        viewModel.setMapboxMap(map)
                        val annotationApi = mapView.annotations
                        viewModel.setAnnotationManagers(
                            annotationApi.createPointAnnotationManager(),
                            annotationApi.createPolylineAnnotationManager()
                        )
                    }
                },
                update = { mapView ->
                    if (showRoute && routeGeometry.isNotEmpty()) {
                        viewModel.drawRouteOnMap(routeGeometry)
                    }
                }
            )
            IconButton(
                onClick = {
                    if (context.hasLocationPermission()) {
                        viewModel.getUserLocation(activity) { newLocation ->
                            viewModel.updateUserLocation(newLocation)
                        }
                    } else {
                        permissionLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(18.dp)
                    .size(38.dp)
                    .background(Color.Transparent, shape = CircleShape)
            ) {
                Icon(
                    painterResource(id = R.drawable.target),
                    contentDescription = "Current Location"
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    viewModel.setShowRoute(true)
                },
            ) {
                Text(text = "Start Navigation")
            }

            Button(
                onClick = {
                    viewModel.clearRoute()
                },
            ) {
                Text(text = "Clear Route")
            }
        }
    }
}

private fun requestLocationServices(activity: ComponentActivity, onResult: (Boolean) -> Unit) {
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

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        android.Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

// for usa in case of emulator
//private val DESTINATION = Point.fromLngLat(-122.088883, 37.4104279)

// delhi
private val DESTINATION = Point.fromLngLat(77.2882359, 28.5351112)

// bangalore
//private val DESTINATION = Point.fromLngLat(77.5076781, 12.9944368)
private const val REQUEST_CHECK_SETTINGS = 1001

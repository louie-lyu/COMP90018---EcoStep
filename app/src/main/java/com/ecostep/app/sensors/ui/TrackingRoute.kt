package com.ecostep.app.sensors.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import java.util.Locale

@Composable
fun TrackingRoute(viewModel: TrackingViewModel, onJourneySaved: (String) -> Unit) {
    val context = LocalContext.current
    val state by viewModel.tracking.collectAsState()
    val ui by viewModel.ui.collectAsState()
    var askedLocation by remember { mutableStateOf(false) }
    var needsSettings by remember { mutableStateOf(false) }

    LaunchedEffect(ui.savedJourneyId) {
        ui.savedJourneyId?.let {
            onJourneySaved(it)
            viewModel.onNavigated()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || hasFineLocation(context)) {
            needsSettings = false
            viewModel.start()
        } else {
            val activity = generateSequence<Context>(context) {
                (it as? ContextWrapper)?.baseContext
            }.filterIsInstance<Activity>().firstOrNull()
            needsSettings = askedLocation && activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.ACCESS_FINE_LOCATION,
                )
            viewModel.showPermissionMessage(
                "Precise location is needed for distance. Allow it, or enable it in app settings.",
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Journey tracking")
        Text(if (state.isRecording) "Recording" else "Ready to record")
        Text("Duration: ${state.elapsedSeconds / 60}:${(state.elapsedSeconds % 60).toString().padStart(2, '0')}")
        Text(String.format(Locale.getDefault(), "Distance: %.0f m", state.distanceMeters))
        Text(
            state.lastAccuracyMeters?.let {
                String.format(Locale.getDefault(), "GPS accuracy: %.0f m", it)
            } ?: "GPS: waiting for a valid fix",
        )
        Text("GPS samples: ${state.gpsCount}")
        Text("Accelerometer samples: ${state.accelCount}")
        Text("Gyroscope samples: ${state.gyroCount} (0 may mean unavailable)")
        state.sensorError?.let { Text(it) }
        ui.message?.let { Text(it) }
        if (ui.isSaving) CircularProgressIndicator()

        if (state.isRecording) {
            Button(onClick = viewModel::stop, enabled = !ui.isSaving, modifier = Modifier.fillMaxWidth()) {
                Text("End journey")
            }
        } else {
            Button(
                onClick = {
                    if (hasFineLocation(context)) {
                        viewModel.start()
                    } else {
                        askedLocation = true
                        val permissions = buildList {
                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                            add(Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        launcher.launch(permissions.toTypedArray())
                    }
                },
                enabled = !ui.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (ui.message != null && !needsSettings) "Retry permission" else "Start journey") }
        }
        if (needsSettings && !state.isRecording) {
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)),
                )
            }) { Text("Open app settings") }
        }
    }
}

private fun hasFineLocation(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

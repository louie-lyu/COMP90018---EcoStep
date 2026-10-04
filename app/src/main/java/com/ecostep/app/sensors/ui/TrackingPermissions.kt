package com.ecostep.app.sensors.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Returns a function that makes sure recording permissions are granted, asking only for the
 * missing ones, then calls [onGranted]. Precise location is required; activity recognition
 * and notifications improve classification and the foreground notice but are optional.
 */
@Composable
fun rememberTrackingPermissionRequest(
    onGranted: () -> Unit,
    onDenied: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentOnGranted by rememberUpdatedState(onGranted)
    val currentOnDenied by rememberUpdatedState(onDenied)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || hasFineLocationPermission(context)) {
            currentOnGranted()
        } else {
            currentOnDenied()
        }
    }
    return {
        val missing = buildList {
            if (!hasFineLocationPermission(context)) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= 29 && !isGranted(context, Manifest.permission.ACTIVITY_RECOGNITION)) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            if (Build.VERSION.SDK_INT >= 33 && !isGranted(context, Manifest.permission.POST_NOTIFICATIONS)) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missing.isEmpty()) currentOnGranted() else launcher.launch(missing.toTypedArray())
    }
}

fun hasFineLocationPermission(context: Context): Boolean =
    isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)

private fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

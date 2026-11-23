package com.fuguang.localdiary.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object LocationHelper {
    suspend fun currentLocation(context: Context): Location = withTimeout(20_000L) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) throw SecurityException("尚未获得定位权限。请在系统权限设置中允许拾零使用位置信息。")

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOfNotNull(
            LocationManager.NETWORK_PROVIDER.takeIf { manager.isProviderEnabled(it) },
            LocationManager.GPS_PROVIDER.takeIf { fine && manager.isProviderEnabled(it) }
        )
        if (providers.isEmpty()) throw IllegalStateException("没有可用的定位服务，请开启系统定位；仅授予大致位置时需要网络定位服务。")

        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (continuation.isActive) continuation.resume(location)
                    manager.removeUpdates(this)
                }
                @Deprecated("Deprecated in Android") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }
            try {
                continuation.invokeOnCancellation { manager.removeUpdates(listener) }
                var registered = false
                var lastError: Exception? = null
                providers.forEach { provider ->
                    if (continuation.isActive) {
                        try {
                            @Suppress("MissingPermission")
                            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                            registered = true
                        } catch (error: Exception) {
                            lastError = error
                        }
                    }
                }
                if (!registered && continuation.isActive) throw lastError ?: IllegalStateException("无法启动定位服务")
            } catch (error: Exception) {
                manager.removeUpdates(listener)
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }
}

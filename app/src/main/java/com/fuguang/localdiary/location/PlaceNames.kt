package com.fuguang.localdiary.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.coroutines.resume

object PlaceNames {
    private val workers=Executors.newFixedThreadPool(2)
    private val failures=ConcurrentHashMap<String,Long>()

    suspend fun resolve(context:Context,latitude:Double,longitude:Double):String? {
        if(!latitude.isFinite()||!longitude.isFinite()||latitude !in -90.0..90.0||longitude !in -180.0..180.0||!Geocoder.isPresent())return null
        val key="$latitude/$longitude"
        if(System.currentTimeMillis()-(failures[key]?:0L)<120_000L)return null
        val addresses=withTimeoutOrNull(6_000L) {
            suspendCancellableCoroutine<List<Address>?> { continuation ->
                val geocoder=Geocoder(context.applicationContext,Locale.getDefault())
                if(Build.VERSION.SDK_INT>=33) modern(geocoder,latitude,longitude,continuation)
                else {
                    val work=workers.submit {
                        @Suppress("DEPRECATION")
                        val result=runCatching {geocoder.getFromLocation(latitude,longitude,1)}.getOrNull()
                        if(continuation.isActive)continuation.resume(result)
                    }
                    continuation.invokeOnCancellation {work.cancel(true)}
                }
            }
        }
        val name=addresses?.firstOrNull()?.let { label(it) }
        if(name==null)failures[key]=System.currentTimeMillis() else failures.remove(key)
        return name
    }

    @RequiresApi(33)
    private fun modern(geocoder:Geocoder,latitude:Double,longitude:Double,continuation:CancellableContinuation<List<Address>?>) {
        try {geocoder.getFromLocation(latitude,longitude,1,object:Geocoder.GeocodeListener {
            override fun onGeocode(addresses:MutableList<Address>) {if(continuation.isActive)continuation.resume(addresses)}
            override fun onError(errorMessage:String?) {if(continuation.isActive)continuation.resume(null)}
        })}catch(_:Exception){if(continuation.isActive)continuation.resume(null)}
    }

    internal fun label(address:Address):String? {
        val feature=address.featureName?.trim().orEmpty()
        if(feature.isNotBlank()&&!feature.matches(Regex("[\\d\\s.,+°-]+")))return feature.take(120)
        val street=listOfNotNull(address.subLocality,address.thoroughfare,address.subThoroughfare).filter {it.isNotBlank()}.distinct().joinToString("")
        return street.ifBlank {address.locality?:address.subAdminArea?:address.adminArea?:address.getAddressLine(0).orEmpty()}.trim().takeIf {it.isNotBlank()}?.take(120)
    }
}

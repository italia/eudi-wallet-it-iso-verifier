package it.infocert.proximityverification.core.handlers

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.SystemConfiguration.SCNetworkReachabilityCreateWithName
import platform.SystemConfiguration.SCNetworkReachabilityFlagsVar
import platform.SystemConfiguration.SCNetworkReachabilityGetFlags
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsConnectionRequired
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsReachable

actual object NativeUtils {
    actual fun isIos(): Boolean = true
    actual fun isAndroid(): Boolean = false

    @OptIn(ExperimentalForeignApi::class)
    actual fun isNetworkAvailable(): Boolean {
        val reachability = SCNetworkReachabilityCreateWithName(null, "apple.com") ?: return false
        memScoped {
            val flags = alloc<SCNetworkReachabilityFlagsVar>()
            if (!SCNetworkReachabilityGetFlags(reachability, flags.ptr)) return false
            val isReachable =
                flags.value.toInt() and kSCNetworkReachabilityFlagsReachable.toInt() != 0
            val needsConnection =
                flags.value.toInt() and kSCNetworkReachabilityFlagsConnectionRequired.toInt() != 0
            return isReachable && !needsConnection
        }
    }
}
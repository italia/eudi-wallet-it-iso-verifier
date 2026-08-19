package it.infocert.proximityverification.logging

import android.util.Log

actual object PlatformLogger {
    actual fun log(level: LogLevel, tag: String, message: String) {
        val fullMessage = "[${level.name}] $message"
        when (level) {
            LogLevel.TRACE, LogLevel.DEBUG -> Log.d(tag, fullMessage)
            LogLevel.INFO -> Log.i(tag, fullMessage)
            LogLevel.WARN -> Log.w(tag, fullMessage)
            LogLevel.ERROR -> Log.e(tag, fullMessage)
            else -> {}
        }
    }
}
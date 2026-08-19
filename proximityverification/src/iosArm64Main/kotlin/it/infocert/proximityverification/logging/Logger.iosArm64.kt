package it.infocert.proximityverification.logging

import platform.Foundation.NSLog

actual object PlatformLogger {
    actual fun log(level: LogLevel, tag: String, message: String) {
        NSLog("[${level.name}] [$tag] $message")
    }
}
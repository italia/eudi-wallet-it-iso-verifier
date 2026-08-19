package it.infocert.proximityverification.logging

interface LoggerDelegate {
    fun log(level: LogLevel, tag: String, message: String)
}

object LoggerConfig {
    var delegateLogger: LoggerDelegate? = null
}
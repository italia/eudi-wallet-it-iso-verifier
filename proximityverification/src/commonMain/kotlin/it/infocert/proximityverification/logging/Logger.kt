package it.infocert.proximityverification.logging

expect object PlatformLogger {
    fun log(level: LogLevel, tag: String, message: String)
}

object Logger {
    var currentLevel: LogLevel = LogLevel.INFO

    fun setLogLevel(level: LogLevel) {
        currentLevel = level
        syncMultipazDebugFlag()
    }

    fun log(level: LogLevel, tag: String, message: () -> String) {
        if (level.priority >= currentLevel.priority) {
            val msg = message()
            if (LoggerConfig.delegateLogger == null) {
                PlatformLogger.log(level, tag, msg)
            } else {
                LoggerConfig.delegateLogger?.log(level, tag, msg)
            }
        }
    }

    fun trace(tag: String, message: () -> String) = log(LogLevel.TRACE, tag, message)
    fun debug(tag: String, message: () -> String) = log(LogLevel.DEBUG, tag, message)
    fun info(tag: String, message: () -> String) = log(LogLevel.INFO, tag, message)
    fun warn(tag: String, message: () -> String) = log(LogLevel.WARN, tag, message)
    fun error(tag: String, message: () -> String) = log(LogLevel.ERROR, tag, message)
}
package it.infocert.proximityverification.logging

enum class LogLevel(val priority: Int) {
    TRACE(0), DEBUG(1), INFO(2), WARN(3), ERROR(4), NONE(5);

    companion object {
        fun fromString(level: String): LogLevel {
            return when (level.uppercase()) {
                "TRACE" -> TRACE
                "DEBUG" -> DEBUG
                "INFO" -> INFO
                "WARN" -> WARN
                "ERROR" -> ERROR
                else -> NONE
            }
        }

        fun fromPriority(priority: Int): LogLevel {
            return entries.find { it.priority == priority } ?: NONE
        }
    }
}
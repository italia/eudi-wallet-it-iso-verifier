import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.LoggerDelegate

class CrashlyticsLogger(private val logAllInfo: Boolean = false) : LoggerDelegate {
    override fun log(level: LogLevel, tag: String, message: String) {
        val shouldLog = logAllInfo || level.priority >= LogLevel.WARN.priority

        if (shouldLog) {
            // FirebaseCrashlytics.getInstance().log("[$level][$tag] $message")
            if (level == LogLevel.ERROR) {
                // FirebaseCrashlytics.getInstance().recordException(Exception("[$tag] $message"))
            }
            when (level) {
                LogLevel.TRACE, LogLevel.DEBUG -> android.util.Log.d("SDKLogger", "[$level][$tag] $message")
                LogLevel.INFO -> android.util.Log.i("DemoAppLog", "[$level][$tag] $message")
                LogLevel.WARN -> android.util.Log.w("DemoAppLog", "[$level][$tag] $message")
                LogLevel.ERROR -> android.util.Log.e("DemoAppLog", "[$level][$tag] $message")
                else -> android.util.Log.v("DemoAppLog", "[$level][$tag] $message")
            }
        }
    }
}
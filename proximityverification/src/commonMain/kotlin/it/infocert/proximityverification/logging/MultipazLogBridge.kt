package it.infocert.proximityverification.logging

import org.multipaz.util.Logger as MultipazLogger

/**
 * Bridge that forwards multipaz's internal logging ([org.multipaz.util.Logger]) into the KTM
 * [Logger], so engagement/handover internals (reader mode, `onTagDiscovered`, tag-lost, NFC
 * handover, ...) reach the same delegate/export as KTM logs instead of living only on logcat.
 *
 * Observability only: multipaz is consumed read-only through its public
 * [MultipazLogger.logPrinter]/[MultipazLogger.isDebugEnabled] hooks. The forwarded entries go
 * through [Logger.log], so they inherit the KTM level filter and the configured delegate. In prod
 * (level `INFO`) multipaz `DEBUG` logs are neither produced (`isDebugEnabled = false`) nor
 * forwarded (filtered by [Logger.currentLevel]).
 */

/**
 * Multipaz's own internal logger tag. Skipped by the bridge to avoid forwarding multipaz's
 * housekeeping messages (and any chance of a feedback loop) back through the KTM logger.
 */
private const val MULTIPAZ_INTERNAL_TAG = "Logger"

private val bridgePrinter = MultipazLogger.LogPrinter { level, tag, msg, throwable ->
    if (tag == MULTIPAZ_INTERNAL_TAG) return@LogPrinter
    val ktmLevel = when (level) {
        MultipazLogger.LogPrinter.Level.DEBUG -> LogLevel.DEBUG
        MultipazLogger.LogPrinter.Level.INFO -> LogLevel.INFO
        MultipazLogger.LogPrinter.Level.WARNING -> LogLevel.WARN
        MultipazLogger.LogPrinter.Level.ERROR -> LogLevel.ERROR
    }
    Logger.log(ktmLevel, tag) {
        if (throwable != null) "$msg\n$throwable" else msg
    }
}

/**
 * Idempotently installs the multipaz → KTM log bridge by setting [MultipazLogger.logPrinter].
 * Safe to call multiple times (e.g. on every initialization): re-installs only if needed.
 */
fun installMultipazLogBridge() {
    if (MultipazLogger.logPrinter !== bridgePrinter) {
        MultipazLogger.logPrinter = bridgePrinter
    }
}

/**
 * Aligns multipaz's debug gate ([MultipazLogger.isDebugEnabled]) with the current KTM log level:
 * multipaz emits `DEBUG` logs only when the KTM level is `DEBUG` or `TRACE`, keeping prod quiet.
 */
fun syncMultipazDebugFlag() {
    MultipazLogger.isDebugEnabled = Logger.currentLevel.priority <= LogLevel.DEBUG.priority
}

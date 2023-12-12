package it.ipzs.scan_data.managers

import android.util.Log
import it.ipzs.debug_data.DebugRepository
import javax.inject.Inject

class LogManagerImpl @Inject constructor(
    private val debugRepository: DebugRepository
): LogManager {

    override fun startEngagement(qrDeviceEngagement: String) {
        super.startEngagement(qrDeviceEngagement)
        debugRepository.startDebug(qrDeviceEngagement)
    }

    override fun logRequest(request: String) {
        super.logRequest(request)
        debugRepository.logRequest(request)
    }

    override fun logReceived(received: String) {
        super.logReceived(received)
        debugRepository.logReceived(received)
    }

    override fun logError(e: Throwable) {
        super.logError(e)
        debugRepository.logError(e)
    }

    override fun end() {
        super.end()
        debugRepository.end()
    }

    override fun canSetName() = true

    override fun setCustomName(customName: String) {
        super.setCustomName(customName)
        debugRepository.setCustomName(customName)
    }

}
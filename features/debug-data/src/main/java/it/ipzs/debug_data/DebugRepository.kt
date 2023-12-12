package it.ipzs.debug_data

import it.ipzs.database.daos.LogDao
import it.ipzs.database.entities.LogDb
import it.ipzs.debug_data.model.LogRepo
import it.ipzs.utils.mapToModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugRepository @Inject constructor(
    private val logDao: LogDao,
    private val coroutineScope: CoroutineScope
) {

    private var logDb: LogDb? = null

    fun setCustomName(customName: String){
        logDb = LogDb(
            timestamp = System.currentTimeMillis(),
            customName = customName,
            qrCode = "",
            success = false,
            requested = "",
            received = null,
            additionalMessage = null
        )
    }

    fun startDebug(qrDeviceEngagement: String){
        logDb = logDb?.copy(
            qrCode = qrDeviceEngagement
        )
    }

    fun logRequest(request: String) {
        logDb = logDb?.copy(
            requested = request
        )
    }

    fun logReceived(received: String) {
        logDb = logDb?.copy(
            success = true,
            received = received
        )

    }

    fun logError(e: Throwable) {

        val stackTrace = e.stackTraceToString()

        val message = logDb?.additionalMessage?.let {
            "$it\n\n$stackTrace"
        } ?: stackTrace

        logDb = logDb?.copy(
            additionalMessage = message
        )
    }

    fun end() {
        logDb?.let {
            coroutineScope.launch {
                logDao.insertLog(it)
            }
        }

        logDb = null
    }

    fun getLogs() = logDao.getLogs()
        .distinctUntilChanged()
        .mapToModel<LogDb, LogRepo>()

    suspend fun getLogById(id: Long) = logDao.getById(id).let {
        LogRepo.from(it)
    }

    suspend fun deleteItem(id: Long) = logDao.deleteItem(id)

    suspend fun prettyPrint(id: Long): String{

        val logDb = getLogById(id)

        val qrCode = logDb.qrCode
        val received = logDb.received
        val addtionalMessage = logDb.additionalMessage
        val requested = logDb.requested
        val timestamp = logDb.timestamp

        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        val localDateTime = Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()

        val date = localDateTime.format(formatter)

        val builder = StringBuilder()

        builder.append(date)
        builder.append("\n\n")
        builder.append("Device engagement with qr code:\n$qrCode")
        builder.append("\n\n")
        builder.append("Fields requested:\n$requested")
        builder.append("\n\n")
        builder.append("Response:\n$received")
        builder.append("\n\n")
        builder.append("Logged errors:\n$addtionalMessage")

        return builder.toString()
    }

}
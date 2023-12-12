package it.ipzs.debug_data.model

import it.ipzs.database.entities.LogDb
import it.ipzs.utils.Mappable

data class LogRepo(
    val timestamp: Long,
    val customName: String,
    val qrCode: String,
    val success: Boolean,
    val requested: String,
    val received: String?,
    val additionalMessage: String?
){

    companion object: Mappable<LogDb, LogRepo>{
        override fun from(origin: LogDb) = LogRepo(
            timestamp = origin.timestamp,
            customName = origin.customName.ifBlank {
                "No name"
            },
            qrCode = origin.qrCode,
            success = origin.success,
            requested = origin.requested,
            received = origin.received,
            additionalMessage = origin.additionalMessage
        )
    }

}
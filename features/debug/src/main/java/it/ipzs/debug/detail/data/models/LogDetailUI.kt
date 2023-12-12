package it.ipzs.debug.detail.data.models

import it.ipzs.debug_data.model.LogRepo
import it.ipzs.utils.Mappable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class LogDetailUI(
    val id: Long,
    val date: String,
    val qrCode: String,
    val success: Boolean,
    val requested: String,
    val received: String?,
    val errorMessage: String?
){

    val requestedPrettyPrint = requested.split(",").sorted().joinToString(separator = "\n").trim()

    companion object: Mappable<LogRepo, LogDetailUI>{
        override fun from(origin: LogRepo): LogDetailUI{

            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            val localDateTime = Instant.ofEpochMilli(origin.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()

            val date = localDateTime.format(formatter)

            return LogDetailUI(
                id = origin.timestamp,
                date = date,
                qrCode = origin.qrCode,
                success = origin.success,
                requested = origin.requested,
                received = origin.received,
                errorMessage = origin.additionalMessage
            )

        }

        fun empty() = LogDetailUI(
            id = 0L,
            date = "",
            qrCode = "",
            success = false,
            requested = "",
            received = null,
            errorMessage = null
        )

    }

}
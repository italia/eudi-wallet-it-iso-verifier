package it.ipzs.debug.list.data.models

import it.ipzs.debug_data.model.LogRepo
import it.ipzs.utils.Mappable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class LogUI(
    val id: Long,
    val name: String,
    val success: Boolean,
    val date: String
){

    companion object: Mappable<LogRepo, LogUI>{

        override fun from(origin: LogRepo): LogUI{

            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            val localDateTime = Instant.ofEpochMilli(origin.timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()

            val date = localDateTime.format(formatter)

            return LogUI(
                id = origin.timestamp,
                name = origin.customName,
                success = origin.success,
                date = date
            )

        }

    }

}
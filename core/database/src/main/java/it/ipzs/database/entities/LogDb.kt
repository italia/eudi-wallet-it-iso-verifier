package it.ipzs.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "logs"
)
data class LogDb(
    @PrimaryKey val timestamp: Long,
    val customName: String,
    val qrCode: String,
    val success: Boolean,
    val requested: String,
    val received: String?,
    val additionalMessage: String?
)
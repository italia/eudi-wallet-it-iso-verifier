package it.infocert.proximityverification.core.models

import it.infocert.proximityverification.core.models.engagement.EngagementType
import kotlin.time.Duration

data class SessionInfo(
    val engagementType: EngagementType,
    val connectionMethod: String,
    val timing: SessionTiming,
    val requestSize: Long?,
    val responseSize: Long?,
    val deviceResponseStatus: Long?
)

data class SessionTiming(
    val nfcTapToEngagement: Duration?,
    val engagementReceivedToRequestSent: Duration,
    val requestSentToResponseReceived: Duration,
    val scanningTime: Duration?
)

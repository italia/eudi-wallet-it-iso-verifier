package it.infocert.proximityverification.status.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response of an issuer's `status_list_aggregation_endpoint` per
 * draft-ietf-oauth-status-list-13 §9.
 *
 * @property statusLists absolute URIs of Status Lists published by the issuer.
 */
@Serializable
data class StatusListAggregationResponse(
    @SerialName("status_lists")
    val statusLists: List<String>
)

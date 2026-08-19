package it.infocert.proximityverification.network

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import it.infocert.proximityverification.logging.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Logs a non-success HTTP response, extracting the OpenID Federation §8.9 /
 * OAuth-style `error` + `error_description` envelope when the body parses as
 * such an object. Falls back to the bare HTTP status code when the body is
 * absent or not parseable.
 *
 * Used by both `RegistryHttp` (Federation endpoints, where the envelope is
 * mandated) and `StatusListHttp` (Token Status List endpoints, where it is not
 * required by the spec but is the de-facto convention used across IT-Wallet
 * issuer implementations).
 */
internal suspend fun logHttpErrorResponse(tag: String, url: String, response: HttpResponse) {
    val status: Int = response.status.value
    val body: String? = try { response.bodyAsText().ifBlank { null } } catch (_: Throwable) { null }
    if (body != null) {
        val errorObj: JsonObject? = parseJsonObjectOrNull(body)
        val error: String? = (errorObj?.get("error") as? JsonPrimitive)?.contentOrNull
        val desc: String? = (errorObj?.get("error_description") as? JsonPrimitive)?.contentOrNull
        if (error != null) {
            Logger.warn(tag) { "GET $url → HTTP $status: $error — ${desc ?: "(no description)"}" }
            return
        }
    }
    Logger.warn(tag) { "GET $url → HTTP $status" }
}

private val errorBodyJson: Json = Json { ignoreUnknownKeys = true; isLenient = true }

private fun parseJsonObjectOrNull(text: String): JsonObject? = try {
    errorBodyJson.decodeFromString(JsonObject.serializer(), text)
} catch (_: Throwable) {
    null
}

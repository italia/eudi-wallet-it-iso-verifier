package it.infocert.proximityverification.network

import io.ktor.client.*
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

object HttpClientProvider {
    val client: HttpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 45_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis  = 25_000
            }
        }
    }

    /**
     * Variant without ContentNegotiation for endpoints that serve raw bytes
     * (e.g. status list payloads as `application/statuslist+jwt` / `+cwt`):
     * the JSON converter would otherwise auto-append `Accept: application/json`,
     * which negotiating issuers reject with 404.
     */
    val rawClient: HttpClient by lazy {
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 45_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis  = 25_000
            }
        }
    }
}
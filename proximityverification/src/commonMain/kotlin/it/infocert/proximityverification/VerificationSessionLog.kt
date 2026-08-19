package it.infocert.proximityverification

import it.infocert.proximityverification.core.catalog.CatalogBridge
import it.infocert.proximityverification.core.models.request.ClaimStatus
import it.infocert.proximityverification.core.models.request.DocumentRequestOutcome
import it.infocert.proximityverification.core.models.request.DocumentRequestStatus
import it.infocert.proximityverification.core.models.request.RequestOutcome
import it.infocert.proximityverification.logging.Logger
import org.multipaz.mdoc.response.MdocDocument

/**
 * Log di forma della sessione: cosa si chiede al wallet e cosa il wallet restituisce.
 *
 * Rende leggibile in collaudo, dai soli log dell'SDK, l'avvio della verifica e la risposta ricevuta.
 *
 * **Solo fatti.** Questo oggetto non conosce le feature dei consumer e non ne inferisce nessuna: non
 * riconosce "forme" di richiesta né emette verdetti di prodotto. Una libreria riusabile che imparasse
 * una regola di un suo consumatore la vedrebbe cambiare sotto i piedi, e il log degraderebbe in
 * silenzio — un log diagnostico che smette di dire la verità senza segnalarlo è peggio della sua
 * assenza, perché in collaudo si legge come informazione negativa. Chi ha una feature la logga da sé,
 * dove quella feature vive.
 *
 * Livelli: il riepilogo sta a `INFO` (visibile in produzione); l'elenco integrale dei claim sta a
 * `DEBUG` (in preproduzione l'app configura già `debug`). Si loggano soltanto **nomi di schema**
 * (docType, identificatori di claim) e conteggi: nessun valore di data element.
 */
internal object VerificationSessionLog {

    private const val REQ_TAG = "VerificationSession:REQ"
    private const val RESP_TAG = "VerificationSession:RESP"

    /** Numero massimo di identificatori inlinati nella riga di riepilogo a INFO. */
    private const val MAX_INLINE_CLAIMS = 3

    /** Logga i documenti richiesti, con i claim di ciascuno. */
    internal fun logRequests(requests: List<VerificationRequest>) {
        Logger.info(REQ_TAG) {
            buildString {
                append("Requesting ${requests.size} document(s):")
                requests.forEachIndexed { index, request ->
                    append("\n  [$index] ${request.docType} — ${claimSummary(request)}")
                }
            }
        }
        requests.forEachIndexed { index, request ->
            Logger.debug(REQ_TAG) {
                "[$index] ${request.docType} claims: ${request.qualifiedClaims().joinToString(", ")}"
            }
        }
    }

    /**
     * Logga i documenti verificati e i docType che il wallet ha negato.
     *
     * Per ogni documento riporta anche quali data element sono **immagini**, classificati con lo stesso
     * criterio con cui l'SDK produce `DocumentValueMimeType.IMAGE`. È il fatto che
     * rende diagnosticabile qualunque flusso costruito sulle foto — un `portrait` che arriva o non
     * arriva si legge qui — senza che l'SDK sappia nulla di quel flusso.
     *
     * Emette un `WARN` quando un documento porta data element **device-signed**: l'SDK non li espone
     * in `DocumentVerificationResult.data` e la riconciliazione li conta come assenti, quindi senza
     * questa riga un claim arrivato per quella via sarebbe indistinguibile da un claim mai arrivato.
     *
     * @param documentErrors i docType negati dal wallet, così come letti da `DeviceResponse`: è il
     *   canale con cui dichiara di non possedere un documento, e il resto dell'SDK lo ignora ai fini
     *   della verifica.
     */
    internal fun logResponse(documentErrors: List<Map<String, Int>>, documents: List<MdocDocument>) {
        Logger.info(RESP_TAG) {
            buildString {
                append("Received ${documents.size} document(s):")
                documents.forEachIndexed { index, document ->
                    append(
                        "\n  [$index] ${document.docType} — ${document.elementCount()} element(s), " +
                            "images=[${document.imageElements().joinToString(", ")}]"
                    )
                }
            }
        }
        for (document in documents) {
            warnAboutDeviceSignedElements(document)
        }
        val denied = documentErrors.flatMap { it.entries }
        if (denied.isNotEmpty()) {
            Logger.warn(RESP_TAG) {
                "Wallet returned no document for: " +
                    denied.joinToString(", ") { (docType, code) -> "$docType (error $code)" }
            }
        }
    }

    /**
     * Logga l'esito della riconciliazione fra richiesta e risposta.
     *
     * Sono **fatti**, non un verdetto: l'SDK non sa quali claim siano obbligatori (in ISO 18013-5 non
     * è nemmeno esprimibile) e non lo deve sapere. Servono a rendere leggibile in collaudo la
     * differenza fra un claim negato dal wallet e uno omesso in silenzio, che dai soli dati ricevuti è
     * invisibile.
     */
    internal fun logRequestOutcome(outcome: RequestOutcome) {
        val documents = outcome.documents
        val claims = documents.flatMap { it.claims }
        Logger.info(RESP_TAG) {
            "Request outcome: ${documents.size} document(s) requested — " +
                "${documents.count { it.status == DocumentRequestStatus.RETURNED }} returned, " +
                "${documents.count { it.status == DocumentRequestStatus.DENIED }} denied, " +
                "${documents.count { it.status == DocumentRequestStatus.ABSENT }} absent; claims: " +
                "${claims.count { it.status == ClaimStatus.RETURNED }} returned, " +
                "${claims.count { it.status == ClaimStatus.WITHHELD }} withheld, " +
                "${claims.count { it.status == ClaimStatus.ABSENT }} absent, " +
                "${claims.count { it.status == ClaimStatus.UNREQUESTED }} unrequested"
        }
        for (document in documents) {
            logMissingClaims(document)
        }
    }

    /** Dettaglio a `DEBUG` dei claim che non sono tornati, con il codice dichiarato se c'è. */
    private fun logMissingClaims(document: DocumentRequestOutcome) {
        val withheld = document.claims
            .filter { it.status == ClaimStatus.WITHHELD }
            .map { "${it.nameSpace}/${it.identifier} (${it.errorCode})" }
        val absent = document.claims
            .filter { it.status == ClaimStatus.ABSENT }
            .map { "${it.nameSpace}/${it.identifier}" }
        if (withheld.isEmpty() && absent.isEmpty()) return
        Logger.debug(RESP_TAG) {
            "${document.docType}: withheld=[${withheld.joinToString(", ")}], " +
                "absent=[${absent.joinToString(", ")}]"
        }
    }

    private fun warnAboutDeviceSignedElements(document: MdocDocument) {
        val deviceSigned = document.deviceNamespaces.data.values.sumOf { it.size }
        if (deviceSigned == 0) return
        Logger.warn(RESP_TAG) {
            "Document '${document.docType}' carries $deviceSigned device-signed element(s), " +
                "which the SDK does not surface: they are reported as absent"
        }
    }

    private fun claimSummary(request: VerificationRequest): String {
        val identifiers = request.claimIdentifiers()
        val count = "${identifiers.size} claim(s)"
        return if (identifiers.isEmpty() || identifiers.size > MAX_INLINE_CLAIMS) {
            count
        } else {
            "$count (${identifiers.joinToString(", ")})"
        }
    }

    /** Identificatori richiesti, senza namespace. */
    private fun VerificationRequest.claimIdentifiers(): List<String> =
        requestedClaims.values.flatMap { it.keys }.distinct()

    private fun VerificationRequest.qualifiedClaims(): List<String> =
        requestedClaims.entries.flatMap { (namespace, elements) ->
            elements.keys.map { "$namespace/$it" }
        }

    private fun MdocDocument.elementCount(): Int =
        issuerNamespaces.data.values.sumOf { it.size }

    private fun MdocDocument.imageElements(): List<String> =
        issuerNamespaces.data.values
            .flatMap { it.keys }
            .filter { CatalogBridge.isPictureIdentifier(it) }
}

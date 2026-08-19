package it.infocert.proximityverification.core.reconciliation

import it.infocert.proximityverification.VerificationRequest
import it.infocert.proximityverification.core.models.request.ClaimOutcome
import it.infocert.proximityverification.core.models.request.ClaimStatus
import it.infocert.proximityverification.core.models.request.DocumentRequestOutcome
import it.infocert.proximityverification.core.models.request.DocumentRequestStatus
import it.infocert.proximityverification.core.models.request.RequestOutcome
import org.multipaz.mdoc.response.MdocDocument

/**
 * I soli fatti di un documento restituito che servono alla riconciliazione.
 *
 * È una proiezione deliberatamente povera di [MdocDocument]: il riconciliatore resta così una
 * funzione pura, provabile con letterali invece che con response firmate, e il confine con multipaz —
 * che per noi è read-only, e la cui unica leva è la versione consumata — sta in un punto solo, la
 * factory qui sotto.
 *
 * **Non porta i `deviceNamespaces`, e non è una dimenticanza:** un data element device-signed non è un
 * claim ottenuto, perché l'SDK non lo espone in `DocumentVerificationResult.data`; dichiararlo
 * `RETURNED` prometterebbe un valore che il consumatore non trova da nessuna parte. Il riconciliatore
 * non deve nemmeno poterlo vedere. Il `WARN` che ne segnala la presenza vive nel log di sessione, che
 * ha in mano l'[MdocDocument] intero.
 *
 * @property elements namespace → identificatori issuer-signed presenti.
 * @property errors namespace → identificatore → codice dichiarato dal wallet.
 */
internal data class ReturnedDocumentFacts(
    val docType: String,
    val elements: Map<String, Set<String>>,
    val errors: Map<String, Map<String, Int>>,
) {
    internal companion object {
        /** Proietta un documento verificato, scartando tutto ciò che non è issuer-signed. */
        fun from(document: MdocDocument): ReturnedDocumentFacts = ReturnedDocumentFacts(
            docType = document.docType,
            elements = document.issuerNamespaces.data.mapValues { (_, items) -> items.keys.toSet() },
            errors = document.errors,
        )
    }
}

/**
 * Riconcilia ciò che è stato chiesto con ciò che è tornato.
 *
 * Risponde a una sola domanda — *di ciò che ho chiesto, cosa mi è tornato e cosa no?* — e non a
 * *cosa era obbligatorio*, che è policy del consumatore. L'obbligatorietà non è imponibile in ISO
 * 18013-5 (§1.1 del design): esiste solo come regola che il verificatore applica sui fatti prodotti
 * qui.
 *
 * Non si limita a riportare `errors` e `documentErrors` perché popolarli è facoltativo per il wallet:
 * un'API costruita solo su quelle mappe non rileverebbe l'omissione silenziosa, che è il caso più
 * probabile nel campo.
 *
 * L'albero prodotto rispecchia la **richiesta**: un docType restituito o negato ma mai richiesto non
 * compare (un documento inatteso resta comunque visibile in `DocumentReceived.documents`).
 *
 * Non può fallire: è aritmetica su dati già in memoria.
 */
internal object RequestReconciler {

    fun reconcile(
        requests: List<VerificationRequest>,
        documents: List<ReturnedDocumentFacts>,
        documentErrors: List<Map<String, Int>>,
    ): RequestOutcome {
        val deniedCodes = firstCodePerDocType(documentErrors)
        val returnedByDocType = documents.groupBy { it.docType }
        val outcomes = requestedClaimsByDocType(requests).map { (docType, requestedByNamespace) ->
            val returned = returnedByDocType[docType].orEmpty()
            val errorCode = deniedCodes[docType]
            DocumentRequestOutcome(
                docType = docType,
                status = when {
                    // Un documento verificato è un fatto autenticato; documentErrors non lo è.
                    returned.isNotEmpty() -> DocumentRequestStatus.RETURNED
                    errorCode != null -> DocumentRequestStatus.DENIED
                    else -> DocumentRequestStatus.ABSENT
                },
                errorCode = errorCode,
                claims = claimOutcomes(requestedByNamespace, returned),
            )
        }
        return RequestOutcome(documents = outcomes)
    }

    /** docType → namespace → identificatori richiesti, in unione sui `DocRequest` ripetuti. */
    private fun requestedClaimsByDocType(
        requests: List<VerificationRequest>,
    ): Map<String, Map<String, Set<String>>> {
        val byDocType = LinkedHashMap<String, LinkedHashMap<String, LinkedHashSet<String>>>()
        for (request in requests) {
            val byNamespace = byDocType.getOrPut(request.docType) { LinkedHashMap() }
            for ((namespace, claims) in request.requestedClaims) {
                byNamespace.getOrPut(namespace) { LinkedHashSet() }.addAll(claims.keys)
            }
        }
        return byDocType
    }

    /** Il primo codice vince: due mappe che dichiarano lo stesso docType sono già un'incoerenza. */
    private fun firstCodePerDocType(documentErrors: List<Map<String, Int>>): Map<String, Int> {
        val codes = LinkedHashMap<String, Int>()
        for (errors in documentErrors) {
            for ((docType, code) in errors) {
                codes.getOrPut(docType) { code }
            }
        }
        return codes
    }

    private fun claimOutcomes(
        requested: Map<String, Set<String>>,
        returned: List<ReturnedDocumentFacts>,
    ): List<ClaimOutcome> {
        if (returned.isEmpty()) return emptyList()
        val present = LinkedHashMap<String, LinkedHashSet<String>>()
        val declared = LinkedHashMap<String, LinkedHashMap<String, Int>>()
        for (document in returned) {
            for ((namespace, identifiers) in document.elements) {
                present.getOrPut(namespace) { LinkedHashSet() }.addAll(identifiers)
            }
            for ((namespace, errors) in document.errors) {
                val byIdentifier = declared.getOrPut(namespace) { LinkedHashMap() }
                for ((identifier, code) in errors) {
                    byIdentifier.getOrPut(identifier) { code }
                }
            }
        }
        val namespaces = LinkedHashSet<String>().apply {
            addAll(requested.keys)
            addAll(present.keys)
        }
        return namespaces.flatMap { namespace ->
            claimOutcomesOfNamespace(
                namespace = namespace,
                requestedIdentifiers = requested[namespace].orEmpty(),
                presentIdentifiers = present[namespace].orEmpty(),
                declaredCodes = declared[namespace].orEmpty(),
            )
        }
    }

    private fun claimOutcomesOfNamespace(
        namespace: String,
        requestedIdentifiers: Set<String>,
        presentIdentifiers: Set<String>,
        declaredCodes: Map<String, Int>,
    ): List<ClaimOutcome> {
        // Un errore dichiarato per un claim né richiesto né presente è rumore: UNREQUESTED significa
        // "arrivato senza essere stato chiesto", e quello non è arrivato.
        val identifiers = LinkedHashSet<String>().apply {
            addAll(requestedIdentifiers)
            addAll(presentIdentifiers)
        }
        return identifiers.map { identifier ->
            val isPresent = identifier in presentIdentifiers
            val isRequested = identifier in requestedIdentifiers
            ClaimOutcome(
                nameSpace = namespace,
                identifier = identifier,
                status = when {
                    isPresent && isRequested -> ClaimStatus.RETURNED
                    isPresent -> ClaimStatus.UNREQUESTED
                    declaredCodes[identifier] != null -> ClaimStatus.WITHHELD
                    else -> ClaimStatus.ABSENT
                },
                errorCode = declaredCodes[identifier],
            )
        }
    }
}

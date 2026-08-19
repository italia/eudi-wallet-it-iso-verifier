package it.infocert.etuitus.sampleandroid

import it.infocert.proximityverification.core.models.request.ClaimStatus
import it.infocert.proximityverification.core.models.request.DocumentRequestStatus
import it.infocert.proximityverification.core.models.request.RequestOutcome

/**
 * Obbligatorietà dei claim: **regola di prodotto del consumer**.
 *
 * ISO 18013-5 non ha modo di rendere obbligatorio un claim — la richiesta è una wish-list e la
 * risposta può essere legittimamente parziale — quindi l'obbligatorietà esiste solo come regola che
 * il verificatore applica *dopo*, sui fatti che l'SDK riporta in `RequestOutcome`.
 *
 * Il sample adotta la regola più severa possibile: **tutto ciò che ho chiesto mi deve tornare**. Non è
 * la regola giusta per ogni prodotto, è quella che rende visibile ogni assenza, che è il punto di un
 * sample.
 *
 * Vive qui, non nell'SDK: `proximity-verification-ktm` è una libreria riusabile e non deve conoscere
 * le policy dei suoi consumatori. L'SDK fornisce l'inventario; il verdetto è roba nostra.
 *
 * **Il verdetto poggia su [ClaimStatus.RETURNED], non sulla differenza fra [ClaimStatus.WITHHELD] e
 * [ClaimStatus.ABSENT].** Quelle due informazioni vengono dalle mappe `errors`/`documentErrors`, che
 * non sono coperte da alcuna firma: sono utili a spiegare un'assenza in collaudo, non a fondare una
 * decisione. Qui sono mostrate, non usate per decidere.
 *
 * Solo logica pura (nessuna dipendenza da Android o Compose), così che sia verificabile con unit test.
 */
object RequiredClaims {

    /** Un claim richiesto che non è tornato, con il motivo dichiarato dal wallet (se c'è). */
    data class MissingClaim(
        val docType: String,
        val namespace: String,
        val identifier: String,
        val status: ClaimStatus,
        val errorCode: Int?,
    )

    /** Un documento richiesto che non è tornato. */
    data class MissingDocument(
        val docType: String,
        val status: DocumentRequestStatus,
        val errorCode: Int?,
    )

    sealed interface Verdict {
        /** Tutto ciò che è stato chiesto è tornato. */
        data object Complete : Verdict

        data class Incomplete(
            val missingDocuments: List<MissingDocument>,
            val missingClaims: List<MissingClaim>,
        ) : Verdict
    }

    fun evaluate(outcome: RequestOutcome): Verdict {
        val missingDocuments = outcome.documents
            .filter { it.status != DocumentRequestStatus.RETURNED }
            .map { MissingDocument(it.docType, it.status, it.errorCode) }
        val missingClaims = outcome.documents.flatMap { document ->
            document.claims
                // UNREQUESTED è roba arrivata in più: non può rendere incompleta la risposta.
                .filter { it.status == ClaimStatus.WITHHELD || it.status == ClaimStatus.ABSENT }
                .map {
                    MissingClaim(
                        docType = document.docType,
                        namespace = it.nameSpace,
                        identifier = it.identifier,
                        status = it.status,
                        errorCode = it.errorCode,
                    )
                }
        }
        return if (missingDocuments.isEmpty() && missingClaims.isEmpty()) {
            Verdict.Complete
        } else {
            Verdict.Incomplete(missingDocuments, missingClaims)
        }
    }
}

import Foundation
import proximityverification

/// Obbligatorietà dei claim: **regola di prodotto del consumer**.
///
/// ISO 18013-5 non ha modo di rendere obbligatorio un claim — la richiesta è una wish-list e la
/// risposta può essere legittimamente parziale — quindi l'obbligatorietà esiste solo come regola che
/// il verificatore applica *dopo*, sui fatti che l'SDK riporta in `RequestOutcome`.
///
/// Il sample adotta la regola più severa possibile: tutto ciò che ho chiesto mi deve tornare.
///
/// **Il verdetto poggia su `RETURNED`, non sulla differenza fra `WITHHELD` e `ABSENT`:** quelle due
/// informazioni vengono da mappe non coperte da firma, e qui sono mostrate, non usate per decidere.
///
/// Porting fedele di `RequiredClaims.kt` del sample Android.
enum RequiredClaims {

    /// `id` è derivato dal contenuto, non un `UUID()`: il verdetto è ricalcolato a ogni ridisegno
    /// della card, e un id generato darebbe a SwiftUI righe con identità sempre nuova, da ricostruire
    /// invece che da aggiornare.
    struct MissingClaim: Identifiable {
        let docType: String
        let namespace: String
        let identifier: String
        let status: ClaimStatus
        let errorCode: Int?
        var id: String { "\(docType)/\(namespace)/\(identifier)" }
    }

    /// Vedi la nota su `MissingClaim`.
    struct MissingDocument: Identifiable {
        let docType: String
        let status: DocumentRequestStatus
        let errorCode: Int?
        var id: String { docType }
    }

    enum Verdict {
        case complete
        case incomplete(missingDocuments: [MissingDocument], missingClaims: [MissingClaim])
    }

    static func evaluate(outcome: RequestOutcome) -> Verdict {
        let missingDocuments = outcome.documents
            .filter { $0.status != DocumentRequestStatus.returned }
            .map {
                MissingDocument(
                    docType: $0.docType,
                    status: $0.status,
                    errorCode: $0.errorCode?.intValue
                )
            }

        let missingClaims = outcome.documents.flatMap { document in
            document.claims
                // UNREQUESTED è roba arrivata in più: non rende incompleta la risposta.
                .filter { $0.status == ClaimStatus.withheld || $0.status == ClaimStatus.absent }
                .map {
                    MissingClaim(
                        docType: document.docType,
                        namespace: $0.nameSpace,
                        identifier: $0.identifier,
                        status: $0.status,
                        errorCode: $0.errorCode?.intValue
                    )
                }
        }

        if missingDocuments.isEmpty && missingClaims.isEmpty {
            return .complete
        }
        return .incomplete(missingDocuments: missingDocuments, missingClaims: missingClaims)
    }
}

import Foundation
import proximityverification

/// Corroborazione del ritratto dalla Patente: **regola di prodotto del consumer**.
///
/// Requisito di prodotto:
/// per le credenziali prive di foto propria, la foto del titolare si recupera dalla mDL nella stessa
/// sessione di prossimità; la sua assenza non blocca la verifica.
///
/// Vive qui, non nell'SDK: `proximity-verification-ktm` è una libreria riusabile e non deve conoscere
/// le feature dei suoi consumatori. L'SDK fornisce i mattoni — `VerificationCheckType` per i check —
/// e logga i fatti; la regola e il verdetto sono roba nostra.
///
/// Porting fedele di `PortraitCorroboration.kt` del sample Android: le due app di esempio devono
/// mostrare lo stesso comportamento.
///
/// ### Perché l'intenzione è dichiarata, non inferita
///
/// Una versione precedente *inferiva* la corroborazione dalla forma della richiesta ("un solo claim,
/// ed è `portrait`"). Era fragile: bastava chiedere alla mDL un claim in più perché il riconoscimento
/// smettesse di scattare, e la diagnostica sparisse **senza un errore** — in collaudo si sarebbe letta
/// come "corroborazione non richiesta". Ora l'intenzione è registrata in `RequestConfig` quando il
/// preset viene applicato, e se la richiesta non ha più la forma attesa lo si dice ad alta voce
/// (`Request.declaredButUnrecognised`).
enum PortraitCorroboration {

    /// La mDL è la **fonte** della foto: non si corrobora da nient'altro (§1 del design).
    static let mdlDocType: String = "org.iso.18013.5.1.mDL"

    /// Identificatore del ritratto, confrontato **ignorando il namespace** (§3.2 del design).
    ///
    /// ### Debito dichiarato
    ///
    /// Questa costante è duplicata: esiste qui, nel sample Android, e domani nell'app in Dart. L'SDK ha
    /// la stessa informazione in `CatalogBridge.KNOWN_ELEMENT_TYPES`, ma internal, e la scelta di non
    /// esporla è deliberata: l'autorità sui nomi dei claim è il registry di federation risolto a
    /// runtime, che pubblica già `aliases` per claim. Una costante pubblicata dall'SDK asserirebbe a
    /// compile-time una cosa che quel documento può cambiare, e i consumatori non avrebbero modo di
    /// accorgersene.
    ///
    /// Conseguenza da conoscere: se il registry pubblicasse un alias per il ritratto, la correzione
    /// toccherebbe `KNOWN_ELEMENT_TYPES` e ogni consumatore. La via d'uscita non è una costante
    /// condivisa ma esporre la classificazione risolta a runtime, che passerebbe dal plugin: fuori
    /// ambito per ora.
    static let portraitIdentifier: String = "portrait"

    private static func isPortrait(_ identifier: String) -> Bool {
        identifier == portraitIdentifier
    }

    /// Check che, se falliti, in produzione impediscono di mostrare i dati del documento — il Tier 0
    /// della policy dell'app (`verification_outcome_policy.dart`).
    ///
    /// Sono valori dell'enum dell'SDK, non stringhe: se un caso viene rinominato o rimosso, questo
    /// file non compila più. Il "Tier 0" in sé resta una policy di prodotto, e sta quindi qui.
    private static let tier0Checks: [VerificationCheckType] = [
        .msoValidity,
        .issuerTrust,
        .issuerCertRevocation
    ]

    /// La forma della richiesta di corroborazione.
    struct Shape: Equatable {
        let primaryDocTypes: [String]
        let corroboratingDocType: String
    }

    /// Cosa si è chiesto, letto contro l'intenzione dichiarata.
    enum Request {
        /// Nessuna corroborazione dichiarata.
        case notRequested
        /// Dichiarata, e la richiesta ha la forma attesa.
        case declared(shape: Shape)
        /// Dichiarata, ma la richiesta **non** ha più la forma attesa: va segnalato, non ignorato.
        /// Succede se la selezione dei claim viene modificata a mano dopo il preset, o se la mDL
        /// viene deregistrata dal catalog.
        case declaredButUnrecognised(reason: String)
    }

    /// Esito dell'applicazione del preset alla configurazione di richiesta.
    enum PresetResult {
        case applied(config: RequestConfig, shape: Shape)
        /// Il preset non si applica: `reason` è mostrabile all'utente così com'è.
        case notApplicable(reason: String)
    }

    /// Verdetto sulla corroborazione, ricalcolato dai documenti ricevuti.
    enum Verdict {
        /// Niente da dire: nessuna corroborazione dichiarata.
        case notRequested
        /// La foto è arrivata. Se `failedTier0Checks` non è vuoto, in produzione **non** verrebbe
        /// mostrata (gate A del design, §5.2).
        case satisfied(docType: String, failedTier0Checks: [VerificationCheckType])
        case notSatisfied(docType: String, reason: String)
        /// La corroborazione era dichiarata ma la richiesta era malformata: difetto nostro.
        case broken(reason: String)
    }

    // MARK: - Lato richiesta

    /// Imposta la forma prescritta dal §4.1 sui docType **attualmente abilitati** — ognuno con tutti i
    /// suoi claim, più la mDL con il solo `portrait` — e registra l'intenzione in `RequestConfig`.
    ///
    /// Rifiuta — con una ragione leggibile — i casi che il design esclude: mDL non registrata o priva
    /// del claim `portrait` (§4.2, condizioni 3 e 4: aggiungerla comunque farebbe fallire l'intera
    /// richiesta con PVE35/PVE34, non solo il docType problematico); nessun documento abilitato da
    /// corroborare; documento abilitato con una foto propria (§2, il caso della European Disability
    /// Card).
    static func applyPreset(
        config: RequestConfig,
        registeredConfigs: [CredentialConfiguration]
    ) -> PresetResult {
        guard let mdl = registeredConfigs.first(where: { $0.docType == mdlDocType }) else {
            return .notApplicable(
                reason: "\(mdlDocType) is not registered in the catalog: nothing to corroborate from."
            )
        }
        guard let portraitClaim = mdl.claims.first(where: { isPortrait($0.identifier) }) else {
            return .notApplicable(
                reason: "\(mdlDocType) exposes no '\(portraitIdentifier)' claim in this catalog."
            )
        }

        let primaries = registeredConfigs.filter { credential in
            credential.docType != mdlDocType
                && (config.docTypeConfigs[credential.docType]?.enabled ?? false)
        }
        guard !primaries.isEmpty else {
            return .notApplicable(
                reason: "Enable the credential to verify first: corroboration adds the mDL alongside it."
            )
        }
        let withOwnPortrait = primaries.filter { credential in
            credential.claims.contains { isPortrait($0.identifier) }
        }
        guard withOwnPortrait.isEmpty else {
            let docTypes = withOwnPortrait.map { $0.docType }.joined(separator: ", ")
            return .notApplicable(
                reason: "\(docTypes) already carries its own '\(portraitIdentifier)': "
                    + "the mDL is a fallback source, not a canonical one."
            )
        }

        var updated = config.docTypeConfigs
        for credential in primaries {
            var claims: [String: ClaimSelectionEntry] = [:]
            for claim in credential.claims {
                let key = RequestConfig.claimKey(namespace: claim.ns, identifier: claim.identifier)
                claims[key] = ClaimSelectionEntry(selected: true, intentToRetain: false)
            }
            updated[credential.docType] = DocTypeSelectionConfig(enabled: true, claimSelections: claims)
        }
        // Il namespace del portrait è letto dal catalog, non cablato: se il registry lo spostasse,
        // la richiesta resterebbe corretta.
        var mdlClaims: [String: ClaimSelectionEntry] = [:]
        for claim in mdl.claims {
            let key = RequestConfig.claimKey(namespace: claim.ns, identifier: claim.identifier)
            let isThePortrait = claim.ns == portraitClaim.ns && isPortrait(claim.identifier)
            // Non tratteniamo la foto.
            mdlClaims[key] = ClaimSelectionEntry(selected: isThePortrait, intentToRetain: false)
        }
        updated[mdlDocType] = DocTypeSelectionConfig(enabled: true, claimSelections: mdlClaims)

        var newConfig = config
        newConfig.docTypeConfigs = updated
        newConfig.corroborationRequested = true
        return .applied(
            config: newConfig,
            shape: Shape(
                // Ordinati: la forma è confrontabile a prescindere dall'ordine di iterazione.
                primaryDocTypes: primaries.map { $0.docType }.sorted(),
                corroboratingDocType: mdlDocType
            )
        )
    }

    /// Dimentica l'intenzione, lasciando intatta la selezione dei claim.
    static func clearIntent(config: RequestConfig) -> RequestConfig {
        var updated = config
        updated.corroborationRequested = false
        return updated
    }

    /// Risolve cosa si sta chiedendo, confrontando l'**intenzione dichiarata** con la forma effettiva
    /// della richiesta: più di un documento richiesto, esattamente uno chiede il solo `portrait`, e
    /// nessun altro lo chiede.
    ///
    /// - Parameter registeredDocTypes: i docType effettivamente registrati; quelli non registrati non
    ///   finiscono nella richiesta, quindi non contano.
    static func resolveRequest(config: RequestConfig, registeredDocTypes: Set<String>) -> Request {
        guard config.corroborationRequested else { return .notRequested }

        var requested: [String: Set<String>] = [:]
        for (docType, docConfig) in config.docTypeConfigs {
            guard docConfig.enabled, registeredDocTypes.contains(docType) else { continue }
            let identifiers = Set(
                docConfig.claimSelections
                    .filter { $0.value.selected }
                    .keys
                    .compactMap { key -> String? in
                        let parts = key.components(separatedBy: "::")
                        return parts.count == 2 ? parts[1] : nil
                    }
            )
            if !identifiers.isEmpty {
                requested[docType] = identifiers
            }
        }

        guard requested.count >= 2 else {
            return .declaredButUnrecognised(
                reason: "corroboration is declared but only \(requested.count) document(s) are being requested"
            )
        }
        let portraitOnly = requested.filter { $0.value.count == 1 && isPortrait($0.value.first!) }
        guard portraitOnly.count == 1, let corroborating = portraitOnly.first else {
            return .declaredButUnrecognised(
                reason: "corroboration is declared but \(portraitOnly.count) document(s) request "
                    + "'\(portraitIdentifier)' alone (exactly one expected)"
            )
        }
        let others = requested.filter { $0.key != corroborating.key }
        let alsoAskingPortrait = others.filter { entry in
            entry.value.contains { isPortrait($0) }
        }.keys.sorted()
        guard alsoAskingPortrait.isEmpty else {
            return .declaredButUnrecognised(
                reason: "corroboration is declared but \(alsoAskingPortrait.joined(separator: ", ")) "
                    + "also request '\(portraitIdentifier)': that credential has its own photo"
            )
        }
        return .declared(
            shape: Shape(
                primaryDocTypes: others.keys.sorted(),
                corroboratingDocType: corroborating.key
            )
        )
    }

    // MARK: - Lato risposta

    /// Valuta l'esito della corroborazione sui documenti ricevuti, distinguendo le due cause di
    /// mancata corroborazione: il wallet non possiede la mDL (caso ordinario, non bloccante per il
    /// requisito) oppure la restituisce senza ritratto.
    static func evaluate(request: Request, documents: [DocumentResult]) -> Verdict {
        switch request {
        case .notRequested:
            return .notRequested

        case let .declaredButUnrecognised(reason):
            return .broken(reason: reason)

        case let .declared(shape):
            guard let corroborating = documents.first(where: { $0.docType == shape.corroboratingDocType }) else {
                return .notSatisfied(
                    docType: shape.corroboratingDocType,
                    reason: "the wallet returned no \(shape.corroboratingDocType)"
                )
            }
            guard portrait(of: corroborating) != nil else {
                return .notSatisfied(
                    docType: shape.corroboratingDocType,
                    reason: "\(shape.corroboratingDocType) came back without a '\(portraitIdentifier)' element"
                )
            }
            let failed = corroborating.checks
                .filter { check in
                    tier0Checks.contains(check.type) && check.status == CheckStatus.failed
                }
                .map { $0.type }
            return .satisfied(docType: shape.corroboratingDocType, failedTier0Checks: failed)
        }
    }

    /// I byte del ritratto nel documento corroborante, se presenti.
    ///
    /// Cerca il ritratto per identificatore, non "la prima immagine": una mDL può restituire anche
    /// `signature_usual_mark`, che è un'immagine ma non è la foto del titolare.
    static func portrait(of document: DocumentResult) -> Data? {
        document.kvPairs.first { isPortrait($0.key) && $0.imageData != nil }?.imageData
    }
}

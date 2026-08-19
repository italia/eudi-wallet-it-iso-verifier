package it.infocert.etuitus.sampleandroid

import it.infocert.proximityverification.core.catalog.CredentialConfiguration
import it.infocert.proximityverification.core.models.CheckStatus
import it.infocert.proximityverification.core.models.VerificationCheckType

/**
 * Corroborazione del ritratto dalla Patente: **regola di prodotto del consumer**.
 *
 * Requisito di prodotto:
 * per le credenziali prive di foto propria, la foto del titolare si recupera dalla mDL nella stessa
 * sessione di prossimità; la sua assenza non blocca la verifica.
 *
 * Vive qui, non nell'SDK: `proximity-verification-ktm` è una libreria riusabile e non deve conoscere
 * le feature dei suoi consumatori. L'SDK fornisce i mattoni — [VerificationCheckType] per i check — e
 * logga i fatti; la regola e il verdetto sono roba nostra.
 *
 * Solo logica pura (nessuna dipendenza da Android o Compose), così che sia verificabile con unit test.
 *
 * ### Perché l'intenzione è dichiarata, non inferita
 *
 * Una versione precedente *inferiva* la corroborazione dalla forma della richiesta ("un solo claim,
 * ed è `portrait`"). Era fragile: bastava chiedere alla mDL un claim in più perché il riconoscimento
 * smettesse di scattare, e la diagnostica sparisse **senza un errore** — in collaudo si sarebbe letta
 * come "corroborazione non richiesta". Ora l'intenzione è registrata in [RequestConfig] quando il
 * preset viene applicato, e se la richiesta non ha più la forma attesa lo si dice ad alta voce
 * ([Request.DeclaredButUnrecognised]).
 */
object PortraitCorroboration {

    /** La mDL è la **fonte** della foto: non si corrobora da nient'altro (§1 del design). */
    const val MDL_DOCTYPE: String = "org.iso.18013.5.1.mDL"

    /**
     * Identificatore del ritratto, confrontato **ignorando il namespace** (§3.2 del design).
     *
     * ### Debito dichiarato
     *
     * Questa costante è duplicata: esiste qui, nel sample iOS, e domani nell'app in Dart. L'SDK ha la
     * stessa informazione in `CatalogBridge.KNOWN_ELEMENT_TYPES`, ma **internal**, e la scelta di non
     * esporla è deliberata: l'autorità sui nomi dei claim è il registry di federation risolto a
     * runtime, che pubblica già `aliases` per claim (`RegistryResolver`). Una costante pubblicata
     * asserirebbe a compile-time una cosa che quel documento può cambiare, e i consumatori non
     * avrebbero modo di accorgersene.
     *
     * Conseguenza da conoscere: se il registry pubblicasse un alias per il ritratto, la correzione
     * toccherebbe `KNOWN_ELEMENT_TYPES` e ogni consumatore. La via d'uscita non è una costante
     * condivisa ma esporre la classificazione **risolta a runtime**, che passerebbe dal plugin: fuori
     * ambito per ora.
     */
    const val PORTRAIT_IDENTIFIER: String = "portrait"

    /**
     * Check che, se falliti, in produzione impediscono di mostrare i dati del documento — il Tier 0
     * della policy dell'app (`verification_outcome_policy.dart`).
     *
     * Sono valori dell'enum dell'SDK, non stringhe: se un caso viene rinominato o rimosso, questo
     * file non compila più. Il "Tier 0" in sé resta una policy di prodotto, e sta quindi qui.
     */
    private val TIER_0_CHECKS: Set<VerificationCheckType> = setOf(
        VerificationCheckType.MSO_VALIDITY,
        VerificationCheckType.ISSUER_TRUST,
        VerificationCheckType.ISSUER_CERT_REVOCATION,
    )

    private fun isPortrait(claimIdentifier: String): Boolean = claimIdentifier == PORTRAIT_IDENTIFIER

    /** La forma della richiesta di corroborazione. */
    data class Shape(
        val primaryDocTypes: List<String>,
        val corroboratingDocType: String,
    )

    /** Cosa si è chiesto, letto contro l'intenzione dichiarata. */
    sealed interface Request {
        /** Nessuna corroborazione dichiarata. */
        data object NotRequested : Request

        /** Dichiarata, e la richiesta ha la forma attesa. */
        data class Declared(val shape: Shape) : Request

        /**
         * Dichiarata, ma la richiesta **non** ha più la forma attesa: va segnalato, non ignorato.
         * Succede se la selezione dei claim viene modificata a mano dopo il preset, o se la mDL viene
         * deregistrata dal catalog.
         */
        data class DeclaredButUnrecognised(val reason: String) : Request
    }

    /** Esito dell'applicazione del preset alla configurazione di richiesta. */
    sealed interface PresetResult {
        data class Applied(val config: RequestConfig, val shape: Shape) : PresetResult

        /** Il preset non si applica: [reason] è mostrabile all'utente così com'è. */
        data class NotApplicable(val reason: String) : PresetResult
    }

    /** Verdetto sulla corroborazione, ricalcolato dai documenti ricevuti. */
    sealed interface Verdict {
        /** Niente da dire: nessuna corroborazione dichiarata. */
        data object NotRequested : Verdict

        /**
         * La foto è arrivata.
         *
         * @param failedTier0Checks check di Tier 0 falliti sul documento corroborante: se non è
         *   vuoto, in produzione questa foto **non** verrebbe mostrata (gate A del design, §5.2).
         */
        data class Satisfied(
            val docType: String,
            val failedTier0Checks: List<VerificationCheckType>,
        ) : Verdict

        data class NotSatisfied(val docType: String, val reason: String) : Verdict

        /** La corroborazione era dichiarata ma la richiesta era malformata: difetto nostro. */
        data class Broken(val reason: String) : Verdict
    }

    // ---------------------------------------------------------------------------------------------
    // Lato richiesta
    // ---------------------------------------------------------------------------------------------

    /**
     * Imposta la forma prescritta dal §4.1 sui docType **attualmente abilitati** — ognuno con tutti i
     * suoi claim, più la mDL con il solo `portrait` — e registra l'intenzione in [RequestConfig].
     *
     * Rifiuta — con una ragione leggibile — i casi che il design esclude:
     * - la mDL non è registrata nel catalog, o non espone un claim `portrait` (§4.2, condizioni 3 e
     *   4): aggiungerla comunque farebbe fallire l'intera richiesta con PVE35/PVE34, non solo il
     *   docType problematico;
     * - nessun documento abilitato oltre alla mDL: non ci sarebbe una credenziale da corroborare;
     * - il documento abilitato ha una **foto propria** (§2, il caso della European Disability Card):
     *   la mDL è fonte di fallback, non fonte canonica.
     */
    fun applyPreset(
        config: RequestConfig,
        registeredConfigs: List<CredentialConfiguration>,
    ): PresetResult {
        val mdl = registeredConfigs.firstOrNull { it.docType == MDL_DOCTYPE }
            ?: return PresetResult.NotApplicable(
                "$MDL_DOCTYPE is not registered in the catalog: nothing to corroborate from."
            )
        val portraitClaim = mdl.claims.firstOrNull { isPortrait(it.identifier) }
            ?: return PresetResult.NotApplicable(
                "$MDL_DOCTYPE exposes no '$PORTRAIT_IDENTIFIER' claim in this catalog."
            )

        val primaries = registeredConfigs.filter { credential ->
            credential.docType != MDL_DOCTYPE && config.docTypeConfigs[credential.docType]?.enabled == true
        }
        if (primaries.isEmpty()) {
            return PresetResult.NotApplicable(
                "Enable the credential to verify first: corroboration adds the mDL alongside it."
            )
        }
        val withOwnPortrait = primaries.filter { credential ->
            credential.claims.any { isPortrait(it.identifier) }
        }
        if (withOwnPortrait.isNotEmpty()) {
            return PresetResult.NotApplicable(
                "${withOwnPortrait.joinToString(", ") { it.docType }} already carries its own " +
                    "'$PORTRAIT_IDENTIFIER': the mDL is a fallback source, not a canonical one."
            )
        }

        val updated: MutableMap<String, DocTypeSelectionConfig> = config.docTypeConfigs.toMutableMap()
        primaries.forEach { credential ->
            updated[credential.docType] = DocTypeSelectionConfig(
                enabled = true,
                claimSelections = credential.claims.associate { claim ->
                    RequestConfig.claimKey(claim.ns, claim.identifier) to
                        ClaimSelectionEntry(selected = true, intentToRetain = false)
                },
            )
        }
        // Il namespace del portrait è letto dal catalog, non cablato: se il registry lo spostasse,
        // la richiesta resterebbe corretta.
        updated[MDL_DOCTYPE] = DocTypeSelectionConfig(
            enabled = true,
            claimSelections = mdl.claims.associate { claim ->
                RequestConfig.claimKey(claim.ns, claim.identifier) to ClaimSelectionEntry(
                    selected = claim.ns == portraitClaim.ns && isPortrait(claim.identifier),
                    // Non tratteniamo la foto.
                    intentToRetain = false,
                )
            },
        )
        return PresetResult.Applied(
            config = config.copy(docTypeConfigs = updated, corroborationRequested = true),
            shape = Shape(
                // Ordinati: la forma è confrontabile a prescindere dall'ordine di iterazione.
                primaryDocTypes = primaries.map { it.docType }.sorted(),
                corroboratingDocType = MDL_DOCTYPE,
            ),
        )
    }

    /** Dimentica l'intenzione, lasciando intatta la selezione dei claim. */
    fun clearIntent(config: RequestConfig): RequestConfig =
        config.copy(corroborationRequested = false)

    /**
     * Risolve cosa si sta chiedendo, confrontando l'**intenzione dichiarata** con la forma effettiva
     * della richiesta: più di un documento richiesto, esattamente uno chiede il solo `portrait`, e
     * nessun altro lo chiede.
     *
     * @param registeredDocTypes i docType effettivamente registrati: quelli non registrati non
     *   finiscono nella richiesta, quindi non contano.
     */
    fun resolveRequest(config: RequestConfig, registeredDocTypes: Set<String>): Request {
        if (!config.corroborationRequested) return Request.NotRequested

        val requested: Map<String, Set<String>> = config.docTypeConfigs
            .filter { (docType, cfg) -> cfg.enabled && docType in registeredDocTypes }
            .mapValues { (_, cfg) ->
                cfg.claimSelections.filterValues { it.selected }.keys
                    .mapNotNull { key -> key.split("::").takeIf { it.size == 2 }?.get(1) }
                    .toSet()
            }
            .filterValues { it.isNotEmpty() }

        if (requested.size < 2) {
            return Request.DeclaredButUnrecognised(
                "corroboration is declared but only ${requested.size} document(s) are being requested"
            )
        }
        val portraitOnly = requested.entries.filter {
            it.value.size == 1 && isPortrait(it.value.first())
        }
        if (portraitOnly.size != 1) {
            return Request.DeclaredButUnrecognised(
                "corroboration is declared but ${portraitOnly.size} document(s) request " +
                    "'$PORTRAIT_IDENTIFIER' alone (exactly one expected)"
            )
        }
        val corroborating = portraitOnly.single()
        val others = requested.filterKeys { it != corroborating.key }
        val alsoAskingPortrait = others.filterValues { claims ->
            claims.any { isPortrait(it) }
        }.keys
        if (alsoAskingPortrait.isNotEmpty()) {
            return Request.DeclaredButUnrecognised(
                "corroboration is declared but ${alsoAskingPortrait.joinToString(", ")} " +
                    "also request '$PORTRAIT_IDENTIFIER': that credential has its own photo"
            )
        }
        return Request.Declared(
            Shape(
                primaryDocTypes = others.keys.sorted(),
                corroboratingDocType = corroborating.key,
            )
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Lato risposta
    // ---------------------------------------------------------------------------------------------

    /**
     * Valuta l'esito della corroborazione sui documenti ricevuti.
     *
     * Distingue le due cause di mancata corroborazione, che hanno significato diverso: il wallet non
     * possiede la mDL (caso ordinario, non bloccante per il requisito) oppure la restituisce senza
     * ritratto.
     */
    fun evaluate(request: Request, documents: List<UiDocumentResult>): Verdict = when (request) {
        is Request.NotRequested -> Verdict.NotRequested
        is Request.DeclaredButUnrecognised -> Verdict.Broken(request.reason)
        is Request.Declared -> {
            val shape = request.shape
            val corroborating = documents.firstOrNull { it.docType == shape.corroboratingDocType }
            when {
                corroborating == null -> Verdict.NotSatisfied(
                    docType = shape.corroboratingDocType,
                    reason = "the wallet returned no ${shape.corroboratingDocType}",
                )

                portraitOf(corroborating) == null -> Verdict.NotSatisfied(
                    docType = shape.corroboratingDocType,
                    reason = "${shape.corroboratingDocType} came back without a " +
                        "'$PORTRAIT_IDENTIFIER' element",
                )

                else -> Verdict.Satisfied(
                    docType = shape.corroboratingDocType,
                    failedTier0Checks = corroborating.checks
                        .filter { it.type in TIER_0_CHECKS && it.status == CheckStatus.FAILED }
                        .map { it.type },
                )
            }
        }
    }

    /**
     * I byte del ritratto nel documento corroborante, se presenti.
     *
     * Cerca il ritratto per identificatore, non "la prima immagine": una mDL può restituire anche
     * `signature_usual_mark`, che è un'immagine ma non è la foto del titolare.
     */
    fun portraitOf(document: UiDocumentResult): ByteArray? = document.kvPairs
        .firstOrNull { isPortrait(it.key) && it.imageData != null }
        ?.imageData
}

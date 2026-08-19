package it.infocert.etuitus.sampleandroid

import it.infocert.proximityverification.core.catalog.ClaimDescriptor
import it.infocert.proximityverification.core.catalog.CredentialConfiguration
import it.infocert.proximityverification.core.models.CheckStatus
import it.infocert.proximityverification.core.models.VerificationCheckType
import it.infocert.proximityverification.core.models.document.DocumentValueMimeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regole della corroborazione del ritratto, requisito di prodotto del consumer.
 *
 * Interessa soprattutto presidiare i casi in cui la corroborazione **non** si applica: sono quelli
 * in cui un'implementazione ingenua sbaglierebbe, e il §4.2 avverte che aggiungere la mDL a un
 * catalog che non la contiene fa fallire l'intera verifica, non solo il docType aggiunto.
 */
class PortraitCorroborationTest {

    private fun claim(namespace: String, identifier: String) = ClaimDescriptor(
        ns = namespace,
        identifier = identifier,
        isMandatory = false,
        display = emptyList(),
    )

    private fun credential(docType: String, vararg identifiers: String) = CredentialConfiguration(
        docType = docType,
        display = emptyList(),
        claims = identifiers.map { claim(docType, it) },
    )

    private val mdl = CredentialConfiguration(
        docType = PortraitCorroboration.MDL_DOCTYPE,
        display = emptyList(),
        claims = listOf(
            claim(MDL_NAMESPACE, "portrait"),
            claim(MDL_NAMESPACE, "family_name"),
            claim(MDL_NAMESPACE, "given_name"),
        ),
    )

    private val educationDegree = credential(
        EDUCATION_DEGREE,
        "tax_id_code",
        "issuing_authority",
        "verification",
    )

    private val disabilityCard = credential(DISABILITY_CARD, "portrait", "family_name")

    private fun configWithEnabled(vararg docTypes: String) = RequestConfig(
        docTypeConfigs = docTypes.associateWith { DocTypeSelectionConfig(enabled = true) }
    )

    // ---------------------------------------------------------------------------------------------
    // Preset
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `preset selects all primary claims and only the mDL portrait`() {
        val result = PortraitCorroboration.applyPreset(
            config = configWithEnabled(EDUCATION_DEGREE),
            registeredConfigs = listOf(educationDegree, mdl),
        )

        val applied = result as PortraitCorroboration.PresetResult.Applied
        assertEquals(listOf(EDUCATION_DEGREE), applied.shape.primaryDocTypes)
        assertEquals(PortraitCorroboration.MDL_DOCTYPE, applied.shape.corroboratingDocType)

        val primary = applied.config.docTypeConfigs.getValue(EDUCATION_DEGREE)
        assertTrue(primary.enabled)
        assertEquals(3, primary.claimSelections.count { it.value.selected })

        val corroborating = applied.config.docTypeConfigs.getValue(PortraitCorroboration.MDL_DOCTYPE)
        assertTrue(corroborating.enabled)
        val selected = corroborating.claimSelections.filterValues { it.selected }.keys
        assertEquals(setOf(RequestConfig.claimKey(MDL_NAMESPACE, "portrait")), selected)
        assertTrue(
            "la foto non va trattenuta",
            corroborating.claimSelections.values.none { it.intentToRetain }
        )
        assertTrue("il preset dichiara l'intenzione", applied.config.corroborationRequested)
    }

    /** §4.2, condizione 3: la mDL deve essere registrata, altrimenti PVE35 abbatte tutto. */
    @Test
    fun `preset refuses when the mDL is not registered`() {
        val result = PortraitCorroboration.applyPreset(
            config = configWithEnabled(EDUCATION_DEGREE),
            registeredConfigs = listOf(educationDegree),
        )

        val reason = (result as PortraitCorroboration.PresetResult.NotApplicable).reason
        assertTrue(reason, reason.contains("not registered"))
    }

    /** §4.2, condizione 4: il claim `portrait` deve esistere nello schema, altrimenti PVE34. */
    @Test
    fun `preset refuses when the mDL has no portrait claim`() {
        val mdlWithoutPortrait = credential(PortraitCorroboration.MDL_DOCTYPE, "family_name")

        val result = PortraitCorroboration.applyPreset(
            config = configWithEnabled(EDUCATION_DEGREE),
            registeredConfigs = listOf(educationDegree, mdlWithoutPortrait),
        )

        val reason = (result as PortraitCorroboration.PresetResult.NotApplicable).reason
        assertTrue(reason, reason.contains("no 'portrait' claim"))
    }

    /** §2: la European Disability Card ha una foto propria, quindi non si corrobora. */
    @Test
    fun `preset refuses when the primary carries its own portrait`() {
        val result = PortraitCorroboration.applyPreset(
            config = configWithEnabled(DISABILITY_CARD),
            registeredConfigs = listOf(disabilityCard, mdl),
        )

        val reason = (result as PortraitCorroboration.PresetResult.NotApplicable).reason
        assertTrue(reason, reason.contains(DISABILITY_CARD))
    }

    @Test
    fun `preset refuses when nothing is enabled`() {
        val result = PortraitCorroboration.applyPreset(
            config = RequestConfig(),
            registeredConfigs = listOf(educationDegree, mdl),
        )

        assertTrue(result is PortraitCorroboration.PresetResult.NotApplicable)
    }

    // ---------------------------------------------------------------------------------------------
    // Riconoscimento della forma (deve rispecchiare la regola dell'SDK)
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `detects the shape produced by the preset`() {
        val applied = PortraitCorroboration.applyPreset(
            config = configWithEnabled(EDUCATION_DEGREE),
            registeredConfigs = listOf(educationDegree, mdl),
        ) as PortraitCorroboration.PresetResult.Applied

        val request = PortraitCorroboration.resolveRequest(
            config = applied.config,
            registeredDocTypes = setOf(EDUCATION_DEGREE, PortraitCorroboration.MDL_DOCTYPE),
        )

        assertEquals(applied.shape, (request as PortraitCorroboration.Request.Declared).shape)
    }

    /** Senza dichiarazione non si inferisce nulla: la corroborazione non e' un caso accidentale. */
    @Test
    fun `without a declaration nothing is corroboration`() {
        val config = RequestConfig(
            docTypeConfigs = mapOf(
                EDUCATION_DEGREE to DocTypeSelectionConfig(
                    enabled = true,
                    claimSelections = mapOf(
                        RequestConfig.claimKey(EDUCATION_DEGREE, "tax_id_code") to
                            ClaimSelectionEntry(selected = true)
                    ),
                )
            )
        )

        assertEquals(
            PortraitCorroboration.Request.NotRequested,
            PortraitCorroboration.resolveRequest(config, setOf(EDUCATION_DEGREE))
        )
    }

    /**
     * Il cuore della correzione: se la corroborazione e' dichiarata ma la richiesta non ha piu' la
     * forma attesa, non si torna in silenzio a "non richiesta" — lo si dice.
     */
    @Test
    fun `a declared corroboration whose mDL was unregistered is reported, not ignored`() {
        val applied = PortraitCorroboration.applyPreset(
            config = configWithEnabled(EDUCATION_DEGREE),
            registeredConfigs = listOf(educationDegree, mdl),
        ) as PortraitCorroboration.PresetResult.Applied

        val request = PortraitCorroboration.resolveRequest(applied.config, setOf(EDUCATION_DEGREE))

        val unrecognised = request as PortraitCorroboration.Request.DeclaredButUnrecognised
        assertTrue(unrecognised.reason, unrecognised.reason.contains("only 1 document"))
    }

    /**
     * L'altro modo di rompere la forma: chiedere alla mDL un claim in piu' del solo `portrait`. Prima
     * la diagnostica spariva senza un errore; ora e' un difetto dichiarato.
     */
    @Test
    fun `a declared corroboration asking the mDL for more than the portrait is reported`() {
        val applied = PortraitCorroboration.applyPreset(
            config = configWithEnabled(EDUCATION_DEGREE),
            registeredConfigs = listOf(educationDegree, mdl),
        ) as PortraitCorroboration.PresetResult.Applied

        val mdlConfig = applied.config.docTypeConfigs.getValue(PortraitCorroboration.MDL_DOCTYPE)
        val alsoFamilyName = mdlConfig.copy(
            claimSelections = mdlConfig.claimSelections + mapOf(
                RequestConfig.claimKey(MDL_NAMESPACE, "family_name") to
                    ClaimSelectionEntry(selected = true)
            )
        )
        val tampered = applied.config.copy(
            docTypeConfigs = applied.config.docTypeConfigs +
                mapOf(PortraitCorroboration.MDL_DOCTYPE to alsoFamilyName)
        )

        val request = PortraitCorroboration.resolveRequest(
            tampered,
            setOf(EDUCATION_DEGREE, PortraitCorroboration.MDL_DOCTYPE)
        )

        val unrecognised = request as PortraitCorroboration.Request.DeclaredButUnrecognised
        assertTrue(unrecognised.reason, unrecognised.reason.contains("0 document(s) request"))
    }

    @Test
    fun `a broken declared request yields a broken verdict`() {
        val verdict = PortraitCorroboration.evaluate(
            PortraitCorroboration.Request.DeclaredButUnrecognised("mDL deregistrata"),
            listOf(document(EDUCATION_DEGREE)),
        )

        assertEquals("mDL deregistrata", (verdict as PortraitCorroboration.Verdict.Broken).reason)
    }

    // ---------------------------------------------------------------------------------------------
    // Verdetto
    // ---------------------------------------------------------------------------------------------

    private val declared = PortraitCorroboration.Request.Declared(
        PortraitCorroboration.Shape(
            primaryDocTypes = listOf(EDUCATION_DEGREE),
            corroboratingDocType = PortraitCorroboration.MDL_DOCTYPE,
        )
    )

    private fun document(
        docType: String,
        portrait: ByteArray? = null,
        checks: List<UiVerificationCheck> = emptyList(),
    ) = UiDocumentResult(
        docType = docType,
        trustPointName = null,
        validity = UiValidityInfo(
            validFrom = "2026-01-01T00:00:00Z",
            validUntil = "2027-01-01T00:00:00Z",
            signedAt = "2026-01-01T00:00:00Z",
            expectedUpdate = null,
        ),
        checks = checks,
        kvPairs = listOfNotNull(
            portrait?.let {
                UiKeyValue(
                    key = PortraitCorroboration.PORTRAIT_IDENTIFIER,
                    textValue = null,
                    mimeType = DocumentValueMimeType.IMAGE,
                    imageData = it,
                )
            }
        ),
    )

    @Test
    fun `verdict is not requested when the shape is absent`() {
        assertEquals(
            PortraitCorroboration.Verdict.NotRequested,
            PortraitCorroboration.evaluate(
                PortraitCorroboration.Request.NotRequested,
                listOf(document(EDUCATION_DEGREE))
            )
        )
    }

    @Test
    fun `verdict is satisfied when the mDL brings a portrait`() {
        val documents = listOf(
            document(EDUCATION_DEGREE),
            document(PortraitCorroboration.MDL_DOCTYPE, portrait = byteArrayOf(1, 2, 3)),
        )

        val verdict = PortraitCorroboration.evaluate(declared, documents)

        val satisfied = verdict as PortraitCorroboration.Verdict.Satisfied
        assertEquals(PortraitCorroboration.MDL_DOCTYPE, satisfied.docType)
        assertTrue(satisfied.failedTier0Checks.isEmpty())
    }

    /**
     * È il caso che l'intervento sulla verifica per-documento ha reso possibile: la mDL torna con
     * l'MSO fuori finestra invece di far fallire l'intera sessione con PVE04. La foto c'è, ma in
     * produzione non andrebbe mostrata.
     */
    @Test
    fun `verdict flags a portrait that production would not show`() {
        val documents = listOf(
            document(EDUCATION_DEGREE),
            document(
                PortraitCorroboration.MDL_DOCTYPE,
                portrait = byteArrayOf(1, 2, 3),
                checks = listOf(
                    UiVerificationCheck(
                        type = VerificationCheckType.MSO_VALIDITY,
                        status = CheckStatus.FAILED,
                        details = null,
                    ),
                    UiVerificationCheck(
                        type = VerificationCheckType.ISSUER_TRUST,
                        status = CheckStatus.PASSED,
                        details = null,
                    ),
                ),
            ),
        )

        val satisfied = PortraitCorroboration.evaluate(declared, documents)
            as PortraitCorroboration.Verdict.Satisfied

        assertEquals(listOf(VerificationCheckType.MSO_VALIDITY), satisfied.failedTier0Checks)
    }

    @Test
    fun `verdict distinguishes a wallet without the mDL`() {
        val verdict = PortraitCorroboration.evaluate(declared, listOf(document(EDUCATION_DEGREE)))

        val notSatisfied = verdict as PortraitCorroboration.Verdict.NotSatisfied
        assertTrue(notSatisfied.reason, notSatisfied.reason.contains("returned no"))
    }

    @Test
    fun `verdict distinguishes an mDL without a portrait element`() {
        val documents = listOf(
            document(EDUCATION_DEGREE),
            document(PortraitCorroboration.MDL_DOCTYPE),
        )

        val verdict = PortraitCorroboration.evaluate(declared, documents)

        val notSatisfied = verdict as PortraitCorroboration.Verdict.NotSatisfied
        assertTrue(notSatisfied.reason, notSatisfied.reason.contains("without a 'portrait' element"))
    }

    private companion object {
        const val MDL_NAMESPACE = "org.iso.18013.5.1"
        const val EDUCATION_DEGREE = "it.ipzs.wallet.ta.education_degree.1"
        const val DISABILITY_CARD = "it.ipzs.wallet.ta.EuropeanDisabilityCard.1"
    }
}

package it.infocert.proximityverification.core.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuiltInCredentialConfigurationsTest {

    @Test
    fun builtInItalianDrivingLicense_isParseable() {
        val actualConfigs: List<CredentialConfiguration> =
            CredentialConfigurationParser.detectAndParse(
                BuiltInCredentialConfigurations.ITALIAN_DRIVING_LICENSE_JSON
            )

        assertEquals(1, actualConfigs.size)
        val config: CredentialConfiguration = actualConfigs.first()
        assertEquals("org.iso.18013.5.1.mDL", config.docType)
        assertEquals(16, config.claims.size)
        val mandatoryClaims: List<ClaimDescriptor> = config.claims.filter { it.isMandatory }
        assertEquals(15, mandatoryClaims.size)
        val optionalClaims: List<ClaimDescriptor> = config.claims.filter { !it.isMandatory }
        assertEquals(1, optionalClaims.size)
        assertEquals("verification", optionalClaims.first().identifier)
    }

    @Test
    fun builtInConfigurations_haveCorrectNamespaces() {
        val mdlConfig: CredentialConfiguration =
            CredentialConfigurationParser.parseSingle(
                BuiltInCredentialConfigurations.ITALIAN_DRIVING_LICENSE_JSON
            )

        val namespaces: Set<String> = mdlConfig.claims.map { it.ns }.toSet()
        assertTrue(namespaces.contains("org.iso.18013.5.1"))
        assertTrue(namespaces.contains("org.iso.18013.5.1.IT"))
    }
}

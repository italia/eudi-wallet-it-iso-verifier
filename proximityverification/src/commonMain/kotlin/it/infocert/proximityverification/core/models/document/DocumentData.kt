package it.infocert.proximityverification.core.models.document

import it.infocert.proximityverification.core.crypto.CrlCheckStatus
import it.infocert.proximityverification.core.crypto.CrlChecker
import it.infocert.proximityverification.core.crypto.DocumentExpiryChecker
import it.infocert.proximityverification.core.crypto.MsoRevocationChecker
import it.infocert.proximityverification.core.crypto.MsoValidityChecker
import it.infocert.proximityverification.core.crypto.RevocationResult
import it.infocert.proximityverification.core.crypto.TrustUtils
import it.infocert.proximityverification.core.handlers.NativeUtils
import it.infocert.proximityverification.core.models.CheckMetadata
import it.infocert.proximityverification.core.models.CheckStatus
import it.infocert.proximityverification.core.models.VerificationCheck
import it.infocert.proximityverification.core.models.VerificationCheckType
import it.infocert.proximityverification.core.utils.Constants
import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.status.FreshnessExtractor
import it.infocert.proximityverification.status.StatusListStore
import it.infocert.proximityverification.status.StatusListTrust
import it.infocert.proximityverification.status.model.Usability
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DiagnosticOption
import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.response.MdocDocument
import org.multipaz.trustmanagement.TrustManager
import kotlin.time.ExperimentalTime

data class DocumentData(
    val kvPairs: List<DocumentKeyValuePair>
) {
    companion object {
        private const val TAG = "DocumentData"

        @OptIn(ExperimentalTime::class)
        suspend fun fromMdocDeviceResponseDocument(
            document: MdocDocument,
            documentTypeRepository: DocumentTypeRepository,
            issuerTrustManager: TrustManager?,
        ): DocumentData {
            val mn = "fromMdocDeviceResponseDocument"
            Logger.info(TAG) { "$mn - Parsing device response document of type: ${document.docType}" }
            val kvPairs = mutableListOf<DocumentKeyValuePair>()

            kvPairs.add(DocumentKeyValuePair(DocumentMetadataKey.Type.label, Constants.DOCUMENT_SPECIFICATION_LABEL))
            kvPairs.add(DocumentKeyValuePair(DocumentMetadataKey.DocType.label, document.docType))
            kvPairs.add(DocumentKeyValuePair(DocumentMetadataKey.ValidFrom.label, formatTime(document.mso.validFrom)))
            kvPairs.add(DocumentKeyValuePair(DocumentMetadataKey.ValidUntil.label, formatTime(document.mso.validUntil)))
            kvPairs.add(DocumentKeyValuePair(DocumentMetadataKey.SignedAt.label, formatTime(document.mso.signedAt)))
            kvPairs.add(
                DocumentKeyValuePair(
                    DocumentMetadataKey.ExpectedUpdate.label,
                    document.mso.expectedUpdate?.let(::formatTime) ?: "Not Set"
                )
            )

            val mdocType = documentTypeRepository.getDocumentTypeForMdoc(document.docType)?.mdocDocumentType
            Logger.debug(TAG) { "$mn - Resolved mdocType: ${mdocType?.docType ?: "null"}" }

            for (namespaceName in document.issuerNamespaces.data.keys) {
                Logger.debug(TAG) { "$mn - Processing namespace: $namespaceName" }

                val mdocNamespace = mdocType?.namespaces?.get(namespaceName)
                    ?: documentTypeRepository.getDocumentTypeForMdocNamespace(namespaceName)
                        ?.mdocDocumentType?.namespaces?.get(namespaceName)

                kvPairs.add(DocumentKeyValuePair(DocumentMetadataKey.Namespace.label, namespaceName))

                val namespaceEntries = document.issuerNamespaces.data[namespaceName] ?: continue
                for (dataElementName in namespaceEntries.keys) {
                    val mdocDataElement = mdocNamespace?.dataElements?.get(dataElementName)
                    val dataElement = namespaceEntries[dataElementName]?.dataElementValue ?: continue

                    val key = mdocDataElement?.attribute?.identifier ?: dataElementName
                    val value = mdocDataElement?.renderValue(dataElement)
                        ?: Cbor.toDiagnostics(
                            dataElement,
                            setOf(
                                DiagnosticOption.PRETTY_PRINT,
                                DiagnosticOption.EMBEDDED_CBOR,
                                DiagnosticOption.BSTR_PRINT_LENGTH
                            )
                        )

                    val isImage = (dataElement is Bstr && mdocDataElement?.attribute?.type == DocumentAttributeType.Picture)
                    val imageBytes = if (isImage) dataElement.value else null
                    val mimeType = if (isImage) DocumentValueMimeType.IMAGE else DocumentValueMimeType.TEXT

                    kvPairs.add(DocumentKeyValuePair(key, value, mimeType, imageBytes))
                }
            }

            Logger.info(TAG) { "$mn - Parsing complete: ${kvPairs.size} key-value pairs extracted." }
            return DocumentData(kvPairs)
        }

        @OptIn(ExperimentalTime::class)
        internal suspend fun toDocumentVerificationResult(
            document: MdocDocument,
            documentTypeRepository: DocumentTypeRepository,
            issuerTrustManager: TrustManager?,
            statusListStore: StatusListStore? = null,
            crlChecker: CrlChecker? = null
        ): DocumentVerificationResult {
            val mn = "toDocumentVerificationResult"
            val documentData: DocumentData = fromMdocDeviceResponseDocument(
                document = document,
                documentTypeRepository = documentTypeRepository,
                issuerTrustManager = issuerTrustManager
            )
            var issuerTrustStatus: CheckStatus = CheckStatus.NOT_DETERMINED
            var trustPointName: String? = null
            var revocationStatus: CheckStatus = CheckStatus.NOT_DETERMINED
            var crlCheckStatus: CrlCheckStatus = CrlCheckStatus.OFFLINE_NO_DATA
            val issuerCertChain = document.issuerCertChain
            if (issuerTrustManager != null) {
                Logger.debug(TAG) { "$mn - Checking issuer trust..." }
                val trustResult = issuerTrustManager.verify(issuerCertChain.certificates)
                issuerTrustStatus = if (trustResult.isTrusted) {
                    CheckStatus.PASSED
                } else {
                    CheckStatus.FAILED
                }
                trustPointName = trustResult.trustPoints.firstOrNull()?.metadata?.displayName
                    ?: trustResult.trustPoints.firstOrNull()?.certificate?.subject?.name
                if (trustResult.isTrusted) {
                    Logger.info(TAG) { "$mn - Trusted issuer: ${trustPointName ?: "Unknown"}" }
                } else {
                    Logger.warn(TAG) { "$mn - Issuer is not in trust list" }
                }
                Logger.debug(TAG) { "$mn - Start CRL check on each certificate of the full chain constructed by the trust manager." }
                val fullTrustedCertChain = trustResult.trustChain ?: issuerCertChain
                val revocationResult: RevocationResult = if (crlChecker != null) {
                    TrustUtils.verifyNoneOfCertificateIsRevoked(
                        fullTrustedCertChain.certificates,
                        crlChecker
                    )
                } else if (NativeUtils.isNetworkAvailable()) {
                    @Suppress("DEPRECATION")
                    TrustUtils.verifyNoneOfCertificateIsRevoked(
                        fullTrustedCertChain.certificates
                    )
                } else {
                    RevocationResult(
                        isRevoked = false,
                        trustChain = fullTrustedCertChain,
                        crlCheckStatus = CrlCheckStatus.OFFLINE_NO_DATA
                    )
                }
                crlCheckStatus = revocationResult.crlCheckStatus
                revocationStatus = when {
                    revocationResult.isRevoked -> {
                        Logger.warn(TAG) { "$mn - Certificate revoked: ${revocationResult.error?.message}" }
                        CheckStatus.FAILED
                    }
                    crlChecker == null && revocationResult.crlCheckStatus == CrlCheckStatus.OFFLINE_NO_DATA -> {
                        Logger.warn(TAG) { "$mn - Device is offline, cannot check certificate revocation." }
                        CheckStatus.NOT_DETERMINED
                    }
                    else -> {
                        Logger.info(TAG) { "$mn - No certificate in the chain is revoked (status=${revocationResult.crlCheckStatus})." }
                        CheckStatus.PASSED
                    }
                }
            }
            val msoRevocationOutcome = MsoRevocationChecker.check(
                revocationStatus = document.mso.revocationStatus,
                isOfflineEnabled = { statusListStore?.isEnabled() ?: false },
                cacheLookup = { uri -> statusListStore?.get(uri) },
                cachePut = { uri, payload ->
                    statusListStore?.put(
                        uri = uri,
                        payload = payload,
                        meta = FreshnessExtractor.extract(payload)
                    )
                },
                cacheUsability = { entry, now ->
                    statusListStore?.usability(entry, now) ?: Usability.Fresh
                },
                resolveSigningKey = { payload, format ->
                    // Anchor the Status List Token's own x5c / x5chain to a configured trust
                    // point (same TrustManager used for the issuer chain) and hand the leaf key
                    // to multipaz. Without this the token cannot be validated at use time and the
                    // revocation status silently collapses to NOT_DETERMINED.
                    issuerTrustManager?.let { tm ->
                        StatusListTrust.resolveSigningKey(
                            payload = payload,
                            format = format,
                            trustManager = tm,
                            atTime = Clock.System.now()
                        )
                    }
                }
            )
            val checks: List<VerificationCheck> = buildVerificationChecks(
                issuerTrustStatus = issuerTrustStatus,
                issuerCertRevocationStatus = revocationStatus,
                crlCheckStatus = crlCheckStatus,
                msoRevocation = msoRevocationOutcome,
                msoValidity = MsoValidityChecker.check(document.mso.validFrom, document.mso.validUntil),
                documentExpiry = DocumentExpiryChecker.check(
                    expiryDateRaw = extractExpiryDateRaw(documentData.kvPairs),
                ),
            )
            val issuerCertInfo: IssuerCertificateInfo? = if (issuerCertChain.certificates.isNotEmpty()) {
                IssuerCertificateInfo(
                    trustPointName = trustPointName,
                    certChain = issuerCertChain.certificates.map { certificate ->
                        certificate.encoded.toByteArray()
                    }
                )
            } else {
                null
            }
            return DocumentVerificationResult(
                docType = document.docType,
                checks = checks,
                validityInfo = DocumentValidityInfo(
                    validFrom = document.mso.validFrom,
                    validUntil = document.mso.validUntil,
                    signedAt = document.mso.signedAt,
                    expectedUpdate = document.mso.expectedUpdate
                ),
                issuerCertInfo = issuerCertInfo,
                data = documentData.kvPairs
            )
        }

        /**
         * Estrae il valore grezzo del claim di scadenza amministrativa (`expiry_date`) dai
         * key-value pair già renderizzati, da passare a [DocumentExpiryChecker].
         *
         * Dipendenze fragili (coperte da `ExpiryDateBridgeTest`):
         *  - (a) l'identifier esatto `"expiry_date"` — è il claim mDL ISO 18013-5 (e dei
         *    doctype che riusano lo stesso identifier); doctype con un claim di scadenza
         *    diverso richiederebbero di estendere questa risoluzione (doctype-aware).
         *  - (b) il formato prodotto da `MdocDataElement.renderValue` per
         *    [org.multipaz.documenttype.DocumentAttributeType.Date], ossia `"YYYY-MM-DD"`
         *    (multipaz: `format(ISO).substring(0, 9)`), che [DocumentExpiryChecker] sa parsare.
         *
         * Se l'elemento non è mappato come `Date` (manca `mdocDataElement`), `textValue`
         * ricade sul rendering `Cbor.toDiagnostics`: non sarà parsabile come data e
         * [DocumentExpiryChecker] restituirà `null` (check non emesso, N/A non bloccante).
         */
        internal fun extractExpiryDateRaw(kvPairs: List<DocumentKeyValuePair>): String? =
            kvPairs.firstOrNull { it.key == "expiry_date" }?.textValue

        internal fun buildVerificationChecks(
            issuerTrustStatus: CheckStatus,
            issuerCertRevocationStatus: CheckStatus,
            crlCheckStatus: CrlCheckStatus,
            msoRevocation: MsoRevocationChecker.MsoRevocationOutcome,
            msoValidity: VerificationCheck,
            documentExpiry: VerificationCheck?,
        ): List<VerificationCheck> {
            val checks = mutableListOf(
                VerificationCheck(VerificationCheckType.ISSUER_TRUST, issuerTrustStatus),
                VerificationCheck(
                    VerificationCheckType.ISSUER_CERT_REVOCATION,
                    issuerCertRevocationStatus,
                    CheckMetadata.CrlRevocation(crlCheckStatus),
                ),
                VerificationCheck(VerificationCheckType.MSO_REVOCATION, msoRevocation.status, msoRevocation.metadata),
                msoValidity,
            )
            if (documentExpiry != null) checks.add(documentExpiry)
            return checks
        }

        @OptIn(ExperimentalTime::class)
        private fun formatTime(instant: Instant): String {
            val tz = TimeZone.currentSystemDefault()
            val isoStr = instant.toLocalDateTime(tz).format(LocalDateTime.Formats.ISO)
            return isoStr.substring(0, 10) + " " + isoStr.substring(11)
        }
    }
}

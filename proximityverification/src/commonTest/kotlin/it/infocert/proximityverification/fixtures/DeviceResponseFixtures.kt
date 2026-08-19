package it.infocert.proximityverification.fixtures

import it.infocert.proximityverification.logging.LogLevel
import it.infocert.proximityverification.logging.LoggerConfig
import it.infocert.proximityverification.logging.LoggerDelegate
import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Simple
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.addCborMap
import org.multipaz.cbor.buildCborArray
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.putCborArray
import org.multipaz.cbor.toDataItem
import org.multipaz.cose.Cose
import org.multipaz.cose.toCoseLabel
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.AsymmetricKey
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.crypto.X509KeyUsage
import org.multipaz.mdoc.devicesigned.buildDeviceNamespaces
import org.multipaz.mdoc.mso.MobileSecurityObject
import org.multipaz.mdoc.response.DeviceResponse
import org.multipaz.mdoc.issuersigned.buildIssuerNamespaces
import org.multipaz.util.Logger as MultipazLogger
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Come corrompere una fixture. Ogni valore diverso da [None] riproduce una delle cinque cause per
 * cui `MdocDocument.verify()` di multipaz solleva `IllegalStateException`, tranne la finestra di
 * validità dell'MSO — quella si ottiene con `validFrom`/`validUntil` di [DocSpec].
 *
 * Le corruzioni sono **strutturali** (surgery sul `DataItem` del documento, non su offset di byte
 * fissi): sono quindi stabili e riproducibili anche se le chiavi di test cambiano a ogni run.
 */
internal enum class Corruption {
    /** Documento integro. */
    None,

    /** Flip di un byte nella firma di `issuerAuth` → "Signature on MSO failed to verify". */
    MsoSignature,

    /** Flip di un byte nella firma di `deviceSigned` → "Device authentication signature failed to verify". */
    DeviceAuth,

    /** `docType` del documento diverso da quello dell'MSO → "Mismatch between docType in document and MSO". */
    DocTypeMismatch,

    /** `elementValue` alterato dentro un `IssuerSignedItemBytes` → "Digest mismatch for data element ...". */
    DigestMismatch,
}

/**
 * Specifica di un documento da inserire nella `DeviceResponse` sintetica.
 *
 * @param docType il docType del documento *e* dell'MSO (li disallinea solo [Corruption.DocTypeMismatch]).
 * @param namespaces i data element issuer-signed, per namespace.
 * @param validFrom inizio della finestra di validità firmata nell'MSO (secondi interi: l'MSO ISO
 *   non ammette frazioni di secondo).
 * @param validUntil fine della finestra di validità firmata nell'MSO.
 * @param corruption la corruzione da applicare dopo la generazione.
 * @param errors la mappa `errors` del documento (namespace → identificatore → codice), con cui il
 *   wallet dichiara di non aver restituito un claim. Non è coperta da alcuna firma: serve a
 *   esercitare la distinzione fra claim negato e claim omesso in silenzio.
 * @param deviceNamespaces i data element **device-signed**, che l'SDK non espone. Servono a provare
 *   che un claim arrivato per quella via resti `ABSENT` invece di essere dichiarato ottenuto.
 */
internal data class DocSpec(
    val docType: String = DeviceResponseFixtures.MDL_DOCTYPE,
    val namespaces: Map<String, Map<String, DataItem>> = DeviceResponseFixtures.defaultNamespaces(),
    val validFrom: Instant,
    val validUntil: Instant,
    val corruption: Corruption = Corruption.None,
    val errors: Map<String, Map<String, Int>> = emptyMap(),
    val deviceNamespaces: Map<String, Map<String, DataItem>> = emptyMap(),
)

/**
 * Costruttore di `DeviceResponse` sintetiche **firmate**, per i test della verifica per-documento.
 *
 * Tutto è generato in memoria: chiavi ECDSA P-256 di test, un DS certificate self-signed, un MSO
 * con la finestra di validità richiesta e un `deviceAuth` ECDSA valido. Nessun artefatto reale.
 *
 * Il `deviceAuth` è sempre in modalità **firma** (non MAC): basta a esercitare tutte le cause di
 * fallimento d'integrità e non richiede che la chiave eReader sia coerente con quella di sessione.
 */
@OptIn(ExperimentalTime::class)
internal object DeviceResponseFixtures {

    const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
    const val MDL_NAMESPACE = "org.iso.18013.5.1"
    const val PHOTO_ID_DOCTYPE = "org.iso.23220.photoid.1"
    const val PHOTO_ID_NAMESPACE = "org.iso.23220.1"

    /** Seed fisso: i `digestId` e i `random` degli `IssuerSignedItem` sono riproducibili. */
    private const val RANDOM_SEED = 42

    fun defaultNamespaces(): Map<String, Map<String, DataItem>> = mapOf(
        MDL_NAMESPACE to mapOf(
            "family_name" to "Rossi".toDataItem(),
            "given_name" to "Mario".toDataItem(),
        )
    )

    fun photoIdNamespaces(): Map<String, Map<String, DataItem>> = mapOf(
        PHOTO_ID_NAMESPACE to mapOf(
            "family_name" to "Rossi".toDataItem(),
            "given_name" to "Mario".toDataItem(),
        )
    )

    /**
     * Ripristina i logger allo stato precedente una [silenceLoggers].
     *
     * Esiste perché i logger sono **stato globale di processo**: silenziarli senza ripristinarli li
     * lascerebbe muti per tutta la JVM di test, e ogni altra suite dipenderebbe dall'ordine di
     * esecuzione per non accorgersene.
     */
    class LoggerRestore internal constructor(
        private val previousPrinter: MultipazLogger.LogPrinter?,
        private val previousDelegate: LoggerDelegate?,
    ) {
        fun restore() {
            MultipazLogger.logPrinter = previousPrinter
            LoggerConfig.delegateLogger = previousDelegate
        }
    }

    /**
     * Silenzia i logger (multipaz e KTM) e restituisce l'handle per ripristinarli.
     *
     * Sul runner JVM dei test Android `android.util.Log` non è mockato, e sia multipaz sia il KTM
     * finiscono lì di default: `DeviceNamespaces.fromDataItem` logga **incondizionatamente**
     * (`Logger.iCbor`), quindi qualunque `DeviceResponse.fromDataItem` esploderebbe con "Method i in
     * android.util.Log not mocked". Installando un [MultipazLogger.LogPrinter] e un
     * [LoggerDelegate] nessuno dei due tocca più la piattaforma.
     *
     * Va invocata prima di qualunque parsing, e il suo [LoggerRestore] va invocato dopo — tipicamente
     * in `@BeforeTest`/`@AfterTest`. [buildDeviceResponse] la usa e ripristina da sé.
     */
    fun silenceLoggers(): LoggerRestore {
        val restore = LoggerRestore(MultipazLogger.logPrinter, LoggerConfig.delegateLogger)
        MultipazLogger.logPrinter = silentPrinter
        // Non si sovrascrive un delegate già installato: un test che sta catturando i log del KTM
        // deve continuare a vederli.
        if (LoggerConfig.delegateLogger == null) {
            LoggerConfig.delegateLogger = silentDelegate
        }
        return restore
    }

    private val silentPrinter = MultipazLogger.LogPrinter { _, _, _, _ -> }

    private val silentDelegate = object : LoggerDelegate {
        override fun log(level: LogLevel, tag: String, message: String) = Unit
    }

    /** Un session transcript qualunque: non deve essere quello reale, solo lo stesso in firma e verifica. */
    fun sessionTranscript(): DataItem = buildCborArray {
        add(Simple.NULL)
        add(Simple.NULL)
        add(byteArrayOf(1, 2, 3))
    }

    /** `now` troncato al secondo, per non violare la `require` sulle frazioni di secondo dell'MSO. */
    fun nowWholeSeconds(): Instant = Instant.fromEpochSeconds(Clock.System.now().epochSeconds)

    /** Finestra di validità che contiene [now]. */
    fun validWindow(now: Instant = nowWholeSeconds()): Pair<Instant, Instant> =
        (now - 30.days) to (now + 30.days)

    /**
     * Genera la `DeviceResponse` e ne restituisce il CBOR, come arriva dal transport.
     *
     * @param sessionTranscript il session transcript su cui è calcolato il `deviceAuth`.
     * @param eReaderKey la chiave eReader, necessaria solo per `deviceAuth` in modalità MAC (qui mai).
     * @param documents i documenti da includere, nell'ordine.
     * @param otherDocumentFormats formati dei `otherDocuments` da aggiungere (es. `sd-jwt+kb`).
     *   Iniettati **come CBOR grezzo**, non via `DeviceResponse.Builder`: multipaz 0.99.0 non modella
     *   affatto `otherDocuments`, quindi non saprebbe emetterli. Servono a verificare che una response
     *   mista non venga trattata come valida dal giro per-documento, che sa isolare solo i mdoc.
     * @param documentErrors le mappe `documentErrors` della response (docType → codice), con cui il
     *   wallet dichiara di non possedere un documento. Come `errors`, non è coperta da firma.
     */
    suspend fun buildDeviceResponse(
        sessionTranscript: DataItem,
        eReaderKey: EcPublicKey? = null,
        documents: List<DocSpec>,
        otherDocumentFormats: List<String> = emptyList(),
        documentErrors: List<Map<String, Int>> = emptyList(),
    ): ByteArray {
        val loggers = silenceLoggers()
        try {
            val builder = DeviceResponse.Builder(
                sessionTranscript = sessionTranscript,
                status = DeviceResponse.STATUS_OK,
                eReaderKey = eReaderKey,
            )
            for (spec in documents) {
                addDocument(builder, sessionTranscript, eReaderKey, spec)
            }
            for (documentError in documentErrors) {
                builder.addDocumentError(documentError)
            }
            val root = builder.build().toDataItem()

            val rawDocuments = root.getOrNull("documents")?.asArray ?: emptyList()
            require(rawDocuments.size == documents.size) {
                "expected ${documents.size} documents in the generated response, got ${rawDocuments.size}"
            }
            val finalDocuments = rawDocuments.mapIndexed { index, rawDocument ->
                corrupt(rawDocument, documents[index].corruption)
            }

            return Cbor.encode(
                buildCborMap {
                    put("version", root["version"])
                    put("status", root["status"])
                    if (finalDocuments.isNotEmpty()) {
                        putCborArray("documents") { finalDocuments.forEach { add(it) } }
                    }
                    if (otherDocumentFormats.isNotEmpty()) {
                        putCborArray("otherDocuments") {
                            otherDocumentFormats.forEach { format ->
                                addCborMap {
                                    put("docFormat", format)
                                    put("data", Bstr(byteArrayOf(1, 2, 3)))
                                }
                            }
                        }
                    }
                    // La mappa radice è ricostruita a mano: senza questa riga i documentErrors
                    // prodotti dal builder verrebbero silenziosamente persi.
                    root.getOrNull("documentErrors")?.let { put("documentErrors", it) }
                }
            )
        } finally {
            loggers.restore()
        }
    }

    private suspend fun addDocument(
        builder: DeviceResponse.Builder,
        sessionTranscript: DataItem,
        eReaderKey: EcPublicKey?,
        spec: DocSpec,
    ) {
        val deviceKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val issuerNamespaces = buildIssuerNamespaces(randomProvider = Random(RANDOM_SEED)) {
            spec.namespaces.forEach { (namespaceName, dataElements) ->
                addNamespace(namespaceName) {
                    dataElements.forEach { (name, value) -> addDataElement(name, value) }
                }
            }
        }
        val mso = MobileSecurityObject(
            version = "1.0",
            docType = spec.docType,
            signedAt = spec.validFrom,
            validFrom = spec.validFrom,
            validUntil = spec.validUntil,
            expectedUpdate = null,
            digestAlgorithm = Algorithm.SHA256,
            valueDigests = issuerNamespaces.getValueDigests(Algorithm.SHA256),
            deviceKey = deviceKey.publicKey,
        )
        builder.addDocument(
            docType = spec.docType,
            issuerAuth = signMso(mso),
            issuerNamespaces = issuerNamespaces,
            deviceNamespaces = buildDeviceNamespaces {
                spec.deviceNamespaces.forEach { (namespaceName, dataElements) ->
                    addNamespace(namespaceName) {
                        dataElements.forEach { (name, value) -> addDataElement(name, value) }
                    }
                }
            },
            deviceKey = AsymmetricKey.anonymous(deviceKey),
            errors = spec.errors,
        )
    }

    private suspend fun signMso(mso: MobileSecurityObject) = Cose.coseSign1Sign(
        signingKey = AsymmetricKey.anonymous(dsPrivateKey(), Algorithm.ESP256),
        message = Cbor.encode(
            Tagged(Tagged.ENCODED_CBOR, Bstr(Cbor.encode(mso.toDataItem())))
        ),
        includeMessageInPayload = true,
        protectedHeaders = mapOf(
            Cose.COSE_LABEL_ALG.toCoseLabel to Algorithm.ES256.coseAlgorithmIdentifier!!.toDataItem()
        ),
        unprotectedHeaders = mapOf(
            Cose.COSE_LABEL_X5CHAIN.toCoseLabel to dsCertChain().toDataItem()
        ),
    )

    // La chiave DS e il suo certificato sono generati una volta per processo: le fixture non hanno
    // bisogno di catene diverse fra documenti, e generare una P-256 per documento è solo più lento.
    private var cachedDsKey: EcPrivateKey? = null
    private var cachedDsCertChain: X509CertChain? = null

    private suspend fun dsPrivateKey(): EcPrivateKey =
        cachedDsKey ?: Crypto.createEcPrivateKey(EcCurve.P256).also { cachedDsKey = it }

    private suspend fun dsCertChain(): X509CertChain {
        cachedDsCertChain?.let { return it }
        val key = dsPrivateKey()
        // Secondi interi: X509Cert.Builder logga un warning sulle frazioni di secondo via
        // android.util.Log, non mockato nel runner JVM dei test Android.
        val now = nowWholeSeconds()
        val cert = X509Cert.Builder(
            publicKey = key.publicKey,
            signingKey = AsymmetricKey.anonymous(key, key.curve.defaultSigningAlgorithm),
            serialNumber = ASN1Integer(1L),
            subject = X500Name.fromName("CN=Test DS"),
            issuer = X500Name.fromName("CN=Test DS"),
            validFrom = now - 365.days,
            validUntil = now + 365.days,
        ).includeSubjectKeyIdentifier()
            .setKeyUsage(setOf(X509KeyUsage.DIGITAL_SIGNATURE))
            .build()
        return X509CertChain(listOf(cert)).also { cachedDsCertChain = it }
    }

    // ---------------------------------------------------------------------------------------------
    // Corruzioni: surgery sul DataItem del documento.
    //
    // Il documento viene ricostruito con buildCborMap preservando l'ordine delle chiavi; i sotto-item
    // non toccati sono riferimenti condivisi, quindi i byte degli IssuerSignedItemBytes restano
    // identici (tranne dove è proprio quello che vogliamo alterare).
    // ---------------------------------------------------------------------------------------------

    private fun corrupt(document: DataItem, corruption: Corruption): DataItem = when (corruption) {
        Corruption.None -> document
        Corruption.MsoSignature -> replace(document, "issuerSigned") { issuerSigned ->
            replace(issuerSigned, "issuerAuth") { flipSignature(it) }
        }
        Corruption.DeviceAuth -> replace(document, "deviceSigned") { deviceSigned ->
            replace(deviceSigned, "deviceAuth") { deviceAuth ->
                replace(deviceAuth, "deviceSignature") { flipSignature(it) }
            }
        }
        Corruption.DocTypeMismatch -> replace(document, "docType") {
            "${it.asTstr}.tampered".toDataItem()
        }
        Corruption.DigestMismatch -> replace(document, "issuerSigned") { issuerSigned ->
            replace(issuerSigned, "nameSpaces") { namespaces ->
                // Altera il primo elemento del primo namespace: l'MSO porta il digest dei byte
                // originali, quindi il confronto fallisce.
                val firstNamespace = namespaces.asMap.keys.first()
                replace(namespaces, firstNamespace.asTstr) { items ->
                    val all = items.asArray
                    buildCborArray {
                        add(tamperIssuerSignedItem(all.first()))
                        all.drop(1).forEach { add(it) }
                    }
                }
            }
        }
    }

    /** Ricostruisce la mappa [map] con il valore di [key] sostituito da [transform]. */
    private fun replace(map: DataItem, key: String, transform: (DataItem) -> DataItem): DataItem {
        val entries = map.asMap
        val target = entries.keys.firstOrNull { it.asTstr == key }
            ?: throw IllegalStateException("key '$key' not found in fixture document")
        return buildCborMap {
            entries.forEach { (k, v) -> put(k, if (k == target) transform(v) else v) }
        }
    }

    /** Flip di un byte della firma di una `COSE_Sign1` (`[protected, unprotected, payload, signature]`). */
    private fun flipSignature(coseSign1: DataItem): DataItem {
        val items = coseSign1.asArray
        require(items.size == 4) { "unexpected COSE_Sign1 layout: ${items.size} items" }
        val signature = items[3].asBstr.copyOf()
        signature[0] = (signature[0].toInt() xor 0xff).toByte()
        return buildCborArray {
            items.take(3).forEach { add(it) }
            add(Bstr(signature))
        }
    }

    /** Sostituisce l'`elementValue` dentro un `IssuerSignedItemBytes` (`24(<<IssuerSignedItem>>)`). */
    private fun tamperIssuerSignedItem(issuerSignedItemBytes: DataItem): DataItem {
        val item = issuerSignedItemBytes.asTaggedEncodedCbor
        val tampered = replace(item, "elementValue") { "TAMPERED".toDataItem() }
        return Tagged(Tagged.ENCODED_CBOR, Bstr(Cbor.encode(tampered)))
    }
}

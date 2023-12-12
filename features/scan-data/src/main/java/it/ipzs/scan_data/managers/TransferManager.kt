package it.ipzs.scan_data.managers

import android.content.Context
import android.icu.text.SimpleDateFormat
import android.icu.util.GregorianCalendar
import android.icu.util.TimeZone
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.DataItem
import com.android.identity.android.mdoc.deviceretrieval.VerificationHelper
import com.android.identity.android.mdoc.transport.DataTransportOptions
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle
import com.android.identity.mdoc.request.DeviceRequestGenerator
import com.android.identity.mdoc.response.DeviceResponseParser
import com.android.identity.util.CborUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import it.ipzs.scan_data.model.DriveLicenseData
import it.ipzs.scan_data.utils.SupportedCurves
import it.ipzs.scan_data.utils.TransferStatus
import it.ipzs.scan_data.utils.certificate.KeysAndCertificates
import it.ipzs.scan_data.utils.certificate.ReaderCertificateGenerator
import it.ipzs.scan_data.utils.certificate.SimpleIssuerTrustStore
import it.ipzs.scan_data.utils.documents.DocumentHandler
import it.ipzs.scan_data.utils.documents.RequestDocumentList
import it.ipzs.scan_data.utils.documents.types.RequestMdl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.ByteArrayInputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Locale
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

private val authValues = listOf(
    "0",
    "SECP256R1",
    "SECP384R1",
    "SECP521R1",
    "BRAINPOOLP256R1",
    "BRAINPOOLP384R1",
    "BRAINPOOLP512R1",
    "ED25519",
    "ED448"
)

@Singleton
class TransferManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logManager: LogManager
) {

    val canSetName = logManager.canSetName()

    //currently we are getting the first
    private val authName = authValues.first()

    private var mdocConnectionMethod: ConnectionMethod? = null

    private var hasStarted = false


    var responseBytes: ByteArray? = null
        private set
    private var verification: VerificationHelper? = null


    private val transferStatusLd = MutableStateFlow(TransferStatus.IDLE)
    fun getTransferStatus(): StateFlow<TransferStatus> = transferStatusLd.asStateFlow()

    fun setCustomName(customName: String){
        logManager.setCustomName(customName)
    }

    private fun initVerification() {
        val builder = VerificationHelper.Builder(
            context,
            responseListener,
            Executors.newSingleThreadScheduledExecutor()
        )
        val options = DataTransportOptions.Builder()
            .setBleUseL2CAP(true)
            .setBleClearCache(true)
            .build()
        builder.setDataTransportOptions(options)
        verification = builder.build()
    }

    fun setQrDeviceEngagement(qrDeviceEngagement: String) {
        initVerification()
        logManager.startEngagement(qrDeviceEngagement)
        verification?.setDeviceEngagementFromQrCode(qrDeviceEngagement)
    }

    private fun setAvailableTransferMethods(availableMdocConnectionMethods: Collection<ConnectionMethod>) {
        availableMdocConnectionMethods.find{
            it is ConnectionMethodBle
        }?.let {
            mdocConnectionMethod = it
        }
    }

    fun connect() {

        if (hasStarted)
            throw IllegalStateException("Connection has already started. It is necessary to stop verification before starting a new one.")

        if (verification == null)
            throw IllegalStateException("It is necessary to start a new engagement.")

        if (mdocConnectionMethod == null)
            throw IllegalStateException("No mdoc connection method selected.")

        // Start connection
        verification?.let {
            mdocConnectionMethod?.let { dr ->
                it.connect(dr)
            }
            hasStarted = true
        }
    }

    fun stopVerification(
        sendSessionTerminationMessage: Boolean,
        useTransportSpecificSessionTermination: Boolean
    ) {

        verification?.setSendSessionTerminationMessage(sendSessionTerminationMessage)
        try {
            if (verification?.isTransportSpecificTerminationSupported == true && useTransportSpecificSessionTermination) {
                verification?.setUseTransportSpecificSessionTermination(true)
            }
        } catch (e: IllegalStateException) {
            logManager.logError(e)
            e.printStackTrace()
        }
        disconnect()

    }

    private fun disconnect(){
        try {
            verification?.disconnect()
        } catch (e: RuntimeException) {
            logManager.logError(e)
            e.printStackTrace()
        }
        transferStatusLd.value = TransferStatus.IDLE
        destroy()
        hasStarted = false
    }

    private fun destroy() {
        responseBytes = null
        verification = null
        logManager.end()
    }

    private val responseListener = object : VerificationHelper.Listener {
        override fun onReaderEngagementReady(readerEngagement: ByteArray) {
            transferStatusLd.value = TransferStatus.READER_ENGAGEMENT_READY
        }

        override fun onDeviceEngagementReceived(connectionMethods: MutableList<ConnectionMethod>) {
            // Need to disambiguate the connection methods here to get e.g. two ConnectionMethods
            // if both BLE modes are available at the same time.
            setAvailableTransferMethods(ConnectionMethod.disambiguate(connectionMethods))
            transferStatusLd.value = TransferStatus.ENGAGED
        }

        override fun onMoveIntoNfcField() {}

        override fun onDeviceConnected() {
            logManager.logDeviceConnected()
            transferStatusLd.value = TransferStatus.CONNECTED
        }

        override fun onResponseReceived(deviceResponseBytes: ByteArray) {
            logManager.logResponse()
            responseBytes = deviceResponseBytes
            transferStatusLd.value = TransferStatus.RESPONSE
        }

        override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
            logManager.logDeviceDisconnected(transportSpecificTermination)
            transferStatusLd.value = TransferStatus.DISCONNECTED
        }

        override fun onError(error: Throwable) {
            logManager.logError(error)
            transferStatusLd.value = TransferStatus.ERROR
        }
    }

    fun sendRequest() {
        if (verification == null)
            throw IllegalStateException("It is necessary to start a new engagement.")

        val requestDocumentList = RequestDocumentList()
        val mdl = RequestMdl
        val map = mutableMapOf<String, Boolean>()

        val requestStringBuilder = StringBuilder()

        RequestMdl.dataItems.forEach {
            requestStringBuilder.append(it.identifier)
            requestStringBuilder.append(",")
            map[it.identifier] = true
        }

        logManager.logRequest(requestStringBuilder.toString())

        mdl.setSelectedDataItems(map)
        requestDocumentList.addRequestDocument(mdl)

        verification?.let {
            var signature: Signature? = null
            var readerKeyCertificateChain: Collection<X509Certificate>? = null

            val provider = BouncyCastleProvider()

            when (authName) {
                SupportedCurves.SECP256R1.name, SupportedCurves.BRAINPOOLP256R1.name -> {
                    val keyPair = ReaderCertificateGenerator.generateECDSAKeyPair(authName)

                    signature = Signature.getInstance("SHA256withECDSA", provider)
                    signature.initSign(keyPair.private)

                    val readerCA = KeysAndCertificates.getGoogleReaderCA(context)
                    val readerCertificate =
                        ReaderCertificateGenerator.createReaderCertificate(
                            keyPair,
                            readerCA,
                            getReaderCAPrivateKey()
                        )
                    readerKeyCertificateChain = listOf(readerCertificate)
                }
                SupportedCurves.SECP384R1.name, SupportedCurves.BRAINPOOLP384R1.name -> {
                    val keyPair = ReaderCertificateGenerator.generateECDSAKeyPair(authName)

                    signature = Signature.getInstance("SHA384withECDSA", provider)
                    signature.initSign(keyPair.private)

                    val readerCA = KeysAndCertificates.getGoogleReaderCA(context)
                    val readerCertificate =
                        ReaderCertificateGenerator.createReaderCertificate(
                            keyPair,
                            readerCA,
                            getReaderCAPrivateKey()
                        )
                    readerKeyCertificateChain = listOf(readerCertificate)
                }
                SupportedCurves.SECP521R1.name, SupportedCurves.BRAINPOOLP512R1.name -> {
                    val keyPair = ReaderCertificateGenerator.generateECDSAKeyPair(authName)

                    signature = Signature.getInstance("SHA512withECDSA", provider)
                    signature.initSign(keyPair.private)

                    val readerCA = KeysAndCertificates.getGoogleReaderCA(context)
                    val readerCertificate =
                        ReaderCertificateGenerator.createReaderCertificate(
                            keyPair,
                            readerCA,
                            getReaderCAPrivateKey()
                        )
                    readerKeyCertificateChain = listOf(readerCertificate)
                }
                SupportedCurves.ED25519.name, SupportedCurves.ED448.name -> {
                    val keyPair = ReaderCertificateGenerator.generateECDSAKeyPair(authName)

                    signature = Signature.getInstance(authName, provider)
                    signature.initSign(keyPair.private)

                    val readerCA = KeysAndCertificates.getGoogleReaderCA(context)
                    val readerCertificate =
                        ReaderCertificateGenerator.createReaderCertificate(
                            keyPair, readerCA, getReaderCAPrivateKey()
                        )
                    readerKeyCertificateChain = listOf(readerCertificate)
                }
            }

            val generator =
                DeviceRequestGenerator()
            generator.setSessionTranscript(it.sessionTranscript)
            requestDocumentList.getAll().forEach { requestDocument ->
                generator.addDocumentRequest(
                    requestDocument.docType,
                    requestDocument.getItemsToRequest(),
                    null,
                    signature,
                    readerKeyCertificateChain
                )
            }
            verification?.sendRequest(generator.generate())
        }
    }

    private fun getReaderCAPrivateKey(): PrivateKey {
        // TODO: should get private key from KeysAndCertificates class instead of
        //  hard-coding it here.
        val keyBytes: ByteArray = Base64.getDecoder()
            .decode("ME4CAQAwEAYHKoZIzj0CAQYFK4EEACIENzA1AgEBBDCI6BG/yRDzi307Rqq2Ndw5mYi2y4MR+n6IDqjl2Qw/Sdy8D5eCzp8mlcL/vCWnEq0=")
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePrivate(spec)
    }

    private fun getDeviceResponse(): DeviceResponseParser.DeviceResponse {
        responseBytes?.let { rb ->
            verification?.let { v ->
                val parser =
                    DeviceResponseParser()
                parser.setSessionTranscript(v.sessionTranscript)
                parser.setEphemeralReaderKey(v.ephemeralReaderKey)
                parser.setDeviceResponse(rb)
                return parser.parse()
            } ?: throw IllegalStateException("Verification is null")
        } ?: throw IllegalStateException("Response not received")
    }

////////////////////////////////////////////////////////////////////////////////////////////////////

    fun hasDocuments() = try {
        getDeviceResponse().documents.isNotEmpty()
    } catch(e: Exception) {
        logManager.logError(e)
        false
    }

    suspend fun parseResult(): DriveLicenseData? {

        return withContext(Dispatchers.IO) {

            try{

                val info = StringBuilder()

                val documents = getDeviceResponse().documents

                documents.firstOrNull()?.let { doc ->
                    val simpleIssuerTrustStore =
                        SimpleIssuerTrustStore(KeysAndCertificates.getTrustedIssuerCertificates(context))

                    val certPath =
                        simpleIssuerTrustStore.createCertificationTrustPath(doc.issuerCertificateChain.toList())
                    val certChain = if (certPath?.isNotEmpty() == true) {
                        certPath
                    } else {
                        doc.issuerCertificateChain.toList()
                    }

                    val issuerItems = certChain.last().issuerX500Principal.name.split(",")
                    var cnFound = false
                    val commonName = StringBuffer()
                    for (issuerItem in issuerItems) {
                        when {
                            issuerItem.contains("CN=") -> {
                                val (key, value) = issuerItem.split("=", limit = 2)
                                commonName.append(value)
                                cnFound = true
                            }

                            cnFound && !issuerItem.contains("=") -> commonName.append(", $issuerItem")
                            cnFound -> break
                        }
                    }

                    info.append("Issuer’s DS Key Recognized")
                    info.append(" -> ")
                    info.append(commonName)
                    info.append("\n\n")

                    info.append("Issuer Signed Authenticated")
                    info.append(" -> ")
                    info.append(doc.issuerSignedAuthenticated)
                    info.append("\n\n")

                    var macOrSignatureString = "MAC"

                    if (doc.deviceSignedAuthenticatedViaSignature)
                        macOrSignatureString = "ECDSA"

                    info.append("Device Signed Authenticated")
                    info.append(" -> ")
                    info.append(macOrSignatureString)
                    info.append("\n\n")

                    val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())

                    val calSigned = GregorianCalendar(TimeZone.getTimeZone("UTC"))
                    val calValidFrom = GregorianCalendar(TimeZone.getTimeZone("UTC"))
                    val calValidUntil = GregorianCalendar(TimeZone.getTimeZone("UTC"))

                    calSigned.timeInMillis = doc.validityInfoSigned.toEpochMilli()
                    calValidFrom.timeInMillis = doc.validityInfoValidFrom.toEpochMilli()
                    calValidUntil.timeInMillis = doc.validityInfoValidUntil.toEpochMilli()

                    val currentTime = System.currentTimeMillis()

                    val isValidOnDates =
                        currentTime in calValidFrom.timeInMillis..calValidUntil.timeInMillis

                    info.append("Signed")
                    info.append(" -> ")
                    info.append(df.format(calSigned))
                    info.append("\n")
                    info.append("Valid From")
                    info.append(" -> ")
                    info.append(df.format(calValidFrom))
                    info.append("\n")
                    info.append("Valid Until")
                    info.append(" -> ")
                    info.append(df.format(calValidUntil))
                    info.append("\n\n")

                    val deviceKeySha1 = encodeToString(
                        MessageDigest.getInstance("SHA-1").digest(doc.deviceKey.encoded)
                    )

                    info.append("SHA-1")
                    info.append(" -> ")
                    info.append(deviceKeySha1)
                    info.append("\n\n")

                    val documentHandler = DocumentHandler()

                    for (ns in doc.issuerNamespaces) {

                        for (elem in doc.getIssuerEntryNames(ns)) {

                            val value: ByteArray = doc.getIssuerEntryData(ns, elem)

                            if (isPortraitElement(
                                    doc.docType,
                                    ns,
                                    elem
                                ) || (doc.docType == MICOV_DOCTYPE && ns == MICOV_ATT_NAMESPACE && elem == "fac")
                            ) {
                                info.append("portrait")
                                info.append("\n")
                                documentHandler.setPortraitBytes(doc.getIssuerEntryByteString(ns, elem))
                            } else if (doc.docType == MDL_DOCTYPE && ns == MDL_NAMESPACE && elem == "signature_usual_mark") {
                                info.append("signature")
                                info.append("\n")
                                //signatureBytes = doc.getIssuerEntryByteString(ns, elem)
                            } else if (doc.docType == MDL_DOCTYPE && ns == MDL_NAMESPACE && elem == "extra") {
                                info.append("extras")
                                info.append("\n")
                                //nothing
                            } else if (doc.docType == EU_PID_DOCTYPE && ns == EU_PID_NAMESPACE && elem == "biometric_template_finger") {
                                info.append("biometric")
                                info.append("\n")
                                //nothing
                            } else {
                                info.append(elem)
                                info.append(" -> ")
                                info.append(CborUtil.toDiagnostics(
                                    value,
                                    CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT or CborUtil.DIAGNOSTICS_FLAG_EMBEDDED_CBOR
                                ))
                                info.append("\n")

                                documentHandler.addField(elem, getDataItems(value))
                            }

                        }
                    }
                    logManager.logReceived(info.toString())
                    DriveLicenseData.fromDocumentHandler(documentHandler, isValidOnDates)
                }


            } catch (e: Exception){
                logManager.logError(e)
                transferStatusLd.value = TransferStatus.ERROR
                null
            }

        }

    }

    private fun isPortraitApplicable(docType: String, namespace: String?): Boolean{
        val hasPortrait = docType == MDL_DOCTYPE || docType == EU_PID_DOCTYPE
        val namespaceContainsPortrait = namespace == MDL_NAMESPACE || namespace == EU_PID_NAMESPACE
        return hasPortrait && namespaceContainsPortrait
    }

    private fun isPortraitElement(
        docType: String,
        namespace: String?,
        entryName: String?
    ): Boolean {
        val portraitApplicable = isPortraitApplicable(docType, namespace)
        return portraitApplicable && entryName == "portrait"
    }

    private fun encodeToString(bytes: ByteArray): String {
        val stringBuilder = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            stringBuilder.append(String.format("%02x", byte))
        }
        return stringBuilder.toString()
    }

    private fun getDataItems(
        encodedBytes: ByteArray
    ): List<DataItem>{

        val inputStream = ByteArrayInputStream(encodedBytes)

        return try {
            CborDecoder(inputStream).decode()
        } catch (e: CborException) {
            throw IllegalStateException(e)
        }

    }

    companion object {
        private const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        private const val MICOV_DOCTYPE = "org.micov.1"
        private const val MDL_NAMESPACE = "org.iso.18013.5.1"
        private const val MICOV_ATT_NAMESPACE = "org.micov.attestation.1"
        private const val EU_PID_DOCTYPE = "eu.europa.ec.eudiw.pid.1"
        private const val EU_PID_NAMESPACE = "eu.europa.ec.eudiw.pid.1"
    }

}
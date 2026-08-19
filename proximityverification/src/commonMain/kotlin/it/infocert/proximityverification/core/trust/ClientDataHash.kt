package it.infocert.proximityverification.core.trust

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.X509SignatureAlgorithm
import at.asitplus.signum.indispensable.asn1.Asn1String
import at.asitplus.signum.indispensable.pki.AttributeTypeAndValue
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest
import at.asitplus.signum.indispensable.pki.RelativeDistinguishedName
import at.asitplus.signum.indispensable.pki.TbsCertificationRequest
import it.infocert.proximityverification.core.crypto.LocalSecureAreaFactory
import it.infocert.proximityverification.logging.Logger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.EcPublicKeyDoubleCoordinate
import org.multipaz.crypto.EcSignature
import org.multipaz.securearea.SecureArea
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val TAG = "ClientDataHash"

expect fun sha256(input: ByteArray): ByteArray

internal val canonicalJsonFormatter = Json {
    encodeDefaults = true
    prettyPrint = false
    explicitNulls = false
}

@OptIn(ExperimentalEncodingApi::class)
fun ByteArray.encodeBase64UrlNoPadding(): String =
    Base64.UrlSafe.encode(this).trimEnd('=')

fun produceClientData(nonce: String, jwk: Jwk): ClientData {
    val thumbprint = computeJwkThumbprint(jwk)
    return ClientData(nonce = nonce, jwkThumbprint = thumbprint)
}

fun produceClientDataHash(clientData: ClientData): ByteArray {
    val canonicalJson = canonicalJsonFormatter.encodeToString(clientData)
    val bytes = canonicalJson.encodeToByteArray()
    return sha256(bytes)
}

fun produceBArrayFromClientData(cd:ClientData): ByteArray {
    val canonicalJson = canonicalJsonFormatter.encodeToString(cd)
    val bytes = canonicalJson.encodeToByteArray()
    return bytes
}

fun produceClientDataHashBase64Url(clientData: ClientData): String =
    produceClientDataHash(clientData).encodeBase64UrlNoPadding()

suspend fun produceHardwareSignature(
    alias: String,
    clientDataHash: ByteArray,
): ByteArray {
    val mn = "produceHardwareSignature"

    Logger.info(TAG) { "$mn - Preparing hardware signature..." }

    val eCs : EcSignature = LocalSecureAreaFactory.createLocalSecureAreaProvider().get().sign(
        alias = alias,
        dataToSign = clientDataHash
    )

    Logger.info(TAG) { "$mn - ec signature generated" }

    return eCs.toCoseEncoded()
}
package it.infocert.proximityverification.core.trust

fun computeJwkThumbprint(jwk: Jwk): String {
    val canonicalJson = """{"crv":"${jwk.crv}","kty":"${jwk.kty}","x":"${jwk.x}","y":"${jwk.y}"}"""
    val bytes = canonicalJson.encodeToByteArray()
    val hash = sha256(bytes)
    return hash.encodeBase64UrlNoPadding()
}
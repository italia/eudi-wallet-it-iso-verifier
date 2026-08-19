package it.infocert.proximityverification

object TestUtils {
    const val BASE_URL = "https://stoplight.io/mocks/infocert-api/verifierbe/857987083/v1"

    fun signedJwtStub(): String = "signed.jwt.token"

    fun errorJson(
        code: String,
        description: String
    ) = """
        {
            "error": "$code",
            "error_description": "$description"
        }
    """.trimIndent()
}
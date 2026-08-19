package it.infocert.proximityverification.core.models.initialization

data class CSRParam(
    val commonName: String = "",
    val organization: String = "",
    val organizationalUnit: String = "",
    val country: String = ""
) {
    override fun toString(): String {
        return "CN=$commonName, O=$organization, OU=$organizationalUnit, C=$country"
    }
}
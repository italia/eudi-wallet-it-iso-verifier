package it.infocert.proximityverification.core.crypto
import org.multipaz.securearea.SecureArea
import org.multipaz.securearea.SecureAreaProvider


expect object LocalSecureAreaFactory {
    fun createLocalSecureAreaProvider(): SecureAreaProvider<SecureArea>
}
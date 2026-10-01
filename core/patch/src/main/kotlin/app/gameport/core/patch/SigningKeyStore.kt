package app.gameport.core.patch

import com.android.apksig.ApkSigner
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/**
 * The key patched games are signed with. It is created on first use, on this device only, and
 * lives in the app's private storage; nothing about it is shared with other apps or the repository.
 */
class SigningKeyStore(private val file: File) {
    fun signerConfig(): ApkSigner.SignerConfig {
        val store = loadOrCreate()
        val key = store.getKey(ALIAS, PASSWORD) as PrivateKey
        val certificate = store.getCertificate(ALIAS) as X509Certificate
        return ApkSigner.SignerConfig.Builder("gameport", key, listOf(certificate)).build()
    }

    private fun loadOrCreate(): KeyStore {
        val store = KeyStore.getInstance(STORE_TYPE)
        if (file.exists()) {
            file.inputStream().use { store.load(it, PASSWORD) }
            return store
        }
        store.load(null, PASSWORD)
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_BITS) }.generateKeyPair()
        val subject = X500Name("CN=GamePort patched game")
        val now = System.currentTimeMillis()
        val certificate = JcaX509CertificateConverter().getCertificate(
            JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(now),
                Date(now - DAY_MS),
                Date(now + VALID_YEARS * 365L * DAY_MS),
                subject,
                pair.public,
            ).build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private)),
        )
        store.setKeyEntry(ALIAS, pair.private, PASSWORD, arrayOf(certificate))
        file.parentFile?.mkdirs()
        file.outputStream().use { store.store(it, PASSWORD) }
        return store
    }

    private companion object {
        const val STORE_TYPE = "PKCS12"
        const val ALIAS = "gameport"
        val PASSWORD = "gameport-local".toCharArray()
        const val KEY_BITS = 2048
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val VALID_YEARS = 30
    }
}

package misk.security.ssl

import misk.security.ssl.Keystores.TYPE_JCEKS
import java.io.FileInputStream

data class KeystoreConfig(
        val path: String,
        val passphrase: String? = null,
        val type: String = TYPE_JCEKS
) {
    fun load() = Keystores.load(FileInputStream(path), type, passphrase)
}
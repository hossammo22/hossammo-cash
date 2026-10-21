package com.hossammo.cash.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * يدير مفتاح تشفير قاعدة البيانات:
 * - يُولَّد مفتاح عشوائي (32 بايت) لقاعدة SQLCipher عند أول تشغيل.
 * - يُشفَّر هذا المفتاح بمفتاح AES-GCM مخزن داخل Android Keystore (لا يمكن استخراجه).
 * - يُحفظ المفتاح المشفر فقط في ملف داخل noBackupFilesDir.
 * لا يُحفظ أي مفتاح كنص واضح.
 */
class DatabaseKeyManager(private val context: Context) {

    private val keyFile: File get() = File(context.noBackupFilesDir, KEY_FILE)

    @Synchronized
    fun getOrCreatePassphrase(): ByteArray {
        val file = keyFile
        if (file.exists()) {
            return decrypt(file.readText())
        }
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        file.writeText(encrypt(passphrase))
        return passphrase
    }

    fun hasKey(): Boolean = keyFile.exists()

    /** يحذف المفتاح (يُستخدم فقط عند حذف جميع البيانات). */
    fun destroy() {
        keyFile.delete()
        runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            ks.deleteEntry(ALIAS)
        }
    }

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(data: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val enc = cipher.doFinal(data)
        return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): ByteArray {
        val (ivB64, dataB64) = stored.trim().split(":", limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(ivB64, Base64.NO_WRAP)))
        return cipher.doFinal(Base64.decode(dataB64, Base64.NO_WRAP))
    }

    /** تشفير/فك تشفير عام لبيانات صغيرة بمفتاح Keystore (للنسخة التلقائية قبل الاستعادة). */
    fun encryptBlob(data: ByteArray): String = encrypt(data)
    fun decryptBlob(stored: String): ByteArray = decrypt(stored)

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "hossammo_cash_db_key"
        private const val KEY_FILE = "db_key.enc"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

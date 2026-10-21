package com.hossammo.cash.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * تشفير النسخ الاحتياطية بكلمة مرور:
 * PBKDF2-HMAC-SHA256 (210,000 تكرار) لاشتقاق مفتاح AES-256، ثم AES-GCM (يوفر فحص سلامة).
 * بنية الملف: MAGIC(4) | version(1) | salt(16) | iv(12) | ciphertext(gzip(json))
 */
object BackupCrypto {
    private const val ITERATIONS = 210_000
    private const val SALT_LEN = 16
    private const val IV_LEN = 12

    fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        cipher.updateAAD(BackupFormat.MAGIC)
        val enc = cipher.doFinal(gzip(plain))
        return ByteArrayOutputStream().apply {
            write(BackupFormat.MAGIC)
            write(BackupFormat.FORMAT_VERSION)
            write(salt)
            write(iv)
            write(enc)
        }.toByteArray()
    }

    /** يعيد null إذا كانت كلمة المرور خاطئة أو الملف تالفًا. يرمي IllegalArgumentException إذا لم يكن الملف نسخة احتياطية. */
    fun decrypt(data: ByteArray, password: CharArray): ByteArray? {
        require(isBackup(data)) { "not a backup" }
        val header = BackupFormat.MAGIC.size + 1
        if (data.size < header + SALT_LEN + IV_LEN + 16) return null
        val salt = data.copyOfRange(header, header + SALT_LEN)
        val iv = data.copyOfRange(header + SALT_LEN, header + SALT_LEN + IV_LEN)
        val enc = data.copyOfRange(header + SALT_LEN + IV_LEN, data.size)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
            cipher.updateAAD(BackupFormat.MAGIC)
            gunzip(cipher.doFinal(enc))
        } catch (e: javax.crypto.AEADBadTagException) {
            null
        } catch (e: java.io.IOException) {
            null
        } catch (e: java.security.GeneralSecurityException) {
            null
        }
    }

    fun isBackup(data: ByteArray): Boolean =
        data.size > BackupFormat.MAGIC.size && data.copyOfRange(0, BackupFormat.MAGIC.size).contentEquals(BackupFormat.MAGIC)

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun gzip(b: ByteArray): ByteArray = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(b) } }.toByteArray()
    private fun gunzip(b: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(b)).use { it.readBytes() }
}

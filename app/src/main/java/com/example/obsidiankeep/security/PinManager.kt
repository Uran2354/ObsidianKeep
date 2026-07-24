package com.example.obsidiankeep.security

import android.content.Context
import java.security.MessageDigest
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Хранилище PIN-кода и decoy PIN, авто-блокировки.
 *
 * PIN хранится как PBKDF2-HMAC-SHA256 hash с солью — даже при компрометации
 * SharedPreferences восстановить PIN невозможно.
 *
 * Decoy PIN — отдельный hash; при его вводе приложение показывает только
 * заметки вне защищённых папок (как в Signal).
 *
 * Используется SharedPreferences для синхронного доступа (важно для LockScreen
 * в attachBaseContext / onCreate).
 */
class PinManager private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** PIN включён? */
    fun isPinEnabled(): Boolean = prefs.getString(KEY_PIN_HASH, null) != null

    /** Возвращает хэш текущего PIN (или null, если PIN не задан). */
    private fun pinHash(): String? = prefs.getString(KEY_PIN_HASH, null)

    /** Возвращает хэш decoy PIN (или null, если decoy не задан). */
    private fun decoyHash(): String? = prefs.getString(KEY_DECOY_HASH, null)

    /** Соль для хэширования (генерируется при первом включении PIN). */
    private fun salt(): ByteArray {
        var s = prefs.getString(KEY_SALT, null)
        if (s == null) {
            val random = java.security.SecureRandom()
            val bytes = ByteArray(16).also { random.nextBytes(it) }
            s = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            prefs.edit().putString(KEY_SALT, s).apply()
        }
        return android.util.Base64.decode(s, android.util.Base64.NO_WRAP)
    }

    /** Установить новый PIN (хэширует и сохраняет). */
    fun setPin(pin: String) {
        require(pin.length in 4..16) { "PIN length must be 4..16" }
        val hash = hashPin(pin, salt())
        prefs.edit().putString(KEY_PIN_HASH, hash).apply()
    }

    /** Установить decoy PIN (или null, чтобы отключить). */
    fun setDecoyPin(pin: String?) {
        if (pin == null) {
            prefs.edit().remove(KEY_DECOY_HASH).apply()
        } else {
            require(pin.length in 4..16) { "Decoy PIN length must be 4..16" }
            val hash = hashPin(pin, salt())
            prefs.edit().putString(KEY_DECOY_HASH, hash).apply()
        }
    }

    /** Отключить PIN и decoy. */
    fun clear() {
        prefs.edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_DECOY_HASH)
            .remove(KEY_SALT)
            .apply()
    }

    /**
     * Проверить введённый PIN.
     * @return PinCheckResult.MAIN, PinCheckResult.DECOY или PinCheckResult.WRONG
     */
    fun checkPin(pin: String): PinCheckResult {
        val salt = salt()
        val inputHash = hashPin(pin, salt)
        return when {
            pinHash() != null && constantTimeEquals(inputHash, pinHash()!!) -> PinCheckResult.MAIN
            decoyHash() != null && constantTimeEquals(inputHash, decoyHash()!!) -> PinCheckResult.DECOY
            else -> PinCheckResult.WRONG
        }
    }

    /** Decoy PIN задан? */
    fun hasDecoy(): Boolean = decoyHash() != null

    /** Установить таймаут авто-блокировки в секундах (0 = без авто-блокировки). */
    fun setAutoLockSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_AUTOLOCK, seconds.coerceAtLeast(0)).apply()
    }

    /** Получить таймаут авто-блокировки (по умолчанию 60 сек). */
    fun getAutoLockSeconds(): Int = prefs.getInt(KEY_AUTOLOCK, 60)

    /** Timestamp последней разблокировки (epoch ms). */
    fun setLastUnlockTime(timestampMs: Long) {
        prefs.edit().putLong(KEY_LAST_UNLOCK, timestampMs).apply()
    }

    fun getLastUnlockTime(): Long = prefs.getLong(KEY_LAST_UNLOCK, 0L)

    /** Нужна ли блокировка сейчас (прошло больше autoLock секунд с момента lastUnlock). */
    fun shouldLockNow(): Boolean {
        val autoLock = getAutoLockSeconds()
        if (autoLock <= 0) return false
        val elapsed = System.currentTimeMillis() - getLastUnlockTime()
        return elapsed > autoLock * 1000L
    }

    /** Режим decoy активирован в этой сессии? (true, если в последний раз введён decoy) */
    fun isDecoySession(): Boolean = prefs.getBoolean(KEY_DECOY_SESSION, false)

    fun setDecoySession(active: Boolean) {
        prefs.edit().putBoolean(KEY_DECOY_SESSION, active).apply()
    }

    private fun hashPin(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 100_000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    companion object {
        private const val PREFS_NAME = "pin_prefs"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_DECOY_HASH = "decoy_hash"
        private const val KEY_SALT = "salt"
        private const val KEY_AUTOLOCK = "autolock_seconds"
        private const val KEY_LAST_UNLOCK = "last_unlock_ms"
        private const val KEY_DECOY_SESSION = "decoy_session_active"

        @Volatile
        private var INSTANCE: PinManager? = null

        fun get(context: Context): PinManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PinManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}

enum class PinCheckResult { MAIN, DECOY, WRONG }

package ru.sash0k.bluetooth_terminal

import android.content.Context
import android.preference.PreferenceManager
import android.text.InputFilter
import android.text.Spanned
import android.util.Log

/**
 * Вспомогательные методы
 * Created by sash0k on 29.01.14.
 */
object Utils {

    /**
     * Общий метод вывода отладочных сообщений в лог
     */
    fun log(message: String?) {
        if (BuildConfig.DEBUG) {
            if (message != null) Log.i(Const.TAG, message)
        }
    }
    // ============================================================================
    /**
     * Конвертация hex-команд в строку для отображения
     */
    fun printHex(hex: String): String {
        val sb = StringBuilder()
        val len = hex.length
        try {
            var i = 0
            while (i < len) {
                sb.append("0x").append(hex.substring(i, i + 2)).append(" ")
                i += 2
            }
        } catch (e: NumberFormatException) {
            log("printHex NumberFormatException: " + e.message)
        } catch (e: StringIndexOutOfBoundsException) {
            log("printHex StringIndexOutOfBoundsException: " + e.message)
        }
        return sb.toString()
    }
    // ============================================================================
    /**
     * Перевод введенных ASCII-команд в hex побайтно.
     * @param hex - команда
     * @return - массив байт команды
     */
    fun toHex(hex: String): ByteArray {
        val len = hex.length
        val result = ByteArray(len)
        try {
            var index = 0
            var i = 0
            while (i < len) {
                result[index] = hex.substring(i, i + 2).toInt(16).toByte()
                index++
                i += 2
            }
        } catch (e: NumberFormatException) {
            log("toHex NumberFormatException: " + e.message)
        } catch (e: StringIndexOutOfBoundsException) {
            log("toHex StringIndexOutOfBoundsException: " + e.message)
        }
        return result
    }
    // ============================================================================
    /**
     * Метод сливает два массива в один
     */
    fun concat(A: ByteArray, B: ByteArray): ByteArray {
        val C = ByteArray(A.size + B.size)
        System.arraycopy(A, 0, C, 0, A.size)
        System.arraycopy(B, 0, C, A.size, B.size)
        return C
    }
    // ============================================================================
    /**
     * Modulo
     */
    fun mod(x: Int, y: Int): Int {
        val result = x % y
        return if (result < 0) result + y else result
    }
    // ============================================================================
    /**
     * Расчёт контрольной суммы
     */
    fun calcModulo256(command: String): String {
        var crc = 0
        for (i in 0 until command.length) {
            crc += command[i].code
        }
        return Integer.toHexString(mod(crc, 256))
    }
    // ============================================================================
    /**
     * Раскрасить текст нужным цветом
     */
    fun mark(text: String, color: String): String {
        return "<font color=$color>$text</font>"
    }
    // ============================================================================
    /**
     * Получение id сохранённого в игрушке звукового набора
     */
    fun getPrefence(context: Context?, item: String?): String {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        return settings.getString(item, Const.TAG) ?: ""
    }
    // ============================================================================
    /**
     * Получение флага из настроек
     */
    fun getBooleanPrefence(context: Context?, tag: String?): Boolean {
        val settings = PreferenceManager.getDefaultSharedPreferences(context)
        return settings.getBoolean(tag, true)
    }

    // ============================================================================
    fun formatNumber(input: String): Int {
        val value: Int
        value = try {
            input.toInt()
        } catch (e: NumberFormatException) {
            0
        }
        return value
    }
    // ============================================================================
    /**
     * Класс-фильтр полей ввода
     */
    // ============================================================================
    class InputFilterHex : InputFilter {

        override fun filter(
            source: CharSequence,
            start: Int,
            end: Int,
            dest: Spanned,
            dstart: Int,
            dend: Int
        ): CharSequence? {
            for (i in start until end) {
                if (!Character.isDigit(source[i])
                    && source[i] != 'A'
                    && source[i] != 'D'
                    && source[i] != 'B'
                    && source[i] != 'E'
                    && source[i] != 'C'
                    && source[i] != 'F'
                ) {
                    return ""
                }
            }
            return null
        }
    }
}

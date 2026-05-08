package de.ju0x.mensacard.nfc

import android.nfc.tech.IsoDep
import android.util.Log

class BalanceReader {

    companion object {
        private const val TAG = "BalanceReader"

        private val STATUS_OK     = 0x91.toByte() to 0x00.toByte()
        private val STATUS_OK_ALT = 0x90.toByte() to 0x00.toByte()

        val MENSA_AID = byteArrayOf(0x5F.toByte(), 0x84.toByte(), 0x15.toByte())
    }


    /**
     * Reads current balance and currency of the MensaCard.
     * Returns CardData-Object on success, or null if an error occurred.
     */
    fun read(isoDep: IsoDep, aid: ByteArray = MENSA_AID): CardData? {
        return try {
            isoDep.connect()
            selectApplication(isoDep, aid)

            val currency = readCurrency(isoDep, fileId = 0x00)
            val balanceCents = readBalance(isoDep, fileId = 0x01)

            if (balanceCents != null) {
                CardData(
                    balanceCents = balanceCents,
                    currency = currency
                )
            } else {
                Log.w(TAG, "Balance not readable (Read key: blocked)")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading card: ${e.message}")
            null
        } finally {
            runCatching { isoDep.close() }
        }
    }

    /**
     * DESFire: Selects the application (Cmd 0x5A).
     */
    private fun selectApplication(isoDep: IsoDep, aid: ByteArray) {
        val cmd = byteArrayOf(0x90.toByte(), 0x5A.toByte(), 0x00, 0x00, 0x03) + aid + 0x00
        val response = isoDep.transceive(cmd)
        check(response.isSuccess()) {
            "SelectApplication failed: ${response.toHexString()}"
        }
        Log.d(TAG, "Selected App: ${aid.toHexString()}")
    }

    /**
     * DESFire: Read Data File (Cmd 0xBD)
     * Used for currency detection.
     */
    private fun readDataFile(
        isoDep: IsoDep,
        fileId: Int,
        offset: Int = 0,
        length: Int = 0
    ): ByteArray? {
        val cmd = byteArrayOf(
            0x90.toByte(), 0xBD.toByte(), // CLA INS (ReadData)
            0x00, 0x00,                   // P1 P2
            0x07,                         // Lc: 7 Bytes
            fileId.toByte(),
            (offset and 0xFF).toByte(),
            ((offset shr 8) and 0xFF).toByte(),
            ((offset shr 16) and 0xFF).toByte(),
            (length and 0xFF).toByte(),
            ((length shr 8) and 0xFF).toByte(),
            ((length shr 16) and 0xFF).toByte(),
            0x00                          // Le
        )
        val response = isoDep.transceive(cmd)
        return response.takeIfSuccess()
    }

    /**
     * Reads the currency that is used from file 0x00.
     */
    private fun readCurrency(isoDep: IsoDep, fileId: Int): String {
        val raw = readDataFile(isoDep, fileId)
        val currency = raw?.toString(Charsets.UTF_8)?.take(3) ?: "EUR"
        return currency
    }

    /**
     * DESFire: Reads the value file that contains the balance  (Cmd 0x6C).
     * Returns raw data of the current balance (in mill)
     */
    private fun readBalance(isoDep: IsoDep, fileId: Int): Int? {
        val cmd = byteArrayOf(
            0x90.toByte(), 0x6C.toByte(), // CLA INS (GetValue)
            0x00, 0x00,                   // P1 P2
            0x01,                         // Lc: 1 Byte
            fileId.toByte(),
            0x00                          // Le
        )
        val response = isoDep.transceive(cmd)
        val data = response.takeIfSuccess() ?: return null

        if (data.size < 4) return null

        // 4 Bytes Little-Endian
        return (data[0].toInt() and 0xFF) or
                ((data[1].toInt() and 0xFF) shl 8) or
                ((data[2].toInt() and 0xFF) shl 16) or
                ((data[3].toInt() and 0xFF) shl 24)
    }


    private fun ByteArray.toHexString() = joinToString("") { "%02X".format(it) }

    private fun ByteArray.isSuccess(): Boolean {
        if (size < 2) return false
        val sw1 = this[size - 2]
        val sw2 = this[size - 1]
        return (sw1 == STATUS_OK.first  && sw2 == STATUS_OK.second) ||
                (sw1 == STATUS_OK_ALT.first && sw2 == STATUS_OK_ALT.second)
    }

    private fun ByteArray.takeIfSuccess(): ByteArray? {
        if (size < 2) {
            Log.w(TAG, "Response too short: ${toHexString()}")
            return null
        }
        val sw1 = this[size - 2]
        val sw2 = this[size - 1]
        Log.d(TAG, "SW: ${"%02X".format(sw1)}${"%02X".format(sw2)}, Data: ${toHexString()}")
        return if (isSuccess()) copyOfRange(0, size - 2) else null
    }
}
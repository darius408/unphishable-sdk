package org.unphishable.sdk.utils

import android.util.Log
import java.nio.ByteBuffer

internal object PacketParser {

    private const val TAG = "Unphishable:Parser"

    fun extractUrl(packet: ByteArray, length: Int): String? {
        return try {
            if (length < 40) return null

            val buffer = ByteBuffer.wrap(packet, 0, length)

            // Read IP header
            val ipVersion = (packet[0].toInt() shr 4) and 0xF
            if (ipVersion != 4) return null

            val ipHeaderLength = (packet[0].toInt() and 0xF) * 4
            val protocol = packet[9].toInt() and 0xFF
            if (protocol != 6) return null

            // Read TCP header
            val tcpOffset = ipHeaderLength
            if (length <= tcpOffset + 13) return null

            val destPort = ((packet[tcpOffset + 2].toInt() and 0xFF) shl 8) or
                           (packet[tcpOffset + 3].toInt() and 0xFF)

            val tcpHeaderLength = ((packet[tcpOffset + 12].toInt() shr 4) and 0xF) * 4
            val payloadOffset = tcpOffset + tcpHeaderLength
            val payloadLength = length - payloadOffset

            if (payloadLength <= 0) return null

            val payload = packet.copyOfRange(payloadOffset, payloadOffset + payloadLength)

            return when (destPort) {
                80   -> extractFromHttp(payload)
                443  -> extractFromHttps(payload)
                8080 -> extractFromHttp(payload)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractFromHttp(payload: ByteArray): String? {
        return try {
            val text = String(payload, Charsets.ISO_8859_1)
            if (!text.startsWith("GET ") &&
                !text.startsWith("POST ") &&
                !text.startsWith("HEAD ")) return null

            val hostMatch = Regex("Host:\\s*([^\\r\\n]+)", RegexOption.IGNORE_CASE)
                .find(text)?.groupValues?.get(1)?.trim() ?: return null

            val pathMatch = Regex("^(?:GET|POST|HEAD) ([^\\s]+)", RegexOption.MULTILINE)
                .find(text)?.groupValues?.get(1)?.trim() ?: "/"

            "http://$hostMatch$pathMatch"
        } catch (e: Exception) {
            null
        }
    }

    private fun extractFromHttps(payload: ByteArray): String? {
        return try {
            if (payload.size < 5) return null
            if (payload[0].toInt() and 0xFF != 22) return null
            if (payload.size < 6 || payload[5].toInt() and 0xFF != 1) return null

            val sni = extractSni(payload) ?: return null
            "https://$sni"
        } catch (e: Exception) {
            null
        }
    }

    private fun extractSni(payload: ByteArray): String? {
        return try {
            var pos = 43

            if (pos >= payload.size) return null

            val sessionIdLength = payload[pos].toInt() and 0xFF
            pos += 1 + sessionIdLength
            if (pos + 2 >= payload.size) return null

            val cipherSuitesLength = ((payload[pos].toInt() and 0xFF) shl 8) or
                                     (payload[pos + 1].toInt() and 0xFF)
            pos += 2 + cipherSuitesLength
            if (pos + 1 >= payload.size) return null

            val compressionLength = payload[pos].toInt() and 0xFF
            pos += 1 + compressionLength
            if (pos + 2 >= payload.size) return null

            val extensionsLength = ((payload[pos].toInt() and 0xFF) shl 8) or
                                   (payload[pos + 1].toInt() and 0xFF)
            pos += 2

            val extensionsEnd = pos + extensionsLength

            while (pos + 4 <= extensionsEnd && pos + 4 <= payload.size) {
                val extType = ((payload[pos].toInt() and 0xFF) shl 8) or
                              (payload[pos + 1].toInt() and 0xFF)
                val extLength = ((payload[pos + 2].toInt() and 0xFF) shl 8) or
                                (payload[pos + 3].toInt() and 0xFF)
                pos += 4

                if (extType == 0) {
                    if (pos + 5 > payload.size) return null
                    val nameLength = ((payload[pos + 3].toInt() and 0xFF) shl 8) or
                                     (payload[pos + 4].toInt() and 0xFF)
                    pos += 5
                    if (pos + nameLength > payload.size) return null
                    return String(payload, pos, nameLength, Charsets.US_ASCII)
                }

                pos += extLength
            }

            null
        } catch (e: Exception) {
            null
        }
    }
}

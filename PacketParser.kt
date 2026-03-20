package org.unphishable.sdk.utils

import android.util.Log

/**
 * PacketParser — Extracts URLs from raw IP/TCP packets from the VPN tunnel.
 *
 * HTTP:  Reads Host header + request path → full URL
 * HTTPS: Reads SNI from TLS ClientHello → domain only
 * Other: Returns null (traffic passes through silently)
 */
internal object PacketParser {

    private const val TAG = "Unphishable:Parser"

    /**
     * Parse a raw IP packet and extract a URL if present.
     * Returns null if packet is not HTTP/HTTPS or URL cannot be extracted.
     */
    fun extractUrl(buffer: ByteArray, length: Int): String? {
        return try {
            val bytes = buffer.copyOf(length)

            // Minimum IP header: 20 bytes
            if (length < 20) return null

            val ipVersion = (bytes[0].toInt() and 0xFF) shr 4
            if (ipVersion != 4) return null // IPv6 not handled yet

            val ipHeaderLength = (bytes[0].toInt() and 0x0F) * 4
            val protocol = bytes[9].toInt() and 0xFF

            // Only handle TCP (protocol 6)
            if (protocol != 6) return null
            if (length < ipHeaderLength + 20) return null

            val tcpHeaderLength = ((bytes[ipHeaderLength + 12].toInt() and 0xFF) shr 4) * 4
            val destPort = ((bytes[ipHeaderLength + 2].toInt() and 0xFF) shl 8) or
                    (bytes[ipHeaderLength + 3].toInt() and 0xFF)

            val payloadOffset = ipHeaderLength + tcpHeaderLength
            if (payloadOffset >= length) return null
            val payload = bytes.copyOfRange(payloadOffset, length)

            return when (destPort) {
                80  -> extractHttpUrl(payload)
                443 -> extractHttpsHost(payload)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractHttpUrl(payload: ByteArray): String? {
        return try {
            val text = String(payload, Charsets.ISO_8859_1)
            if (!text.startsWith("GET ") && !text.startsWith("POST ") &&
                !text.startsWith("HEAD ") && !text.startsWith("PUT ")) return null

            val lines = text.split("\r\n")
            val requestLine = lines.firstOrNull() ?: return null
            val parts = requestLine.split(" ")
            val path = if (parts.size >= 2) parts[1] else "/"

            val hostLine = lines.firstOrNull { it.startsWith("Host:", ignoreCase = true) }
            val host = hostLine?.substringAfter(":")?.trim() ?: return null

            "http://$host$path"
        } catch (e: Exception) {
            null
        }
    }

    private fun extractHttpsHost(payload: ByteArray): String? {
        return try {
            // TLS record: type=22 (handshake), version, length
            if (payload.size < 5) return null
            if (payload[0].toInt() and 0xFF != 22) return null // not handshake

            // Handshake type: 1 = ClientHello
            if (payload.size < 6) return null
            if (payload[5].toInt() and 0xFF != 1) return null

            // Parse SNI extension from ClientHello
            extractSni(payload)
        } catch (e: Exception) {
            null
        }
    }

    private fun extractSni(data: ByteArray): String? {
        return try {
            var i = 43 // Skip fixed fields in ClientHello

            if (i >= data.size) return null

            // Skip session ID
            val sessionIdLength = data[i].toInt() and 0xFF
            i += 1 + sessionIdLength
            if (i + 2 >= data.size) return null

            // Skip cipher suites
            val cipherSuitesLength = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2 + cipherSuitesLength
            if (i + 1 >= data.size) return null

            // Skip compression methods
            val compressionLength = data[i].toInt() and 0xFF
            i += 1 + compressionLength
            if (i + 2 >= data.size) return null

            // Extensions length
            val extensionsLength = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
            val extensionsEnd = i + extensionsLength

            // Walk extensions looking for SNI (type 0x0000)
            while (i + 4 <= extensionsEnd && i + 4 <= data.size) {
                val extType = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
                val extLength = ((data[i + 2].toInt() and 0xFF) shl 8) or (data[i + 3].toInt() and 0xFF)
                i += 4

                if (extType == 0) { // SNI extension
                    if (i + 5 <= data.size) {
                        // Skip SNI list length (2) + type (1) + name length (2)
                        val nameLength = ((data[i + 3].toInt() and 0xFF) shl 8) or (data[i + 4].toInt() and 0xFF)
                        if (i + 5 + nameLength <= data.size) {
                            val sni = String(data, i + 5, nameLength, Charsets.US_ASCII)
                            return "https://$sni"
                        }
                    }
                }
                i += extLength
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}

package org.unphishable.sdk.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.unphishable.sdk.model.ScanResult
import java.io.IOException
import java.util.concurrent.TimeUnit

internal class ScanApiClient(
    private val apiKey: String,
    private val backendUrl: String,
    private val debug: Boolean = false
) {
    private val TAG = "Unphishable:API"
    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    @Throws(IOException::class)
    fun scan(url: String): ScanResult {
        val body = JsonObject().apply {
            addProperty("url", url)
        }.toString().toRequestBody(JSON)

        val request = Request.Builder()
            .url("$backendUrl/sdk/scan")
            .addHeader("X-API-Key", apiKey)
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", "UnphishableSDK/1.0 Android")
            .post(body)
            .build()

        if (debug) Log.d(TAG, "Scanning: $url")

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return ScanResult.safe(url)
            if (!response.isSuccessful) {
                if (debug) Log.w(TAG, "Backend returned ${response.code} — failing open")
                return ScanResult.safe(url)
            }
            parseResponse(url, responseBody)
        } catch (e: Exception) {
            if (debug) Log.e(TAG, "Network error: ${e.message} — failing open")
            ScanResult.safe(url)
        }
    }

    private fun parseResponse(url: String, json: String): ScanResult {
        return try {
            val obj = gson.fromJson(json, JsonObject::class.java)
            val details = obj.getAsJsonObject("details")

            val patterns = mutableListOf<String>()
            obj.getAsJsonArray("patterns_triggered")?.forEach {
                patterns.add(
                    it.asString
                        .replace(Regex("P\\d+:"), "")
                        .replace("_", " ")
                        .replaceFirstChar { c -> c.uppercase() }
                )
            }

            ScanResult(
                url = url,
                verdict = obj.get("verdict")?.asString ?: "SAFE",
                riskLevel = obj.get("risk_level")?.asString ?: "SAFE",
                score = obj.get("score")?.asInt ?: 0,
                patternsTriggered = patterns,
                warningMessage = obj.get("warning_message")?.asString ?: "",
                recommendation = obj.get("recommendation")?.asString ?: "",
                domain = details?.get("domain")?.asString ?: "",
                sslStatus = details?.get("ssl")?.asString ?: "unknown",
                sslIssuer = details?.get("ssl_issuer")?.asString ?: "Unknown",
                ageMonths = details?.get("age_months")?.asInt,
                httpCode = details?.get("http_code")?.asInt,
                cached = obj.get("cached")?.asBoolean ?: false,
                scanId = obj.get("scan_id")?.asString ?: ""
            )
        } catch (e: Exception) {
            if (debug) Log.e(TAG, "Parse error: ${e.message}")
            ScanResult.safe(url)
        }
    }
}

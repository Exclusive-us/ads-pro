package com.adspro.app.net
import com.adspro.app.AdsProApp
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.io.File
import java.util.concurrent.TimeUnit

object Supabase {
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val JSON = "application/json; charset=utf-8".toMediaType()
    fun insert(table: String, json: String): Boolean = runCatching {
        client.newCall(Request.Builder().url("${AdsProApp.REST_URL}/$table").addHeader("apikey", AdsProApp.SUPABASE_KEY).addHeader("Authorization", "Bearer ${AdsProApp.SUPABASE_KEY}").addHeader("Content-Type", "application/json").addHeader("Prefer", "return=minimal").post(json.toRequestBody(JSON)).build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)
    fun uploadFile(path: String, file: File, mime: String): Boolean = runCatching {
        client.newCall(Request.Builder().url("${AdsProApp.STORAGE_URL}/object/media/$path").addHeader("apikey", AdsProApp.SUPABASE_KEY).addHeader("Authorization", "Bearer ${AdsProApp.SUPABASE_KEY}").addHeader("Content-Type", mime).addHeader("x-upsert", "true").post(file.asRequestBody(mime.toMediaType())).build()).execute().use { it.isSuccessful }
    }.getOrDefault(false)
    fun listMedia(prefix: String): List<String> = runCatching {
        val body = """{"prefix":"$prefix","limit":500,"sortBy":{"column":"name","order":"desc"}}"""
        client.newCall(Request.Builder().url("${AdsProApp.STORAGE_URL}/object/list/media").addHeader("apikey", AdsProApp.SUPABASE_KEY).addHeader("Authorization", "Bearer ${AdsProApp.SUPABASE_KEY}").addHeader("Content-Type", "application/json").post(body.toRequestBody(JSON)).build()).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val arr = JSONArray(resp.body?.string().orEmpty())
            (0 until arr.length()).mapNotNull { val name = arr.getJSONObject(it).optString("name", ""); if (name.isNotBlank()) name else null }
        }
    }.getOrDefault(emptyList())
    fun publicUrl(path: String) = "${AdsProApp.SUPABASE_URL}/storage/v1/object/public/media/$path"
}

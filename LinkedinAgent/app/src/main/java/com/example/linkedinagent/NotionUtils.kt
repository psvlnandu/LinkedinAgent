package com.example.linkedinagent

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log.e
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object NotionUtils {

    private  val NOTION_TOKEN = BuildConfig.NOTION_TOKEN
    private val client = OkHttpClient()
    /**
     * Updates the status of a specific page in Notion
     * @param pageId The ID of the page to update
     * @param newStatus The name of the status (e.g., "Accepted", "Interview")
     */

    suspend fun updateNotionStatus(pageId: String, newStatus: String) {

        // Notion API requires a PATCH request to update page properties
        val jsonBody = """
        {
            "properties": {
                "Status": {
                    "status": { "name": "$newStatus" }
                }
            }
        }
    """.trimIndent()

        val request = Request.Builder()
            .url("https://api.notion.com/v1/pages/$pageId")
            .addHeader("Authorization", "Bearer $NOTION_TOKEN")
            .addHeader("Notion-Version", "2022-06-28")
            .patch(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) println("Notion Update Failed: ${response.code}")
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    /**
     * Searches for a company in Notion using your keyword-split logic.
     * Returns Pair(PageID, OfficialName) if found.
     */
    suspend fun findPageIdForCompany(headline: String): Pair<String?, String?> = withContext(Dispatchers.IO) {
        val keywords = headline.split(",", "|", "@", " at ").map { it.trim() }

        val jsonFilter = """
            {
                "filter": {
                    "or": [
                        ${
            keywords.joinToString(",") { keyword ->
                """{ "property": "Company", "title": { "contains": "$keyword" } }"""
            }
        }
                    ]
                }
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://api.notion.com/v1/databases/${BuildConfig.DATABASE_ID}/query")
            .addHeader("Authorization", "Bearer ${BuildConfig.NOTION_TOKEN}")
            .addHeader("Notion-Version", "2022-06-28")
            .addHeader("Content-Type", "application/json")
            .post(jsonFilter.toRequestBody("application/json".toMediaType()))
            .build()
        try {

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                val jsonResponse = JSONObject(body ?: "{}")
                val results = jsonResponse.optJSONArray("results")

                if (results != null && results.length() > 0) {
                    val firstMatch = results.getJSONObject(0)
                    val pageId = firstMatch.getString("id")

                    // Digging into properties to get the official name (your reference logic)
                    val properties = firstMatch.getJSONObject("properties")
                    val companyProperty = properties.getJSONObject("Company")
                    val titleArray = companyProperty.getJSONArray("title")

                    val actualNotionName = if (titleArray.length() > 0) {
                        titleArray.getJSONObject(0).getJSONObject("text").getString("content")
                    } else {
                        null
                    }

                    return@withContext pageId to actualNotionName
                }

            }

        } catch (e: Exception) {
            e.printStackTrace()

        }
        null to null
    }

    suspend fun createNotionPage(company: String,
                                 jobTitle: String,
                                 appliedDate: String): Boolean = withContext(Dispatchers.IO) {
        val jsonBody = """
    {
        "parent": { "database_id": "${BuildConfig.DATABASE_ID}" },
        "properties": {
            "Company": {
                "title": [{ "text": { "content": "$company" } }]
            },
            "Title": {
                "rich_text": [{ "text": { "content": "$jobTitle" } }]
            },
            "Status": {
                "status": { "name": "Applied" }
            },
            "Date Applied": {
                "date": { "start": "$appliedDate" }
            }
        }
    }
    """.trimIndent()

        val request = Request.Builder()
            .url("https://api.notion.com/v1/pages")
            .addHeader("Authorization", "Bearer ${BuildConfig.NOTION_TOKEN}")
            .addHeader("Notion-Version", "2022-06-28")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        return@withContext try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            e.printStackTrace()
            false

        }
    }
}
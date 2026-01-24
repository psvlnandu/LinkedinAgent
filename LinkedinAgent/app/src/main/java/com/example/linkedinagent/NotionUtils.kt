package com.example.linkedinagent

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class NotionUtils {

    private  val NOTION_TOKEN = BuildConfig.NOTION_TOKEN
    /**
     * Updates the status of a specific page in Notion
     * @param pageId The ID of the page to update
     * @param newStatus The name of the status (e.g., "Accepted", "Interview")
     */

    private suspend fun updateNotionStatus(pageId: String, newStatus: String) {
        val client = OkHttpClient()

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
}
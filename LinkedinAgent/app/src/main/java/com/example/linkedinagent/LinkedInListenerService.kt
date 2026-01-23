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


import com.example.linkedinagent.BuildConfig
// Instead of hardcoding, use the generated BuildConfig
private const val NOTION_TOKEN = BuildConfig.NOTION_TOKEN
private const val DATABASE_ID = BuildConfig.DATABASE_ID


private const val INTEGRATION="LinkedinAgent"
    // https://www.notion.so/2e0d5d2c2bc8804e8249dce812e4d837?v=2e0d5d2c2bc8807881cb000cbe3f5b28

class LinkedInListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getString("android.text") ?: ""
        // We only care about LinkedIn
        if (packageName == "com.linkedin.android") {

            var nameToSearch = when {
                text.contains("accepted your invitation", true) -> {
                    text.split(" accepted")[0]
                }
                title.contains(":") -> {
                    title.split(":").last().trim()
                }
                else -> title.trim()

            }
            if(nameToSearch.isNotEmpty()) {
//                triggerGmailSearch(nameToSearch)
            }



        }
        else if (packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b") {
           // later
            triggerGmailSearch("kelda ")
        }
        /*
        else if (packageName=="com.google.android.gm"){
            val extras = sbn.notification.extras
            println("--- GMAIL NOTIFICATION START ---")
            for (key in extras.keySet()) {
                val value = extras.get(key)
                println("Key: $key | Value: $value")
            }
            println("--- GMAIL NOTIFICATION END ---")

            // 2. Fetch the standard fields
            val title = extras.getString("android.title")       // Subject or Sender
            val text = extras.getCharSequence("android.text")?.toString()    // Snippet or Sender
            val bigText = extras.getCharSequence("android.bigText")?.toString() // Extended content
            val subText = extras.getCharSequence("android.subText")?.toString() // Often the Account Email

            // 3. Search for your keyword in ALL likely fields
            val searchKeyword = "Poorna"
            val matchFound = (title?.contains(searchKeyword, true) == true) ||
                    (text?.contains(searchKeyword, true) == true) ||
                    (bigText?.contains(searchKeyword, true) == true)

            if (matchFound) {
                println("MATCH FOUND! Subject: $title | Snippet: $text")
                scope.launch {
                    withContext(Dispatchers.Main) {
                        AgentState.emailLogs.add(0, "Gmail Alert: $title")
                    }
                }
            }



        }
        */

    }
    private fun triggerGmailSearch(personName: String) {
        // 1. Capture Notification Time immediately
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val notifTime = sdf.format(java.util.Date())

        scope.launch {
            val context = applicationContext

            // 2. Get the saved email address from SharedPreferences
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val accountEmail = prefs.getString("user_email", null)

            if (accountEmail != null) {
                try {
                    // 3. Initialize Gmail Service
                    val gmailService = getGmailService(context, accountEmail)

                    // 4. Fetch the email details (Returns Pair: ResultText to EmailTimestamp)
                    val result = fetchLinkedInAcceptanceEmail(gmailService, personName)
                    val parsedText = result.first
                    val emailTime = result.second

                    println("resultSnippet: $parsedText")

                    if (parsedText != null) {
                        // 5. Update UI with the structured Log object
                        withContext(Dispatchers.Main) {
                            AgentState.emailLogs.add(0, AgentLog(
                                message = parsedText,
                                notificationTime = notifTime,
                                emailTime = emailTime ?: "Unknown"
                            ))
                        }

                        // 6. Extract headline for Notion Search
                        // headline is usually inside the parentheses in parsedText
                        if (parsedText.contains("(")) {
                            val headline = parsedText.substringAfter("(").substringBefore(")")
                            searchDatabaseForCompany(headline)
                        } else {
                            // If no parentheses, try searching with the whole text
                            searchDatabaseForCompany(parsedText)
                        }
                    } else {
                        println("Gmail Search: No matching email found for $personName yet.")
                    }
                } catch (e: Exception) {
                    println("Gmail Search Error: ${e.message}")
                }
            } else {
                println("Gmail Search: No account email found in prefs. User must sign in first.")
            }
        }
    }
    // Add these constants at the top of your class

    private suspend fun searchDatabaseForCompany(headline: String) {
        val client = OkHttpClient()

        // Extract company name (e.g., "Software Engineer at Google" -> "Google")
        val companyName = if (headline.contains(" at ", true)) {
            headline.split(" at ", ignoreCase = true).last().trim()
        } else {
            headline
        }

        // Notion Filter JSON: Search for a property named "Company" (adjust to your column name)
        val keywords = headline.split(",", "|", "@", " at ").map { it.trim() }

        val jsonFilter = """
            {
                "filter": {
                    "or": [
                        ${keywords.joinToString(",") { keyword ->
                        """{ "property": "Company", "title": { "contains": "$keyword" } }"""
                    }}
                    ]
                }
            }
            """.trimIndent()

        val request = Request.Builder()
            .url("https://api.notion.com/v1/databases/$DATABASE_ID/query")
            .addHeader("Authorization", "Bearer $NOTION_TOKEN")
            .addHeader("Notion-Version", "2022-06-28")
            .addHeader("Content-Type", "application/json")
            .post(jsonFilter.toRequestBody("application/json".toMediaType()))
            .build()

        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    val jsonResponse = JSONObject(body ?: "{}")
                    val results = jsonResponse.optJSONArray("results")
                    println("jsonResponse:$jsonResponse")
                    withContext(Dispatchers.Main) {
                        // Capture current time for the log
                        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        val currentTime = sdf.format(java.util.Date())

                        if (results != null && results.length() > 0) {
                            // We create an AgentLog object instead of just a string
                            AgentState.emailLogs.add(0, AgentLog(
                                message = "MATCH: Found $companyName in Job Tracker!",
                                notificationTime = currentTime,
                                emailTime = "DB Sync"
                            ))
                        } else {
                            AgentState.emailLogs.add(0, AgentLog(
                                message = "❌ No record for $companyName in Notion.",
                                notificationTime = currentTime,
                                emailTime = "DB Sync"
                            ))
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    val currentTime = sdf.format(java.util.Date())

                    // Fix: Pass an AgentLog object instead of a String
                    AgentState.emailLogs.add(0, AgentLog(
                        message = "Notion Error: ${e.message}",
                        notificationTime = currentTime,
                        emailTime = "Error"
                    ))
                }
            }
        }
    }
}
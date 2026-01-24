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

// Instead of hardcoding, use the generated BuildConfig
private const val NOTION_TOKEN = BuildConfig.NOTION_TOKEN
private const val DATABASE_ID = BuildConfig.DATABASE_ID


private const val INTEGRATION = "LinkedinAgent"
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
            if (nameToSearch.isNotEmpty()) {
                triggerGmailSearch(nameToSearch)
            }


        } else if (packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b") {
            // later
//            triggerGmailSearch("Varun ")
        }
        else if (packageName == "com.google.android.gm") {



            scope.launch {

                scope.launch {
                    val context = applicationContext
                    val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                    val accountEmail = prefs.getString("user_email", null)

                    if (accountEmail != null) {
                        try {
                            val gmailService = getGmailService(context, accountEmail)

                            // We search for the message based on the subject to get the ID
                            // so the processor can do the full fetch.
                            val query = "$title newer_than:1h"

                            val response = gmailService.users().messages().list("me")
                                .setQ(query)
                                .setMaxResults(1L)
                                .execute()
//                            println("response:$response")
                            val mId = response.messages?.firstOrNull()?.id
                            println("Gmail Trigger: $mId")

                            if (mId != null) {
//                                println("Processing Email...")
                                val processor = EmailProcessor(gmailService)
                                // This now contains your 2-step AI Subject & Body check
                                processor.processMessage(mId)
                            }
                        } catch (e: Exception) {
                            println("Gmail Trigger Error: ${e.message}")
                        }
                    }
                }
            }
        }

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
                    val mId = result.third // Note: We need to update fetch to return the ID too


                    println("resultSnippet: $parsedText")

                    if (parsedText != null) {
                        // 5. Update UI with the structured Log object
                        withContext(Dispatchers.Main) {
                            val alreadyExists = AgentState.emailLogs.any { it.messageId == mId }
                            if (!alreadyExists) {
                                AgentState.emailLogs.add(
                                    0, AgentLog(
                                        message = parsedText,
                                        notificationTime = notifTime,
                                        emailTime = emailTime ?: "Unknown",
                                        isCompleted = false,
                                        messageId = mId ?: ""
                                    )
                                )
                            }
                        }

                        // 6. Extract headline for Notion Search
                        // headline is usually inside the parentheses in parsedText
                        if (parsedText.contains("(")) {
                            val headline = parsedText.substringAfter("(").substringBefore(")")
                            searchDatabaseForCompany(headline, mId)
                        } else {
                            // If no parentheses, try searching with the whole text
                            searchDatabaseForCompany(parsedText, mId)
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

    private suspend fun searchDatabaseForCompany(headline: String, mId: String?) {
        val client = OkHttpClient()


        // Notion Filter JSON: Search for a property named "Company" (adjust to your column name)
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
                        val sdf =
                            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                        val currentTime = sdf.format(java.util.Date())

                        if (results != null && results.length() > 0) {
                            val firstMatch = results.getJSONObject(0)
                            val properties = firstMatch.getJSONObject("properties")
                            val companyProperty = properties.getJSONObject("Company")
                            val titleArray = companyProperty.getJSONArray("title")

                            val actualNotionName = if (titleArray.length() > 0) {
                                titleArray.getJSONObject(0).getJSONObject("text")
                                    .getString("content")
                            } else {
                                //nothing
                            }

                            // We create an AgentLog object instead of just a string
                            AgentState.emailLogs.add(
                                0, AgentLog(
                                    message = "MATCH: Found $actualNotionName in Job Tracker!",
                                    notificationTime = currentTime,
                                    emailTime = "DB Sync",
                                    messageId = mId ?: "",
                                    isCompleted = false,
                                )
                            )
                        } else {
                            AgentState.emailLogs.add(
                                0, AgentLog(
                                    message = "❌ No record for $headline in Notion.",
                                    notificationTime = currentTime,
                                    emailTime = "DB Sync",
                                    messageId = mId ?: "",
                                    isCompleted = false
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    val currentTime = sdf.format(java.util.Date())

                    // Fix: Pass an AgentLog object instead of a String
                    AgentState.emailLogs.add(
                        0, AgentLog(
                            message = "Notion Error: ${e.message}",
                            notificationTime = currentTime,
                            emailTime = "Error",
                            messageId = mId ?: "",
                            isCompleted = false
                        )
                    )
                }
            }
        }
    }
}
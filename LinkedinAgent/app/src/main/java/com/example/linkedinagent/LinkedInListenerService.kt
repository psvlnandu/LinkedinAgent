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
//            if (nameToSearch.isNotEmpty()) {
////                triggerGmailSearch(nameToSearch)
//            }


        } else if (packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b") {
            // later
//            triggerGmailSearch("Varun ")
        } else if (packageName == "com.google.android.gm") {
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
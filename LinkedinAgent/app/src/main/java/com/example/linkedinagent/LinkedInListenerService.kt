package com.example.linkedinagent

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.activity.result.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LinkedInListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getString("android.text") ?: ""
        // We only care about LinkedIn
        if (packageName == "com.linkedin.android") {

            val nameToSearch = when {
                text.contains("accepted your invitation", true) -> {
                    text.split(" accepted")[0]
                }
                title.contains(":") -> {
                    title.split(":").last().trim()
                }
                else -> null
            }

            if (nameToSearch != null) {
                println("nameToSearch:$nameToSearch ")
                triggerGmailSearch(nameToSearch)
            }


        }
        else if (packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b") {
            val extras = sbn.notification.extras
            val sender = extras.getString("android.title") // Name of the person or group
            val message = extras.getCharSequence("android.text")?.toString() // The actual message content

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
        scope.launch {
            val context = applicationContext

            // 1. Get the saved email address from SharedPreferences
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val accountEmail = prefs.getString("user_email", null)

            if (accountEmail != null) {
                try {
                    // 2. Initialize Gmail Service
                    val gmailService = getGmailService(context, accountEmail)

                    // 3. Search specifically for this person in the Socials/Invitations
                    // We pass the personName to the search function
                    val resultSnippet = fetchLinkedInAcceptanceEmail(gmailService, personName)

                    if (resultSnippet != null) {
                        withContext(Dispatchers.Main) {
                            // Update the global state so the LazyColumn refreshes
                            AgentState.emailLogs.add(0, "LinkedIn Match: $resultSnippet")
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
}
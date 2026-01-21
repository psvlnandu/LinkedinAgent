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

        // We only care about LinkedIn
        if (packageName == "com.linkedin.android") {
            val extras = sbn.notification.extras
            val title = extras.getString("android.title") // Usually the Person's Name
            val text = extras.getString("android.text")   // Usually "accepted your invitation"
            var tempLog = "title:$title\ttext:$text"
            println(tempLog)
            if (text?.contains("accepted your invitation", ignoreCase = true) == true) {
                val candidateName = title ?: "Someone"

                // NEXT STEP: Trigger your Gmail API search here
                println("LinkedIn Alert: $candidateName just accepted!")
                scope.launch {
                    val context = applicationContext
                    // Get the saved email from Sign-In
                    val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                    val accountEmail = prefs.getString("user_email", null)

                    if (accountEmail != null) {
                        val gmailService = getGmailService(context, accountEmail)
                        val emailSnippet = fetchLinkedInAcceptanceEmail(gmailService)

                        if (emailSnippet != null) {
                            // Update the UI list globally
                            withContext(Dispatchers.Main) {
                                AgentState.emailLogs.add(0, "Email Found: $emailSnippet")
                            }
                        }
                    }
                }
            }
        }
        else if (packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b") {
            val extras = sbn.notification.extras
            val sender = extras.getString("android.title") // Name of the person or group
            val message = extras.getCharSequence("android.text")?.toString() // The actual message content
            println("notification received; Sender: $sender, message: $message")
        }
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

    }
}
package com.example.linkedinagent
// Corrected import

import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.*

class MyFcmService : FirebaseMessagingService() {

    /*
    Regular job:
        Failure of a child coroutine cancels the parent and all other children (siblings).
        Used by default in coroutineScope
    SupervisorJob:
        Failure of a child coroutine does not cancel the parent or its other children (siblings).
        Independent operations where one failure shouldn't affect others (e.g., loading different UI elements on a screen).
        Used with CoroutineScope(SupervisorJob()) for long-lived scopes or within a supervisorScope { } block for temporary use.
    Dispatchers.IO: Tells the phone: "Do this work on the background thread, not the UI thread," so the screen doesn't freeze.
     */
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)


    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val historyId = remoteMessage.data["historyId"]
        println("FCM Received historyId: $historyId") // Add this to verify the doorbell is ringing

        scope.launch {
            val context = applicationContext
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val accountEmail = prefs.getString("user_email", null)

            if (accountEmail != null && historyId != null) {
                try {
                    val gmailService = getGmailService(context, accountEmail)
                    val processor = EmailProcessor(gmailService) // Create ONCE per sync

                    val historyResponse = gmailService.users().history().list("me")
                        .setStartHistoryId(historyId.toBigInteger()).execute()

                    val messageIds = historyResponse.history?.flatMap { it.messagesAdded ?: emptyList() }
                        ?.mapNotNull { it.message.id }
                        ?.distinct()

                    if (messageIds.isNullOrEmpty()) println("No new messages found in this history sync.")

                    messageIds?.forEach { mId ->
                        processor.processMessage(mId)
                    }

                } catch (e: Exception) {
                    println("Gmail FCM Error: ${e.message}")
                }
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}

// In a separate file or as a companion object
object AgentDependencies {
    val processedIds = mutableSetOf<String>()
    // Share one instance so it remembers what it has seen
}
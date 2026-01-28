package com.example.linkedinagent
// Corrected import

import android.content.Context
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.*

class MyFcmService : FirebaseMessagingService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // The 'historyId' comes from your Cloud Pub/Sub -> FCM backend push
        val historyId = remoteMessage.data["historyId"]

        scope.launch {
            val context = applicationContext
            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            val accountEmail = prefs.getString("user_email", null)

            if (accountEmail != null && historyId != null) {
                try {
                    val gmailService = getGmailService(context, accountEmail)

                    // 1. Get the list of changes since the last historyId
                    val historyResponse = gmailService.users().history()
                        .list("me")
                        .setStartHistoryId(historyId.toBigInteger())
                        .execute()

                    // 2. Extract the new message IDs from the history
                    val messageIds = historyResponse.history
                        ?.flatMap { it.messagesAdded ?: emptyList() }
                        ?.map { it.message.id }
                        ?.distinct()

                    // 3. Process each new message
                    messageIds?.forEach { mId ->
                        println("Gmail FCM Trigger: $mId")
                        val processor = EmailProcessor(gmailService)
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
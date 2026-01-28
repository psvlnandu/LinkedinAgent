package com.example.linkedinagent

import com.google.firebase.messaging.FirebaseMessagingService

class MyFcmService : FirebaseMessagingService() {
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // 1. Extract the historyId if your backend sent it
        val historyId = remoteMessage.data["historyId"]

        // 2. Trigger your existing EmailProcessor
        // Since we are in a background service, use a Coroutine
        CoroutineScope(Dispatchers.IO).launch {
            val gmailService = getGmailService(applicationContext, getSavedEmail())

            // Fetch only the changes since the last historyId
            val historyResponse = gmailService.users().history()
                .list("me")
                .setStartHistoryId(historyId?.toBigInteger())
                .execute()

            historyResponse.history?.forEach { historyItem ->
                historyItem.messagesAdded?.forEach { msgAdded ->
                    val processor = EmailProcessor(gmailService)
                    processor.processMessage(msgAdded.message.id)
                }
            }
        }
    }
}
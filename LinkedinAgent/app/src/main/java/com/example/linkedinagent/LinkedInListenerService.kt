package com.example.linkedinagent

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class LinkedInListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName

        // We only care about LinkedIn
        if (packageName == "com.linkedin.android") {
            val extras = sbn.notification.extras
            val title = extras.getString("android.title") // Usually the Person's Name
            val text = extras.getString("android.text")   // Usually "accepted your invitation"

            if (text?.contains("accepted your invitation", ignoreCase = true) == true) {
                val candidateName = title ?: "Someone"

                // NEXT STEP: Trigger your Gmail API search here
                println("LinkedIn Alert: $candidateName just accepted!")
            }
        }
        
    }
}
package com.example.linkedinagent

import androidx.compose.runtime.mutableStateListOf

// Define the log structure
data class AgentLog(
    val message: String,
    val notificationTime: String,
    val emailTime: String
)

data class LinkedInContact(val name: String, val headline: String)

// Global object to hold your logs
object AgentState {
    // This list will automatically update your LazyColumn when items are added
    val emailLogs = mutableStateListOf<AgentLog>()
}

package com.example.linkedinagent

import androidx.compose.runtime.mutableStateListOf

data class LinkedInContact(val name: String, val headline: String)

// Global object to hold your logs
object AgentState {
    // This list will automatically update your LazyColumn when items are added

    val careerUpdates = mutableStateListOf<CareerUpdate>()
}

enum class EmailCategory { REJECTION, INTERVIEW, APPLIED, OTHER, LINKEDIN_ACCEPTED, PENDING }

data class CareerUpdate(
    val company: String,
    val subject: String,
    val category: EmailCategory,
    val timestamp: String,
    val personName: String? = null // New field for LinkedIn connections

)

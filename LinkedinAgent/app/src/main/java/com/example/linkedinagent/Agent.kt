package com.example.linkedinagent

import androidx.compose.runtime. *
import com.google.firebase.Firebase
import com.google.firebase.database.database
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener


data class LinkedInContact(val name: String, val headline: String)

// Global object to hold your logs
object AgentState {
    // This list will automatically update your LazyColumn when items are added
    var isAutomationEnabled by mutableStateOf(false) // Default to manual
    val careerUpdates = mutableStateListOf<CareerUpdate>()

    private val dbRef = Firebase.database.reference.child("career_updates")
    fun startObserving() {
        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                careerUpdates.clear()
                // Convert Firebase children into our CareerUpdate objects
                for (postSnapshot in snapshot.children) {
                    val update = postSnapshot.getValue(CareerUpdate::class.java)
                    // We use messageId as the key to prevent duplicates
                    val messageId = postSnapshot.key
                    update?.let {
                        // Attach the ID so we know which one to delete later
                        careerUpdates.add(0, it.copy(messageId = messageId ?: ""))
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                println("Firebase Error: ${error.message}")
            }
        })
    }
    // Helper to remove from Firebase (automatically updates UI via the listener)
    fun removeUpdate(messageId: String) {
        dbRef.child(messageId).removeValue()
    }
}

enum class EmailCategory { REJECTION, INTERVIEW, APPLIED, OTHER, LINKEDIN_ACCEPTED, PENDING }

data class CareerUpdate(
    val messageId: String = "",
    val company: String = "",
    val jobTitle: String="",
    val subject: String = "",
    val category: String = "OTHER",
    val isoDate: String = "",
    val timestamp: Long = 0L,
    val personName: String? = null
)
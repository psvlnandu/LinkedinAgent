package com.example.linkedinagent

/**
 * Represents a person who accepted your LinkedIn invitation.
 * @property name The full name of the contact (e.g., "Fahmi Omer")
 * @property headline The professional headline (e.g., "Engineering @ Suno")
 */
data class LinkedInContact(
    val name: String,
    val headline: String
)
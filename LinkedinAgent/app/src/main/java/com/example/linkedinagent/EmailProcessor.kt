package com.example.linkedinagent

import com.google.ai.client.generativeai.GenerativeModel
import com.google.api.services.gmail.Gmail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Ensure this matches your package name exactly
import com.example.linkedinagent.BuildConfig
/**
 * The Engine responsible for the Fetch -> Classify -> State workflow
 */
private val GEMINI_API_KEY = BuildConfig.GEMINI_API_KEY
class EmailProcessor(private val gmailService: Gmail) {
    private val geminiModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey =GEMINI_API_KEY
    )
    suspend fun processMessage(messageId: String) = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch Metadata (Optimization: Only get Subject and From)
            val metadata = gmailService.users().messages().get("me", messageId)
                .setFormat("metadata")
                .setMetadataHeaders(listOf("Subject", "From"))
                .execute()

            val subject = metadata.payload.headers.find { it.name == "Subject" }?.value ?: ""
            val sender = metadata.payload.headers.find { it.name == "From" }?.value ?: ""
            val subjectPrompt = "Read the Subject Line and return only the word 'TRUE' if it sounds like a job application, candidate update, or recruitment email. Otherwise return 'FALSE'. Subject: $subject"
            val isJobRelated = classifyUsingAI(subjectPrompt).contains("TRUE", ignoreCase = true)

            if (isJobRelated) {


                val fullMessage = gmailService.users().messages().get("me", messageId).execute()
                val body = extractHtmlFromBody(fullMessage) ?: fullMessage.snippet ?: ""


                val bodyPrompt = "Read the Email body and return exactly one word: 'REJECTION', 'INTERVIEW', or 'OTHER'. Body: $body"
                val categoryResult = classifyUsingAI(bodyPrompt).uppercase()

                val category = when {
                    categoryResult.contains("REJECTION") -> EmailCategory.REJECTION
                    categoryResult.contains("INTERVIEW") -> EmailCategory.INTERVIEW
                    else -> EmailCategory.OTHER
                }

                val companyPrompt = "Extract only the company name from this text. Subject: $subject Body: ${body.take(200)}"
                val company = classifyUsingAI(companyPrompt).trim()

                // 6. Update State for Compose
                withContext(Dispatchers.Main) {
                    AgentState.careerUpdates.add(0, CareerUpdate(
                        company = company,
                        subject = subject,
                        category = category,
                        timestamp = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                    ))
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    /*
    PROMPT
        SubjectClassify : Read the Subject Line & return true if it sounds like candidate application is moved further
        BodyClassify    : Read the Email body & return the classify accordingly "REJECTION", "INTERVIEW/EXAM", "OTHER"
    */
    private suspend fun classifyUsingAI(prompt: String): String {
        return try {
            geminiModel.generateContent(prompt).text ?: "OTHER"
        } catch (e: Exception) {
            "ERROR"
        }
    }

}
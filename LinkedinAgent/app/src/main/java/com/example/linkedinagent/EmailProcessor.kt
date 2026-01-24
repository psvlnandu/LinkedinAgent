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
    init {
        println("Gemini initialized with key length: ${GEMINI_API_KEY.length}")
    }
    private val geminiModel = GenerativeModel(
        modelName = "gemini-2.5-flash",
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

            val isLinkedInAcceptance = subject.contains("accepted your invitation", ignoreCase = true)
            if (isLinkedInAcceptance) {
                processLinkedInAcceptance(messageId)
            }

            val subjectPrompt = "Read the Subject Line and return only the word 'TRUE' if it sounds like a job application, candidate update, or recruitment email. Otherwise return 'FALSE'. Subject: $subject"
            val isJobRelated = classifyUsingAI(subjectPrompt).contains("TRUE", ignoreCase = true)
            //println("isJobRelated: $isJobRelated")

            if (isJobRelated) {


                val fullMessage = gmailService.users().messages().get("me", messageId).execute()
                val body = extractHtmlFromBody(fullMessage) ?: fullMessage.snippet ?: ""
//                println("body: $body")

                val bodyPrompt =
                    "Read the Email body and return exactly one word: 'REJECTION', 'INTERVIEW', or 'OTHER'. Body: $body"
                val categoryResult = classifyUsingAI(bodyPrompt).uppercase()
                println("categoryResult: $categoryResult")
                val category = when {
                    categoryResult.contains("REJECTION") -> EmailCategory.REJECTION
                    categoryResult.contains("INTERVIEW") -> EmailCategory.INTERVIEW
                    else -> EmailCategory.OTHER
                }

                val companyPrompt =
                    "Extract only the company name from this text. Subject: $subject Body: ${
                        body.take(1000)
                    }"
                val company = classifyUsingAI(companyPrompt).trim()
                println("company: $company")

                // 6. Update State for Compose
                withContext(Dispatchers.Main) {
                    AgentState.careerUpdates.add(
                        0, CareerUpdate(
                            company = company,
                            subject = subject,
                            category = category,
                            timestamp = java.text.SimpleDateFormat(
                                "HH:mm",
                                java.util.Locale.getDefault()
                            ).format(java.util.Date())
                        )
                    )
                }
                val (pageId, officialName) = NotionUtils.findPageIdForCompany(company)
                println("pageId: $pageId, officialName: $officialName")
                if (pageId != null) {
                    //   2. Map the AI result to your Notion Status Tags
                    val notionStatus = when (category) {
                        EmailCategory.REJECTION -> "Rejected"
                        EmailCategory.INTERVIEW -> "Exam Scheduled"
                        else -> null
                    }


                if (notionStatus != null) {
                    // You'll first need to find the Page ID for that company

                    val success = NotionUtils.updateNotionStatus(pageId, notionStatus)
//                    println("success?$success")
                } }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    suspend fun processLinkedInAcceptance(messageId: String) = withContext(Dispatchers.IO) {
        try {
            val query = "from:invitations@linkedin.com category:social \"accepted your invitation\" is:unread newer_than:2d"
            val searchResponse = gmailService.users().messages().list("me")
                .setQ(query)
                .setMaxResults(5L)
                .execute()

            val mId = searchResponse.messages?.firstOrNull()?.id ?: return@withContext

            val fullMessage = gmailService.users().messages().get("me", messageId).execute()
            val bodyHtml = extractHtmlFromBody(fullMessage) ?: ""

            // 2. Use AI to extract Company Name precisely
            // This solves the problem of users not putting the company in their title
            val extractionPrompt = """
            Analyze this LinkedIn acceptance email body. 
            Identify the person who accepted the invite and their current company/organization.
            If the company is not explicitly in their headline, look for context in the 'explore their network' section or signature.
            Return ONLY the Company Name. If not found, return 'UNKNOWN'.
            Body: ${bodyHtml.take(1000)}
        """.trimIndent()

            val companyName = classifyUsingAI(extractionPrompt).trim()

            if (companyName != "UNKNOWN") {
                // 3. Search Notion using your refined search logic
                val (pageId, officialName) = NotionUtils.findPageIdForCompany(companyName)

                if (pageId != null) {
                    // 4. Update the Notion Status to "Linkedin Chat" (the yellow tag from your screenshot)
                    NotionUtils.updateNotionStatus(pageId, "Linkedin Chat")

                    withContext(Dispatchers.Main) {
                        AgentState.emailLogs.add(
                            0, AgentLog(
                                message = "CONNECTED: Found $officialName. Status set to Linkedin Chat.",
                                notificationTime = "",
                                emailTime = "LinkedIn Sync",
                                messageId = messageId,
                                isCompleted = true
                            )
                        )
                    }
                }
            }
        }catch (e: Exception) { e.printStackTrace() }
    }
    /*
    PROMPT
        SubjectClassify : Read the Subject Line & return true if it sounds like candidate application is moved further
        BodyClassify    : Read the Email body & return the classify accordingly "REJECTION", "INTERVIEW/EXAM", "OTHER"
    */
    private suspend fun classifyUsingAI(prompt: String): String {
        return try {
            val aiResponse = geminiModel.generateContent(prompt)
//            println("aiResponse:$aiResponse")
            val result = aiResponse.text ?: "OTHER"
            result
        } catch (e: Exception) {
            e.printStackTrace()
            "ERROR"
        }
    }

}
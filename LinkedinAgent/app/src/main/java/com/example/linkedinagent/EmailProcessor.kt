package com.example.linkedinagent
import com.google.ai.client.generativeai.GenerativeModel
import com.google.api.services.gmail.Gmail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Ensure this matches your package name exactly
import com.example.linkedinagent.BuildConfig
import org.json.JSONObject

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

            val isLinkedInAcceptance =
                subject.contains("accepted your invitation", ignoreCase = true)
            if (isLinkedInAcceptance) {
                processLinkedInAcceptance(messageId)
            } else {



            val subjectPrompt =
                "Read the Subject Line and return only the word 'TRUE' if it sounds like a job application, candidate update, or recruitment email. Otherwise return 'FALSE'. Subject: $subject"
            val isJobRelated = classifyUsingAI(subjectPrompt).contains("TRUE", ignoreCase = true)
            //println("isJobRelated: $isJobRelated")

            if (isJobRelated) {


                val fullMessage = gmailService.users().messages().get("me", messageId).execute()
                val body = extractHtmlFromBody(fullMessage) ?: fullMessage.snippet ?: ""
//                println("body: $body")

                // Get the actual internal date from Gmail and convert to ISO 8601
                val emailMillis = fullMessage.internalDate ?: System.currentTimeMillis()
                val isoDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(emailMillis))

                val extractionPrompt = """
                    Analyze this job application email.
                    Extract the following and return strictly as JSON:
                    1. "category": one of (APPLIED, REJECTION, INTERVIEW, OTHER)
                    2. "company": the name of the company
                    3. "role": the job title or position (e.g., SWE III, Software Engineer)
                    
                    Email Body: ${body.take(1500)}
                """.trimIndent()

                val aiJson = classifyUsingAI(extractionPrompt)
                val parsed = JSONObject(aiJson)

                val category = parsed.getString("category")
                val company = parsed.getString("company")
                val jobTitle = parsed.getString("role")

                if (category == "APPLIED") {
                    val (pageId, _) = NotionUtils.findPageIdForCompany(company)

                    if (pageId == null) {
                        // Company not in DB? Create it!
                        NotionUtils.createNotionPage(company, jobTitle, isoDate)
                    } else {
                        // Already there? Just ensure status is 'Applied'
                        NotionUtils.updateNotionStatus(pageId, "Applied")
                    }
                }

//                val bodyPrompt ="Read this email body and classify it into one word: \n" +
//                        "    'REJECTION', 'INTERVIEW', 'APPLIED', or 'OTHER'. \n" +
//                        "    'APPLIED' is for 'Application Received' or 'Thank you for applying' emails.\n" +
//                        "    Body: \$body\n" +
//                        "\"\"\".trimIndent()"
//                val categoryResult = classifyUsingAI(bodyPrompt).uppercase()
//                println("categoryResult: $categoryResult")
//                val category = when {
//                    categoryResult.contains("REJECTION") -> EmailCategory.REJECTION
//                    categoryResult.contains("INTERVIEW") -> EmailCategory.INTERVIEW
//                    categoryResult.contains("APPLIED") -> EmailCategory.APPLIED
//                    else -> EmailCategory.OTHER
//                }

//                val companyPrompt =
//                    "Extract only the company name from this text. Subject: $subject Body: ${
//                        body.take(1000)
//                    }"
//                val company = classifyUsingAI(companyPrompt).trim()
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
            }
        }

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    suspend fun processLinkedInAcceptance(messageId: String) = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch the actual email content directly using the messageId
            val fullMessage = gmailService.users().messages().get("me", messageId).execute()
            val bodyHtml = extractHtmlFromBody(fullMessage) ?: fullMessage.snippet ?: ""

            // 2. ONE Single, Powerful AI Prompt
            // Combining Name and Company extraction into one call saves tokens and time
            val extractionPrompt = """
            Analyze this LinkedIn acceptance email. 
            Identify the person who accepted the invite and their current company.
            Look at the headline, footer, or 'explore their network' sections.
            Return ONLY in this format: Name | Company
            If company is not found, return Name | UNKNOWN.
            Body: $bodyHtml
        """.trimIndent()

            val aiResult = classifyUsingAI(extractionPrompt) // Result: "Varun | Groq"
            val parts = aiResult.split("|")

            if (parts.size == 2) {
                val personName = parts[0].trim()
                val companyName = parts[1].trim()

                if (companyName != "UNKNOWN") {
                    // 3. Search and Sync with Notion (Your refined logic)
                    val (pageId, officialName) = NotionUtils.findPageIdForCompany(companyName)

                    if (pageId != null) {
                        NotionUtils.updateNotionStatus(pageId, "Linkedin Chat")

                        withContext(Dispatchers.Main) {
                            AgentState.emailLogs.add(0, AgentLog(
                                message = "CONNECTED: $personName @ ${officialName ?: companyName}",
                                notificationTime = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
                                emailTime = "LinkedIn Sync",
                                messageId = messageId,
                                isCompleted = true
                            ))
                        }
                    }
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
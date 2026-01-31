    package com.example.linkedinagent

    import com.google.ai.client.generativeai.GenerativeModel
    import com.google.api.services.gmail.Gmail
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.withContext

    // Ensure this matches your package name exactly
    import com.example.linkedinagent.Utils.extractHtmlFromBody
    import com.google.firebase.Firebase
    import com.google.firebase.database.database
    import java.util.Date
    import java.util.Locale

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
            apiKey = GEMINI_API_KEY
        )

        suspend fun processMessage(messageId: String) = withContext(Dispatchers.IO) {

            // USE THE GLOBAL ONE INSTEAD
            if (AgentDependencies.processedIds.contains(messageId)) {
                println("Skipping already processed message: $messageId")
                return@withContext
            }
            AgentDependencies.processedIds.add(messageId)

            // Keep the memory lean
            if (AgentDependencies.processedIds.size > 100) {
                AgentDependencies.processedIds.remove(AgentDependencies.processedIds.first())
            }

            try {
                // 1. Fetch Metadata (Optimization: Only get Subject and From)
                val metadata = gmailService.users().messages().get("me", messageId)
                    .setFormat("metadata")
                    .setMetadataHeaders(listOf("Subject", "From"))
                    .execute()

                val internalDate = metadata.internalDate ?: 0L
                val oneHourAgo = System.currentTimeMillis() - 3600000
                if (internalDate < oneHourAgo) {
                    println("Skipping old email from history sync: $messageId")
                    return@withContext
                }

                val subject = metadata.payload.headers.find { it.name == "Subject" }?.value ?: ""
                val sender = metadata.payload.headers.find { it.name == "From" }?.value ?: ""

                val isLinkedInAcceptance =
                    subject.contains("accepted your invitation", ignoreCase = true)
                println("Subject: $subject, Sender: $sender, isLinkedInAcceptance: $isLinkedInAcceptance")

                if (isLinkedInAcceptance) {
                    processLinkedInAcceptance(sender, messageId)
                } else {
                    // for the other category- APPLIED, REJECTED , INTERVIEW


                    val subjectPrompt =
                        "Read the Subject Line and return only the word 'TRUE' if it sounds like a job application, candidate update, or recruitment email. Otherwise return 'FALSE'. Subject: $subject"
                    val isJobRelated =
                        classifyUsingAI(subjectPrompt).contains("TRUE", ignoreCase = true)
                    //println("isJobRelated: $isJobRelated")

                    if (isJobRelated) {

                        val fullMessage = gmailService.users().messages().get("me", messageId).execute()
                        val body = extractHtmlFromBody(fullMessage) ?: fullMessage.snippet ?: ""

                        // Get the actual internal date from Gmail and convert to ISO 8601
                        val emailMillis = fullMessage.internalDate ?: System.currentTimeMillis()
                        val isoDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                            .format(java.util.Date(emailMillis))

                        val extractionPrompt = """
                                Read this email body and classify it into one word: 
                                'REJECTION', 'INTERVIEW', 'APPLIED', or 'OTHER'. 
                                 Body: ${body.take(7000)}
                            """.trimIndent()
                        val categoryResult = classifyUsingAI(extractionPrompt).uppercase()
                        println("categoryResult: $categoryResult")

                        val category = when {
                            categoryResult.contains("REJECTION") -> EmailCategory.REJECTION
                            categoryResult.contains("INTERVIEW") -> EmailCategory.INTERVIEW
                            categoryResult.contains("APPLIED") -> EmailCategory.APPLIED
                            else -> EmailCategory.OTHER
                        }
                        val companyPrompt = """
                        Target: Extract the EMPLOYER company name.
                        Context: This email is categorized as $category.
                        Subject: $subject
                        
                        Rules:
                        1. Look for the company name that is offering the job or sending the status update.
                        2. Return ONLY the name of the company. 
                        3. If no company is found, return 'UNKNOWN'.
                        Body Snippet: ${body.take(5000)}
                    """.trimIndent()
                        val extractedCompany = classifyUsingAI(companyPrompt).trim()

                        val jobTitlePrompt ="""
                            Target: Extract the JOB TITLE.
                            Context: This email is categorized as $category.
                            Subject: $subject
                            
                            Rules:
                            1. Look for Job or Internship Title
                            2. Return ONLY the title of the position
                            3. If no title found, return 'UNKNOWN'.
                            Body Snippet: ${body.take(5000)}

                        """.trimIndent()
                        val extractedJobTitle = classifyUsingAI(jobTitlePrompt).trim()

                        val updateData = CareerUpdate(
                            messageId = messageId,
                            company = extractedCompany,
                            jobTitle=extractedJobTitle,
                            subject = subject,
                            category = category.name,
                            isoDate = isoDate,
                            timestamp = System.currentTimeMillis()
                        )
                        Firebase.database.reference.child("career_updates").child(messageId).setValue(updateData)


                        val (pageId, officialName) = NotionUtils.findPageIdForCompany(extractedCompany)
                        println("Company: $extractedCompany, PageID: $pageId, OfficialName: $officialName")
                        if (AgentState.isAutomationEnabled) {
                            when (category) {
                                EmailCategory.APPLIED -> {
                                    if (pageId == null) {
                                        val success = NotionUtils.createNotionPage(
                                            extractedCompany,
                                            jobTitle = extractedJobTitle,
                                            status = category,
                                            isoDate
                                        )
                                        println("Notion: ${if (success) "Created" else "Failed to Create"} page for $extractedCompany")

                                    } else {
                                        // when pageId is not null
                                        // May be I have the company details in database which I have in row to apply later-"to apply"
                                        /*
                                       check if current status is to Apply & only update then
                                        */
                                        val success = NotionUtils.updateNotionStatus(pageId, "Applied")
                                        println("Notion: Updated $extractedCompany to 'Applied'\n$success")

                                    }
                                }

                                EmailCategory.INTERVIEW, EmailCategory.REJECTION -> {
                                    if (pageId != null) {
                                        /*
                                        check if current status is Applied  then only update
                                         */
                                        val targetStatus = if (category == EmailCategory.INTERVIEW) "Exam Scheduled" else "Rejected"
                                        val success =NotionUtils.updateNotionStatus(pageId, targetStatus)
                                        println("Notion: Updated $extractedCompany to $targetStatus\n$success")
                                    } else {
                                        println("Notion: No page found for $extractedCompany")
                                        NotionUtils.createNotionPage(
                                            extractedCompany,
                                            extractedJobTitle,
                                            category,
                                            isoDate
                                        )
                                    }
                                }

                                else -> { /* Do nothing for OTHER */
                                }
                            }
                        }
                        else {
                            // JUST LOG IT FOR MANUAL REVIEW
                            println("Automation Disabled: Added $extractedCompany to app list for manual review.")
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        suspend fun processLinkedInAcceptance(sender: String, messageId: String) =
            withContext(Dispatchers.IO) {
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
                    println("aiResult: $aiResult, parts: $parts")
                    if (parts.size == 2) {
                        val personName = parts[0].trim()
                        val companyName = parts[1].trim()

                        if (companyName != "UNKNOWN") {
                            // 3. Search and Sync with Notion (Your refined logic)
                            val (pageId, officialName) = NotionUtils.findPageIdForCompany(companyName)

                            if (pageId != null) {
                                NotionUtils.updateNotionStatus(pageId, "Linkedin Chat")

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
                "$e for $prompt"
            }
        }

    }
package com.example.linkedinagent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material3.Button
import androidx.compose.material3.Card

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.example.linkedinagent.ui.theme.LinkedinAgentTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.gmail.Gmail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.content.edit

class MainActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LinkedinAgentTheme {
                PermissionScreen()
            }
        }
    }


}



@Composable
fun PermissionScreen(context: Context = LocalContext.current) {
    // state to track
    var signedInAccount by remember {
        mutableStateOf<com.google.android.gms.auth.api.signin.GoogleSignInAccount?>(
            null
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            signedInAccount = task.getResult(ApiException::class.java)

            // SUCCESS: You have the account here
            println("Signed in as: ${signedInAccount?.email}")

            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            prefs.edit { putString("user_email", signedInAccount?.email) }


        } catch (e: ApiException) {
            println("Signin failed ; $e")
        }
    }
    // 2. AUTO-SIGNIN LOGIC: Runs once when the screen opens
    androidx.compose.runtime.LaunchedEffect(Unit) {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (lastAccount != null) {
            // User was previously signed in!
            signedInAccount = lastAccount
            println("Auto-signed in as: ${lastAccount.email}")

            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)

            // If the email is missing from storage (e.g. first time after login), save it
            if (prefs.getString("user_email", null) == null && lastAccount.email != null) {
                prefs.edit {
                    putString("user_email", lastAccount.email)
                }
                println("Email saved to prefs during auto-signin")
            }
        }
    }

    fun launchGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            // Scope for Gmail readonly access
            .requestScopes(Scope("https://www.googleapis.com/auth/gmail.readonly"))
            .build()

        val client = GoogleSignIn.getClient(context, gso)
        launcher.launch(client.signInIntent)
    }

    // 1. Logic to check if permission is already granted
    fun isPermissionGranted(): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledPackages.contains(context.packageName)
    }

    var hasAccess by remember { mutableStateOf(isPermissionGranted()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {

        if (signedInAccount == null) {
            Button(onClick = { launchGoogleSignIn() }) {
                Text("Sign in with Google")
            }
        } else {
            Text("Signed in as: ${signedInAccount?.email}")
            // Optional: Add a Sign Out button
            Button(onClick = {
                GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
                signedInAccount = null
            }) {
                Text("Sign Out")
            }
        }
        Text("Processed LinkedIn Emails:", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        // This list updates automatically when the Service finds an email!
        LazyColumn(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()) {
            items(AgentState.emailLogs) { log ->
                Card(Modifier
                    .padding(4.dp)
                    .fillMaxWidth()) {
                    Column(Modifier.padding(8.dp)) {
                        Text(text = log.message, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "🔔 Notif: ${log.notificationTime}", fontSize = 10.sp, color = Color.Gray)
                            Text(text = "📧 Email: ${log.emailTime}", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }


        Text(text = if (hasAccess) "Agent is Active" else "Access Required")
        Spacer(modifier = Modifier.height(10.dp))
        Button(onClick = {
            if (!hasAccess) {
                // 2. Open the system settings page
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                context.startActivity(intent)
            }
        }) {
            Text(if (hasAccess) "Permission Granted" else "Grant Notification Access")
        }
    }
}

/*
Gmail Service Helper:
added a getGmailService helper function at the bottom. Once the user signs in successfully,
you can call this to start searching for those LinkedIn emails.
 */
suspend fun getGmailService(context: Context, accountEmail: String): Gmail =
    withContext(Dispatchers.IO) {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf("https://www.googleapis.com/auth/gmail.readonly")
        ).setSelectedAccountName(accountEmail)

        Gmail.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("LinkedinAgent").build()
    }

suspend fun fetchLinkedInAcceptanceEmail(service: Gmail, personName:String): Pair<String?, String?> = withContext(Dispatchers.IO) {
    try {
        // Updated Query:
        // 1. from:invitations@linkedin.com -> precise sender
        // 2. "accepted your invitation" -> the key phrase
        // 3. category:social -> targets the correct Gmail tab
        // 4. $personName -> looks for the name specifically

        val query = "from:invitations@linkedin.com category:social \"$personName\" \"accepted your invitation\" newer_than:1d"

        val response = service.users().messages().list("me")
            .setQ(query)
            .setMaxResults(1L)
            .execute()

        val messageId = response.messages?.firstOrNull()?.id ?: return@withContext null to null

        // Fetch the full message content
        val fullMessage = service.users().messages().get("me", messageId).execute()

        // SIMPLE: Get the time from the message metadata
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val emailTime = sdf.format(java.util.Date(fullMessage.internalDate ?: 0L))


        val htmlBody = extractHtmlFromBody(fullMessage) ?: return@withContext null to emailTime
        val contact = parseLinkedInFinal(htmlBody)

        val resultText = if (contact != null) {
            "${contact.name} (${contact.headline})"
        } else {
            fullMessage.snippet
        }

        return@withContext resultText to emailTime
    } catch (e: Exception) {
        e.printStackTrace()
        null to null
    }
}
/**
 * Helper to dig through Gmail's multi-part message structure to find the HTML string
 */

private fun extractHtmlFromBody(message: com.google.api.services.gmail.model.Message): String? {
    val payload = message.payload

    // Recursive function to find text/html part in multipart emails
    fun findHtml(part: com.google.api.services.gmail.model.MessagePart?): String? {
        if (part?.mimeType == "text/html") {
            return part.body?.data?.let {
                String(android.util.Base64.decode(it, android.util.Base64.URL_SAFE))
            }
        }
        part?.parts?.forEach { subPart ->
            val result = findHtml(subPart)
            if (result != null) return result
        }
        return null
    }

    return findHtml(payload) ?: payload.body?.data?.let {
        String(android.util.Base64.decode(it, android.util.Base64.URL_SAFE))
    }
}
fun parseLinkedInFinal(htmlBody: String): LinkedInContact? {
    try {
        // 1. Extract Name from the "Preheader" data attribute or the bold text

        val namePattern = """See\s+([^'’\s]+)\s*[’']s""".toRegex()
        val name = namePattern.find(htmlBody)?.groups?.get(1)?.value?.trim() ?: "Unknown"

        // 2. Extract Headline: LinkedIn uses a very specific style for the headline below the photo
        // Looking for the text following the name in the body table
        // We look for text inside <td> tags that follow the name pattern
        val headlinePattern = """font-size:\s*14px;[^>]*>\s*([^<]+)\s*</td>""".toRegex()
        val headlineMatch = headlinePattern.findAll(htmlBody).map { it.groups[1]?.value?.trim() }.firstOrNull {
            !it.isNullOrBlank() && !it.contains("accepted your invitation", true)
        }

        // Clean up HTML entities
        val cleanHeadline = (headlineMatch ?: "Connection")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .trim()

        return LinkedInContact(name, cleanHeadline)
    } catch (e: Exception) {
        return null
    }
}
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    LinkedinAgentTheme {
        Greeting("Nandu")
    }
}
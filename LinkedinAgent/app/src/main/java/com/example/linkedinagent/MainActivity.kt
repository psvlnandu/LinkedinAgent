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
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.linkedinagent.Utils.startGmailWatch
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AgentState.startObserving()
        setContent {
            LinkedinAgentTheme {
                PermissionScreen()
            }
        }
    }


}


@Composable
fun PermissionScreen(context: Context = LocalContext.current) {
    val scope = rememberCoroutineScope()

    // state to track
    var signedInAccount by remember {
        mutableStateOf<com.google.android.gms.auth.api.signin.GoogleSignInAccount?>(
            null
        )
    }
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
        if (!task.isSuccessful) return@addOnCompleteListener
        val token = task.result
        println("My FCM Token: $token") // Copy this for the next step!
    }
//    Helper to trigger the watch
    fun triggerWatch(account: GoogleSignInAccount) {
        scope.launch {
            val email = account.email ?: return@launch
            try {
                val service = getGmailService(context, email)
                // Use your specific project/topic path
                startGmailWatch(service, "projects/linkedinagent-485019/topics/gmail_notification")
                println("Gmail Watch successfully started for $email")
            } catch (e: Exception) {
                println("Error starting watch: ${e.message}")
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            signedInAccount = account
            // Save to Prefs
            context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit {
                putString("user_email", account.email)
            }
            // TRIGGER WATCH HERE (Manual Sign-in)
            triggerWatch(account)
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
//            val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
//            // If the email is missing from storage (e.g. first time after login), save it
//            if (prefs.getString("user_email", null) == null && lastAccount.email != null) {
//                prefs.edit {
//                    putString("user_email", lastAccount.email)
//                }
//                println("Email saved to prefs during auto-signin")
//            }
            triggerWatch(lastAccount)
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

        // Sign-In Section
        if (signedInAccount == null) {
            Button(onClick = {
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestEmail()
                    .requestScopes(Scope("https://www.googleapis.com/auth/gmail.readonly"))
                    .build()
                launcher.launch(GoogleSignIn.getClient(context, gso).signInIntent)
            }) { Text("Sign in with Google") }
        } else {
            Text("Logged in: ${signedInAccount?.email}", fontSize = 14.sp)
            Button(onClick = {
                GoogleSignIn.getClient(context, GoogleSignInOptions.DEFAULT_SIGN_IN).signOut()
                signedInAccount = null
            }) { Text("Sign Out") }
        }

        Spacer(modifier = Modifier.height(8.dp))
        // Live Feed Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Agent Live Feed", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Auto-Sync", fontSize = 12.sp)
                Switch(
                    checked = AgentState.isAutomationEnabled,
                    onCheckedChange = { AgentState.isAutomationEnabled = it }
                )
            }
        }


        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val groupedUpdates = AgentState.careerUpdates.groupBy { it.category }

            item { ExpandableCategorySection("Applied", groupedUpdates["APPLIED"] ?: emptyList(), Color.Gray) }
            item { ExpandableCategorySection("Interview/Exam", groupedUpdates["INTERVIEW"] ?: emptyList(), Color(0xFF2196F3)) }
            item { ExpandableCategorySection("Rejections", groupedUpdates["REJECTION"] ?: emptyList(), Color(0xFFF44336)) }
            item { ExpandableCategorySection("LinkedIn", groupedUpdates["LINKEDIN_ACCEPTED"] ?: emptyList(), Color(0xFFFFC107)) }
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

@Composable
fun ExpandableCategorySection(title: String, updates: List<CareerUpdate>, color: Color) {
    var isExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
            Spacer(Modifier.width(12.dp))
            Text(text = title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text(text = "[${updates.size}]", fontSize = 12.sp, color = Color.Gray)
            Icon(imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
        }

        if (isExpanded) {
            updates.forEach { update ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "• ${update.company}", fontSize = 13.sp, modifier = Modifier.weight(1f))

                    // TRASH: Removes from Firebase immediately
                    IconButton(onClick = { AgentState.removeUpdate(update.messageId) }) {
                        Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                    }

                    // SYNC: Manual push to Notion
                    // In MainActivity / ExpandableCategorySection
                    IconButton(onClick = {
                        scope.launch(Dispatchers.IO) {
                            // 1. Check if the company already exists in Notion
                            val (pageId, _) = NotionUtils.findPageIdForCompany(update.company)

                            val success = when (update.category) {
                                "APPLIED" -> {
                                    if (pageId == null) {
                                        // Create new page if it's a new application
                                        NotionUtils.createNotionPage(
                                            update.company,
                                            "Applied",
                                            status = EmailCategory.APPLIED,
                                            update.isoDate
                                        )
                                    } else {
                                        // Update existing page to 'Applied'
                                        NotionUtils.updateNotionStatus(pageId, "Applied")
                                    }
                                }
                                "INTERVIEW", "REJECTION" -> {
                                    if (pageId != null) {
                                        val targetStatus = if (update.category == "INTERVIEW") "Exam Scheduled" else "Rejected"
                                        NotionUtils.updateNotionStatus(pageId, targetStatus)
                                    } else {
                                        println("Manual Sync: No Notion page found for ${update.company}")
                                        false // Fail because we can't update a non-existent page
                                    }
                                }
                                else -> false
                            }

                            // 2. If successful, remove from Firebase/App List
                            if (success) {
                                withContext(Dispatchers.Main) {
                                    AgentState.removeUpdate(update.messageId)
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.ArrowForward, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.4f))
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
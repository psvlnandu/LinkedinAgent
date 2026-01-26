package com.example.linkedinagent

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.provider.SyncStateContract.Helpers.update
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp

import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

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
import androidx.compose.ui.text.style.TextDecoration
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
import com.example.linkedinagent.Utils.extractHtmlFromBody
import com.example.linkedinagent.Utils.parseLinkedInFinal

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
                .fillMaxWidth()
        ) {
            val groupedUpdates = AgentState.careerUpdates.groupBy { it.category }

            // 1. APPLIED SECTION
            item {
                ExpandableCategorySection(
                    title = "Applied",
                    updates = groupedUpdates[EmailCategory.APPLIED] ?: emptyList(),
                    color = Color.Gray
                )
            }
            // 2. INTERVIEW SECTION
            item {
                ExpandableCategorySection(
                    title = "Interview/Exam",
                    updates = groupedUpdates[EmailCategory.INTERVIEW] ?: emptyList(),
                    color = Color(0xFF2196F3) // Blue
                )
            }
            // 3. REJECTION SECTION
            item {
                ExpandableCategorySection(
                    title = "Rejections",
                    updates = groupedUpdates[EmailCategory.REJECTION] ?: emptyList(),
                    color = Color(0xFFF44336) // Red
                )
            }
            // 4. LINKEDIN SECTION
            item {

                ExpandableCategorySection(
                    title = "LinkedIn Accepted",
                    updates = groupedUpdates[EmailCategory.LINKEDIN_ACCEPTED]?: emptyList(),
                    color = Color(0xFFFFC107) // Yellow
                )
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

@Composable
fun ExpandableCategorySection(
    title: String,
    updates: List<CareerUpdate>,
    color: Color
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
            Spacer(Modifier.width(12.dp))
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold
            )
            Text(text = "[${updates.size}]", fontSize = 12.sp, color = Color.Gray)
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.Gray
            )
        }

        if (isExpanded) {
            updates.forEach { update ->
                val displayText = when (update.category) {
                    EmailCategory.APPLIED -> "${update.company} Applied"
                    EmailCategory.REJECTION -> "${update.company} Rejected"
                    EmailCategory.INTERVIEW -> "${update.company} Scheduled"
                    EmailCategory.LINKEDIN_ACCEPTED-> update.personName?.let { "$it from ${update.company} accepted" } ?: update.subject
                    else -> ""
                }

                Text(
                    text = "• $displayText",
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))
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
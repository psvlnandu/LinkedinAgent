# Linkedin Career Agent

**What is this?**

The LinkedIn Career Agent is an AI-powered Android background service designed to eliminate the manual "grunt work" of tracking job applications. It acts as a bridge between your messy Gmail inbox and your organized Notion workspace.

**Why I built this?**

Managing hundreds of job applications is a full-time job in itself. This project was born out of the need to stay on top of recruitment cycles without constantly checking emails. It ensures you never miss an interview invite and automatically keeps your job tracker updated while you sleep.
And Since I follow **Apply-> Connect-> Follow-up** pipeline, its important for me to not miss that "Appected your invite" notification from linkedin Connections. 

**For Who?**
- Active Job Seekers who want a "zero-effort" way to maintain their application database.
- Students & Grads applying to high volumes of roles across multiple platforms.

## Flow

- Gmail receives an email $\rightarrow$ Pub/Sub catches the event.
- Firebase sends a silent ping to your phone
- The Agent fetches the email,  Gemini to classify it.
- Data is saved to Firebase and optionally synced to Notion.

## Features
- Real-time Gmail Monitoring: Uses Google Cloud Pub/Sub to "wake up" the app the second a recruitment email arrives.
- Gemini AI Classification: Analyzes full email bodies to distinguish between Applied, Interview/Exam Scheduled, and Rejection status.
- LinkedIn Sync: Detects when your connection (requested one) accepts your invitation and marks the company as "Ready to Chat" in Notion.
- Manual/Auto Modes: A built-in "Inbox" feed allows you to review AI classifications before syncing them to your database.
- Persistent Live Feed: Powered by Firebase to ensure you never lose track of a notification, even if the app is closed.
  
## Tech Stack & Services
I used a "Cloud-Native" approach to keep the app lightweight and responsive:
- Android (Kotlin/Compose): Modern UI and background services.
- Gemini 2.5 Flash: The "Brain" that reads and understands your emails.
- Gmail API: Securely fetches email metadata and bodies via OAuth 2.0.
- Firebase (FCM & Realtime DB): Handles instant push notifications and live app state.
- Google Cloud Pub/Sub: The infrastructure that connects Gmail to the Android device.
- Notion API: The final destination for all career data and application tracking.

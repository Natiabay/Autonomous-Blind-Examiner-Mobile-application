@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ablindexaminer.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import java.util.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun ContactUsScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val textToSpeech = remember { setupTextToSpeech(context) }
    
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    
    // Announce when screen loads
    LaunchedEffect(Unit) {
        speak(textToSpeech, "Contact Us page. Here you can send a message to the development team or find contact information.")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Contact Us") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Contact information card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Contact Information",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Phone
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.parse("tel:+251972805097")
                                    }
                                    context.startActivity(intent)
                                }
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                        Column {
                            Text(
                                text = "Phone",
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "📞 +251 972 805 097",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .clickable { 
                                        val intent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:+251972805097")
                                        }
                                        context.startActivity(intent)
                                    }
                                    .semantics {
                                        contentDescription = "Call +251 972 805 097"
                                    }
                            )
                        }
                    }
                    
                    // Email
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:support@blindexaminer.com")
                                    }
                                    context.startActivity(intent)
                                }
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                        Column {
                            Text(
                                text = "Email",
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "✉️ support@blindexaminer.com",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(vertical = 8.dp)
                                    .clickable { 
                                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                                            data = Uri.parse("mailto:support@blindexaminer.com")
                                        }
                                        context.startActivity(intent)
                                    }
                                    .semantics {
                                        contentDescription = "Send email to support at blind examiner dot com"
                                    }
                            )
                        }
                    }
                    
                    // Address
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                        Column {
                            Text(
                                text = "Address",
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Addis Ababa Institute of Technology, Addis Ababa University",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            
            // Contact form
            Text(
                text = "Send Us a Message",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 16.dp)
                    .semantics { 
                        contentDescription = "Send Us a Message form section" 
                    }
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your Name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .semantics { 
                        contentDescription = "Name input field. Enter your name." 
                    }
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Your Email") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .semantics { 
                        contentDescription = "Email input field. Enter your email address." 
                    }
            )

            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("Subject") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .semantics { 
                        contentDescription = "Subject input field. Enter the subject of your message." 
                    }
            )

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Message") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(bottom = 16.dp)
                    .semantics { 
                        contentDescription = "Message input field. Enter your message text." 
                    },
                maxLines = 5
            )

            Button(
                onClick = {
                    speak(textToSpeech, "Message sent. Thank you for your feedback.")
                    showSuccessDialog = true
                    // In a production app, implement actual message sending logic
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send Message")
            }
            
            // Accessibility button
            OutlinedButton(
                onClick = {
                    speak(textToSpeech, "This is the contact page. You can call us at +251 972 805 097, email us at support@blindexaminer.com, or send a message using the form on this page.")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("Read Contact Information Aloud")
            }
        }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = { Text("Message Sent") },
            text = { Text("Thank you for your message. We'll get back to you soon!") },
            confirmButton = {
                TextButton(
                    onClick = { 
                        showSuccessDialog = false 
                        name = ""
                        email = ""
                        subject = ""
                        message = ""
                    }
                ) {
                    Text("OK")
                }
            }
        )
    }
}

private fun setupTextToSpeech(context: Context): TextToSpeech {
    var textToSpeech: TextToSpeech? = null
    textToSpeech = TextToSpeech(context) { status ->
        if (status == TextToSpeech.SUCCESS) {
            val result = TextToSpeech.LANG_AVAILABLE
            // Set speaking rate slightly slower for better comprehension
            textToSpeech?.setSpeechRate(0.9f)
        }
    }
    return textToSpeech
}

private fun speak(textToSpeech: TextToSpeech, text: String) {
    val utteranceId = UUID.randomUUID().toString()
    textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
} 
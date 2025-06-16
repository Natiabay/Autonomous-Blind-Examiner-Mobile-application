package com.example.ablindexaminer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBarWithDrawer(
    title: String,
    navController: NavController,
    username: String,
    userRole: String,
    onLogout: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Drawer header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "A. Blind Examiner",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Welcome, $username",
                        fontSize = 16.sp
                    )
                    
                    Text(
                        text = "Role: $userRole",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Divider()
                
                // Navigation Items
                NavigationDrawerItem(
                    label = { Text("Dashboard") },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    selected = false,
                    onClick = {
                        // Return to the dashboard based on user role
                        try {
                            val encodedUsername = java.net.URLEncoder.encode(username, "UTF-8")
                            when (userRole.lowercase()) {
                                "student" -> navController.popBackStack("student_dashboard/${encodedUsername}", false)
                                "teacher" -> navController.popBackStack("teacher_dashboard/${encodedUsername}", false)
                                "admin" -> navController.popBackStack("admin_dashboard/${encodedUsername}", false)
                            }
                        } catch (e: Exception) {
                            // Fallback to non-encoded if encoding fails
                            when (userRole.lowercase()) {
                                "student" -> navController.popBackStack("student_dashboard/${username}", false)
                                "teacher" -> navController.popBackStack("teacher_dashboard/${username}", false)
                                "admin" -> navController.popBackStack("admin_dashboard/${username}", false)
                            }
                        }
                        scope.launch { drawerState.close() }
                    }
                )
                
                NavigationDrawerItem(
                    label = { Text("Profile") },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    selected = false,
                    onClick = {
                        try {
                            val encodedUsername = java.net.URLEncoder.encode(username, "UTF-8")
                            navController.navigate("profile/${encodedUsername}/${userRole.lowercase()}")
                        } catch (e: Exception) {
                            navController.navigate("profile/${username}/${userRole.lowercase()}")
                        }
                        scope.launch { drawerState.close() }
                    }
                )
                
                NavigationDrawerItem(
                    label = { Text("Contact Us") },
                    icon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    selected = false,
                    onClick = {
                        navController.navigate("contact_us")
                        scope.launch { drawerState.close() }
                    }
                )
                
                Divider()
                
                // Logout button
                NavigationDrawerItem(
                    label = { Text("Logout") },
                    icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onLogout()
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    drawerState.open()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Menu"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    actions = actions
                )
            }
        ) { paddingValues ->
            content(paddingValues)
        }
    }
} 
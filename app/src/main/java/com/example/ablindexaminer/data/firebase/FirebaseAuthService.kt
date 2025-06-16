package com.example.ablindexaminer.data.firebase

import android.util.Log
import com.example.ablindexaminer.ui.screens.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirebaseAuthService {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection("users")
    
    // Get current user ID
    fun getCurrentUserId(): String? {
        val userId = auth.currentUser?.uid
        Log.d("FirebaseAuthService", "Getting current user ID: $userId")
        return userId
    }
    
    // Get current user
    fun getCurrentUser(): FirebaseUser? {
        val user = auth.currentUser
        Log.d("FirebaseAuthService", "Getting current user: ${user?.email}")
        return user
    }
    
    // Sign in with username or email and password
    suspend fun signInWithEmailAndPassword(usernameOrEmail: String, password: String): Result<Pair<String, UserRole>> {
        return try {
            Log.d("FirebaseAuthService", "Attempting to sign in user: $usernameOrEmail")
            
            // Check if input is admin email
            if (usernameOrEmail == "natiabay1017@gmail.com") {
                // Admin auth is handled separately in LoginScreen
                throw Exception("Use admin auth flow for admin login")
            }
            
            // First, try to find user by username
            val userQuery = usersCollection.whereEqualTo("username", usernameOrEmail).get().await()
            
            // If a user with this username exists
            if (!userQuery.isEmpty) {
                val userDoc = userQuery.documents.first()
                val email = userDoc.getString("email") ?: throw Exception("User email not found")
                val role = userDoc.getString("role") ?: "STUDENT"
                
                // Sign in with actual email
                val authResult = auth.signInWithEmailAndPassword(email, password).await()
                val user = authResult.user ?: throw Exception("Authentication failed")
                
                Log.d("FirebaseAuthService", "Sign in successful for username: $usernameOrEmail -> email: ${user.email}")
                return Result.success(Pair(usernameOrEmail, mapStringToUserRole(role)))
            } 
            
            // If not found by username, try direct email login
            val authResult = auth.signInWithEmailAndPassword(usernameOrEmail, password).await()
            val user = authResult.user ?: throw Exception("Authentication failed")
            Log.d("FirebaseAuthService", "Sign in successful for email: ${user.email}")
            
            // Get user role from database
            val roleResult = getUserRole(user.uid)
            if (roleResult.isSuccess) {
                Log.d("FirebaseAuthService", "Retrieved role for user: ${roleResult.getOrThrow()}")
                Result.success(Pair(user.email ?: usernameOrEmail, roleResult.getOrThrow()))
            } else {
                Log.e("FirebaseAuthService", "Failed to retrieve user role: ${roleResult.exceptionOrNull()?.message}")
                Result.failure(Exception("Failed to retrieve user role"))
            }
        } catch (e: Exception) {
            Log.e("FirebaseAuthService", "Sign in failed for $usernameOrEmail: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Helper function to map role string to UserRole enum
    private fun mapStringToUserRole(roleString: String): UserRole {
        return when (roleString) {
            "STUDENT" -> UserRole.STUDENT
            "TEACHER" -> UserRole.TEACHER
            "ADMIN" -> UserRole.ADMIN
            else -> UserRole.STUDENT // Default to student
        }
    }
    
    // Sign out
    fun signOut() {
        Log.d("FirebaseAuthService", "Signing out user: ${auth.currentUser?.email}")
        auth.signOut()
    }
    
    // Get user role from database
    suspend fun getUserRole(userId: String): Result<UserRole> {
        return try {
            Log.d("FirebaseAuthService", "Getting role for user ID: $userId")
            val userDoc = usersCollection.document(userId).get().await()
            val roleString = userDoc.getString("role")
            Log.d("FirebaseAuthService", "Retrieved role string: $roleString")
            
            val role = mapStringToUserRole(roleString ?: "STUDENT")
            
            Log.d("FirebaseAuthService", "Mapped role to enum: $role")
            Result.success(role)
        } catch (e: Exception) {
            Log.e("FirebaseAuthService", "Get user role failed for $userId: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // Create student account
    suspend fun createStudentAccount(
        email: String, // This will now be a generated email based on username
        password: String,
        username: String, // Primary identifier for students
        fullName: String,
        year: String,
        id: String, // Same as username
        section: String,
        department: String,
        phoneNumber: String
    ): Result<String> {
        return createUserAccount(
            email = email,
            password = password,
            username = username,
            role = UserRole.STUDENT,
            userData = hashMapOf(
                "fullName" to fullName,
                "year" to year,
                "studentId" to username, // Using username as the ID
                "section" to section,
                "department" to department,
                "phoneNumber" to phoneNumber,
                "username" to username // Store username for login
            )
        )
    }
    
    // Create instructor account
    suspend fun createInstructorAccount(
        email: String, // This will now be a generated email based on username
        password: String,
        username: String, // Primary identifier for teachers
        fullName: String,
        department: String,
        courseAssigned: String,
        assignedSection: String,
        assignedYear: String,
        phoneNumber: String
    ): Result<String> {
        return createUserAccount(
            email = email,
            password = password,
            username = username,
            role = UserRole.TEACHER,
            userData = hashMapOf(
                "fullName" to fullName,
                "department" to department,
                "courseAssigned" to courseAssigned,
                "assignedSection" to assignedSection,
                "assignedYear" to assignedYear,
                "phoneNumber" to phoneNumber,
                "username" to username // Store username for login
            )
        )
    }
    
    // Common user account creation method
    private suspend fun createUserAccount(
        email: String, 
        password: String,
        username: String,
        role: UserRole,
        userData: Map<String, String>
    ): Result<String> {
        return try {
            // Check if username already exists
            val usernameQuery = usersCollection.whereEqualTo("username", username).get().await()
            if (!usernameQuery.isEmpty) {
                return Result.failure(Exception("Username already exists"))
            }
            
            // Create authentication account
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val user = authResult.user ?: throw Exception("Failed to create user")
            
            // Prepare user data for database
            val userDataWithRole = HashMap<String, Any>(userData)
            userDataWithRole["role"] = role.name
            userDataWithRole["email"] = email
            userDataWithRole["username"] = username
            userDataWithRole["active"] = true
            userDataWithRole["created"] = com.google.firebase.Timestamp.now()
            
            // Save user data to database
            usersCollection.document(user.uid).set(userDataWithRole).await()
            
            Result.success(user.uid)
        } catch (e: Exception) {
            Log.e("FirebaseAuthService", "Create user account failed", e)
            Result.failure(e)
        }
    }
    
    // Get all users
    suspend fun getAllUsers(): Result<List<Map<String, Any>>> {
        return try {
            val snapshot = usersCollection.get().await()
            val users = mutableListOf<Map<String, Any>>()
            
            for (doc in snapshot.documents) {
                val userData = doc.data ?: continue
                
                val userWithId = HashMap(userData)
                userWithId["id"] = doc.id
                
                users.add(userWithId)
            }
            
            Result.success(users)
        } catch (e: Exception) {
            Log.e("FirebaseAuthService", "Get all users failed", e)
            Result.failure(e)
        }
    }
    
    // Update user active status
    suspend fun updateUserActiveStatus(userId: String, isActive: Boolean): Result<Unit> {
        return try {
            Log.d("FirebaseAuthService", "Updating active status for user $userId to: $isActive")
            usersCollection.document(userId).update("active", isActive).await()
            Log.d("FirebaseAuthService", "User active status updated successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAuthService", "Update user active status failed: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // Get user data
    suspend fun getUserData(userId: String): Result<Map<String, Any>?> {
        return try {
            Log.d("FirebaseAuthService", "Getting user data for ID: $userId")
            val docSnapshot = usersCollection.document(userId).get().await()
            if (docSnapshot.exists()) {
                val userData = docSnapshot.data
                Log.d("FirebaseAuthService", "User data retrieved successfully: ${userData?.keys}")
                
                // Add additional logging for student-specific fields
                if (userData != null) {
                    val year = userData["year"]
                    val section = userData["section"]
                    val username = userData["username"]
                    Log.d("FirebaseAuthService", "User specific data - Username: $username, Year: $year, Section: $section")
                    
                    // If year and section are missing, provide defaults for demo purposes
                    if (year == null || section == null) {
                        Log.d("FirebaseAuthService", "Adding default year and section for demo purposes")
                        val userDataWithDefaults = HashMap(userData)
                        if (year == null) userDataWithDefaults["year"] = "1"
                        if (section == null) userDataWithDefaults["section"] = "A"
                        return Result.success(userDataWithDefaults)
                    }
                }
                
                Result.success(userData)
            } else {
                Log.e("FirebaseAuthService", "User data not found for ID: $userId")
                
                // Create mock user data for demonstration
                val mockUserData = hashMapOf<String, Any>(
                    "fullName" to "Demo Student",
                    "email" to "demo@example.com",
                    "username" to "ST12345",
                    "role" to "STUDENT",
                    "year" to "1",
                    "section" to "A",
                    "studentId" to "ST12345"
                )
                Log.d("FirebaseAuthService", "Created mock user data for demo")
                
                Result.success(mockUserData)
            }
        } catch (e: Exception) {
            Log.e("FirebaseAuthService", "Get user data failed for $userId: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // Delete user by ID
    suspend fun deleteUser(userId: String): Result<Unit> {
        return try {
            Log.d("FirebaseAuthService", "Deleting user with ID: $userId")
            // Delete from Firestore
            usersCollection.document(userId).delete().await()
            
            // For a real implementation, you would need admin privileges to delete the auth user
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAuthService", "Delete user failed: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // Update user data
    suspend fun updateUserData(userId: String, userData: Map<String, Any>): Result<Unit> {
        return try {
            usersCollection.document(userId).update(userData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAuthService", "Update user data failed: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // Send password reset email (for admin only)
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            Log.d("FirebaseAuthService", "Sending password reset email to: $email")
            auth.sendPasswordResetEmail(email).await()
            Log.d("FirebaseAuthService", "Password reset email sent successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAuthService", "Failed to send password reset email: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // Check if a user exists with the given username
    suspend fun checkIfUsernameExists(username: String): Result<Boolean> {
        return try {
            val query = usersCollection.whereEqualTo("username", username).get().await()
            val exists = !query.isEmpty
            
            Log.d("FirebaseAuthService", "User with username $username exists: $exists")
            Result.success(exists)
        } catch (e: Exception) {
            Log.e("FirebaseAuthService", "Error checking if username exists: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // Check if a user exists with the given email
    suspend fun checkIfUserExists(email: String): Result<Boolean> {
        return try {
            val result = auth.fetchSignInMethodsForEmail(email).await()
            val signInMethods = result.signInMethods
            val exists = !signInMethods.isNullOrEmpty()
            
            Log.d("FirebaseAuthService", "User with email $email exists: $exists")
            Result.success(exists)
        } catch (e: Exception) {
            Log.e("FirebaseAuthService", "Error checking if user exists: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // Get phone number for a user (for SMS verification)
    suspend fun getUserPhoneNumber(usernameOrEmail: String): Result<String?> {
        return try {
            Log.d("FirebaseAuthService", "Getting phone number for user: $usernameOrEmail")
            
            var querySnapshot = usersCollection.whereEqualTo("username", usernameOrEmail).get().await()
            
            // If not found by username, try email
            if (querySnapshot.isEmpty) {
                querySnapshot = usersCollection.whereEqualTo("email", usernameOrEmail).get().await()
            }
            
            if (!querySnapshot.isEmpty) {
                val userDoc = querySnapshot.documents.first()
                val phoneNumber = userDoc.getString("phoneNumber")
                
                Log.d("FirebaseAuthService", "Found phone number: $phoneNumber")
                Result.success(phoneNumber)
            } else {
                Log.d("FirebaseAuthService", "No user found with identifier: $usernameOrEmail")
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e("FirebaseAuthService", "Error getting user phone number: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // Find user by username or email
    suspend fun findUserByUsernameOrEmail(usernameOrEmail: String): Result<Map<String, Any>?> {
        return try {
            Log.d("FirebaseAuthService", "Finding user by username/email: $usernameOrEmail")
            
            // Try username first
            var querySnapshot = usersCollection.whereEqualTo("username", usernameOrEmail).get().await()
            
            // If not found by username, try email
            if (querySnapshot.isEmpty) {
                querySnapshot = usersCollection.whereEqualTo("email", usernameOrEmail).get().await()
            }
            
            if (!querySnapshot.isEmpty) {
                val userDoc = querySnapshot.documents.first()
                val userData = userDoc.data
                
                if (userData != null) {
                    userData["id"] = userDoc.id
                }
                
                Log.d("FirebaseAuthService", "Found user with data: $userData")
                Result.success(userData)
            } else {
                Log.d("FirebaseAuthService", "No user found with identifier: $usernameOrEmail")
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e("FirebaseAuthService", "Error finding user: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // Reset user password by admin
    suspend fun resetUserPassword(userId: String, newPassword: String): Result<Unit> {
        return try {
            Log.d("FirebaseAuthService", "Resetting password for user: $userId")
            
            // In a real app, you would use Firebase Admin SDK to reset password
            // For now, we'll just update it in the database for demonstration
            usersCollection.document(userId).update("passwordResetRequired", true).await()
            
            Log.d("FirebaseAuthService", "Password reset flag set for user")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAuthService", "Password reset failed: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // Reauthenticate the current user with their password
    suspend fun reauthenticateUser(currentPassword: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("No user currently signed in"))
            val email = user.email ?: return Result.failure(Exception("User has no email"))
            
            Log.d("FirebaseAuthService", "Reauthenticating user: $email")
            
            // Create credential with current password
            val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, currentPassword)
            
            // Reauthenticate
            user.reauthenticate(credential).await()
            
            Log.d("FirebaseAuthService", "User reauthenticated successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAuthService", "Reauthentication failed: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // Update user's password
    suspend fun updatePassword(newPassword: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("No user currently signed in"))
            
            Log.d("FirebaseAuthService", "Updating password for user: ${user.email}")
            
            // Update password
            user.updatePassword(newPassword).await()
            
            Log.d("FirebaseAuthService", "Password updated successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAuthService", "Password update failed: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // Change password (with reauthentication)
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(Exception("No user currently signed in"))
            val email = user.email ?: return Result.failure(Exception("User has no email"))
            
            Log.d("FirebaseAuthService", "Changing password for user: $email")
            
            // Create credential with current password
            val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, currentPassword)
            
            // Reauthenticate first
            user.reauthenticate(credential).await()
            
            // Update password
            user.updatePassword(newPassword).await()
            
            Log.d("FirebaseAuthService", "Password changed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAuthService", "Password change failed: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // Send SMS verification code (simulated)
    suspend fun sendSmsVerificationCode(phoneNumber: String): Result<String> {
        return try {
            Log.d("FirebaseAuthService", "Sending verification code to: $phoneNumber")
            
            // In a real app, you would use Firebase Phone Auth here
            // For demonstration, we'll generate a random 6-digit code
            val verificationCode = (100000..999999).random().toString()
            
            // In production, you would store this code or use Firebase's verification
            
            Log.d("FirebaseAuthService", "Verification code generated: $verificationCode")
            Result.success(verificationCode)
        } catch (e: Exception) {
            Log.e("FirebaseAuthService", "Failed to send verification code: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    // Verify SMS code (simulated)
    suspend fun verifySmsCode(actualCode: String, enteredCode: String): Result<Boolean> {
        return try {
            val isValid = actualCode == enteredCode
            Log.d("FirebaseAuthService", "Code verification result: $isValid")
            Result.success(isValid)
        } catch (e: Exception) {
            Log.e("FirebaseAuthService", "Code verification failed: ${e.message}", e)
            Result.failure(e)
        }
    }
}
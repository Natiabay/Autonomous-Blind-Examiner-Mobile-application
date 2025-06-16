package com.example.ablindexaminer.data.firebase

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Utility class to help debug Firebase database issues.
 * This can be called from any activity to dump database contents to logs.
 */
class FirebaseDebugHelper {
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "FirebaseDebugHelper"
    
    /**
     * Dump the contents of a specific collection to the logs
     */
    fun dumpDatabaseNode(collectionPath: String) {
        val collection = db.collection(collectionPath)
        collection.get().addOnSuccessListener { snapshot ->
            Log.d(TAG, "----------- Firestore Collection Dump: $collectionPath -----------")
            Log.d(TAG, "Document count: ${snapshot.size()}")
            
            for (doc in snapshot.documents) {
                Log.d(TAG, "Document ID: ${doc.id}")
                try {
                    val data = doc.data
                    Log.d(TAG, "Document data: $data")
                    
                    data?.forEach { (key, value) ->
                        Log.d(TAG, "  Field '$key': $value (${value.javaClass.name})")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting data for document ${doc.id}", e)
                }
            }
            Log.d(TAG, "----------- End Firestore Collection Dump -----------")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to dump collection $collectionPath", e)
        }
    }
    
    /**
     * Verify a direct write to Firestore to check if writes are working properly
     */
    suspend fun testDirectWrite(): Boolean {
        try {
            val testData = mapOf(
                "timestamp" to com.google.firebase.Timestamp.now(),
                "message" to "Test write from FirebaseDebugHelper",
                "nested" to mapOf(
                    "field1" to "value1",
                    "field2" to 123
                ),
                "array" to listOf("item1", "item2", "item3")
            )
            
            db.collection("debug_test").add(testData).await()
            Log.d(TAG, "Test write to Firestore successful")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Test write to Firestore failed", e)
            return false
        }
    }
    
    /**
     * Verify Firestore config
     */
    fun verifyDatabaseConfig() {
        try {
            val app = com.google.firebase.FirebaseApp.getInstance()
            val options = app.options
            Log.d(TAG, "Firebase app name: ${app.name}")
            Log.d(TAG, "Project ID: ${options.projectId}")
            Log.d(TAG, "API Key: ${options.apiKey}")
            
            // Check connectivity by trying to fetch a small document
            db.collection("_connectivity_test")
                .document("_test")
                .get()
                .addOnSuccessListener {
                    Log.d(TAG, "Firestore connection test successful")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Firestore connection test failed", e)
                }
                
            Log.d(TAG, "Firestore settings: ${db.firestoreSettings}")
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying Firestore config", e)
        }
    }
}
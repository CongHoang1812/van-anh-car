package com.example.authenticateuserandpass.model
import android.util.Log
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore


data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",     // Có thể rỗng
    val address: String = "",   // Có thể rỗng
    val avatarUrl: String = ""
)
fun addUserToFirestore(user: User) {
    val db = Firebase.firestore
    // Create a new user document in the "users" collection
    // You can use the user's UID as the document ID for easy lookup
    db.collection("users").document(user.uid)
        .set(user) // Use set() to create or overwrite the document with the User object
        .addOnSuccessListener {
            Log.d("Firestore", "User added successfully with ID: ${user.uid}")
            // Handle success, e.g., navigate to another screen, show a success message
        }
        .addOnFailureListener { e ->
            Log.w("Firestore", "Error adding user", e)
            // Handle failure, e.g., show an error message
        }
}


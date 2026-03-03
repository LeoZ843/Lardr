package com.zanoni.lardr.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseDataSource @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    private val TAG = "FirebaseDataSource"

    suspend fun signIn(email: String, password: String): FirebaseUser? {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user
    }

    suspend fun signInWithGoogle(idToken: String): FirebaseUser? {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        return result.user
    }

    suspend fun signUp(email: String, password: String): FirebaseUser? {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        return result.user
    }

    fun signOut() {
        auth.signOut()
    }

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    suspend fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun <T : Any> mergeDocument(
        collection: String,
        documentId: String,
        data: T
    ) {
        firestore.collection(collection)
            .document(documentId)
            .set(data, SetOptions.merge())
            .await()
    }

    suspend fun <T : Any> queryDocuments(
        collection: String,
        field: String,
        value: Any,
        clazz: Class<T>
    ): List<T> {
        return firestore.collection(collection)
            .whereEqualTo(field, value)
            .get()
            .await()
            .documents
            .mapNotNull { it.toObject(clazz) }
    }

    suspend fun <T : Any> queryArrayContains(
        collection: String,
        field: String,
        value: Any,
        clazz: Class<T>
    ): List<T> {
        return firestore.collection(collection)
            .whereArrayContains(field, value)
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                doc.toObject(clazz)?.also { obj ->
                    try {
                        val idField = obj.javaClass.getDeclaredField("id")
                        idField.isAccessible = true
                        idField.set(obj, doc.id)
                    } catch (e: Exception) {
                    }
                }
            }
    }

    fun <T : Any> observeQuery(
        collection: String,
        field: String,
        value: Any,
        clazz: Class<T>
    ): Flow<List<T>> = callbackFlow {
        val listener = firestore.collection(collection)
            .whereEqualTo(field, value)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val data = snapshot?.documents?.mapNotNull {
                    it.toObject(clazz)
                } ?: emptyList()
                trySend(data)
            }
        awaitClose { listener.remove() }
    }

    fun <T : Any> observeDocument(
        collection: String,
        documentId: String,
        clazz: Class<T>
    ): Flow<T?> = callbackFlow {
        Log.d(TAG, "Setting up listener for $collection/$documentId")

        var listener: ListenerRegistration? = null

        try {
            listener = firestore.collection(collection)
                .document(documentId)
                .addSnapshotListener { snapshot, error ->
                    Log.d(TAG, "Snapshot received for $collection/$documentId")

                    if (error != null) {
                        Log.e(TAG, "ERROR in listener: ${error.message}", error)
                        // DON'T close, DON'T send null, just skip this update
                        return@addSnapshotListener
                    }

                    if (snapshot == null) {
                        Log.w(TAG, "Snapshot is null")
                        return@addSnapshotListener
                    }

                    if (!snapshot.exists()) {
                        Log.w(TAG, "Document doesn't exist: $collection/$documentId")
                        trySend(null)
                        return@addSnapshotListener
                    }

                    // DON'T skip pending writes - we WANT to see our updates!
                    // The pending write IS the data we just wrote

                    try {
                        Log.d(TAG, "Parsing document: $collection/$documentId")
                        val data = snapshot.toObject(clazz)

                        if (data == null) {
                            Log.e(TAG, "Failed to parse document - toObject returned null")
                        } else {
                            Log.d(TAG, "Successfully parsed document: ${data::class.simpleName}")
                            // Use trySend for non-blocking emit
                            val sendResult = trySend(data)
                            if (sendResult.isFailure) {
                                Log.w(TAG, "Failed to send update - channel full or closed")
                            } else {
                                Log.d(TAG, "Successfully sent update to Flow")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "EXCEPTION parsing document: ${e.message}", e)
                        Log.e(TAG, "Document data: ${snapshot.data}")
                        // Don't send anything, just skip this update
                    }
                }

            Log.d(TAG, "Listener registered successfully for $collection/$documentId")
        } catch (e: Exception) {
            Log.e(TAG, "EXCEPTION setting up listener: ${e.message}", e)
            close(e)
        }

        awaitClose {
            Log.d(TAG, "Closing listener for $collection/$documentId")
            listener?.remove()
        }
    }.buffer(capacity = 64) // Add buffer to prevent backpressure

    // Placeholder for other existing methods
    suspend fun <T : Any> getDocument(
        collection: String,
        documentId: String,
        clazz: Class<T>
    ): T? {
        return try {
            val snapshot = firestore.collection(collection)
                .document(documentId)
                .get()
                .await()
            snapshot.toObject(clazz)
        } catch (e: Exception) {
            // Don't re-throw cancellation - let user-initiated operations complete
            Log.e(TAG, "Error getting document: ${e.message}", e)
            null
        }
    }

    suspend fun setDocument(collection: String, documentId: String, data: Any) {
        firestore.collection(collection)
            .document(documentId)
            .set(data)
            .await()
    }

    suspend fun updateDocument(collection: String, documentId: String, updates: Map<String, Any>) {
        Log.d(TAG, "Updating document: $collection/$documentId with ${updates.keys}")
        firestore.collection(collection)
            .document(documentId)
            .update(updates)
            .await()
        Log.d(TAG, "Update completed for $collection/$documentId")
    }

    suspend fun deleteDocument(collection: String, documentId: String) {
        firestore.collection(collection)
            .document(documentId)
            .delete()
            .await()
    }

    suspend fun addArrayValue(
        collection: String,
        documentId: String,
        field: String,
        value: Any
    ) {
        Log.d(TAG, "Adding array value to $collection/$documentId field $field")
        firestore.collection(collection)
            .document(documentId)
            .update(field, com.google.firebase.firestore.FieldValue.arrayUnion(value))
            .await()
        Log.d(TAG, "Array value added to $collection/$documentId")
    }

    suspend fun removeArrayValue(
        collection: String,
        documentId: String,
        field: String,
        value: Any
    ) {
        Log.d(TAG, "Removing array value from $collection/$documentId field $field")
        firestore.collection(collection)
            .document(documentId)
            .update(field, com.google.firebase.firestore.FieldValue.arrayRemove(value))
            .await()
        Log.d(TAG, "Array value removed from $collection/$documentId")
    }

    fun <T : Any> observeArrayContains(
        collection: String,
        field: String,
        value: Any,
        clazz: Class<T>
    ): Flow<List<T>> = callbackFlow {
        val listener = firestore.collection(collection)
            .whereArrayContains(field, value)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error in array query: ${error.message}", error)
                    return@addSnapshotListener
                }

                val results = snapshot?.documents?.mapNotNull {
                    try {
                        it.toObject(clazz)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing array item: ${e.message}", e)
                        null
                    }
                } ?: emptyList()

                trySend(results)
            }

        awaitClose { listener.remove() }
    }

    suspend fun addArrayValueWithMerge(
        collection: String,
        documentId: String,
        field: String,
        value: Any
    ) {
        firestore.collection(collection)
            .document(documentId)
            .set(
                mapOf(field to com.google.firebase.firestore.FieldValue.arrayUnion(value)),
                SetOptions.merge()
            )
            .await()
    }
}
package com.zanoni.lardr.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
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
        var listener: ListenerRegistration? = null

        try {
            listener = firestore.collection(collection)
                .document(documentId)
                // INCLUDE metadata changes so we get both cache and server snapshots.
                // Without this, Firestore only fires once the server responds, but may still
                // deliver a stale/empty cache snapshot first with no way to distinguish it.
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "observeDocument error [$collection/$documentId]: ${error.message}")
                        // Transient Firestore error — keep the listener alive for the next snapshot.
                        return@addSnapshotListener
                    }

                    val isFromCache = snapshot?.metadata?.isFromCache ?: true

                    // Cache reports document absent: we cannot distinguish "does not exist"
                    // from "not cached yet". Skip and wait for the server snapshot.
                    if ((snapshot == null || !snapshot.exists()) && isFromCache) {
                        Log.d(TAG, "observeDocument skipping empty cache snapshot for $collection/$documentId")
                        return@addSnapshotListener
                    }

                    // Server confirmed the document is gone (or snapshot is null from server).
                    if (snapshot == null || !snapshot.exists()) {
                        trySend(null)
                        return@addSnapshotListener
                    }

                    try {
                        val data = snapshot.toObject(clazz)
                        trySend(data)
                    } catch (e: Exception) {
                        Log.e(TAG, "observeDocument parse error [$collection/$documentId]: ${e.message}")
                        // Skip malformed snapshot — listener stays active.
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "observeDocument setup error [$collection/$documentId]: ${e.message}")
            close(e)
        }

        awaitClose { listener?.remove() }
    }.buffer(capacity = 64)

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
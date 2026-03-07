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

    // ─── Auth ─────────────────────────────────────────────────────────────────

    suspend fun signIn(email: String, password: String): FirebaseUser? =
        auth.signInWithEmailAndPassword(email, password).await().user

    suspend fun signInWithGoogle(idToken: String): FirebaseUser? =
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await().user

    suspend fun signUp(email: String, password: String): FirebaseUser? =
        auth.createUserWithEmailAndPassword(email, password).await().user

    fun signOut() = auth.signOut()

    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    suspend fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    fun observeAuthState(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    // ─── Awaiting writes (AuthRepository / UserRepository) ───────────────────
    // These paths are intentionally awaited because the caller needs confirmation
    // before proceeding (e.g. user must exist before navigating to home screen).

    suspend fun <T : Any> mergeDocument(collection: String, documentId: String, data: T) {
        firestore.collection(collection).document(documentId)
            .set(data, SetOptions.merge()).await()
    }

    suspend fun setDocument(collection: String, documentId: String, data: Any) {
        firestore.collection(collection).document(documentId).set(data).await()
    }

    suspend fun updateDocument(collection: String, documentId: String, updates: Map<String, Any>) {
        Log.d(TAG, "updateDocument $collection/$documentId with ${updates.keys}")
        firestore.collection(collection).document(documentId).update(updates).await()
        Log.d(TAG, "updateDocument completed $collection/$documentId")
    }

    suspend fun deleteDocument(collection: String, documentId: String) {
        firestore.collection(collection).document(documentId).delete().await()
    }

    suspend fun addArrayValue(collection: String, documentId: String, field: String, value: Any) {
        firestore.collection(collection).document(documentId)
            .update(field, com.google.firebase.firestore.FieldValue.arrayUnion(value)).await()
    }

    suspend fun removeArrayValue(collection: String, documentId: String, field: String, value: Any) {
        firestore.collection(collection).document(documentId)
            .update(field, com.google.firebase.firestore.FieldValue.arrayRemove(value)).await()
    }

    suspend fun addArrayValueWithMerge(collection: String, documentId: String, field: String, value: Any) {
        firestore.collection(collection).document(documentId)
            .set(mapOf(field to com.google.firebase.firestore.FieldValue.arrayUnion(value)), SetOptions.merge())
            .await()
    }

    // ─── Fire-and-forget writes (StoreRepository) ────────────────────────────
    // Return immediately after submitting to the local Firestore cache.
    // The local cache is updated synchronously so observeStore fires instantly
    // with a pending-write snapshot. Server sync happens in the background;
    // on server rejection Firestore rolls back and observeStore reverts.

    fun setDocumentAsync(collection: String, documentId: String, data: Any) {
        firestore.collection(collection).document(documentId).set(data)
            .addOnFailureListener { e ->
                Log.e(TAG, "setDocumentAsync failed $collection/$documentId: ${e.message}")
            }
    }

    fun updateDocumentAsync(collection: String, documentId: String, updates: Map<String, Any>) {
        Log.d(TAG, "updateDocumentAsync $collection/$documentId with ${updates.keys}")
        firestore.collection(collection).document(documentId).update(updates)
            .addOnSuccessListener {
                Log.d(TAG, "updateDocumentAsync confirmed $collection/$documentId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "updateDocumentAsync failed $collection/$documentId: ${e.message}")
            }
    }

    fun deleteDocumentAsync(collection: String, documentId: String) {
        firestore.collection(collection).document(documentId).delete()
            .addOnFailureListener { e ->
                Log.e(TAG, "deleteDocumentAsync failed $collection/$documentId: ${e.message}")
            }
    }

    fun addArrayValueAsync(collection: String, documentId: String, field: String, value: Any) {
        Log.d(TAG, "addArrayValueAsync $collection/$documentId field $field")
        firestore.collection(collection).document(documentId)
            .update(field, com.google.firebase.firestore.FieldValue.arrayUnion(value))
            .addOnFailureListener { e ->
                Log.e(TAG, "addArrayValueAsync failed $collection/$documentId.$field: ${e.message}")
            }
    }

    fun removeArrayValueAsync(collection: String, documentId: String, field: String, value: Any) {
        firestore.collection(collection).document(documentId)
            .update(field, com.google.firebase.firestore.FieldValue.arrayRemove(value))
            .addOnFailureListener { e ->
                Log.e(TAG, "removeArrayValueAsync failed $collection/$documentId.$field: ${e.message}")
            }
    }

    // ─── Reads ────────────────────────────────────────────────────────────────

    suspend fun <T : Any> getDocument(collection: String, documentId: String, clazz: Class<T>): T? {
        return try {
            firestore.collection(collection).document(documentId).get().await().toObject(clazz)
        } catch (e: Exception) {
            Log.e(TAG, "getDocument error $collection/$documentId: ${e.message}", e)
            null
        }
    }

    suspend fun <T : Any> queryDocuments(
        collection: String,
        field: String,
        value: Any,
        clazz: Class<T>
    ): List<T> {
        return firestore.collection(collection)
            .whereEqualTo(field, value).get().await()
            .documents.mapNotNull { it.toObject(clazz) }
    }

    suspend fun <T : Any> queryArrayContains(
        collection: String,
        field: String,
        value: Any,
        clazz: Class<T>
    ): List<T> {
        return firestore.collection(collection)
            .whereArrayContains(field, value).get().await()
            .documents.mapNotNull { doc ->
                doc.toObject(clazz)?.also { obj ->
                    try {
                        val idField = obj.javaClass.getDeclaredField("id")
                        idField.isAccessible = true
                        idField.set(obj, doc.id)
                    } catch (e: Exception) { /* no id field */ }
                }
            }
    }

    fun <T : Any> observeQuery(
        collection: String,
        field: String,
        value: Any,
        clazz: Class<T>
    ): Flow<List<T>> = callbackFlow {
        val listener = firestore.collection(collection).whereEqualTo(field, value)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(snapshot?.documents?.mapNotNull { it.toObject(clazz) } ?: emptyList())
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
            listener = firestore.collection(collection).document(documentId)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "observeDocument error [$collection/$documentId]: ${error.message}")
                        return@addSnapshotListener
                    }
                    val isFromCache = snapshot?.metadata?.isFromCache ?: true
                    if ((snapshot == null || !snapshot.exists()) && isFromCache) {
                        Log.d(TAG, "observeDocument skipping empty cache snapshot for $collection/$documentId")
                        return@addSnapshotListener
                    }
                    if (snapshot == null || !snapshot.exists()) {
                        trySend(null); return@addSnapshotListener
                    }
                    try {
                        trySend(snapshot.toObject(clazz))
                    } catch (e: Exception) {
                        Log.e(TAG, "observeDocument parse error [$collection/$documentId]: ${e.message}")
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "observeDocument setup error [$collection/$documentId]: ${e.message}")
            close(e)
        }
        awaitClose { listener?.remove() }
    }.buffer(capacity = 64)

    fun <T : Any> observeArrayContains(
        collection: String,
        field: String,
        value: Any,
        clazz: Class<T>
    ): Flow<List<T>> = callbackFlow {
        val listener = firestore.collection(collection).whereArrayContains(field, value)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeArrayContains error: ${error.message}")
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull {
                    try { it.toObject(clazz) } catch (e: Exception) { null }
                } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }
}
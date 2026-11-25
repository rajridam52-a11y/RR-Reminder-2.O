package com.example.core.firebase

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class OwnerConfigRepository(
    private val db: FirebaseFirestore?,
    private val authOverride: FirebaseAuth? = null,
) {
    constructor(context: Context) : this(
        db = runCatching {
            FirebaseFirestore.getInstance(
                context.applicationContext.getString(R.string.firestore_database_id),
            )
        }.getOrNull(),
    )

    private fun resolveAuth(): FirebaseAuth? =
        authOverride ?: runCatching { Firebase.auth }.getOrNull()

    private fun requireSignedInUser() =
        resolveAuth()?.currentUser ?: throw IllegalStateException("Please sign in with Google first.")

    private fun requireVerifiedOwner() {
        val user = requireSignedInUser()
        if (!isOwnerUser(user.email, user.isEmailVerified)) {
            throw SecurityException("Owner Mode is restricted to verified $OWNER_ADMIN_EMAIL.")
        }
    }

    fun observeGlobalConfig(): Flow<Result<GlobalAppConfig?>> = flow {
        val auth = resolveAuth()
        val firestore = db
        val uid = auth?.currentUser?.uid
        if (uid.isNullOrBlank() || firestore == null) {
            emit(Result.success(null))
            return@flow
        }
        val docRef = firestore.collection(APP_CONFIG_COLLECTION).document(GLOBAL_CONFIG_DOC_ID)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    val cfg = if (snapshot.exists()) {
                        snapshot.toObject(
                            GlobalAppConfig::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                        )
                    } else {
                        null
                    }
                    Result.success(cfg)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception) ?: Exception(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, GLOBAL_CONFIG_PATH)
                    }
                    emit(Result.failure(firestoreError ?: exceptionToLog))
                },
        )
    }

    suspend fun saveGlobalConfig(config: GlobalAppConfig): Result<Unit> {
        return try {
            requireVerifiedOwner()
            val firestore = db ?: throw IllegalStateException("Firebase Firestore is unavailable.")
            val sanitized = config.validatedForSave()
            val docRef = firestore.collection(APP_CONFIG_COLLECTION).document(GLOBAL_CONFIG_DOC_ID)
            val existingSnap = docRef.get().await()
            if (existingSnap.exists()) {
                docRef.update(sanitized.toMutableFieldMap(FieldValue.serverTimestamp())).await()
            } else {
                docRef.set(
                    sanitized.toCreateFieldMap(
                        createdAtValue = FieldValue.serverTimestamp(),
                        updatedAtValue = FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, GLOBAL_CONFIG_PATH)
            Result.failure(e)
        }
    }

    suspend fun resetGlobalConfig(): Result<Unit> =
        saveGlobalConfig(GlobalAppConfig(syncOverrideEnabled = false))
}

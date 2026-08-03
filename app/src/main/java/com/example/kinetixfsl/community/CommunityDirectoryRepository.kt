package com.example.kinetixfsl.community

import com.example.kinetixfsl.community.model.Community
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * The single point of contact with Firestore for communities — the ones users
 * create through the "Start a community" wizard and browse through Discover.
 *
 * Kept separate from [CommunityRepository] (which owns posts, comments, votes
 * and follows) so the two concerns don't tangle: this file only ever touches
 * the `communities` collection.
 */
class CommunityDirectoryRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {

    /**
     * Creates a community owned by the signed-in user and returns its new id.
     *
     * The creator's name and avatar are copied onto the document so the home
     * screen can show them without a second read, the same way posts carry
     * their author's details.
     */
    suspend fun createCommunity(
        name: String,
        description: String,
        categories: List<String>,
    ): Result<String> {
        val user = auth.currentUser ?: return Result.failure(Exception("Not signed in."))

        val data = hashMapOf(
            "name" to name.trim(),
            "description" to description.trim(),
            "categories" to categories,
            "creatorId" to user.uid,
            "creatorName" to (user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore('@') ?: "Anonymous"),
            "creatorAvatarUrl" to user.photoUrl?.toString(),
            "contributionsPerWeek" to 0L,
            "memberCount" to 0L,
            "createdAt" to Timestamp.now(),
        )
        return try {
            val ref = firestore.collection(COMMUNITIES).add(data).await()
            // The creator is a member from the start — otherwise they couldn't
            // post to their own community. Best-effort: a failure here doesn't
            // undo the community, it just leaves the creator to Join manually.
            join(ref.id, name.trim())
            Result.success(ref.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // -------------------------------------------------------------------------
    // Membership
    // -------------------------------------------------------------------------

    /**
     * Joins [communityId]. Writes both sides — the community's member marker and
     * the user's joined list — plus the member counter, in one transaction so a
     * join can never land half-written. Idempotent.
     */
    suspend fun join(communityId: String, communityName: String): Result<Unit> {
        val me = auth.currentUser ?: return Result.failure(Exception("Not signed in."))
        val communityRef = firestore.collection(COMMUNITIES).document(communityId)
        val memberRef = communityRef.collection(MEMBERS).document(me.uid)
        val joinedRef = firestore.collection(USERS).document(me.uid)
            .collection(JOINED).document(communityId)
        val myName = me.displayName?.takeIf { it.isNotBlank() }
            ?: me.email?.substringBefore('@') ?: "Anonymous"

        val myProfileRef = firestore.collection(USERS).document(me.uid)

        return try {
            firestore.runTransaction { tx ->
                if (tx.get(memberRef).exists()) return@runTransaction
                tx.set(memberRef, mapOf("displayName" to myName, "joinedAt" to Timestamp.now()))
                tx.set(joinedRef, mapOf("name" to communityName, "joinedAt" to Timestamp.now()))
                tx.set(communityRef, mapOf("memberCount" to FieldValue.increment(1)), SetOptions.merge())
                // Mirrored onto the (public) profile doc so anyone can see which
                // communities a user has joined — the private subcollection
                // above is only readable by the user themselves.
                tx.set(
                    myProfileRef,
                    mapOf("joinedCommunityIds" to FieldValue.arrayUnion(communityId)),
                    SetOptions.merge(),
                )
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Undoes [join]. Also idempotent. */
    suspend fun leave(communityId: String): Result<Unit> {
        val me = auth.currentUser ?: return Result.failure(Exception("Not signed in."))
        val communityRef = firestore.collection(COMMUNITIES).document(communityId)
        val memberRef = communityRef.collection(MEMBERS).document(me.uid)
        val joinedRef = firestore.collection(USERS).document(me.uid)
            .collection(JOINED).document(communityId)
        val myProfileRef = firestore.collection(USERS).document(me.uid)

        return try {
            firestore.runTransaction { tx ->
                if (!tx.get(memberRef).exists()) return@runTransaction
                tx.delete(memberRef)
                tx.delete(joinedRef)
                tx.set(communityRef, mapOf("memberCount" to FieldValue.increment(-1)), SetOptions.merge())
                tx.set(
                    myProfileRef,
                    mapOf("joinedCommunityIds" to FieldValue.arrayRemove(communityId)),
                    SetOptions.merge(),
                )
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes the community document and the creator's own joined markers. The
     * member *posts* are removed separately (see the ViewModel) because they
     * live in the top-level `posts` collection.
     *
     * The member roster (`members` subcollection) is intentionally left behind:
     * once the community document is gone nothing ever reads it, so those
     * markers are harmless orphans — and deleting them in bulk would blow the
     * per-request budget in the security rules. Creator-only, enforced by rules.
     */
    suspend fun deleteCommunity(communityId: String): Result<Unit> {
        val me = auth.currentUser ?: return Result.failure(Exception("Not signed in."))
        val communityRef = firestore.collection(COMMUNITIES).document(communityId)
        return try {
            // Best-effort: drop it from the creator's own joined list.
            try {
                firestore.collection(USERS).document(me.uid)
                    .collection(JOINED).document(communityId).delete().await()
                firestore.collection(USERS).document(me.uid)
                    .set(
                        mapOf("joinedCommunityIds" to FieldValue.arrayRemove(communityId)),
                        SetOptions.merge(),
                    ).await()
            } catch (_: Exception) { /* dangling markers are harmless */ }

            communityRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Fetches full community docs for a set of ids — backs the profile's
     *  "My Communities" sheet, where names and descriptions are needed. */
    suspend fun getCommunitiesByIds(ids: List<String>): List<Community> {
        return ids.distinct().mapNotNull { id ->
            try {
                firestore.collection(COMMUNITIES).document(id).get().await().toCommunityOrNull()
            } catch (_: Exception) {
                null
            }
        }
    }

    /** Ids of the communities the signed-in user has joined. Drives Join buttons. */
    fun observeJoinedCommunityIds(): Flow<Set<String>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptySet())
            awaitClose { }
            return@callbackFlow
        }
        val reg = firestore.collection(USERS).document(uid).collection(JOINED)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptySet()); return@addSnapshotListener }
                trySend(snap?.documents?.map { it.id }?.toSet() ?: emptySet())
            }
        awaitClose { reg.remove() }
    }

    /**
     * The communities the signed-in user has joined, as lightweight [Community]
     * objects carrying just id and name — enough for the create-post picker.
     */
    fun observeJoinedCommunities(): Flow<List<Community>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val reg = firestore.collection(USERS).document(uid).collection(JOINED)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                trySend(
                    snap?.documents?.map { doc ->
                        Community(id = doc.id, name = doc.getString("name").orEmpty())
                    }.orEmpty(),
                )
            }
        awaitClose { reg.remove() }
    }

    /** Live document for one community — backs its home screen header. */
    fun observeCommunity(id: String): Flow<Community?> = callbackFlow {
        val reg = firestore.collection(COMMUNITIES).document(id)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                trySend(snap?.toCommunityOrNull())
            }
        awaitClose { reg.remove() }
    }

    /**
     * Every community, newest first. Discover filters this list by category
     * client-side — the set is small enough that a single ordered read beats a
     * composite index per category combination.
     */
    fun observeAllCommunities(): Flow<List<Community>> = callbackFlow {
        val reg = firestore.collection(COMMUNITIES)
            .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) { close(err); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener
                trySend(snap.documents.mapNotNull { it.toCommunityOrNull() })
            }
        awaitClose { reg.remove() }
    }

    /**
     * Adds [category] to the community's list. `arrayUnion` is idempotent, so
     * adding a category that's already there is a no-op — no duplicates.
     */
    suspend fun addCategory(communityId: String, category: String): Result<Unit> = try {
        firestore.collection(COMMUNITIES).document(communityId)
            .set(mapOf("categories" to FieldValue.arrayUnion(category)), SetOptions.merge())
            .await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Updates the community's profile picture and/or banner. Only the fields
     * passed non-null are written, so changing one leaves the other alone.
     * Admin-only, enforced by rules (only the creator may edit the document).
     */
    suspend fun updateCommunityImages(
        communityId: String,
        avatarUrl: String? = null,
        bannerUrl: String? = null,
    ): Result<Unit> = try {
        val fields = buildMap {
            avatarUrl?.let { put("avatarUrl", it) }
            bannerUrl?.let { put("bannerUrl", it) }
        }
        if (fields.isNotEmpty()) {
            firestore.collection(COMMUNITIES).document(communityId)
                .set(fields, SetOptions.merge())
                .await()
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Removes [category] from the community's list. */
    suspend fun removeCategory(communityId: String, category: String): Result<Unit> = try {
        firestore.collection(COMMUNITIES).document(communityId)
            .set(mapOf("categories" to FieldValue.arrayRemove(category)), SetOptions.merge())
            .await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Deserializes one community document, returning null instead of throwing —
     * so a single malformed document can't take down a whole snapshot the way
     * it would if the listener's map threw. Mirrors [CommunityRepository]'s
     * `toPostOrNull`.
     */
    private fun DocumentSnapshot.toCommunityOrNull(): Community? = try {
        toObject(Community::class.java)?.copy(id = id)
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val COMMUNITIES = "communities"
        const val USERS = "users"
        const val MEMBERS = "members"
        const val JOINED = "joinedCommunities"
        const val FIELD_CREATED_AT = "createdAt"
    }
}

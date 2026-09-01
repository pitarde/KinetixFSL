package com.example.kinetixfsl.community

import com.example.kinetixfsl.community.model.Community
import com.example.kinetixfsl.community.model.storageKeyOf
import com.example.kinetixfsl.community.upload.R2MediaUploader
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

            // R2 before the document goes: the Worker verifies the avatar/banner
            // keys against communities/{id} before deleting them, so it has to
            // still exist. Best-effort — orphaned images must never block the
            // community delete the user asked for.
            deleteCommunityMedia(communityId)

            communityRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Deletes a community's avatar and banner from Cloudflare R2.
     *
     * Reads the document for its two image URLs, turns them into bucket keys and
     * asks the Worker to remove them. Best-effort: any failure only orphans
     * those files and must never stop the community deletion. Call while the
     * document still exists — the Worker checks the keys against it.
     */
    suspend fun deleteCommunityMedia(communityId: String) {
        try {
            val snap = firestore.collection(COMMUNITIES).document(communityId).get().await()
            val keys = listOfNotNull(
                storageKeyOf(snap.getString("avatarUrl")),
                storageKeyOf(snap.getString("bannerUrl")),
            ).distinct()
            if (keys.isNotEmpty()) {
                R2MediaUploader.deleteCommunityObjects(communityId, keys)
            }
        } catch (_: Exception) { /* orphaned media is harmless — see the doc */ }
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

    /**
     * Drops from the signed-in user's joined list any of [candidateIds] whose
     * community no longer exists.
     *
     * A community the user joined can be deleted by its creator — or vanish
     * with the creator's whole account — and nothing then reaches into every
     * member's mirrored list to clean it up. The stale id keeps inflating the
     * "My Communities" count on the profile. This clears it from both places
     * the user is allowed to write: the private `joinedCommunities` marker and
     * the public `joinedCommunityIds` array. Only a definite "doesn't exist"
     * prunes — a read that merely failed leaves the entry for next time.
     * Best-effort throughout.
     */
    suspend fun pruneMissingJoinedCommunities(candidateIds: List<String>) {
        val me = auth.currentUser?.uid ?: return
        val myProfileRef = firestore.collection(USERS).document(me)
        for (id in candidateIds.distinct()) {
            val gone = try {
                !firestore.collection(COMMUNITIES).document(id).get().await().exists()
            } catch (_: Exception) {
                false // A read failure is not proof it's gone.
            }
            if (!gone) continue
            try {
                myProfileRef.collection(JOINED).document(id).delete().await()
                myProfileRef.set(
                    mapOf("joinedCommunityIds" to FieldValue.arrayRemove(id)),
                    SetOptions.merge(),
                ).await()
            } catch (_: Exception) { /* best-effort */ }
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
     * Finds communities whose name contains [query] (case-insensitive).
     * One-shot read + client-side filter, same tradeoff as [observeAllCommunities].
     */
    suspend fun searchCommunities(query: String, limit: Int = 15): List<Community> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return try {
            firestore.collection(COMMUNITIES)
                .get()
                .await()
                .documents
                .mapNotNull { it.toCommunityOrNull() }
                .filter { it.name.lowercase().contains(needle) }
                .take(limit)
        } catch (_: Exception) {
            emptyList()
        }
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
     *
     * Ordering matters, so a swap never shows a broken image. The document is
     * repointed at the new file FIRST — every screen reads the community's
     * avatar/banner straight off this document (or a live listener on it), so
     * they all switch over at once. The replaced URL is kept in
     * `avatarUrlPrev` / `bannerUrlPrev` so the upload Worker still authorises
     * deleting the old file, which happens next; then the `*Prev` markers are
     * cleared. Nothing is denormalised elsewhere, so no fan-out is needed.
     * Best-effort cleanup — an orphan file never fails the save.
     */
    suspend fun updateCommunityImages(
        communityId: String,
        avatarUrl: String? = null,
        bannerUrl: String? = null,
    ): Result<Unit> = try {
        if (avatarUrl == null && bannerUrl == null) {
            Result.success(Unit)
        } else {
            val ref = firestore.collection(COMMUNITIES).document(communityId)
            val current = runCatching { ref.get().await() }.getOrNull()

            val oldAvatar = if (avatarUrl != null) {
                current?.getString("avatarUrl")?.takeIf { it.isNotBlank() && it != avatarUrl }
            } else null
            val oldBanner = if (bannerUrl != null) {
                current?.getString("bannerUrl")?.takeIf { it.isNotBlank() && it != bannerUrl }
            } else null

            // 1) Repoint the document — readers flip to the new file immediately.
            val fields = mutableMapOf<String, Any?>()
            if (avatarUrl != null) {
                fields["avatarUrl"] = avatarUrl
                if (oldAvatar != null) fields["avatarUrlPrev"] = oldAvatar
            }
            if (bannerUrl != null) {
                fields["bannerUrl"] = bannerUrl
                if (oldBanner != null) fields["bannerUrlPrev"] = oldBanner
            }
            ref.set(fields, SetOptions.merge()).await()

            // 2) Old file is unreferenced now — free it (authorised via *Prev).
            val staleKeys = listOfNotNull(storageKeyOf(oldAvatar), storageKeyOf(oldBanner))
            if (staleKeys.isNotEmpty()) {
                runCatching { R2MediaUploader.deleteCommunityObjects(communityId, staleKeys) }
            }

            // 3) Drop the swap bookkeeping.
            val clear = mutableMapOf<String, Any?>()
            if (oldAvatar != null) clear["avatarUrlPrev"] = FieldValue.delete()
            if (oldBanner != null) clear["bannerUrlPrev"] = FieldValue.delete()
            if (clear.isNotEmpty()) {
                runCatching { ref.set(clear, SetOptions.merge()).await() }
            }

            Result.success(Unit)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Renames the community. Admin-only, enforced by rules (only the creator may
     * edit the document). The old name lingers on other users' `joinedCommunities`
     * mirror and past posts' `communityName` — cosmetic staleness only, not worth
     * a fan-out write across every member and post to fix.
     */
    suspend fun updateCommunityName(communityId: String, name: String): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(Exception("Name can't be empty."))
        return try {
            firestore.collection(COMMUNITIES).document(communityId)
                .set(mapOf("name" to trimmed), SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Rewrites `creatorName` on every community [uid] created, so a rename
     * doesn't leave "Created by {old name}" showing on their communities' home
     * screens. Best-effort and unbounded — see [CommunityRepository.propagateAuthorName],
     * the same pattern for posts and comments.
     */
    suspend fun renameCreator(uid: String, newName: String) {
        try {
            val docs = firestore.collection(COMMUNITIES).whereEqualTo("creatorId", uid).get().await()
            if (docs.isEmpty) return
            val batch = firestore.batch()
            docs.documents.forEach { doc ->
                batch.set(doc.reference, mapOf("creatorName" to newName), SetOptions.merge())
            }
            batch.commit().await()
        } catch (_: Exception) { /* best-effort */ }
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

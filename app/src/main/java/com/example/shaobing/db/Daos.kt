package com.example.shaobing.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun all(): List<Bookmark>

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    fun byUrl(url: String): Bookmark?

    @Insert
    suspend fun insert(bookmark: Bookmark): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface UserscriptDao {
    @Query("SELECT * FROM userscripts ORDER BY installedAt DESC")
    fun all(): List<Userscript>

    @Query("SELECT * FROM userscripts WHERE enabled = 1")
    fun enabled(): List<Userscript>

    @Query("SELECT * FROM userscripts WHERE id = :id")
    fun byId(id: Long): Userscript?

    @Insert
    suspend fun insert(script: Userscript): Long

    @Query("UPDATE userscripts SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM userscripts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles")
    fun all(): List<UserProfile>

    @Query("SELECT * FROM profiles WHERE isCurrent = 1 LIMIT 1")
    fun current(): UserProfile?

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun byId(id: Long): UserProfile?

    @Query("SELECT COUNT(*) FROM profiles")
    fun count(): Int

    @Insert
    suspend fun insert(profile: UserProfile): Long

    @Query("UPDATE profiles SET isCurrent = :current WHERE id = :id")
    suspend fun setCurrent(id: Long, current: Boolean)

    @Query("UPDATE profiles SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("UPDATE profiles SET username = :username, uid = :uid WHERE id = :id")
    suspend fun updateUserInfo(id: Long, username: String?, uid: String?)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ProfileSnapshotDao {
    @Query("SELECT * FROM profile_snapshots WHERE profileId = :profileId")
    fun byProfile(profileId: Long): ProfileSnapshot?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: ProfileSnapshot)

    @Query("DELETE FROM profile_snapshots WHERE profileId = :profileId")
    suspend fun delete(profileId: Long)
}

@Dao
interface GmValueDao {
    @Query("SELECT value FROM gm_values WHERE scriptKey = :scriptKey AND key = :key")
    fun get(scriptKey: String, key: String): String?

    @Query("SELECT * FROM gm_values WHERE scriptKey = :scriptKey")
    fun all(scriptKey: String): List<GmValue>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun set(value: GmValue)

    @Query("DELETE FROM gm_values WHERE scriptKey = :scriptKey AND key = :key")
    fun delete(scriptKey: String, key: String)

    @Query("DELETE FROM gm_values WHERE scriptKey = :scriptKey")
    fun clear(scriptKey: String)
}

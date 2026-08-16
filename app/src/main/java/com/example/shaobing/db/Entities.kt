package com.example.shaobing.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "userscripts")
data class Userscript(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val namespace: String? = null,
    val description: String? = null,
    val version: String? = null,
    val sourceUrl: String,
    val file: String,
    val matches: String,
    val includes: String,
    val requires: String,
    val requireFiles: String,
    val runAt: String,
    val grants: String,
    val enabled: Boolean = true,
    val installedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "profiles")
data class UserProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isCurrent: Boolean = false,
    val username: String? = null,
    val uid: String? = null
)

@Entity(tableName = "profile_snapshots")
data class ProfileSnapshot(
    @PrimaryKey val profileId: Long,
    val cookiesJson: String = "[]",
    val storageJson: String = "{}"
)

@Entity(tableName = "gm_values")
data class GmValue(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scriptKey: String,
    val key: String,
    val value: String
)

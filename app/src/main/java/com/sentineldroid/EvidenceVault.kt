package com.sentineldroid

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

class EvidenceVault private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    "sentinel_evidence.db",
    null,
    2
) {
    private val signer = EvidenceSigner()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE evidence (
                id TEXT PRIMARY KEY,
                timestamp INTEGER NOT NULL,
                source_kind TEXT NOT NULL,
                origin TEXT NOT NULL,
                authority TEXT NOT NULL,
                package_name TEXT,
                uid INTEGER,
                content_type TEXT NOT NULL,
                content TEXT NOT NULL,
                verification TEXT NOT NULL,
                content_sha256 TEXT NOT NULL,
                previous_hash TEXT NOT NULL,
                record_hash TEXT NOT NULL,
                signature TEXT NOT NULL,
                signer_key_alias TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_evidence_package_time ON evidence(package_name, timestamp)")
        db.execSQL("CREATE INDEX idx_evidence_source_time ON evidence(source_kind, timestamp)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE evidence ADD COLUMN signature TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE evidence ADD COLUMN signer_key_alias TEXT NOT NULL DEFAULT 'sentinel-evidence-v1'")
        }
    }

    @Synchronized
    fun add(
        sourceKind: String,
        origin: String,
        authority: String,
        packageName: String?,
        uid: Int?,
        contentType: String,
        content: String,
        verification: String
    ): String {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val id = "ev-" + UUID.randomUUID().toString().replace("-", "").take(16)
            val now = System.currentTimeMillis()
            val contentHash = Hashing.sha256(content)
            val previousHash = latestHash(db)
            val canonical = canonicalFields(
                id,
                now,
                sourceKind,
                origin,
                authority,
                packageName,
                uid,
                contentType,
                contentHash,
                previousHash,
                verification
            )
            val recordHash = Hashing.sha256(canonical)
            val signature = signer.sign(canonical)

            val values = ContentValues().apply {
                put("id", id)
                put("timestamp", now)
                put("source_kind", sourceKind)
                put("origin", origin)
                put("authority", authority)
                put("package_name", packageName)
                if (uid == null) putNull("uid") else put("uid", uid)
                put("content_type", contentType)
                put("content", content)
                put("verification", verification)
                put("content_sha256", contentHash)
                put("previous_hash", previousHash)
                put("record_hash", recordHash)
                put("signature", signature)
                put("signer_key_alias", "sentinel-evidence-v1")
            }
            db.insertOrThrow("evidence", null, values)
            db.setTransactionSuccessful()
            return id
        } finally {
            db.endTransaction()
        }
    }

    fun latest(limit: Int = 40): List<Map<String, String>> {
        val out = mutableListOf<Map<String, String>>()
        readableDatabase.query(
            "evidence",
            columns(),
            null, null, null, null,
            "rowid DESC",
            limit.coerceIn(1, 500).toString()
        ).use { c ->
            while (c.moveToNext()) out += rowToMap(c)
        }
        return out
    }

    fun allChronological(): List<Map<String, String>> {
        val out = mutableListOf<Map<String, String>>()
        readableDatabase.query(
            "evidence",
            columns(),
            null, null, null, null,
            "rowid ASC"
        ).use { c ->
            while (c.moveToNext()) out += rowToMap(c)
        }
        return out
    }

    fun verifyChain(): Boolean {
        val rows = allChronological()
        var previous = "GENESIS"
        var previousTimestamp = Long.MIN_VALUE
        val seenIds = HashSet<String>()
        val seenHashes = HashSet<String>()
        for (row in rows) {
            val rowId = row["id"].orEmpty()
            val rowTimestamp = row["timestamp"]?.toLongOrNull() ?: return false
            val rowHash = row["hash"].orEmpty()
            if (!seenIds.add(rowId)) return false
            if (!seenHashes.add(rowHash)) return false
            if (rowTimestamp < previousTimestamp) return false
            previousTimestamp = rowTimestamp
            val contentHash = Hashing.sha256(row["content"].orEmpty())
            if (contentHash != row["content_sha256"]) return false
            if (row["previous_hash"] != previous) return false

            val canonical = canonicalFields(
                row["id"].orEmpty(),
                row["timestamp"]?.toLongOrNull() ?: return false,
                row["source"].orEmpty(),
                row["origin"].orEmpty(),
                row["authority"].orEmpty(),
                row["package"].takeUnless { it.isNullOrEmpty() },
                row["uid"]?.toIntOrNull(),
                row["content_type"].orEmpty(),
                row["content_sha256"].orEmpty(),
                row["previous_hash"].orEmpty(),
                row["verification"].orEmpty()
            )
            if (Hashing.sha256(canonical) != row["hash"]) return false
            if (row["signature"].orEmpty().isBlank()) return false
            if (!signer.verify(canonical, row["signature"].orEmpty())) return false
            previous = row["hash"].orEmpty()
        }
        return true
    }

    fun count(): Long = readableDatabase.rawQuery("SELECT COUNT(*) FROM evidence", null).use {
        if (it.moveToFirst()) it.getLong(0) else 0L
    }

    fun publicSigningKey(): String = signer.publicKeyBase64()

    private fun latestHash(db: SQLiteDatabase): String = db.rawQuery(
        "SELECT record_hash FROM evidence ORDER BY rowid DESC LIMIT 1", null
    ).use { if (it.moveToFirst()) it.getString(0) else "GENESIS" }

    private fun canonicalFields(
        id: String,
        timestamp: Long,
        source: String,
        origin: String,
        authority: String,
        packageName: String?,
        uid: Int?,
        contentType: String,
        contentHash: String,
        previousHash: String,
        verification: String
    ): ByteArray = Hashing.canonicalBytes(
        listOf(
            id,
            timestamp.toString(),
            source,
            origin,
            authority,
            packageName ?: "<null>",
            uid?.toString() ?: "<null>",
            contentType,
            contentHash,
            previousHash,
            verification
        )
    )

    private fun columns() = arrayOf(
        "id", "timestamp", "source_kind", "origin", "authority", "package_name", "uid",
        "content_type", "content", "verification", "content_sha256", "previous_hash",
        "record_hash", "signature", "signer_key_alias"
    )

    private fun rowToMap(c: android.database.Cursor): Map<String, String> = mapOf(
        "id" to c.getString(0),
        "timestamp" to c.getLong(1).toString(),
        "source" to c.getString(2),
        "origin" to c.getString(3),
        "authority" to c.getString(4),
        "package" to (if (c.isNull(5)) "" else c.getString(5)),
        "uid" to (if (c.isNull(6)) "" else c.getInt(6).toString()),
        "content_type" to c.getString(7),
        "content" to c.getString(8),
        "verification" to c.getString(9),
        "content_sha256" to c.getString(10),
        "previous_hash" to c.getString(11),
        "hash" to c.getString(12),
        "signature" to c.getString(13),
        "signer_key_alias" to c.getString(14)
    )

    companion object {
        @Volatile private var instance: EvidenceVault? = null

        fun get(context: Context): EvidenceVault = instance ?: synchronized(this) {
            instance ?: EvidenceVault(context).also { instance = it }
        }
    }
}

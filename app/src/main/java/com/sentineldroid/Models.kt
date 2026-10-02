package com.sentineldroid

data class PackageSnapshot(
    val packageName: String,
    val uid: Int,
    val appLabel: String,
    val versionName: String,
    val versionCode: Long,
    val apkSha256: String?,
    val signerSha256: List<String>,
    val enabled: Boolean,
    val launcherVisible: Boolean,
    val debuggable: Boolean,
    val systemApp: Boolean,
    val updatedSystemApp: Boolean,
    val testOnly: Boolean,
    val directBootAware: Boolean,
    val processName: String?,
    val dataDir: String?,
    val nativeLibraryDir: String?,
    val splitApkCount: Int,
    val dexFileCount: Int,
    val nativeLibFileCount: Int,
    val requestedPermissions: List<String>,
    val grantedPermissions: List<String>,
    val installerPackage: String?,
    val initiatingPackage: String?,
    val originatingPackage: String?,
    val permissions: List<String>,
    val services: List<String>,
    val receivers: List<String>,
    val providers: List<String>,
    val activities: List<String>
)

data class CapabilityFact(
    val packageName: String,
    val capability: String,
    val state: String,
    val source: String,
    val observedAt: Long,
    val evidenceId: String? = null
)

data class UsageEventRecord(
    val packageName: String,
    val eventType: String,
    val className: String?,
    val timestamp: Long
)

data class NetworkUsageRecord(
    val packageName: String,
    val uid: Int,
    val rxBytes: Long,
    val txBytes: Long,
    val windowStart: Long,
    val windowEnd: Long,
    val transport: String,
    val source: String
)

data class InstallSessionRecord(
    val sessionId: Int,
    val installerPackage: String?,
    val appPackage: String?,
    val progress: Float,
    val active: Boolean,
    val staged: Boolean
)

data class SecurityMetaRecord(
    val event: String,
    val affectedEvidenceId: String,
    val reasons: List<String>,
    val generatedAt: Long
)

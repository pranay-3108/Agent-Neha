package com.sentineldroid

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ServiceInfo
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

class PackageCollector(private val context: Context) {
    private val pm = context.packageManager

    companion object {
        private const val REQUESTED_PERMISSION_GRANTED = 2
    }

    fun scan(includeApkHash: Boolean = true): List<PackageSnapshot> {
        val flags = PackageManager.GET_PERMISSIONS or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or
            PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SIGNING_CERTIFICATES

        @Suppress("DEPRECATION")
        val installed = pm.getInstalledPackages(flags)
        return installed.mapNotNull { buildSnapshot(it, includeApkHash) }.sortedBy { it.packageName }
    }

    fun scanPackageRows(packages: Set<String>): List<PackageSnapshot> = packages.mapNotNull { scanPackage(it, false) }

    fun scanPackage(packageName: String, includeApkHash: Boolean = true): PackageSnapshot? = runCatching {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS or
            PackageManager.GET_SERVICES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or
            PackageManager.GET_ACTIVITIES or
            PackageManager.GET_SIGNING_CERTIFICATES)
    }.getOrNull()?.let { buildSnapshot(it, includeApkHash) }

    fun bootReceiverPackages(): Set<String> {
        val result = mutableSetOf<String>()
        for (action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED)) {
            @Suppress("DEPRECATION")
            val receivers = pm.queryBroadcastReceivers(Intent(action), PackageManager.MATCH_ALL)
            receivers.forEach { result += it.activityInfo.packageName }
        }
        return result
    }

    private fun buildSnapshot(info: PackageInfo, includeApkHash: Boolean): PackageSnapshot? {
        val appInfo = info.applicationInfo ?: return null
        val requested = info.requestedPermissions?.toList().orEmpty()
        val permFlags = info.requestedPermissionsFlags ?: IntArray(0)
        val granted = requested.filterIndexed { index, _ ->
            index < permFlags.size && (permFlags[index] and REQUESTED_PERMISSION_GRANTED) != 0
        }
        val permissions = requested.mapIndexed { index, permission ->
            val isGranted = index < permFlags.size && (permFlags[index] and REQUESTED_PERMISSION_GRANTED) != 0
            "$permission=${if (isGranted) "GRANTED" else "REQUESTED"}"
        }

        val installSource = if (Build.VERSION.SDK_INT >= 30) {
            runCatching { pm.getInstallSourceInfo(info.packageName) }.getOrNull()
        } else null

        val appFlags = appInfo.flags
        val apkSignals = if (includeApkHash) inspectApk(File(appInfo.sourceDir)) else ApkSignals(1, 1, 0)
        return PackageSnapshot(
            packageName = info.packageName,
            uid = appInfo.uid,
            appLabel = appInfo.loadLabel(pm).toString(),
            versionName = info.versionName ?: "unknown",
            versionCode = versionCode(info),
            apkSha256 = if (includeApkHash) sha256File(File(appInfo.sourceDir)) else null,
            signerSha256 = signerDigests(info),
            enabled = appInfo.enabled,
            launcherVisible = hasLauncher(info.packageName),
            debuggable = (appFlags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
            systemApp = (appFlags and ApplicationInfo.FLAG_SYSTEM) != 0,
            updatedSystemApp = (appFlags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            testOnly = (appFlags and ApplicationInfo.FLAG_TEST_ONLY) != 0,
            directBootAware = appInfo.directBootAware,
            processName = appInfo.processName,
            dataDir = appInfo.dataDir,
            nativeLibraryDir = appInfo.nativeLibraryDir,
            splitApkCount = 1 + (appInfo.splitSourceDirs?.size ?: 0),
            dexFileCount = apkSignals.dexFiles,
            nativeLibFileCount = apkSignals.nativeLibs,
            requestedPermissions = requested,
            grantedPermissions = granted,
            installerPackage = installSource?.installingPackageName,
            initiatingPackage = installSource?.initiatingPackageName,
            originatingPackage = installSource?.originatingPackageName,
            permissions = permissions,
            services = info.services?.map(::serviceString).orEmpty(),
            receivers = info.receivers?.map(::receiverString).orEmpty(),
            providers = info.providers?.map(::providerString).orEmpty(),
            activities = info.activities?.map(::activityString).orEmpty()
        )
    }

    private fun hasLauncher(packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
        }
        @Suppress("DEPRECATION")
        return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL).isNotEmpty()
    }

    private fun serviceString(info: ServiceInfo): String {
        val fgsType = if (Build.VERSION.SDK_INT >= 28) info.foregroundServiceType else 0
        return "${info.name}|exported=${info.exported}|permission=${info.permission ?: "-"}|process=${info.processName ?: "-"}|directBoot=${info.directBootAware}|fgsType=$fgsType"
    }

    private fun receiverString(info: ActivityInfo): String =
        "${info.name}|exported=${info.exported}|permission=${info.permission ?: "-"}|directBoot=${info.directBootAware}"

    private fun providerString(info: ProviderInfo): String =
        "${info.name}|exported=${info.exported}|read=${info.readPermission ?: "-"}|write=${info.writePermission ?: "-"}|grantUri=${info.grantUriPermissions}|directBoot=${info.directBootAware}"

    private fun activityString(info: ActivityInfo): String =
        "${info.name}|exported=${info.exported}|permission=${info.permission ?: "-"}|directBoot=${info.directBootAware}|process=${info.processName ?: "-"}"


    private data class ApkSignals(val splitCount: Int, val dexFiles: Int, val nativeLibs: Int)

    private fun inspectApk(file: File): ApkSignals = runCatching {
        var dex = 0
        var native = 0
        ZipFile(file).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val name = entries.nextElement().name
                if (name.matches(Regex("classes(\\d+)?\\.dex"))) dex++
                if (name.startsWith("lib/") && name.endsWith(".so")) native++
            }
        }
        ApkSignals(1, dex.coerceAtLeast(1), native)
    }.getOrDefault(ApkSignals(1, 1, 0))

    private fun versionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) {
        info.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        info.versionCode.toLong()
    }

    private fun sha256File(file: File): String? = runCatching {
        file.inputStream().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
    }.getOrNull()

    private fun signerDigests(info: PackageInfo): List<String> = runCatching {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            @Suppress("DEPRECATION") info.signatures?.toList().orEmpty()
        }
        signatures.map { Hashing.sha256(it.toByteArray()) }
    }.getOrDefault(emptyList())
}

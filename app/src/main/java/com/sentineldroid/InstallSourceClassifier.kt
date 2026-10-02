package com.sentineldroid

object InstallSourceClassifier {
    enum class Source(val multiplier: Double) {
        PLAY_STORE(1.0),
        OEM_STORE(1.1),
        ADB_SIDELOAD(2.0),
        OTHER_SIDELOAD(2.5),
        UNKNOWN_SIDELOAD(3.0)
    }

    fun classify(pkg: PackageSnapshot): Source {
        val installer = pkg.installerPackage?.lowercase().orEmpty()
        val initiating = pkg.initiatingPackage?.lowercase().orEmpty()

        if (installer == "com.android.vending") return Source.PLAY_STORE
        if (installer.contains("samsung") || installer.contains("xiaomi") || installer.contains("oppo") || installer.contains("vivo") || installer.contains("oneplus")) {
            return Source.OEM_STORE
        }
        if (installer.isBlank() && (initiating.contains("adb") || initiating == "com.android.shell")) return Source.ADB_SIDELOAD
        if (installer.isBlank() && initiating.isBlank()) return Source.UNKNOWN_SIDELOAD
        if (installer.isBlank()) return Source.OTHER_SIDELOAD
        return Source.OTHER_SIDELOAD
    }
}

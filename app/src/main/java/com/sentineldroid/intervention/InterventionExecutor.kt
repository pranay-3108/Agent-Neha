package com.sentineldroid.intervention

interface InterventionExecutor {
    fun isCapable(): Boolean
    suspend fun fire(request: ShotRequest): ShotResult
    suspend fun revert(shotId: String): RevertResult

    data class ShotRequest(
        val packageName: String,
        val capability: String,
        val reason: String,
        val authorizedBy: String,
        val expiresAt: Long?
    )
    data class ShotResult(val shotId: String, val fired: Boolean, val verified: Boolean, val failureReason: String?)
    data class RevertResult(val reverted: Boolean, val failureReason: String?)
}

class NoOpInterventionExecutor : InterventionExecutor {
    override fun isCapable() = false
    override suspend fun fire(request: InterventionExecutor.ShotRequest) = InterventionExecutor.ShotResult("", false, false, "BUILD_HAS_NO_INTERVENTION_CAPABILITY")
    override suspend fun revert(shotId: String) = InterventionExecutor.RevertResult(false, "BUILD_HAS_NO_INTERVENTION_CAPABILITY")
}

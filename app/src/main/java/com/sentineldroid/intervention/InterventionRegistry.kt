package com.sentineldroid.intervention

object InterventionRegistry {
    private var _planner: InterventionPlanner = NoOpInterventionPlanner()
    private var _executor: InterventionExecutor = NoOpInterventionExecutor()
    private var _verifier: InterventionVerifier = NoOpInterventionVerifier()

    fun planner(): InterventionPlanner = _planner
    fun executor(): InterventionExecutor = _executor
    fun verifier(): InterventionVerifier = _verifier

    fun install(planner: InterventionPlanner? = null, executor: InterventionExecutor? = null, verifier: InterventionVerifier? = null) {
        planner?.let { _planner = it }
        executor?.let { _executor = it }
        verifier?.let { _verifier = it }
    }
}

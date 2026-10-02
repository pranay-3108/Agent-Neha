package com.sentineldroid

enum class AuthorityClass(val rank: Int) {
    EVIDENCE(0),
    LLM_DERIVED(1),
    PLATFORM_DERIVED(2),
    SECURITY_META(3),
    POLICY(4)
}

object AuthorityPolicy {
    fun classify(source: String): AuthorityClass = when {
        source == "POLICY" -> AuthorityClass.POLICY
        source == "SECURITY_META" -> AuthorityClass.SECURITY_META
        source.contains("LLM", true) -> AuthorityClass.LLM_DERIVED
        source.contains("PLATFORM", true) || source.contains("ANDROID", true) -> AuthorityClass.PLATFORM_DERIVED
        else -> AuthorityClass.EVIDENCE
    }

    fun corroborate(records: List<AuthorityClass>): AuthorityClass {
        if (records.isEmpty()) return AuthorityClass.EVIDENCE
        return records.minBy { it.rank }
    }
}

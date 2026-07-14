package com.example.lockin

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ComplianceState { COMPLIANT, BREAK }

data class ComplianceStatus(
    val state: ComplianceState,
    val foregroundApp: String?
)

private val initialStatus = ComplianceStatus(ComplianceState.COMPLIANT, foregroundApp = null)

object LockInMonitor {
    private val _complianceState = MutableStateFlow(initialStatus)
    val complianceState: StateFlow<ComplianceStatus> = _complianceState.asStateFlow()

    // Identifies the current break (the millisecond it started), 0 when
    // compliant. The Service sets it; the UI needs it to stamp a mute
    // request so approvals can't carry over from a previous break.
    private val _breakId = MutableStateFlow(0L)
    val breakId: StateFlow<Long> = _breakId.asStateFlow()

    fun update(status: ComplianceStatus) {
        _complianceState.value = status
    }

    fun setBreakId(id: Long) {
        _breakId.value = id
    }

    fun reset() {
        _complianceState.value = initialStatus
        _breakId.value = 0L
    }
}

fun evaluateCompliance(
    ownPackageName: String,
    isScreenOn: Boolean,
    foregroundApp: String?,
    allowlist: Set<String>
): ComplianceStatus {
    val isCompliant = !isScreenOn ||
        foregroundApp == null ||
        foregroundApp == ownPackageName ||
        allowlist.contains(foregroundApp)

    return ComplianceStatus(
        state = if (isCompliant) ComplianceState.COMPLIANT else ComplianceState.BREAK,
        foregroundApp = foregroundApp
    )
}

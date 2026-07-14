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

    fun update(status: ComplianceStatus) {
        _complianceState.value = status
    }

    fun reset() {
        _complianceState.value = initialStatus
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

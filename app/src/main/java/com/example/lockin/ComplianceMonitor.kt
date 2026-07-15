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

    // True while the alarm is actually making noise. Distinct from BREAK:
    // in a group the alarm outlives the break, so a breaker who returns to a
    // compliant app is COMPLIANT again yet the alarm still blares. The Home
    // header reads this to show "ALARM SOUNDING" instead of a misleading
    // green "LOCK-IN ACTIVE". The Service publishes it each tick.
    private val _alarmSounding = MutableStateFlow(false)
    val alarmSounding: StateFlow<Boolean> = _alarmSounding.asStateFlow()

    fun update(status: ComplianceStatus) {
        _complianceState.value = status
    }

    fun setBreakId(id: Long) {
        _breakId.value = id
    }

    fun setAlarmSounding(sounding: Boolean) {
        _alarmSounding.value = sounding
    }

    fun reset() {
        _complianceState.value = initialStatus
        _breakId.value = 0L
        _alarmSounding.value = false
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

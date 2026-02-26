package com.example.passage

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import java.time.LocalTime
import java.time.format.DateTimeParseException

@Service(Service.Level.PROJECT)
@State(name = "WorkProgressSettings", storages = [Storage("workProgress.xml")])
class WorkSettings : PersistentStateComponent<WorkSettings.State> {

    data class State(
            var workDaysPerMonth: Int = 22,
            var startTimeStr: String = "09:00",
            var endTimeStr: String = "18:00",
            var monthlySalary: Double = 15000.0,  // 新增：月工资（元）
    )

    private var state = State()

    override fun getState() = state
    override fun loadState(state: State) { this.state = state }

    // ── 公开给外部使用的属性 ──
    val workDaysPerMonth: Int
        get() = state.workDaysPerMonth.coerceIn(1, 31)

    val monthlySalary: Double get() = state.monthlySalary.coerceAtLeast(0.0)

    val startTimeString: String
        get() = state.startTimeStr

    val endTimeString: String
        get() = state.endTimeStr

    val startTime: LocalTime
        get() = parseTimeSafely(state.startTimeStr, 9, 0)

    val endTime: LocalTime
        get() = parseTimeSafely(state.endTimeStr, 18, 0)

    fun updateSettings(days: Int, startStr: String, endStr: String, salary: Double) {
        state = state.copy(
                workDaysPerMonth = days.coerceIn(1, 31),
                startTimeStr = startStr.trim(),
                endTimeStr = endStr.trim(),
                monthlySalary = salary.coerceAtLeast(0.0),
        )
    }

    private fun parseTimeSafely(str: String, defH: Int, defM: Int): LocalTime = try {
        LocalTime.parse(str)
    } catch (e: Exception) {
        LocalTime.of(defH, defM)
    }

    companion object {
        fun getInstance(project: Project): WorkSettings = project.service()
    }
}
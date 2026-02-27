package com.example.passage

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

class WorkSettingsDialog(project: Project) : DialogWrapper(project) {

    private val settings = WorkSettings.getInstance(project)

    private val daysField = JBTextField().apply {
        text = settings.workDaysPerMonth.toString()
        columns = 5
    }

    private val startField = JBTextField().apply {
        text = settings.startTimeString
        columns = 8
    }

    private val endField = JBTextField().apply {
        text = settings.endTimeString
        columns = 8
    }

    private val salaryField = JBTextField().apply {
        text = String.format("%.0f", settings.monthlySalary)
        columns = 10
    }

    init {
        title = "设置工作时间参数"
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row("每月工作天数：") {
                cell(daysField)
            }
            row("上班时间 (HH:mm)：") {
                cell(startField)
            }
            row("下班时间 (HH:mm)：") {
                cell(endField)
            }
            row("月工资（元）：") {
                cell(salaryField)
            }
            row {
                label("示例：09:00 ~ 18:00，格式需正确")
                        .comment("时间格式必须为 HH:mm")
            }
        }
    }

    override fun doOKAction() {
        if (applyChanges()) {
            super.doOKAction()
        }
    }

    private fun applyChanges(): Boolean {
        val days = daysField.text.toIntOrNull() ?: 22
        val start = startField.text.trim()
        val end = endField.text.trim()
        val salary = salaryField.text.toDoubleOrNull() ?: 15000.0

        if (!start.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$")) ||
                !end.matches(Regex("^([01]\\d|2[0-3]):[0-5]\\d$"))
        ) {
            setErrorText("时间格式应为 HH:mm，例如 09:00")
            return false
        }

        settings.updateSettings(days, start, end, salary)
        setErrorText(null)
        return true
    }
}
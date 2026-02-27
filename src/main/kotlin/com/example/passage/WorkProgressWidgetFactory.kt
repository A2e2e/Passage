package com.example.passage

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.JBColor
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.jetbrains.rd.util.string.print
import java.awt.Dimension
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.swing.JComponent
import javax.swing.JProgressBar
import javax.swing.SwingUtilities

class WorkProgressWidgetFactory : StatusBarWidgetFactory {
    override fun getId() = "WorkProgressWidget"
    override fun getDisplayName() = "当日工作进度"
    override fun isAvailable(project: Project) = true
    override fun createWidget(project: Project): StatusBarWidget = WorkProgressWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)
    override fun canBeEnabledOn(statusBar: StatusBar) = true
}

class WorkProgressWidget(private val project: Project) : CustomStatusBarWidget, Disposable {

    private val progressBar = JProgressBar(0, 10000).apply {
        isIndeterminate = false
        foreground = JBColor.GREEN.darker()           // 更深的绿色，防被覆盖
        background = JBColor.LIGHT_GRAY.brighter()    // 背景色明显一点
        preferredSize = Dimension(180, 15)            // 再宽一点、高一点
        minimumSize = Dimension(180, 15)
        maximumSize = Dimension(220, 18)
        border = JBUI.Borders.empty(2, 8)
        toolTipText = "点击设置工作时间参数"
        setStringPainted(true)                         // ← 关键！显示数字文字在进度条上
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                showSettingsDialog()
            }
        })
    }

    private var statusBar: StatusBar? = null
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD)
    private var isTodayWorkday: Boolean? = null  // null 表示未查询

    init {
        updateProgress()     // 立即刷新
        scheduleUpdate()     // 启动定时刷新
    }

    private fun scheduleUpdate() {
        alarm.addRequest({
            updateProgress()
            scheduleUpdate()  // 递归调度，每 60 秒一次
        }, 1000)
    }

    private fun updateProgress() {


        val today = LocalDate.now()
        val settings = WorkSettings.getInstance(project)

        // 计算日薪（每月工资 ÷ 每月工作天数）
        val dailySalary = settings.monthlySalary / settings.workDaysPerMonth.coerceAtLeast(1)


        val isWorkday = getOrCheckIsWorkday(today)
        if (!isWorkday) {
            SwingUtilities.invokeLater {
                progressBar.value = 0
                progressBar.string = "今日休息 已赚 0.00 元"
                progressBar.toolTipText = "今天是非工作日（周末/法定节假日/调休），今日收入：0.00 元"
                progressBar.isIndeterminate = false
                progressBar.foreground = JBColor.GRAY
                statusBar?.updateWidget(ID())
                progressBar.repaint()
            }
            return
        }

        // 是工作日，继续计算时间进度...
        val now = LocalDateTime.now()
        val currentTime = now.toLocalTime()
        val percentDouble = calculateProgress(currentTime, settings.startTime, settings.endTime)
                .coerceIn(0.0, 100.0)

        val percentScaled = (percentDouble * 100).toInt()

// ===== 工资实时动画 =====

        val totalSeconds = Duration.between(settings.startTime, settings.endTime)
                .seconds
                .coerceAtLeast(1)

        val passedSeconds = Duration.between(settings.startTime, currentTime)
                .seconds
                .coerceIn(0, totalSeconds)

        val salaryPerSecond = dailySalary / totalSeconds.toDouble()

        val earnedToday = salaryPerSecond * passedSeconds

        SwingUtilities.invokeLater {


            // ✅ 1. 使用 model 强制触发 ChangeEvent
            progressBar.model.value = percentScaled

            // ✅ 2. 生成新的显示文本
            val newText = String.format(
                    "%.2f%% ¥%.2f",
                    percentDouble,
                    earnedToday
            )

            // ✅ 3. 只有内容变化才更新（防止Swing优化跳过）
            if (progressBar.string != newText) {
                progressBar.string = ""          // 先清空，打破缓存
                progressBar.repaint()
                progressBar.string = newText     // 再重新赋值
            }

            // ✅ 4. Tooltip 保持你原逻辑
            progressBar.toolTipText = buildString {
                append("当日工作进度: ${String.format("%.2f", percentDouble)}%")
                append(" | 今日已赚 ≈ ¥${String.format("%.2f", earnedToday)}")
                if (percentScaled in 1..99) {
                    val remaining = Duration.between(currentTime, settings.endTime).toMinutes()
                    if (remaining > 0) {
                        append(" | 剩余约 ${remaining / 60}h ${remaining % 60}m 下班")
                    }
                }
                append(" | 日薪 ≈ ¥${String.format("%.2f", dailySalary)}")
                append(" | 点击修改设置（月薪/天数）")
            }

            // ❌ 不建议使用 indeterminate，会干扰文字层刷新
            progressBar.isIndeterminate = false

            // ✅ 5. 颜色逻辑保留
            progressBar.foreground = when {
                percentScaled < 30 -> JBColor(0xFF9800, 0xFFB74D)
                percentScaled < 70 -> JBColor(0x4CAF50, 0x81C784)
                else -> JBColor(0x2196F3, 0x64B5F6)
            }

            // ✅ 6. 强制刷新整个组件树
            progressBar.invalidate()
            progressBar.revalidate()
            progressBar.repaint()
            progressBar.parent?.repaint()

            // ✅ 7. 通知 StatusBar 容器刷新
            statusBar?.updateWidget(ID())
        }
    }

    /**
     * 计算当前时间在工作时间段内的进度百分比
     * @return 0~100 的整数
     */
    private fun calculateProgress(
            now: LocalTime,
            start: LocalTime,
            end: LocalTime
    ): Double {

        if (now.isBefore(start)) return 0.0
        if (now.isAfter(end)) return 100.0

        val totalSeconds = Duration.between(start, end).seconds
        if (totalSeconds <= 0) return 0.0

        val passedSeconds = Duration.between(start, now).seconds

        return (passedSeconds.toDouble() / totalSeconds) * 100.0
    }

    private fun showSettingsDialog() {
        val dialog = WorkSettingsDialog(project)
        if (dialog.showAndGet()) {
            updateProgress()   // 只需要刷新
        }
    }

    override fun ID() = "WorkProgressWidget"
    override fun getComponent(): JComponent = progressBar
    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
    }

    private fun getOrCheckIsWorkday(today: LocalDate): Boolean {
        // 如果已缓存，直接返回
        isTodayWorkday?.let { return it }

        // 首次查询
        val isWork = HolidayChecker.isWorkday(today)
        isTodayWorkday = isWork
        return isWork
    }



    override fun dispose() {
        alarm.cancelAllRequests()
        Disposer.dispose(this)
    }
}
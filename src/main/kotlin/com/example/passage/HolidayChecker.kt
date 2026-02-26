package com.example.passage

import com.intellij.openapi.diagnostic.Logger
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import org.json.JSONObject  // 需要添加 org.json:json 依赖（build.gradle）
import java.nio.channels.AsynchronousCloseException

object HolidayChecker {
    private val LOG = Logger.getInstance(HolidayChecker::class.java)

    /**
     * 查询今天是否为工作日（使用 timor.tech API）
     * @return true = 需要上班（正常工作日或调休上班），false = 休息（周末/节假日）
     */
    fun isWorkdayToday(): Boolean = isWorkday(LocalDate.now())

    fun isWorkday(date: LocalDate): Boolean {
        val dateStr = date.toString()
        val urlStr = "https://timor.tech/api/holiday/info/$dateStr"
        try {
            val url = URL(urlStr)
            (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000

                if (responseCode == 200) {
                    inputStream.use { input ->  // ← 自动关闭 inputStream
                        val response = BufferedReader(InputStreamReader(input)).use { it.readText() }
                        val json = JSONObject(response)

                        if (json.getInt("code") == 0) {
                            val typeObj = json.getJSONObject("type")
                            val typeNum = typeObj.getInt("type")
                            return typeNum == 0 || typeNum == 3  // 工作日或调休上班
                        }
                    }
                }
            }  // ← 离开 apply 块时自动调用 disconnect()
        } catch (e: AsynchronousCloseException) {
            LOG.warn("网络通道被异步关闭（可能是 sandbox 退出或 dispose）: $dateStr", e)
            // fallback
            return date.dayOfWeek.value in 1..5
        } catch (e: Exception) {
            LOG.warn("查询节假日失败: $dateStr", e)
            return date.dayOfWeek.value in 1..5
        }

        return date.dayOfWeek.value in 1..5
    }
}
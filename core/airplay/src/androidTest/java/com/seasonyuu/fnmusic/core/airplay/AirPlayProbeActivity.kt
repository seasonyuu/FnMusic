package com.seasonyuu.fnmusic.core.airplay

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Test APK only. A PIN stays in memory and is consumed once by the JNI worker. */
internal object ProbeUi {
    private val main = Handler(Looper.getMainLooper())
    private val pin = AtomicReference("")
    val cancelled = AtomicBoolean(false)
    var status = "准备连接 Mac…"
        private set
    var awaitingPin = false
        private set
    var observer: (() -> Unit)? = null
    fun reset() {
        check(Looper.myLooper() == Looper.getMainLooper())
        pin.set(""); cancelled.set(false); awaitingPin = false; status = "准备连接 Mac…"
    }
    fun event(event: String) = main.post {
        awaitingPin = event == "pin_required" || event == "invalid_pin"
        status = when (event) {
            "pin_required", "invalid_pin" -> "请输入 Mac 屏幕上本次显示的四位配对码"
            "streaming" -> "正在向 Mac 播放低音、高音、静音循环，请留意声音。"
            "pairing", "pin_start", "pin_setup" -> "正在与 Mac 配对…"
            "authenticated", "session_setup", "event_channel", "stream_setup" -> "认证与音频通道建立中…"
            "stopped" -> "测试会话已结束"
            else -> "测试状态：$event"
        }
        observer?.invoke()
    }
    fun submit(value: String): Boolean {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!awaitingPin || value.length != 4 || value.any { it !in '0'..'9' }) return false
        pin.set(value); awaitingPin = false; status = "已提交，正在验证配对码…"
        observer?.invoke()
        return true
    }
    fun pollPin(): String = pin.getAndSet("")
    fun clear() { pin.set("") }
}

class AirPlayProbeActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var submit: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_SECURE)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        body.addView(TextView(this).apply { text = "FnMusic · AirPlay 真机测试"; textSize = 24f })
        body.addView(TextView(this).apply {
            text = "目标：已由验证脚本核对身份的本机 Mac\n只需在此输入配对码，不必切回电脑。"
            textSize = 16f; setPadding(0, pad, 0, pad)
        })
        status = TextView(this).apply { textSize = 18f }
        body.addView(status)
        input = EditText(this).apply {
            hint = "四位配对码"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(4))
            isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            contentDescription = "AirPlay 配对码"
        }
        body.addView(input)
        submit = Button(this).apply {
            text = "提交配对码"
            setOnClickListener {
                if (ProbeUi.submit(input.text.toString())) input.text.clear()
                else input.error = "请输入四位数字"
            }
        }
        body.addView(submit)
        body.addView(Button(this).apply {
            text = "停止测试"
            setOnClickListener {
                ProbeUi.cancelled.set(true); input.text.clear(); ProbeUi.clear()
                status.text = "正在停止测试并释放连接…"
                isEnabled = false
            }
        })
        setContentView(body)
        ProbeUi.observer = { render() }
        render()
    }
    private fun render() {
        status.text = ProbeUi.status
        input.visibility = if (ProbeUi.awaitingPin) View.VISIBLE else View.GONE
        submit.visibility = input.visibility
        if (ProbeUi.awaitingPin) input.requestFocus()
    }
    override fun onDestroy() {
        ProbeUi.observer = null
        if (!isChangingConfigurations) { ProbeUi.cancelled.set(true); ProbeUi.clear() }
        super.onDestroy()
    }
}

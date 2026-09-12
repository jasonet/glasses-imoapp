package com.jacb.inmocards

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CaptureRequest
import android.os.Bundle
import android.util.Range
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import java.text.NumberFormat
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var database: CardDatabase
    private var sessionId = 0L
    private var config = ShoeConfig()
    private lateinit var engine: ProbabilityEngine
    private lateinit var stabilizer: RankStabilizer
    private lateinit var analyzer: CardAnalyzer
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private val probabilityExecutor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue<Runnable>()
    )
    private var probabilityTask: Future<*>? = null
    private var probabilityGeneration = 0L
    @Volatile private var detectionGeneration = 0L
    private var configurationOpen = false
    private var destroyed = false

    private lateinit var previewView: PreviewView
    private lateinit var guideView: DetectionGuideView
    private lateinit var statusDot: TextView
    private lateinit var currentRank: TextView
    private lateinit var statusText: TextView
    private lateinit var counterText: TextView
    private lateinit var lowText: TextView
    private lateinit var neutralText: TextView
    private lateinit var tenValueText: TextView
    private lateinit var aceText: TextView
    private lateinit var powerButton: Button
    private lateinit var pauseButton: Button
    private lateinit var configButton: Button
    private lateinit var modeHint: TextView
    private val rankViews = mutableListOf<TextView>()

    private var showPreview = true
    private var paused = false
    private var lastStatusMessage: String? = null
    private var lastStatusColor: Int? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) initializeCamera() else setStatus("需要相机权限", COLOR_ERROR)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )

        database = CardDatabase(this)
        val session = database.activeSession()
        sessionId = session.id
        config = session.config
        engine = ProbabilityEngine(database.loadRanks(sessionId), config.decks)
        cameraExecutor = Executors.newSingleThreadExecutor()
        analyzer = CardAnalyzer(
            onRank = { rank ->
                val generation = detectionGeneration
                runOnUiThread {
                    if (generation == detectionGeneration && canObserve()) stabilizer.offer(rank)
                }
            },
            onError = { error -> runOnUiThread {
                if (canObserve()) setStatus("识别错误: ${error.message}", COLOR_ERROR)
            } }
        )
        stabilizer = RankStabilizer(
            onConfirmed = ::recordRank,
            onState = { message -> setStatus(message, COLOR_READY) }
        )

        setContentView(buildInterface())
        renderProbabilities()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            initializeCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun buildInterface(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            alpha = 0.38f
        }
        guideView = DetectionGuideView(this)
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        root.addView(guideView, FrameLayout.LayoutParams(-1, -1))

        val dashboard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        root.addView(dashboard, FrameLayout.LayoutParams(-1, -1))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusDot = text("●", 24f, COLOR_READY, Gravity.CENTER)
        currentRank = text("--", 42f, Color.WHITE, Gravity.CENTER)
        statusText = text("启动相机…", 17f, Color.WHITE, Gravity.START)
        counterText = text("--", 18f, COLOR_MUTED, Gravity.END)
        header.addView(statusDot, LinearLayout.LayoutParams(dp(42), dp(52)))
        header.addView(currentRank, LinearLayout.LayoutParams(dp(92), dp(52)))
        header.addView(statusText, LinearLayout.LayoutParams(0, dp(52), 1f))
        header.addView(counterText, LinearLayout.LayoutParams(dp(130), dp(52)))
        dashboard.addView(header, LinearLayout.LayoutParams(-1, dp(52)))

        configButton = button("${config.label} · 选择玩法/副数") { chooseShoe() }
        dashboard.addView(configButton, LinearLayout.LayoutParams(-1, dp(36)))
        modeHint = text("", 13f, COLOR_MUTED, Gravity.CENTER)
        dashboard.addView(modeHint, LinearLayout.LayoutParams(-1, dp(32)))

        val groups = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        lowText = groupText("● 低 2-6", COLOR_LOW)
        neutralText = groupText("● 中 7-9", COLOR_NEUTRAL)
        tenValueText = groupText("● 十点", COLOR_TEN_VALUE)
        aceText = groupText("● A", COLOR_ACE)
        groups.addView(lowText, weighted(dp(46)))
        groups.addView(neutralText, weighted(dp(46)))
        groups.addView(tenValueText, weighted(dp(46)))
        groups.addView(aceText, weighted(dp(46)))
        dashboard.addView(groups, LinearLayout.LayoutParams(-1, dp(46)))

        val ranks = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        repeat(2) { rowIndex ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            repeat(7) { columnIndex ->
                val index = rowIndex * 7 + columnIndex
                if (index < 13) {
                    text("--", 16f, Color.WHITE, Gravity.CENTER).also {
                        it.setBackgroundColor(Color.argb(145, 0, 0, 0))
                        rankViews += it
                        row.addView(it, weighted(dp(44)))
                    }
                } else {
                    row.addView(View(this), weighted(dp(44)))
                }
            }
            ranks.addView(row, LinearLayout.LayoutParams(-1, dp(44)))
        }
        dashboard.addView(ranks, LinearLayout.LayoutParams(-1, dp(88)))

        val spacer = View(this)
        dashboard.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        controls.addView(button("撤销") { undoLast() }, weighted(dp(46)))
        controls.addView(button("长按重开").apply {
            setOnLongClickListener {
                resetSession()
                true
            }
        }, weighted(dp(46)))
        powerButton = button("省电显示") { togglePreview() }
        controls.addView(powerButton, weighted(dp(46)))
        pauseButton = button("暂停相机") { togglePause() }
        controls.addView(pauseButton, weighted(dp(46)))
        dashboard.addView(controls, LinearLayout.LayoutParams(-1, dp(46)))
        return root
    }

    private fun initializeCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            if (destroyed) return@addListener
            cameraProvider = providerFuture.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun bindCamera() {
        val provider = cameraProvider ?: return
        provider.unbindAll()
        if (paused) return

        val analysisBuilder = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
        Camera2Interop.Extender(analysisBuilder).setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
            Range(5, 15)
        )
        val analysis = analysisBuilder.build().apply { setAnalyzer(cameraExecutor, analyzer) }

        val useCases = mutableListOf<androidx.camera.core.UseCase>(analysis)
        if (showPreview) {
            val preview = Preview.Builder().setTargetResolution(Size(640, 480)).build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
            useCases += preview
        }

        try {
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, *useCases.toTypedArray())
            previewView.visibility = if (showPreview) View.VISIBLE else View.GONE
            guideView.visibility = if (showPreview) View.VISIBLE else View.GONE
            setStatus(if (showPreview) "将牌角数字对准框内" else "省电识别中", COLOR_READY)
        } catch (error: Throwable) {
            setStatus("相机启动失败: ${error.message}", COLOR_ERROR)
        }
    }

    private fun recordRank(rank: CardRank) {
        if (engine.remaining(rank) <= 0) {
            setStatus("${rank.label} 已全部出现", COLOR_ERROR)
            return
        }
        try {
            database.record(sessionId, rank)
        } catch (error: Exception) {
            setStatus("记录失败，请移开后重试", COLOR_ERROR)
            return
        }
        engine.record(rank)
        currentRank.text = rank.label
        setStatus("已记录 ${rank.label}，请移开", COLOR_CONFIRMED)
        renderProbabilities()
    }

    private fun undoLast() {
        val rank = try {
            database.undoLast(sessionId)
        } catch (error: Exception) {
            setStatus("撤销失败，请重试", COLOR_ERROR)
            return
        }
        if (rank == null) {
            Toast.makeText(this, "没有可撤销的记录", Toast.LENGTH_SHORT).show()
            return
        }
        engine.undo(rank)
        detectionGeneration++
        currentRank.text = "↶${rank.label}"
        stabilizer.reset()
        renderProbabilities()
        setStatus("已撤销 ${rank.label}", COLOR_ACE)
    }

    private fun resetSession(nextConfig: ShoeConfig = config) {
        val session = try {
            database.resetSession(nextConfig)
        } catch (error: Exception) {
            setStatus("新牌靴保存失败", COLOR_ERROR)
            return
        }
        sessionId = session.id
        config = session.config
        engine = ProbabilityEngine(decks = config.decks)
        detectionGeneration++
        configButton.text = "${config.label} · 选择玩法/副数"
        currentRank.text = "--"
        stabilizer.reset()
        renderProbabilities()
        setStatus("新牌靴：${config.label}", COLOR_CONFIRMED)
    }

    private fun chooseShoe() {
        if (configurationOpen) return
        configurationOpen = true
        detectionGeneration++
        stabilizer.reset()
        val presets = ShoeConfig.presets
        var selected = presets.indexOf(config)
        val labels = presets.map { preset ->
            when {
                preset == ShoeConfig() -> "${preset.label}（默认）"
                preset.mode == GameMode.BACCARAT && preset.decks < 6 -> "${preset.label}（模拟）"
                preset.mode == GameMode.BACCARAT && preset.decks == 8 -> "${preset.label}（澳门8副参考）"
                else -> preset.label
            }
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择后新开牌靴；历史记录保留")
            .setSingleChoiceItems(labels, selected) { _, which -> selected = which }
            .setNegativeButton("取消", null)
            .setPositiveButton("新开牌靴") { _, _ -> resetSession(presets[selected]) }
            .setOnDismissListener {
                configurationOpen = false
                detectionGeneration++
                stabilizer.reset()
                if (paused) setStatus("相机已关闭", COLOR_PAUSED)
            }
            .show()
    }

    private fun canObserve(): Boolean = !destroyed && !paused && !configurationOpen &&
        lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun togglePreview() {
        showPreview = !showPreview
        analyzer.intervalMs = if (showPreview) 350L else 650L
        powerButton.text = if (showPreview) "省电显示" else "打开预览"
        bindCamera()
    }

    private fun togglePause() {
        detectionGeneration++
        paused = !paused
        pauseButton.text = if (paused) "恢复相机" else "暂停相机"
        if (paused) {
            cameraProvider?.unbindAll()
            previewView.visibility = View.GONE
            guideView.visibility = View.GONE
            setStatus("相机已关闭", COLOR_PAUSED)
        } else {
            stabilizer.reset()
            bindCamera()
        }
    }

    private fun renderProbabilities() {
        probabilityGeneration++
        val generation = probabilityGeneration
        probabilityTask?.cancel(true)
        probabilityExecutor.purge()
        val formatter = NumberFormat.getPercentInstance().apply { maximumFractionDigits = 2 }
        val chances = engine.rankChances()
        chances.forEachIndexed { index, chance ->
            rankViews[index].text = "${chance.rank.label}  ${chance.remaining}\n${formatter.format(chance.probability)}"
        }
        counterText.text = "余 ${engine.totalRemaining}  已出 ${engine.seenCount}"
        if (config.mode == GameMode.BLACKJACK) {
            val groups = engine.blackjackChances()
            lowText.text = "低 ${formatter.format(groups.low)}"
            neutralText.text = "中 ${formatter.format(groups.neutral)}"
            tenValueText.text = "十点 ${formatter.format(groups.tenValue)}"
            aceText.text = "A ${formatter.format(groups.ace)}"
            modeHint.text = "下一张：低2-6 / 中7-9 / 十点10JQK / A · 下方13牌面排序"
            return
        }

        lowText.text = "庄 --"
        neutralText.text = "闲 --"
        tenValueText.text = "和 --"
        val counts = engine.baccaratCounts()
        val total = engine.totalRemaining
        aceText.text = "零点 ${formatter.format(if (total > 0) counts[0].toDouble() / total else 0.0)}"
        modeHint.text = "庄闲和：下一完整局计算中 · 零点/下方牌面：下一张"
        // Cancel superseded calculations and never apply a result to a newer observation/session.
        probabilityTask = probabilityExecutor.submit {
            try {
                val result = BaccaratEngine().calculate(counts)
                runOnUiThread {
                    if (destroyed || generation != probabilityGeneration) return@runOnUiThread
                    if (result == null) {
                        modeHint.text = "不足6张，请重开牌靴 · 下方仍为下一张牌面概率"
                    } else {
                        lowText.text = "庄 ${formatter.format(result.banker)}"
                        neutralText.text = "闲 ${formatter.format(result.player)}"
                        tenValueText.text = "和 ${formatter.format(result.tie)}"
                        modeHint.text = "庄闲和：剩余牌发下一完整局（非当前手牌）· 零点/牌面：下一张"
                    }
                }
            } catch (cancelled: CancellationException) {
                // A newer shoe snapshot owns the display.
            } catch (error: Exception) {
                runOnUiThread {
                    if (!destroyed && generation == probabilityGeneration) {
                        modeHint.text = "整局概率计算失败 · 下一张牌面概率仍可用"
                    }
                }
            }
        }
    }

    private fun setStatus(message: String, color: Int) {
        if (!::statusText.isInitialized) return
        if (message == lastStatusMessage && color == lastStatusColor) return
        lastStatusMessage = message
        lastStatusColor = color
        statusText.text = message
        statusDot.setTextColor(color)
    }

    private fun text(value: String, size: Float, color: Int, gravityValue: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = gravityValue
        includeFontPadding = false
        setPadding(dp(4), dp(2), dp(4), dp(2))
    }

    private fun groupText(label: String, color: Int) = text(label, 17f, color, Gravity.CENTER).apply {
        setBackgroundColor(Color.argb(160, 0, 0, 0))
    }

    private fun button(label: String, action: (() -> Unit)? = null) = Button(this).apply {
        text = label
        textSize = 14f
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.argb(210, 28, 28, 28))
        setPadding(dp(3), 0, dp(3), 0)
        action?.let { setOnClickListener { it() } }
    }

    private fun weighted(height: Int) = LinearLayout.LayoutParams(0, height, 1f).apply {
        marginStart = dp(2)
        marginEnd = dp(2)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        destroyed = true
        detectionGeneration++
        probabilityGeneration++
        probabilityTask?.cancel(true)
        probabilityExecutor.shutdownNow()
        cameraProvider?.unbindAll()
        analyzer.close()
        cameraExecutor.shutdown()
        database.close()
        super.onDestroy()
    }

    private companion object {
        const val COLOR_READY = 0xFF66D19E.toInt()
        const val COLOR_CONFIRMED = 0xFF55D6E8.toInt()
        const val COLOR_ERROR = 0xFFFF665E.toInt()
        const val COLOR_PAUSED = 0xFF8A8A8A.toInt()
        const val COLOR_MUTED = 0xFFB5B5B5.toInt()
        const val COLOR_LOW = 0xFF55D6E8.toInt()
        const val COLOR_NEUTRAL = 0xFFF5C84C.toInt()
        const val COLOR_TEN_VALUE = 0xFFFF6B5E.toInt()
        const val COLOR_ACE = 0xFFF5C84C.toInt()
    }
}

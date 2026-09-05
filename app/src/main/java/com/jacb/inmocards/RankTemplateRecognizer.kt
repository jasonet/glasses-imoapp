package com.jacb.inmocards

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class RankTemplateRecognizer {
    private data class Component(
        var left: Int,
        var top: Int,
        var right: Int,
        var bottom: Int,
        var area: Int
    ) {
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f
        val width: Int get() = right - left + 1
        val height: Int get() = bottom - top + 1
    }

    private data class Template(val rank: CardRank, val pixels: BooleanArray)

    private val templates: List<Template> = buildTemplates()

    fun recognize(bitmap: Bitmap): CardRank? {
        val glyph = extractGlyph(bitmap, requireBrightCard = true) ?: return null
        var bestRank: CardRank? = null
        var bestScore = 0.0
        templates.forEach { template ->
            val score = similarity(glyph, template.pixels)
            if (score > bestScore) {
                bestScore = score
                bestRank = template.rank
            }
        }
        return bestRank.takeIf { bestScore >= MIN_SCORE }
    }

    private fun buildTemplates(): List<Template> {
        val typefaces = listOf(
            Typeface.create(Typeface.SERIF, Typeface.BOLD),
            Typeface.create(Typeface.DEFAULT, Typeface.BOLD),
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        )
        return buildList {
            CardRank.entries.forEach { rank ->
                typefaces.forEach { typeface ->
                    val bitmap = Bitmap.createBitmap(180, 180, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.BLACK
                        textSize = if (rank == CardRank.TEN) 112f else 132f
                        this.typeface = typeface
                        textAlign = Paint.Align.CENTER
                    }
                    val baseline = 90f - (paint.ascent() + paint.descent()) / 2f
                    canvas.drawText(rank.label, 90f, baseline, paint)
                    extractGlyph(bitmap, requireBrightCard = false)?.let { add(Template(rank, it)) }
                    bitmap.recycle()
                }
            }
        }
    }

    private fun extractGlyph(bitmap: Bitmap, requireBrightCard: Boolean): BooleanArray? {
        val scaled = Bitmap.createScaledBitmap(bitmap, ANALYSIS_WIDTH, ANALYSIS_HEIGHT, true)
        val pixels = IntArray(ANALYSIS_WIDTH * ANALYSIS_HEIGHT)
        scaled.getPixels(pixels, 0, ANALYSIS_WIDTH, 0, 0, ANALYSIS_WIDTH, ANALYSIS_HEIGHT)
        if (scaled !== bitmap) scaled.recycle()

        val luminance = IntArray(pixels.size)
        var sum = 0L
        var bright = 0
        pixels.forEachIndexed { index, color ->
            val value = ((Color.red(color) * 77 + Color.green(color) * 150 + Color.blue(color) * 29) shr 8)
            luminance[index] = value
            sum += value
            if (value > 175) bright++
        }
        val mean = sum.toDouble() / pixels.size
        if (requireBrightCard && (mean < 125 || bright < pixels.size * 0.42)) return null

        val threshold = otsuThreshold(luminance).coerceIn(65, 185)
        val dark = BooleanArray(pixels.size) { luminance[it] < threshold }
        val components = components(dark).filter {
            it.area >= 18 && it.width >= 2 && it.height >= 5 &&
                it.width < ANALYSIS_WIDTH * 0.85 && it.height < ANALYSIS_HEIGHT * 0.90
        }
        if (components.isEmpty()) return null

        val centerX = ANALYSIS_WIDTH / 2f
        val centerY = ANALYSIS_HEIGHT / 2f
        val primary = components.minByOrNull {
            abs(it.centerX - centerX) + abs(it.centerY - centerY) - min(it.area, 500) * 0.025f
        } ?: return null
        val selected = components.filter { component ->
            val verticalOverlap = min(primary.bottom, component.bottom) - max(primary.top, component.top) + 1
            val overlapRatio = verticalOverlap.toFloat() / min(primary.height, component.height).coerceAtLeast(1)
            val horizontalGap = max(0, max(primary.left, component.left) - min(primary.right, component.right))
            component == primary || (overlapRatio > 0.45f && horizontalGap < ANALYSIS_WIDTH * 0.22f)
        }

        val left = selected.minOf { it.left }
        val top = selected.minOf { it.top }
        val right = selected.maxOf { it.right }
        val bottom = selected.maxOf { it.bottom }
        val sourceWidth = right - left + 1
        val sourceHeight = bottom - top + 1
        if (sourceWidth < 3 || sourceHeight < 8) return null

        val normalized = BooleanArray(TEMPLATE_WIDTH * TEMPLATE_HEIGHT)
        val scale = min(
            (TEMPLATE_WIDTH - 6).toFloat() / sourceWidth,
            (TEMPLATE_HEIGHT - 6).toFloat() / sourceHeight
        )
        val outWidth = (sourceWidth * scale).toInt().coerceAtLeast(1)
        val outHeight = (sourceHeight * scale).toInt().coerceAtLeast(1)
        val offsetX = (TEMPLATE_WIDTH - outWidth) / 2
        val offsetY = (TEMPLATE_HEIGHT - outHeight) / 2
        for (y in 0 until outHeight) {
            val sourceY = top + (y.toFloat() / outHeight * sourceHeight).toInt().coerceAtMost(sourceHeight - 1)
            for (x in 0 until outWidth) {
                val sourceX = left + (x.toFloat() / outWidth * sourceWidth).toInt().coerceAtMost(sourceWidth - 1)
                if (dark[sourceY * ANALYSIS_WIDTH + sourceX]) {
                    normalized[(offsetY + y) * TEMPLATE_WIDTH + offsetX + x] = true
                }
            }
        }
        val darkCount = normalized.count { it }
        return normalized.takeIf { darkCount in 35..(normalized.size * 0.58).toInt() }
    }

    private fun components(binary: BooleanArray): List<Component> {
        val visited = BooleanArray(binary.size)
        val queue = IntArray(binary.size)
        val result = mutableListOf<Component>()
        for (start in binary.indices) {
            if (!binary[start] || visited[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var left = start % ANALYSIS_WIDTH
            var right = left
            var top = start / ANALYSIS_WIDTH
            var bottom = top
            var area = 0
            while (head < tail) {
                val index = queue[head++]
                val x = index % ANALYSIS_WIDTH
                val y = index / ANALYSIS_WIDTH
                left = min(left, x)
                right = max(right, x)
                top = min(top, y)
                bottom = max(bottom, y)
                area++
                fun add(nx: Int, ny: Int) {
                    if (nx !in 0 until ANALYSIS_WIDTH || ny !in 0 until ANALYSIS_HEIGHT) return
                    val next = ny * ANALYSIS_WIDTH + nx
                    if (binary[next] && !visited[next]) {
                        visited[next] = true
                        queue[tail++] = next
                    }
                }
                add(x - 1, y)
                add(x + 1, y)
                add(x, y - 1)
                add(x, y + 1)
            }
            result += Component(left, top, right, bottom, area)
        }
        return result
    }

    private fun otsuThreshold(values: IntArray): Int {
        val histogram = IntArray(256)
        values.forEach { histogram[it]++ }
        val total = values.size
        var sum = 0L
        histogram.forEachIndexed { value, count -> sum += value.toLong() * count }
        var backgroundWeight = 0
        var backgroundSum = 0L
        var bestVariance = -1.0
        var bestThreshold = 128
        for (threshold in 0..255) {
            backgroundWeight += histogram[threshold]
            if (backgroundWeight == 0) continue
            val foregroundWeight = total - backgroundWeight
            if (foregroundWeight == 0) break
            backgroundSum += threshold.toLong() * histogram[threshold]
            val backgroundMean = backgroundSum.toDouble() / backgroundWeight
            val foregroundMean = (sum - backgroundSum).toDouble() / foregroundWeight
            val variance = backgroundWeight.toDouble() * foregroundWeight *
                (backgroundMean - foregroundMean) * (backgroundMean - foregroundMean)
            if (variance > bestVariance) {
                bestVariance = variance
                bestThreshold = threshold
            }
        }
        return bestThreshold
    }

    private fun similarity(a: BooleanArray, b: BooleanArray): Double {
        val dilatedA = dilate(a)
        val dilatedB = dilate(b)
        var aCount = 0
        var bCount = 0
        var aCovered = 0
        var bCovered = 0
        for (index in a.indices) {
            if (a[index]) {
                aCount++
                if (dilatedB[index]) aCovered++
            }
            if (b[index]) {
                bCount++
                if (dilatedA[index]) bCovered++
            }
        }
        if (aCount == 0 || bCount == 0) return 0.0
        return (aCovered.toDouble() / aCount + bCovered.toDouble() / bCount) / 2.0
    }

    private fun dilate(source: BooleanArray): BooleanArray {
        val output = BooleanArray(source.size)
        for (y in 0 until TEMPLATE_HEIGHT) for (x in 0 until TEMPLATE_WIDTH) {
            if (!source[y * TEMPLATE_WIDTH + x]) continue
            for (dy in -2..2) for (dx in -2..2) {
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until TEMPLATE_WIDTH && ny in 0 until TEMPLATE_HEIGHT) {
                    output[ny * TEMPLATE_WIDTH + nx] = true
                }
            }
        }
        return output
    }

    private companion object {
        const val ANALYSIS_WIDTH = 120
        const val ANALYSIS_HEIGHT = 120
        const val TEMPLATE_WIDTH = 48
        const val TEMPLATE_HEIGHT = 64
        const val MIN_SCORE = 0.44
    }
}

package com.jacb.inmocards

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

class DetectionGuideView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 245, 200, 76)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rect = RectF(width * 0.35f, height * 0.25f, width * 0.65f, height * 0.75f)
        canvas.drawRoundRect(rect, 18f, 18f, paint)
    }
}

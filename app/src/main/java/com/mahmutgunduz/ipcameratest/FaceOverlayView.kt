package com.mahmutgunduz.ipcameratest

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class FaceOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private var faces: List<Rect> = emptyList()

    // Gelen video görüntüsünün gerçek boyutları
    private var sourceWidth: Int = 0
    private var sourceHeight: Int = 0

    // Yüzleri ve video boyutunu buraya gönderiyoruz
    fun setFaces(faceRects: List<Rect>, videoWidth: Int, videoHeight: Int) {
        faces = faceRects
        sourceWidth = videoWidth
        sourceHeight = videoHeight
        invalidate() // Yeniden çiz
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (faces.isEmpty() || sourceWidth == 0 || sourceHeight == 0) return

        // 📐 MATEMATİK ZAMANI: Ölçekleme Çarpanlarını Hesapla
        // Ekran Genişliği / Video Genişliği = Yatay Büyüme Oranı
        val scaleX = width.toFloat() / sourceWidth
        val scaleY = height.toFloat() / sourceHeight

        for (rect in faces) {
            // Yapay zekanın verdiği koordinatları ekran boyutuna göre büyüt
            val left = rect.left * scaleX
            val top = rect.top * scaleY
            val right = rect.right * scaleX
            val bottom = rect.bottom * scaleY

            // Yeni hesaplanan kutuyu çiz
            val scaledRect = RectF(left, top, right, bottom)
            canvas.drawRect(scaledRect, paint)
        }
    }
}
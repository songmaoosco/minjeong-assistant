package com.minjeong.assistant

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * 오로라 배경 효과를 그리는 커스텀 뷰.
 * 여러 개의 원형 그라데이션을 서로 다른 속도로 움직여서 빛나는 느낌을 준다.
 */
class AuroraView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var progress = 0f

    private val colors = intArrayOf(
        Color.parseColor("#8A2BE2"), // 보라
        Color.parseColor("#00CED1"), // 시안
        Color.parseColor("#FF69B4"), // 핑크
        Color.parseColor("#00FA9A"), // 민트
        Color.parseColor("#4169E1")  // 로얄블루
    )

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 12000
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        canvas.drawColor(Color.parseColor("#0A0A1A"))

        colors.forEachIndexed { i, color ->
            val phase = (progress + i * 0.2f) % 1f
            val angle = phase * 2 * Math.PI

            val cx = (w * 0.5f) + (w * 0.35f * Math.cos(angle + i).toFloat())
            val cy = (h * 0.5f) + (h * 0.3f * Math.sin(angle * 1.3f + i).toFloat())
            val radius = w * 0.55f

            paint.shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(color, Color.TRANSPARENT),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            paint.alpha = 180
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }

    fun startAnimating() {
        if (!animator.isStarted) animator.start()
    }

    fun stopAnimating() {
        if (animator.isStarted) animator.cancel()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }
}
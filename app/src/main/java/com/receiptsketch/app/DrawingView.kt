package com.receiptsketch.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Tool { PEN, ERASER }

    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var currentTool = Tool.PEN
    private var lastX = 0f
    private var lastY = 0f
    private var activeStroke = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var strokeMoved = false

    private val penWidth = dp(6f)
    private val eraserWidth = dp(32f)

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        updatePaint()
    }

    fun usePen() {
        currentTool = Tool.PEN
        updatePaint()
    }

    fun useEraser() {
        currentTool = Tool.ERASER
        updatePaint()
    }

    fun clear() {
        bitmapCanvas?.drawColor(Color.WHITE)
        path.reset()
        activeStroke = false
        activePointerId = MotionEvent.INVALID_POINTER_ID
        invalidate()
    }

    fun hasDrawing(): Boolean {
        val source = bitmap ?: return false
        val row = IntArray(source.width)
        for (y in 0 until source.height) {
            source.getPixels(row, 0, source.width, 0, y, source.width, 1)
            for (pixel in row) {
                if (Color.red(pixel) < 245 || Color.green(pixel) < 245 || Color.blue(pixel) < 245) {
                    return true
                }
            }
        }
        return false
    }

    fun bitmapCopy(): Bitmap {
        finishStroke()
        val source = bitmap
        if (source == null) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).also {
                it.eraseColor(Color.WHITE)
            }
        }
        return source.copy(Bitmap.Config.ARGB_8888, false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        val oldBitmap = bitmap
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.WHITE) }
        bitmapCanvas = Canvas(bitmap!!)
        if (oldBitmap != null && !oldBitmap.isRecycled) {
            bitmapCanvas?.drawBitmap(oldBitmap, 0f, 0f, null)
            oldBitmap.recycle()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        if (activeStroke) canvas.drawPath(path, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.getX(0)
                val y = event.getY(0)
                parent?.requestDisallowInterceptTouchEvent(true)
                path.reset()
                path.moveTo(x, y)
                lastX = x
                lastY = y
                activeStroke = true
                activePointerId = event.getPointerId(0)
                strokeMoved = false
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!activeStroke) return true
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) return true
                val x = event.getX(index)
                val y = event.getY(index)
                val dx = abs(x - lastX)
                val dy = abs(y - lastY)
                if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                    path.quadTo(lastX, lastY, (x + lastX) / 2f, (y + lastY) / 2f)
                    lastX = x
                    lastY = y
                    strokeMoved = true
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (activeStroke) {
                    val index = event.findPointerIndex(activePointerId).coerceAtLeast(0)
                    val x = event.getX(index)
                    val y = event.getY(index)
                    path.lineTo(x, y)
                    if (strokeMoved) {
                        finishStroke()
                    } else {
                        bitmapCanvas?.drawCircle(x, y, paint.strokeWidth / 2f, Paint(paint).apply {
                            style = Paint.Style.FILL
                        })
                        path.reset()
                        activeStroke = false
                        invalidate()
                    }
                }
                activePointerId = MotionEvent.INVALID_POINTER_ID
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val liftedIndex = event.actionIndex
                if (event.getPointerId(liftedIndex) == activePointerId) {
                    val x = event.getX(liftedIndex)
                    val y = event.getY(liftedIndex)
                    path.lineTo(x, y)
                    finishStroke()
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                path.reset()
                activeStroke = false
                activePointerId = MotionEvent.INVALID_POINTER_ID
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        bitmap?.recycle()
        bitmap = null
        bitmapCanvas = null
        super.onDetachedFromWindow()
    }

    private fun finishStroke() {
        if (!activeStroke) return
        bitmapCanvas?.drawPath(path, paint)
        path.reset()
        activeStroke = false
        invalidate()
    }

    private fun updatePaint() {
        if (currentTool == Tool.PEN) {
            paint.color = Color.BLACK
            paint.strokeWidth = penWidth
        } else {
            paint.color = Color.WHITE
            paint.strokeWidth = eraserWidth
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private companion object {
        const val TOUCH_TOLERANCE = 2f
    }
}

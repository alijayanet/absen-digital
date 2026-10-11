package com.alijaya.smartschool.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object ImageLoader {
    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val memoryCache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    fun load(url: String?, imageView: ImageView, fallbackResId: Int? = null, circleCrop: Boolean = false) {
        if (url.isNullOrBlank()) {
            fallbackResId?.let { imageView.setImageResource(it) }
            return
        }

        val cached = memoryCache.get(url)
        if (cached != null) {
            imageView.setImageBitmap(cached)
            return
        }

        fallbackResId?.let { imageView.setImageResource(it) }
        imageView.tag = url

        executor.execute {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            if (circleCrop) {
                                bitmap = getCircularBitmap(bitmap)
                            }
                            memoryCache.put(url, bitmap)
                            mainHandler.post {
                                if (imageView.tag == url) {
                                    imageView.setImageBitmap(bitmap)
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Silently fallback to placeholder
            }
        }
    }

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = Math.min(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val color = -0xbdbdbe
        val paint = Paint().apply {
            isAntiAlias = true
            this.color = color
        }

        val rect = Rect((bitmap.width - size) / 2, (bitmap.height - size) / 2, (bitmap.width + size) / 2, (bitmap.height + size) / 2)
        val destRect = Rect(0, 0, size, size)

        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, destRect, paint)

        return output
    }
}

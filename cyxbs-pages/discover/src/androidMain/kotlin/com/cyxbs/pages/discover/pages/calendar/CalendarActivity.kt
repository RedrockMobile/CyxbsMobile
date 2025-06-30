package com.cyxbs.pages.discover.pages.calendar

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.bumptech.glide.request.transition.Transition
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.config.route.DISCOVER_CALENDAR
import com.cyxbs.components.utils.coroutine.appCoroutineScope
import com.cyxbs.components.utils.extensions.isDarkMode
import com.cyxbs.components.utils.logger.TrackingUtils
import com.cyxbs.components.utils.logger.event.NewClickEvent
import com.cyxbs.components.utils.network.getBaseUrl
import com.cyxbs.pages.discover.R
import com.g985892345.provider.api.annotation.KClassProvider
import kotlinx.coroutines.launch

@KClassProvider(clazz = Activity::class, name = DISCOVER_CALENDAR)
class CalendarActivity : BaseActivity() {

    private val mProgressBar by R.id.calendar_progress_bar.view<ProgressBar>()
    private val mImageView by R.id.iv_calendar.view<ImageView>()
    private val mImg404 by R.id.calendar_image_view.view<ImageView>()
    private val mTv404 by R.id.calendar_text_view_tip.view<TextView>()

    private var enterTime: Long = 0

    override fun onResume() {
        super.onResume()
        enterTime = System.currentTimeMillis()
        appCoroutineScope.launch {
            TrackingUtils.trackExposureEvent(NewClickEvent.EXPOSURE_MOBILE_ZSCY_XL_HOMEPAGE)
        }
    }

    override fun onStop() {
        super.onStop()
        val exitTime = System.currentTimeMillis()
        val duration = exitTime - enterTime
        appCoroutineScope.launch {
            TrackingUtils.trackStayEvent(NewClickEvent.TIME_MOBILE_ZSCY_XL,duration,enterTime)
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.discover_calendar_activity)
        val request = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.DATA)
            .skipMemoryCache(true)
        Glide.with(this)
            .load("${getBaseUrl()}/magipoke-jwzx/schoolCalendar")
            .apply(request)
            .into(object : DrawableImageViewTarget(mImageView) {
                override fun onResourceReady(
                    resource: Drawable,
                    transition: Transition<in Drawable>?
                ) {
                    mProgressBar.visibility = View.GONE
                    val width: Int = resource.intrinsicWidth
                    val height: Int = resource.intrinsicHeight
                    var ivWidth: Int = mImageView.width
                    if (ivWidth == 0) {
                        ivWidth = mImageView.resources.displayMetrics.widthPixels
                    }
                    val ivHeight = (height / width * ivWidth)
                    val lp: ViewGroup.LayoutParams = mImageView.layoutParams
                    lp.height = ivHeight
                    mImageView.layoutParams = lp
                    val bitmap = Bitmap.createBitmap((resource as BitmapDrawable).bitmap)
                    if (isDarkMode()) {
                        convertColor(bitmap, resource)
                    }
                    mImageView.setImageBitmap(bitmap)
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    super.onLoadFailed(errorDrawable)
                    mProgressBar.visibility = View.GONE
                    mImg404.visibility = View.VISIBLE
                    mTv404.visibility = View.VISIBLE
                }
            })
    }


    private fun convertColor(bitmap: Bitmap, resource: Drawable) {
        val canvas = Canvas(bitmap)
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        colorMatrix.preConcat(
            ColorMatrix(
                floatArrayOf(
                    0.6333f, 0.0333f, 0.3333f, 0f, 0f,
                    0.6333f, 0.0333f, 0.3333f, 0f, 0f,
                    0.6333f, 0.0333f, 0.3333f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(colorMatrix) }
        canvas.drawBitmap((resource as BitmapDrawable).bitmap, 0f, 0f, paint)
    }
}
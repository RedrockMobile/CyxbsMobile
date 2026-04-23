package com.cyxbs.pages.food.ui.view

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import com.cyxbs.components.utils.extensions.dp2px
import com.cyxbs.components.utils.extensions.setImageFromUrl
import com.cyxbs.pages.food.R

/**
 * Create by bangbangp on 2023/3/15 16:00
 * Email:1678921845@qq.com
 * Description:
 */
class FoodDetailDialog private constructor(
    context: Context,
    val positiveClick: (FoodDetailDialog.() -> Unit)? = null,
    val negativeClick: (FoodDetailDialog.() -> Unit)? = null,
    val data: Data
) :Dialog(context){

    class Builder(
        val context: Context,
        var positiveClick: (FoodDetailDialog.() -> Unit)? = null,
        var negativeClick: (FoodDetailDialog.() -> Unit)? = null,
        val data: Data,
    ){
        fun build(): FoodDetailDialog {
            return FoodDetailDialog(
                context,
                positiveClick,
                negativeClick,
                data
            )
        }

        fun setNegativeClick(click:FoodDetailDialog.() -> Unit):Builder{
            negativeClick = click
            return this
        }

        fun setPositiveClick(click:FoodDetailDialog.() -> Unit):Builder{
            positiveClick = click
            return this
        }

    }

    /**
     * @param content dialog 的文本内容
     * @param foodName 食物的名称
     * @param imageUrl 食物图片的url
     * @param praiseNum 食物的点赞数
     * @param width dialog 的宽
     * @param height dialog 的高
     */
    open class Data(
        val content: String = "弹窗内容为空",
        val foodName: String = "",
        val imageUrl: String = "",
        val praiseNum:Int = 0,
        @param:DrawableRes
        val positiveButtonBackground:Int = 0,
        val praiseIs:Boolean ,
        val width:Int = 0,
        val height:Int = 0
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        // 取消 dialog 默认背景
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        val view = LayoutInflater.from(context).inflate(R.layout.food_food_detail_dialog, null)
        view.findViewById<ImageView>(R.id.food_dialog_img_food).apply {
            setImageFromUrl(data.imageUrl)
        }
        view.findViewById<TextView>(R.id.food_dialog_detail_tv_theme).text = data.foodName
        view.findViewById<TextView>(R.id.food_dialog_detail_tv_content).text = data.content
        view.findViewById<TextView>(R.id.food_dialog_tv_praise_num).text = data.praiseNum.toString()
        view.findViewById<Button>(R.id.food_dialog_detail_btn_positive)
            .run {
                setOnClickListener {
                    positiveClick?.invoke(this@FoodDetailDialog)
                }
                background =  AppCompatResources.getDrawable(
                    context,
                    data.positiveButtonBackground
                )
                text = if (data.praiseIs) "已点赞" else "点赞"
            }
        view.findViewById<Button>(R.id.food_dialog_detail_btn_negative).setOnClickListener {
            negativeClick?.invoke(this)
        }
        setContentView(
            view,
            ViewGroup.LayoutParams(
                data.width.let { if (it > 0) it.dp2px else it },
                data.height.let { if (it > 0) it.dp2px else it }
            )
        )
    }

}
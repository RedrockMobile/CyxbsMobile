package com.cyxbs.pages.electricity.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.edit
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import com.aigestudio.wheelpicker.WheelPicker
import com.cyxbs.components.config.sp.defaultSp
import com.cyxbs.components.utils.extensions.setOnSingleClickListener
import com.cyxbs.pages.electricity.R
import com.cyxbs.pages.electricity.config.BUILDING_NAMES
import com.cyxbs.pages.electricity.config.BUILDING_NAMES_HEADER
import com.cyxbs.pages.electricity.config.SP_BUILDING_FOOT_KEY
import com.cyxbs.pages.electricity.config.SP_BUILDING_HEAD_KEY
import com.cyxbs.pages.electricity.config.SP_ROOM_KEY


class ElectricityFeedSettingDialogFragment : DialogFragment() {
    private lateinit var et_electricity_room_num:EditText
    private lateinit var wp_dormitory_head:WheelPicker
    private lateinit var wp_dormitory_foot:WheelPicker
    private lateinit var btn_dialog_dormitory_confirm:AppCompatButton
    private lateinit var tv_dormitory_num:TextView


    var refresher: ((id: String, room: String) -> Unit)? = null
    private val buildingNames by lazy(LazyThreadSafetyMode.NONE) { BUILDING_NAMES }
    private val buildingHeadNames by lazy(LazyThreadSafetyMode.NONE) { BUILDING_NAMES_HEADER }

    private var selectBuildingHeadPosition = 0
    private var selectBuildingFootPosition = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setStyle(STYLE_NO_TITLE, android.R.style.Theme_Material_Dialog_MinWidth)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        return inflater.inflate(R.layout.electricity_dialog_dormitory_select, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        var room = defaultSp.getString(SP_ROOM_KEY, "101") ?: "101"
        selectBuildingHeadPosition = defaultSp.getInt(SP_BUILDING_HEAD_KEY, 0)
        selectBuildingFootPosition = defaultSp.getInt(SP_BUILDING_FOOT_KEY, 0)
        et_electricity_room_num = view.findViewById(R.id.et_electricity_room_num)
        wp_dormitory_head = view.findViewById(R.id.wp_dormitory_head)
        wp_dormitory_foot = view.findViewById(R.id.wp_dormitory_foot)
        btn_dialog_dormitory_confirm = view.findViewById(R.id.btn_dialog_dormitory_confirm)
        tv_dormitory_num = view.findViewById(R.id.tv_dormitory_num)
        view.apply {
            et_electricity_room_num.apply {
                setText(room)
                text?.let {
                    setSelection(it.length)
                }

            }
            wp_dormitory_head.selectedItemPosition = selectBuildingHeadPosition
            wp_dormitory_foot.selectedItemPosition = selectBuildingFootPosition
            et_electricity_room_num.doOnTextChanged { text, _, _, _ -> room = text.toString() }
            wp_dormitory_head.setOnItemSelectedListener { _, data, _ ->
                wp_dormitory_foot.data = buildingNames[data]?.map { s -> s.replaceAfter("舍", "") }
                setCorrectBuildingNum()
            }

            if (selectBuildingHeadPosition != -1) {
                wp_dormitory_head.data = buildingHeadNames
                wp_dormitory_head.selectedItemPosition = selectBuildingHeadPosition
                wp_dormitory_foot.data = buildingNames[buildingHeadNames[selectBuildingHeadPosition]]?.map { s -> s.replaceAfter("舍", "") }
                wp_dormitory_foot.selectedItemPosition = selectBuildingFootPosition
                setCorrectBuildingNum()
            }
            wp_dormitory_foot.setOnItemSelectedListener { _, _, _ -> setCorrectBuildingNum() }

            btn_dialog_dormitory_confirm.setOnSingleClickListener {
                selectBuildingHeadPosition = wp_dormitory_head.currentItemPosition
                selectBuildingFootPosition = wp_dormitory_foot.currentItemPosition
                val id = BUILDING_NAMES.getValue(BUILDING_NAMES_HEADER[selectBuildingHeadPosition])[selectBuildingFootPosition].split("(")[1].split("栋")[0]

                refresher?.invoke(id, room)
                this@ElectricityFeedSettingDialogFragment.dismiss()

                defaultSp.edit {
                    putInt(SP_BUILDING_HEAD_KEY, selectBuildingHeadPosition)
                    putInt(SP_BUILDING_FOOT_KEY, selectBuildingFootPosition)
                    putString(SP_ROOM_KEY, et_electricity_room_num.text.toString())
                }
            }
        }
        isCancelable = true

    }

    private fun setCorrectBuildingNum() {
        //防止未停止就确定导致空指针
        selectBuildingHeadPosition = wp_dormitory_head.currentItemPosition
        selectBuildingFootPosition = wp_dormitory_foot.currentItemPosition
        tv_dormitory_num.text = buildingNames[buildingHeadNames[selectBuildingHeadPosition]]?.get(selectBuildingFootPosition)?.substringAfter("(")?.replace(")", "")
    }

}
package com.cyxbs.pages.ufield.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItems
import com.bigkoo.pickerview.builder.OptionsPickerBuilder
import com.bigkoo.pickerview.builder.TimePickerBuilder
import com.bigkoo.pickerview.view.OptionsPickerView
import com.bigkoo.pickerview.view.TimePickerView
import com.cyxbs.components.base.ui.BaseActivity
import com.cyxbs.components.utils.extensions.gone
import com.cyxbs.pages.ufield.R
import com.cyxbs.pages.ufield.viewmodel.CreateViewModel
import com.yalantis.ucrop.UCrop
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


class CreateActivity : BaseActivity() {

    private val ivBack by R.id.ufield_iv_back.view<ImageView>()
    private val etIntroduce by R.id.et_introduce.view<EditText>()
    private val coverImage by R.id.iv_cover.view<ImageView>()
    private val coverText by R.id.tv_cover.view<TextView>()
    private val startText by R.id.tv_start.view<TextView>()
    private val endText by R.id.tv_end.view<TextView>()
    private val chooseText by R.id.tv_choose.view<TextView>()
    private val etName by R.id.ufield_et_name.view<EditText>()
    private val etAddress by R.id.ufield_et_address.view<EditText>()
    private val etWay by R.id.ufield_et_way.view<EditText>()
    private val etSponsor by R.id.ufield_et_sponsor.view<EditText>()
    private val etPhone by R.id.ufield_et_phone.view<EditText>()
    private val btCreate by R.id.bt_create.view<Button>()

    private val viewModel by lazy { ViewModelProvider(this)[CreateViewModel::class.java] }

    private lateinit var coverFile: File
    private var isCovered = false

    private lateinit var pvtype: OptionsPickerView<String>
    private var index = 0
    private var isChanged = false
    private var selectedType: String = ""
    private val typeList = mutableListOf("文娱活动", "体育活动", "教育活动")

    private lateinit var pvtime: TimePickerView
    private var selectedStartTimestamp: Long = System.currentTimeMillis() / 1000
    private var selectedEndTimestamp: Long = System.currentTimeMillis() / 1000


    private val watcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {

        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            check()
        }

    }
    private val phoneWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {

        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        @SuppressLint("ResourceAsColor")
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            val text = s.toString()
            val phoneColor = ContextCompat.getColor(this@CreateActivity, R.color.uField_text_phone)
            val editColor = ContextCompat.getColor(this@CreateActivity, com.cyxbs.components.config.R.color.config_level_three_font_color)

            // 只允许输入数字
            if (text.matches(Regex("\\d*"))) {
                // 字符串中的纯数字
                val digits = text.filter { it.isDigit() }

                if (digits.length < 11) {
                    btCreate.setBackgroundResource(R.drawable.ufield_shape_createbutton)
                    btCreate.setOnClickListener(null)
                    etPhone.setTextColor(phoneColor)
                } else {
                    etPhone.setTextColor(editColor)
                    check()
                }
            } else {
                etPhone.text = null
            }
        }
    }

    @SuppressLint("RestrictedApi", "SuspiciousIndentation")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_createactivity)
        initView()
        initListener()
        coverImage.setOnClickListener {
            getPhoto()
        }
        viewModel.publishStatus.observe(this) {
            if (it) {
                btCreate.gone()
                toast("提交成功")
            } else {
                check()
                toast("由什么不知名原因导致提交失败")
            }
        }
    }


    @SuppressLint("SetTextI18n")
    private fun initView() {

        ivBack.setOnClickListener {
            finishAfterTransition()
        }


        val hint = "关于活动的简介（不超过100个字）"

        val spannableStringBuilder = SpannableStringBuilder(hint)
        // 设置文字"（不超过100字）"的大小
        val limitSizeSpan = RelativeSizeSpan(0.8f)
        spannableStringBuilder.setSpan(
            limitSizeSpan,
            7,
            hint.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        etIntroduce.hint = spannableStringBuilder


        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = String.format("%02d", calendar.get(Calendar.MONTH) + 1)
        val day = String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH))
        val hour = String.format("%02d", calendar.get(Calendar.HOUR_OF_DAY))
        val minute = String.format("%02d", calendar.get(Calendar.MINUTE))

        startText.text = "${year}年${month}月${day}日${hour}点${minute}分"
        endText.text = "${year}年${month}月${day}日${hour}点${minute}分"
    }


    @SuppressLint("CheckResult")
    fun getPhoto() {
        MaterialDialog(this).show {
            listItems(items = listOf("拍照", "从相册中选择")) { _, index, _ ->
                if (index == 0) {
                    takePhoto()
                } else {
                    getPhotoInPhotoAlbum.launch("image/*")
                }
            }

            cornerRadius(16F)
        }
    }


    private val getPhotoInPhotoAlbum =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                try {
                    contentResolver.openInputStream(uri)?.use { _ ->
                        startCropActivity(uri)
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }


    private lateinit var photoUri: Uri

    // 定义所需的权限和请求码
    private val permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
    private val permissionsRequestCode = 100
    private val takePhotoResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                startCropActivity(photoUri)
            } else {
                // 拍照取消或失败
                toast("拍照取消或失败")
            }
        }


    private fun takePhoto() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // 请求权限
            ActivityCompat.requestPermissions(this, permissions, permissionsRequestCode)
        } else {
            // 已经拥有权限，直接启动相机
            launchCamera()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // 检查权限请求码
        if (requestCode == permissionsRequestCode) {
            // 检查权限授予结果
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已被授予，启动相机
                launchCamera()
            } else {
                // 权限被拒绝，显示一个提示或执行其他操作
                toast("需要相机和存储权限才能继续操作")
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            val resultUri = UCrop.getOutput(data!!)
            // 获取裁剪后图片的实际文件路径
            val imageFile = File(resultUri!!.path)

            // 获取实际图片路径
            val imagePath = imageFile.absolutePath

            coverFile = convertToPNG(imagePath)?.let { File(it) }!!
            isCovered = true
            coverImage.setImageURI(resultUri)
            coverText.gone()
        } else if (resultCode == UCrop.RESULT_ERROR) {
            toast("裁剪失败")
        }
    }

    private fun convertToPNG(filePath: String): String? {
        val originalFile = File(filePath)

        // 检查文件是否存在
        if (!originalFile.exists()) {
            return null
        }

        // 检查文件是否已经是 PNG 格式
        if (isPNGFile(originalFile)) {
            return filePath
        }

        // 加载原始图片文件为 Bitmap
        val originalBitmap = BitmapFactory.decodeFile(filePath)

        // 创建目标文件的路径
        val targetFilePath = originalFile.parent + File.separator + "converted.png"
        val targetFile = File(targetFilePath)

        try {
            // 创建目标 Bitmap，格式为 ARGB_8888
            val targetBitmap = Bitmap.createBitmap(
                originalBitmap.width,
                originalBitmap.height,
                Bitmap.Config.ARGB_8888
            )

            // 在目标 Bitmap 上绘制原始 Bitmap
            val canvas = Canvas(targetBitmap)

            canvas.drawBitmap(originalBitmap, 0f, 0f, null)

            // 将目标 Bitmap 保存为 PNG 文件
            val outputStream = FileOutputStream(targetFile)
            targetBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.close()

            // 回收原始 Bitmap 和目标 Bitmap 的资源
            originalBitmap.recycle()
            targetBitmap.recycle()

            return targetFilePath
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    private fun isPNGFile(file: File): Boolean {
        return file.extension.equals("png", true)
    }

    private fun launchCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoFile = createPhotoFile()
        photoUri = FileProvider.getUriForFile(
            this,
            applicationContext.packageName + ".fileProvider",
            photoFile
        )
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        takePhotoResultLauncher.launch(intent)
    }

    private fun createPhotoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm ss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun createDestinationFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmm ss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File(storageDir, "cropped_$timeStamp.jpg")
    }

    private fun startCropActivity(uri: Uri) {
        val destinationFile = createDestinationFile()
        val uCrop = UCrop.of(uri, Uri.fromFile(destinationFile))
        val options = UCrop.Options()
        options.setCropGridStrokeWidth(5)
        options.setCompressionFormat(Bitmap.CompressFormat.PNG)
        options.setCompressionQuality(100)
        options.setToolbarColor(
            ContextCompat.getColor(this, com.cyxbs.components.config.R.color.colorPrimaryDark)
        )
        uCrop.withOptions(options)
            .withAspectRatio(1F, 1F)
            .withMaxResultSize(convertDpToPx(106), convertDpToPx(106))
            .start(this)
    }

    private fun convertDpToPx(dp: Int): Int {
        val scale = resources.displayMetrics.density
        return (dp * scale + 0.5f).toInt()
    }

    private fun initListener() {
        initTextListener()
        chooseText.setOnClickListener {
            pvtype = OptionsPickerBuilder(this) { p1, _, _, _ ->
                index = p1
                isChanged = true
                chooseText.text = typeList[index]

                selectedType = when (typeList[index]) {
                    "文娱活动" -> "culture"
                    "体育活动" -> "sports"
                    "教育活动" -> "education"
                    else -> ""
                }
            }
                .setLayoutRes(R.layout.popup_activitytype_layout) {
                    it.findViewById<TextView>(R.id.ufield_tv_dialog_ensure).setOnClickListener {
                        pvtype.returnData()
                        pvtype.dismiss()
                    }
                    it.findViewById<TextView>(R.id.ufield_type_cancel).setOnClickListener {
                        pvtype.dismiss()
                    }
                }
                .setContentTextSize(16)
                .setLineSpacingMultiplier(2.5f)
                .setOutSideCancelable(false)
                .setSelectOptions(index)
                .build()

            pvtype.setPicker(typeList)
            val dialog = pvtype.dialog
            if (dialog != null) {
                val params = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
                )
                    .apply {
                        leftMargin = 0
                        rightMargin = 0
                    }
                pvtype.dialogContainerLayout.layoutParams = params

                val window = dialog.window
                window?.apply {
                    setWindowAnimations(com.bigkoo.pickerview.R.style.picker_view_slide_anim)
                    setGravity(Gravity.BOTTOM)
                    setDimAmount(0.3f)
                }
            }
            pvtype.show()
        }

        startText.setOnClickListener {

            val currentTime = Calendar.getInstance()
            val endTime = Calendar.getInstance().apply {
                add(Calendar.YEAR, 1)
            }
            pvtime = TimePickerBuilder(this) { date, _ ->
                selectedStartTimestamp = date.time / 1000
                startText.text = getDate(date)
            }
                .setLayoutRes(R.layout.popup_time_layout) {
                    it.findViewById<TextView>(R.id.mine_tv_dialog_ensure).setOnClickListener {
                        pvtime.returnData()
                        pvtime.dismiss()
                    }
                    it.findViewById<TextView>(R.id.ufield_time_cancel).setOnClickListener {
                        pvtime.dismiss()
                    }
                }
                .setOutSideCancelable(false)
                .setDate(currentTime)
                .setRangDate(currentTime, endTime)
                .setContentTextSize(18)
                .setLabel("", "月", "日", "点", "分", "")
                .setType(booleanArrayOf(true, true, true, true, true, false))
                .setLineSpacingMultiplier(2.0f)
                .build()

            val dialog = pvtime.dialog
            if (dialog != null) {
                val params = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
                )
                    .apply {
                        leftMargin = 0
                        rightMargin = 0
                    }
                pvtime.dialogContainerLayout.layoutParams = params

                val window = dialog.window
                window?.apply {
                    setWindowAnimations(com.bigkoo.pickerview.R.style.picker_view_slide_anim)
                    setGravity(Gravity.BOTTOM)
                    setDimAmount(0.3f)
                }
            }
            pvtime.show()
        }


        endText.setOnClickListener {
            val currentTime = Calendar.getInstance()
            val endTime = Calendar.getInstance().apply {
                add(Calendar.YEAR, 1)
            }
            pvtime = TimePickerBuilder(this) { date, _ ->
                selectedEndTimestamp = date.time / 1000
                endText.text = getDate(date)
            }
                .setLayoutRes(R.layout.popup_time_layout) {
                    it.findViewById<TextView>(R.id.mine_tv_dialog_ensure).setOnClickListener {
                        pvtime.returnData()
                        pvtime.dismiss()
                    }
                    it.findViewById<TextView>(R.id.ufield_time_cancel).setOnClickListener {
                        pvtime.dismiss()
                    }
                }
                .setOutSideCancelable(false)
                .setDate(currentTime)
                .setRangDate(currentTime, endTime)
                .setContentTextSize(18)
                .setLabel("", "月", "日", "点", "分", "")
                .setType(booleanArrayOf(true, true, true, true, true, false))
                .setLineSpacingMultiplier(2.0f)
                .build()

            val dialog = pvtime.dialog
            if (dialog != null) {
                val params = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
                )
                    .apply {
                        leftMargin = 0
                        rightMargin = 0
                    }
                pvtime.dialogContainerLayout.layoutParams = params

                val window = dialog.window
                window?.apply {
                    setWindowAnimations(com.bigkoo.pickerview.R.style.picker_view_slide_anim)
                    setGravity(Gravity.BOTTOM)
                    setDimAmount(0.3f)
                }
            }
            pvtime.show()
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun getDate(date: Date): String {
        val format = SimpleDateFormat("yyyy年MM月dd日HH点mm分")
        return format.format(date)
    }

    private fun initTextListener() {
        etSponsor.addTextChangedListener(watcher)
        etIntroduce.addTextChangedListener(watcher)
        etAddress.addTextChangedListener(watcher)
        etName.addTextChangedListener(watcher)
        etWay.addTextChangedListener(watcher)
        etPhone.addTextChangedListener(phoneWatcher)
        chooseText.addTextChangedListener(watcher)
        endText.addTextChangedListener(watcher)
        startText.addTextChangedListener(watcher)
    }

    fun check() {
        val name = etName.text.toString()
        val way = etWay.text.toString()
        val sponsor = etSponsor.text.toString()
        val address = etAddress.text.toString()
        val introduce = etIntroduce.text.toString()
        val phone = etPhone.text.toString()
        if(selectedEndTimestamp<selectedStartTimestamp) {
            btCreate.setBackgroundResource(R.drawable.ufield_shape_createbutton)
            btCreate.setOnClickListener(null)
            toast("结束时间要大于开始时间才能创建活动哟")
        }
        if (name.isNotEmpty() && way.isNotEmpty() && address.isNotEmpty() && introduce.isNotEmpty() && sponsor.isNotEmpty() && phone.length == 11 && isChanged) {
            if (selectedEndTimestamp > selectedStartTimestamp) {
                btCreate.apply {
                    setBackgroundResource(R.drawable.ufield_shape_createbutton2)
                    setOnClickListener {
                        if (isCovered) {
                            viewModel.postActivityWithCover(
                                getMap(
                                    name,
                                    selectedType,
                                    selectedStartTimestamp.toInt(),
                                    selectedEndTimestamp.toInt(),
                                    address,
                                    way,
                                    sponsor,
                                    phone,
                                    introduce
                                ),
                                coverFile
                            )
                            setOnClickListener(null)
                        } else {
                            viewModel.postActivityNotCover(
                                getMap(
                                    name,
                                    selectedType,
                                    selectedStartTimestamp.toInt(),
                                    selectedEndTimestamp.toInt(),
                                    address,
                                    way,
                                    sponsor,
                                    phone,
                                    introduce
                                )
                            )
                            setOnClickListener(null)

                        }
                    }
                }
            } else if(selectedEndTimestamp==selectedStartTimestamp) {
                btCreate.setBackgroundResource(R.drawable.ufield_shape_createbutton)
                btCreate.setOnClickListener(null)
                toast("结束时间可不能等于开始时间")
            }
        } else {
            btCreate.setBackgroundResource(R.drawable.ufield_shape_createbutton)
            btCreate.setOnClickListener(null)
        }
    }

    private fun getMap(
        activityTitle: String,
        activityType: String,
        activityStartAt: Int,
        activityEndAt: Int,
        activityPlace: String,
        activityRegistrationType: String,
        activityOrganizer: String,
        creatorPhone: String,
        activityDetail: String
    ): MutableMap<String, RequestBody> {
        val map = mutableMapOf<String, RequestBody>()
        map["activity_title"] =
            activityTitle.toRequestBody("multipart/form-data".toMediaTypeOrNull())
        map["activity_type"] = activityType.toRequestBody("multipart/form-data".toMediaTypeOrNull())
        map["activity_start_at"] =
            activityStartAt.toString().toRequestBody("multipart/form-data".toMediaTypeOrNull())
        map["activity_end_at"] =
            activityEndAt.toString().toRequestBody("multipart/form-data".toMediaTypeOrNull())
        map["activity_place"] =
            activityPlace.toRequestBody("multipart/form-data".toMediaTypeOrNull())
        map["activity_registration_type"] =
            activityRegistrationType.toRequestBody("multipart/form-data".toMediaTypeOrNull())
        map["activity_organizer"] =
            activityOrganizer.toRequestBody("multipart/form-data".toMediaTypeOrNull())
        map["creator_phone"] = creatorPhone.toRequestBody("multipart/form-data".toMediaTypeOrNull())
        map["activity_detail"] =
            activityDetail.toRequestBody("multipart/form-data".toMediaTypeOrNull())
        return map
    }

    //来自学长的代码，源代码在mine模块的资料编辑
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_UP) {
            val v = currentFocus

            //如果不是落在EditText区域，则需要关闭输入法
            if (hideKeyboard(v, ev)) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v?.windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
                v?.clearFocus()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    // 根据EditText所在坐标和用户点击的坐标相对比，来判断是否隐藏键盘
    private fun hideKeyboard(view: View?, event: MotionEvent): Boolean {
        if (view != null && view is EditText) {

            val location = intArrayOf(0, 0)
            view.getLocationInWindow(location)

            //获取现在拥有焦点的控件view的位置，即EditText
            val left = location[0]
            val top = location[1]
            val bottom = top + view.height
            val right = left + view.width
            //判断我们手指点击的区域是否落在EditText上面，如果不是，则返回true，否则返回false
            val isInEt = (event.x > left && event.x < right && event.y > top
                    && event.y < bottom)
            return !isInEt
        }
        return false
    }

}
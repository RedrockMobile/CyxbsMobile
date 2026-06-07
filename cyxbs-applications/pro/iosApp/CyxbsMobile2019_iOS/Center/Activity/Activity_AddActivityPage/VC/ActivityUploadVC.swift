//
//  ActivityAddViewController.swift
//  CyxbsMobile2019_iOS
//
//  Created by 许晋嘉 on 2023/8/21.
//  Copyright © 2023 Redrock. All rights reserved.
//

import UIKit
import TOCropViewController
import Alamofire

class ActivityUploadVC: UIViewController,
                     UIImagePickerControllerDelegate,
                     UINavigationControllerDelegate,
                     TOCropViewControllerDelegate,
                     ActivityTypePickerDelegate,
                     ActivityDatePickerDelegate {
    
    var imageData: Data?
    var activityType: String?
    var startTime: Date = Date()
    var endTime: Date = Date()
    let textLimitManager = TextManager.shared
    var isSetImage: Bool = false
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.dm_color(withLightColor: UIColor(hexString: "#F8F9FC"), darkColor: UIColor(hexString: "#F8F9FC"))
        view.addSubview(backButton)
        view.addSubview(scrollView)
        view.addSubview(confirmButton)
        setPosition()
        for textfield in scrollView.textFields {
            textfield.delegate = self
        }
        scrollView.detailTextView.delegate = self
    }
    
    // MARK: - 懒加载
    lazy var backButton: UIButton = {
        let button = UIButton()
        button.setImage(UIImage(named: "activityBack"), for: .normal)
        button.imageView?.contentMode = .scaleAspectFit
        button.imageEdgeInsets = UIEdgeInsets(top: 7, left: 1, bottom: 8, right: 22)
        button.addTarget(self, action: #selector(popController), for: .touchUpInside)
        return button
    }()
    
    lazy var scrollView: ActivityUploadScrollView = {
        let scrollView = ActivityUploadScrollView()
        let tapGesture = UITapGestureRecognizer(target: self, action: #selector(showImagePickerAlert))
        scrollView.coverImgView.addGestureRecognizer(tapGesture)
        scrollView.typeButton.addTarget(self, action: #selector(showTypePicker), for: .touchUpInside)
        let startTimeLabelTapGesture = UITapGestureRecognizer(target: self, action: #selector(setStartTime))
        scrollView.startTimeLabel.addGestureRecognizer(startTimeLabelTapGesture)
        scrollView.startTimeLabel.isUserInteractionEnabled = true
        scrollView.startTimeLabel.text = formatDateToCustomString(date: startTime)
        let endTimeLabelTapGesture = UITapGestureRecognizer(target: self, action: #selector(setEndTime))
        scrollView.endTimeLabel.addGestureRecognizer(endTimeLabelTapGesture)
        scrollView.endTimeLabel.isUserInteractionEnabled = true
        scrollView.endTimeLabel.text = formatDateToCustomString(date: endTime)
        scrollView.contentSize = CGSize(width: scrollView.frame.size.width, height: 696)
        //为textFiled和textView设定最大输入字数限制
        textLimitManager.setupLimitForTextField(scrollView.titleTextfield, maxLength: 12)
        textLimitManager.setupLimitForTextField(scrollView.placeTextfield, maxLength: 10)
        textLimitManager.setupLimitForTextField(scrollView.registrationTextfield, maxLength: 15)
        textLimitManager.setupLimitForTextField(scrollView.organizerTextfield, maxLength: 10)
        textLimitManager.setupLimitForTextField(scrollView.contactTextfield, maxLength: 11)
        textLimitManager.setupLimitForTextView(scrollView.detailTextView, maxLength: 100)
        return scrollView
    }()
    
    lazy var confirmButton: GradientButton = {
        let button = GradientButton()
        button.frame = CGRectMake((UIScreen.main.bounds.width - 315)/2, UIScreen.main.bounds.height - 86, 315, 51)
        button.setTitle("创建活动", for: .normal)
        button.titleLabel?.textColor = UIColor(red: 1, green: 1, blue: 1, alpha: 1)
        button.titleLabel?.font = UIFont(name: PingFangSCSemibold, size: 18)
        button.layer.cornerRadius = 25.5
        button.clipsToBounds = true
        button.addTarget(self, action: #selector(confirmButtonTapped), for: .touchUpInside)
        button.removeGradientBackground()
        button.backgroundColor = UIColor(red: 0.765, green: 0.831, blue: 0.933, alpha: 1)
        button.isEnabled = false
        return button
    }()
    
    @objc func confirmButtonTapped() {
        self.confirmButton.isEnabled = false // 防止重复点击
        if timeIntervalToNow(from: endTime) > 0 {
            if areTextFieldsEmpty(textFields: scrollView.textFields) || scrollView.detailTextView.text.isEmpty {
                print("活动信息有空的")
            } else {
                if let type = activityType {
                    HttpManager.shared.magipoke_ufield_activity_publish(activity_title: scrollView.titleTextfield.text!, activity_type: type, activity_start_at: dateToTimestamp(date: startTime), activity_end_at: dateToTimestamp(date: endTime), activity_place: scrollView.placeTextfield.text!, activity_registration_type: scrollView.registrationTextfield.text!, activity_organizer: scrollView.organizerTextfield.text!, creator_phone: scrollView.contactTextfield.text!, activity_detail: scrollView.detailTextView.text!, activity_cover_file: imageData).ry_JSON { response in
                        switch response {
                        case .success(let jsonData):
                            let uploadResponseData = StandardResponse(from: jsonData)
                            if uploadResponseData.status == 10000 {
                                ActivityHUD.shared.addProgressHUDView(width: 179,
                                                                            height: 36,
                                                                            text: "活动发布成功",
                                                                            font: UIFont(name: PingFangSCMedium, size: 13)!,
                                                                            textColor: .white,
                                                                            delay: 2,
                                                                            backGroundColor: UIColor(hexString: "#2a4e84"),
                                                                            cornerRadius: 18,
                                                                            yOffset: Float(-UIScreen.main.bounds.height * 0.5 + Constants.statusBarHeight) + 90) {
                                    self.popController()
                                }
                            }
                            break
                        case .failure(let error):
                            print(error)
                            ActivityHUD.shared.showNetworkError()
                            break
                        }
                    }
                } else { print("未选择活动类型")}
            }
        } else {
            ActivityHUD.shared.addProgressHUDView(width: 179,
                                                        height: 36,
                                                        text: "请设置有效的结束时间！",
                                                        font: UIFont(name: PingFangSCMedium, size: 13)!,
                                                        textColor: .white,
                                                        delay: 2,
                                                        backGroundColor: UIColor(hexString: "#2a4e84"),
                                                        cornerRadius: 18,
                                                        yOffset: Float(-UIScreen.main.bounds.height * 0.5 + Constants.statusBarHeight) + 90)
        }
        self.confirmButton.isEnabled = true
    }
    
    // MARK: - 设置子控件位置
    func setPosition() {
        self.backButton.snp.makeConstraints { make in
            make.leading.equalToSuperview().offset(16)
            make.top.equalToSuperview().offset(Constants.statusBarHeight + 6)
            make.width.equalTo(30)
            make.height.equalTo(31)
        }
        
        self.scrollView.snp.makeConstraints { make in
            make.top.equalTo(backButton.snp.bottom).offset(12)
            make.bottom.equalToSuperview().offset(-96)
            make.left.equalToSuperview()
            make.right.equalToSuperview()
        }
        
    }
    
    // MARK: - 返回上一页
    @objc func popController() {
        self.navigationController?.popViewController(animated: true)
    }
    
    //
    @objc func showImagePickerAlert(_ sender: Any) {
        let imagePicker = UIImagePickerController()
        imagePicker.delegate = self
        imagePicker.allowsEditing = false // 禁用系统自带编辑功能
        imagePicker.modalPresentationStyle = .fullScreen
        
        let alert = UIAlertController(title: "请选择照片来源", message: nil, preferredStyle: .actionSheet)
        
        let cameraAction = UIAlertAction(title: "相机", style: .default) { action in
            if UIImagePickerController.isSourceTypeAvailable(.camera) {
                imagePicker.sourceType = .camera
                imagePicker.cameraCaptureMode = .photo
                imagePicker.cameraDevice = .rear
                imagePicker.showsCameraControls = true
                self.present(imagePicker, animated: true, completion: nil)
            } else {
                print("相机不可用")
            }
        }
        
        let photoLibraryAction = UIAlertAction(title: "相册", style: .default) { action in
            imagePicker.sourceType = .photoLibrary
            self.present(imagePicker, animated: true, completion: nil)
        }
        
        let cancelAction = UIAlertAction(title: "取消", style: .cancel, handler: nil)
        
        alert.addAction(cameraAction)
        alert.addAction(photoLibraryAction)
        alert.addAction(cancelAction)
        
        if let popoverController = alert.popoverPresentationController {
            popoverController.sourceView = self.view
            popoverController.sourceRect = CGRect(x: self.view.bounds.midX, y: self.view.bounds.midY, width: 0, height: 0)
            popoverController.permittedArrowDirections = []
        }
        
        present(alert, animated: true, completion: nil)
    }
    
    // MARK: - UIImagePickerControllerDelegate
    
    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
        picker.dismiss(animated: true, completion: nil)
        if let pickedImage = info[UIImagePickerController.InfoKey.originalImage] as? UIImage {
            showCropViewController(image: pickedImage) // 直接调用 showCropViewController 方法
        }
    }
    
    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true, completion: nil)
    }
    
    // MARK: - TOCropViewControllerDelegate
    
    func cropViewController(_ cropViewController: TOCropViewController, didFinishCancelled cancelled: Bool) {
        cropViewController.dismiss(animated: true, completion: nil)
    }
    
    func cropViewController(_ cropViewController: TOCropViewController, didCropTo image: UIImage, with cropRect: CGRect, angle: Int) {
        cropViewController.dismiss(animated: true, completion: nil)
        scrollView.coverImgView.image = image
        isSetImage = true
        // 获取原始图片数据
        guard let originalData = image.pngData() else {
            return
        }
        let targetSizeInMB: Double = 1.0 // 目标大小（单位：MB）
        let targetSizeInBytes = Int(targetSizeInMB * 1024 * 1024)

        // 压缩图片数据
        var compressedData = originalData
        var dataSizeInBytes = compressedData.count

        while dataSizeInBytes > targetSizeInBytes {
            guard let compressedImage = UIImage(data: compressedData),
                  let resizedImage = compressedImage.resized(withPercentage: 0.9),
                  let resizedData = resizedImage.pngData() else {
                break
            }
            compressedData = resizedData
            dataSizeInBytes = compressedData.count
        }
        // 输出压缩后的图片大小
        let dataSizeInMB = Double(dataSizeInBytes) / (1024 * 1024)
        print(String(format: "图片大小为 %.2f MB", dataSizeInMB))

        // 使用压缩后的图片数据
        imageData = compressedData
    }
    
    // MARK: - 展示TOCropViewController裁剪图片
    @objc func showCropViewController(image: UIImage) {
        let cropViewController = TOCropViewController(image: image)
        cropViewController.delegate = self
        cropViewController.aspectRatioLockEnabled = true
        cropViewController.aspectRatioPreset = CGSize(width: 1.0, height: 1.0) // 设置裁剪比例为1:1
        cropViewController.resetAspectRatioEnabled = false // 禁用切换比例功能
        cropViewController.aspectRatioPickerButtonHidden = true // 隐藏比例选择按钮
        present(cropViewController, animated: true)
    }
    
    @objc func showTypePicker() {
        let pickerVC = ActivityTypePickerVC()
        pickerVC.delegate = self
        switch activityType {
        case "culture":
            pickerVC.activityType = .culture
        case "sports":
            pickerVC.activityType = .sports
        case "education":
            pickerVC.activityType = .education
        default:
            break
        }
        pickerVC.modalPresentationStyle = .overCurrentContext
        present(pickerVC, animated: false)
    }
    
    // MARK: - ActivityTypePickerDelegate
    func didSelectActivityType(_ type: String) {
        print("Selected activity type: \(type)")
        // Assign the selected activity type to the variable
        activityType = mapToActivityType(type)
        scrollView.typeButton.setTitle(type, for: .normal)
        scrollView.typeImgView.removeFromSuperview()
        scrollView.typeButton.titleEdgeInsets = UIEdgeInsets(top: 0, left: 0, bottom: 0, right: 0)
        shouldConfirmButtonEnabled()
    }
    
    func mapToActivityType(_ type: String) -> String {
        switch type {
        case "文娱活动":
            return "culture"
        case "体育活动":
            return "sports"
        case "教育活动":
            return "education"
        default:
            return ""
        }
    }
    
    @objc func setStartTime() {
        let dateVC = ActivityDatePickerVC()
        dateVC.minDate = startTime
        dateVC.modalPresentationStyle = .overCurrentContext
        dateVC.timeSelection = .startTime
        dateVC.delegate = self
        present(dateVC, animated: false)
    }
    
    @objc func setEndTime() {
        let dateVC = ActivityDatePickerVC()
        dateVC.minDate = endTime
        dateVC.modalPresentationStyle = .overCurrentContext
        dateVC.timeSelection = .endTime
        dateVC.delegate = self
        present(dateVC, animated: false)
    }
    
    func didSelectStartTime(date: Date) {
        startTime = date
        scrollView.startTimeLabel.text = formatDateToCustomString(date: startTime)
    }
    
    func didSelectEndTime(date: Date) {
        endTime = date
        scrollView.endTimeLabel.text = formatDateToCustomString(date: endTime)
    }
    
    // 使用中文环境
    func formatDateToCustomString(date: Date) -> String {
        let dateFormatter = DateFormatter()
        dateFormatter.locale = Locale(identifier: "zh_CN")
        dateFormatter.dateFormat = "yyyy年M月d日H点m分"
        return dateFormatter.string(from: date)
    }
    
    //同时判断多个UITextfield是否为空
    func areTextFieldsEmpty(textFields: [UITextField]) -> Bool {
        for textField in textFields {
            if let text = textField.text, !text.isEmpty {
                // TextField 不为空
            } else {
                // TextField 为空
                return true
            }
        }
        // 所有 TextField 都不为空
        return false
    }
    
    //将Date对象对应的时间转换为时间戳
    func dateToTimestamp(date: Date) -> Int {
        return Int(date.timeIntervalSince1970)
    }

    func shouldConfirmButtonEnabled() {
        if areTextFieldsEmpty(textFields: scrollView.textFields) || scrollView.detailTextView.text.isEmpty || activityType == nil || scrollView.contactTextfield.text?.count != 11 {
            confirmButton.isEnabled = false
            confirmButton.removeGradientBackground()
        } else {
            confirmButton.isEnabled = true
            confirmButton.setupGradient()
        }
    }

    func timeIntervalToNow(from date: Date) -> TimeInterval {
        let currentTime = Date()
        let timeInterval = date.timeIntervalSince(currentTime)
        return timeInterval
    }
    
}

extension ActivityUploadVC: UITextFieldDelegate {
    func textFieldDidChangeSelection(_ textField: UITextField) {
        shouldConfirmButtonEnabled()
        if scrollView.contactTextfield.text?.count == 11 {
            scrollView.contactTextfield.textColor = UIColor(red: 0.082, green: 0.192, blue: 0.357, alpha: 1)
        } else {
            scrollView.contactTextfield.textColor = .red
        }
    }
}

extension ActivityUploadVC: UITextViewDelegate {
    func textViewDidChange(_ textView: UITextView) {
        shouldConfirmButtonEnabled()
    }
}

extension UIImage {
    func resized(withPercentage percentage: CGFloat) -> UIImage? {
        let canvasSize = CGSize(width: size.width * percentage, height: size.height * percentage)
        UIGraphicsBeginImageContextWithOptions(canvasSize, false, scale)
        defer { UIGraphicsEndImageContext() }
        draw(in: CGRect(origin: .zero, size: canvasSize))
        return UIGraphicsGetImageFromCurrentImageContext()
    }
}




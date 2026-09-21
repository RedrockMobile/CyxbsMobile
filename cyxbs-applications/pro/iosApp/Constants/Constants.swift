//
//  RYConstants.swift
//  RYBaseKit
//
//  Created by SSR on 2023/10/23.
//

import Foundation

public var Constants = _constants.shared

public struct _constants {
    
    public static let shared = _constants()
    
    private init() { }
}

// MARK: Bundle.main.infoDictionary

public extension _constants {
    
    enum BundleInfoKey: String {
        
        case name = "CFBundleName" // 应用程序的名称
        
        case identifier = "CFBundleIdentifier" // 应用程序的唯一标识符
        
        case version = "CFBundleVersion" // 应用程序的版本号
        
        case shortVersionString = "CFBundleShortVersionString" // 应用程序的短版本号
        
        case executable = "CFBundleExecutable" // 应用程序的可执行文件的名称
        
        case developmentRegion = "CFBundleDevelopmentRegion" // 应用程序的开发区域设置
        
        case packageType = "CFBundlePackageType" // 应用程序的包类型
        
        case signature = "CFBundleSignature" // 应用程序的签名
        
        case infoDictionaryVersion = "CFBundleInfoDictionaryVersion" // 信息字典的版本
        
        case supportedPlatforms = "CFBundleSupportedPlatforms" // 应用程序支持的平台
        
        case applicationCategoryType = "LSApplicationCategoryType" // 应用程序的类别类型
        
        case requiredDeviceCapabilities = "UIRequiredDeviceCapabilities" // 应用程序所需的设备功能
        
        case supportedInterfaceOrientations = "UISupportedInterfaceOrientations" // 应用程序支持的界面方向
        
        case mainStoryboardFile = "NSMainStoryboardFile" // 应用程序的主故事板文件名
        
        case principalClass = "NSPrincipalClass" // 应用程序的主要类
    }
    
    func value(for key: BundleInfoKey) -> String? {
        Bundle.main.infoDictionary?[key.rawValue] as? String
    }
}

#if canImport(UIKit)
import UIKit

public extension _constants {
    
    var isPhone: Bool {
        UIDevice.current.userInterfaceIdiom == .phone
    }
    
    var isPad: Bool {
        UIDevice.current.userInterfaceIdiom == .pad
    }
    
    var systemName: String {
        UIDevice.current.systemName
    }
    
    var systemVersion: String {
        UIDevice.current.systemVersion
    }
}


#endif

// MARK: ObjectiveC

#if canImport(ObjectiveC)
import ObjectiveC

public extension _constants {
    
    enum AssociationPolicy: UInt {
    
        case assign = 0
        
        case retain_nonatomic = 1
        
        case copy_nonatomic = 3
        
        case retain = 769
        
        case copy = 771
    }
    
    func setAssociatedObject(_ object: Any, _ key: UnsafeRawPointer, _ value: Any?, _ policy: AssociationPolicy) {
        guard let objc_policy = objc_AssociationPolicy(rawValue: policy.rawValue) else { return }
        if #available(iOS 13.0, *) {
            withUnsafePointer(to: key) {
                objc_setAssociatedObject(self, $0, value, objc_policy)
            }
        } else {
            objc_setAssociatedObject(self, key, value, objc_policy)
        }
    }
    
    func getAssociatedObject(_ object: Any, _ key: UnsafeRawPointer) -> Any? {
        if #available(iOS 13.0, *) {
            return withUnsafePointer(to: key) {
                objc_getAssociatedObject(self, $0)
            }
        } else {
            return objc_getAssociatedObject(self, key)
        }
    }
}

#endif


// MARK: CYXBS

extension _constants {
    
    var cleanInNextVersion: Bool { true }
    
    var widgetGroupID: String {
        "group.com.mredrock.cyxbs.widget.3m9757c25s"
    }
}

//
//  APIConfig.swift
//  CyxbsMobile2019_iOS
//
//  Created by SSR on 2023/9/5.
//  Copyright © 2023 Redrock. All rights reserved.
//

import Foundation
import Alamofire
import SwiftyJSON

extension APIConfig {
    
    enum Environment {
        
        case BE_PROD         // 生产环境
        
        case BE_DEV          // 测试环境
        
        case CLOUD(String)   // 容灾环境
        
        var host: String {
            switch self {
            case .BE_PROD:
                return "be-prod.redrock.cqupt.edu.cn"
            case .BE_DEV:
                return "be-dev.redrock.cqupt.edu.cn"
            case .CLOUD(let base_url):
                return base_url
            }
        }
        
        var url: String {
            "https://\(host)"
        }
    }
}

class APIConfig {
    
    public static let current = APIConfig()
    
    private init() { }
    
    var ipToHost: [String: String] = [:]
    
    private(set) var environment: Environment = {
        #if DEBUG
        return .BE_DEV
        #else
        return .BE_PROD
        #endif
    }()

    /// 统一应用网络环境，并同步仍由 Objective-C 模块读取的 baseURL。
    /// 所有环境切换都必须经过此入口，避免新旧网络栈分别使用不同服务地址。
    func apply(_ environment: Environment) {
        self.environment = environment
        UserDefaults.standard.set(environment.url + "/", forKey: "baseURL")
    }
    
    static func askHost(success: @escaping (Environment) -> Void) {
        AF.request("https://be-prod.redrock.team/cloud-manager/check").ry_JSON { response in
            if case let .success(value) = response {
                if let base_url = value.dictionary?["base_url"]?.string {
                    success(.CLOUD(base_url))
                    return
                }
            }
            success(.BE_PROD)
        }
    }
}

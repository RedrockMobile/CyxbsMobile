//
//  SessionManager.swift
//  CyxbsMobile2019_iOS
//
//  Created by SSR on 2023/9/5.
//  Copyright © 2023 Redrock. All rights reserved.
//

import Alamofire
import SwiftyJSON

// MARK: TrustPlicyManager

class TrustPlicyManager: ServerTrustManager {
    
    class TrustEvaluator: ServerTrustEvaluating {
        
        public func evaluate(_ trust: SecTrust, forHost host: String) throws {
            if let hostName = APIConfig.current.ipToHost[host] {
                try trust.af.performValidation(forHost: hostName)
            } else {
                try trust.af.performDefaultValidation(forHost: host)
            }
        }
    }
    
    override func serverTrustEvaluator(forHost host: String) throws -> ServerTrustEvaluating? {
        return TrustEvaluator()
    }
}

// MARK: BaseConvertible

extension SessionManager {
    
    struct BaseConvertible: URLRequestConvertible, CustomStringConvertible {
        let url: URLConvertible
        let method: HTTPMethod
        let parameters: Parameters?
        let encoding: ParameterEncoding
        var headers: HTTPHeaders
        let requestModifier: RequestModifier?

        func asURLRequest() throws -> URLRequest {
            var request = try URLRequest(url: url, method: method, headers: headers)
            try requestModifier?(&request)

            return try encoding.encode(request, with: parameters)
        }
        
        var description: String {
            let url = try? url.asURL()
            return """
                   [\(method.rawValue)] \(url?.absoluteString ?? "未知URL")
                   \t [headers] \(headers)
                   \t [parameters] \(parameters ?? [:])
                   """
        }
    }
}

// MARK: SessionManager

class SessionManager: Session {
    
    static let shared = SessionManager(serverTrustManager: TrustPlicyManager(allHostsMustBeEvaluated: true, evaluators: [:]))
    
    /// 不直接读取 `UserModel.default.token`，因为 Swift 不支持多线程的同时读写，会直接崩溃，所以这里采取读缓存
    func currentToken() -> String? {
        CacheManager.shared.getCodable(TokenModel.self, in: .token)?.token
    }
    
    @discardableResult
    open func ry_request(_ convertible: URLConvertible,
                      method: HTTPMethod = .get,
                      parameters: Parameters? = nil,
                      encoding: ParameterEncoding = URLEncoding.default,
                      headers: HTTPHeaders = [],
                      interceptor: RequestInterceptor? = nil,
                      requestModifier: RequestModifier? = nil) -> DataRequest {
        var convertible = BaseConvertible(url: convertible,
                                             method: method,
                                             parameters: parameters,
                                             encoding: encoding,
                                             headers: headers,
                                             requestModifier: requestModifier)
        
        convertible.headers.add(.host(APIConfig.current.environment.host))

        if let bearerToken = currentToken() {
            convertible.headers.add(.authorization(bearerToken: bearerToken))
        }

        print("Request \(convertible)")
        return request(convertible, interceptor: interceptor)
    }
    
    @discardableResult
    open func ry_upload(_ convertible: URLConvertible,
                        method: HTTPMethod = .post,
                        parameters: Parameters? = nil,
                        fileData: Data? = nil,
                        withName: String? = nil,
                        fileName: String? = nil,
                        mimeType: String? = nil,
                        headers: HTTPHeaders = [],
                        requestModifier: RequestModifier? = nil) -> UploadRequest {
        var convertible = BaseConvertible(url: convertible,
                                           method: method,
                                           parameters: nil, // 设置为nil，不再在convertible中定义参数
                                           encoding: URLEncoding.default, // Use default encoding
                                           headers: headers,
                                           requestModifier: requestModifier)

        convertible.headers.add(.host(APIConfig.current.environment.host))

        if let bearerToken = currentToken() {
            convertible.headers.add(.authorization(bearerToken: bearerToken))
        }

        print("Request \(convertible)")

        return upload(multipartFormData: { multipartFormData in
            // Add file data to the multipart form data if available
            if let fileData = fileData, let withName = withName, let fileName = fileName, let mimeType = mimeType {
                multipartFormData.append(fileData, withName: withName, fileName: fileName, mimeType: mimeType)
            }

            // Add other parameters if needed
            for (key, value) in parameters ?? [:] {
                if let valueData = String(describing: value).data(using: .utf8) {
                    multipartFormData.append(valueData, withName: key)
                }
            }
        }, with: convertible)
    }
}

// MARK: HTTPHeader

extension HTTPHeader {
    
    static func host(_ value: String) -> HTTPHeader {
        HTTPHeader(name: "Host", value: value)
    }
    
    static func appViersion(_ value: String) -> HTTPHeader {
        HTTPHeader(name: "App-Version", value: value)
    }
    
    static var xxx_form_urlencoded: HTTPHeader {
        .contentType("application/x-www-form-urlencoded")
    }
}

// MARK: DataRequest

extension DataRequest {
    
    static var error_500_count = 0
    
    @discardableResult
    func ry_JSON(decoder: DataDecoder = JSONDecoder(), completionHandler: @escaping (NetResponse<JSON>) -> Void) -> Self {
        responseDecodable(of: JSON.self, decoder: decoder) { response in
            if let urlResponse = response.response {
                print("""
                      Response [\(urlResponse.statusCode)] \(urlResponse.url?.absoluteString ?? "无URL")
                      """)
            }
            
            if let value = response.value {
                completionHandler(.success(value))
            } else {
                let error = NetError(request: response.request, response: response.response, error: response.error)
                print(error)
                completionHandler(.failure(error))
            }
            
            if let code = response.error?.responseCode {
                if (500..<600).contains(code) {
                    DataRequest.error_500_count += 1
                    if DataRequest.error_500_count >= 5 {
                        APIConfig.askHost { enviroment in
                            APIConfig.current.environment = enviroment
                        }
                    }
                }
            }
        }
    }
}

// MARK: NetError

struct NetError: Error, CustomStringConvertible {
    
    let request: URLRequest?

    let response: HTTPURLResponse?
    
    let error: AFError?
    
    var description: String {
        var description = "NetError "
        
        if let url = response?.url {
            description += "\(url) "
        } else {
            description += "未知URL "
        }
        
        if let error {
            description += "AF: \(error)"
        }
        
        return description
    }
}

enum NetResponse<Model> {
    
    case success(Model)
    
    case failure(NetError)
}

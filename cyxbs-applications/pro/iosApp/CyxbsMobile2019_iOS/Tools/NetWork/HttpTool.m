//
//  HttpTool.m
//  CyxbsMobile2019_iOS
//
//  Created by SSR on 2022/5/5.
//  Copyright © 2022 Redrock. All rights reserved.
//

//  All rights By SSR on 2022/5/6

#import "HttpTool.h"
#import "AliyunConfig.h"
#import "ReduceAFSecurityPolicy.h"
#import "CustomSessionManager.h"

#pragma mark - HttpTool ()

@interface HttpTool ()

/// 唯一请求sessionManager
@property (nonatomic, strong) CustomSessionManager *sessionManager;

/// 默认JSON格式上传
@property (nonatomic, strong) AFJSONRequestSerializer *defaultJSONRequest;

/// 以HTTP格式上传
@property (nonatomic, strong) AFHTTPRequestSerializer *HTTPRequest;

/// PropertyList格式上传
@property (nonatomic, strong) AFPropertyListRequestSerializer *propertyListRequest;

@end

#pragma mark - HttpTool

@implementation HttpTool

RisingSingleClass_IMPLEMENTATION(Tool)

- (AFHTTPSessionManager *)sessionManager {
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        _sessionManager = [[CustomSessionManager alloc] init];
        _sessionManager.requestSerializer = self.defaultJSONRequest;
        
        AFJSONResponseSerializer *response = AFJSONResponseSerializer.serializer;
        response.removesKeysWithNullValues = YES;
        response.acceptableContentTypes =
        [NSSet setWithArray:
         @[@"application/json",
           @"text/json",
           @"text/javascript",
           @"text/html",
           @"text/plain",
           @"application/atom+xml",
           @"application/xml",
           @"text/xml",
           @"application/x-www-form-urlencoded"]];
        _sessionManager.responseSerializer = response;
    });
    return _sessionManager;
}

#pragma mark - Method

- (void)request:(NSString * _Nonnull)URLString
           type:(HttpToolRequestType)requestType
     serializer:(HttpToolRequestSerializer)requestSerializer
 bodyParameters:(id _Nullable)parameters
       progress:(nullable void (^)(NSProgress * _Nonnull))progress
        success:(nullable void (^)(NSURLSessionDataTask * _Nonnull, id _Nullable))success
        failure:(nullable void (^)(NSURLSessionDataTask * _Nullable, NSError * _Nonnull))failure {

    // 旧原生接口会通过 NSUserDefaults 拼接 baseURL。全新安装或启动时序异常时，
    // 宏可能生成空地址；在公共入口转成普通网络失败，避免 AFNetworking 直接抛异常。
    NSURL *url = [NSURL URLWithString:URLString];
    NSString *scheme = url.scheme.lowercaseString;
    BOOL isHTTPURL = ([scheme isEqualToString:@"http"] || [scheme isEqualToString:@"https"])
        && url.host.length > 0;
    if (!isHTTPURL) {
        if (failure) {
            NSMutableDictionary *userInfo = [@{
                NSLocalizedDescriptionKey: @"请求地址缺失或格式非法"
            } mutableCopy];
            if (URLString.length > 0) {
                userInfo[NSURLErrorFailingURLStringErrorKey] = URLString;
            }
            NSError *error = [NSError errorWithDomain:NSURLErrorDomain
                                                 code:NSURLErrorBadURL
                                             userInfo:userInfo];
            failure(nil, error);
        }
        return;
    }

    NSString *originalUrl = URLString;
    // 异步接口获取IP
    NSString* ip = [AliyunConfig ipByHost:url.host];
    if (ip) {
        // 通过HTTPDNS获取IP成功，进行URL替换和HOST头设置
        NSRange hostFirstRange = [originalUrl rangeOfString:url.host];
        if (NSNotFound != hostFirstRange.location) {
            NSString *newUrl = [originalUrl stringByReplacingCharactersInRange:hostFirstRange withString:ip];
            //URL替换
            URLString = newUrl;
            [self.sessionManager.requestSerializer setValue:url.host forHTTPHeaderField:@"host"];
        }
    }
    
    //HOST头设置
    [self.sessionManager.requestSerializer setValue:url.host forHTTPHeaderField:@"host"];
    
    switch (requestSerializer) {
        case HttpToolRequestSerializerPropertyList:
            self.sessionManager.requestSerializer = self.propertyListRequest;
            break;
        case HttpToolRequestSerializerJSON:
            self.sessionManager.requestSerializer = self.defaultJSONRequest;
            break;
        case HttpToolRequestSerializerHTTP:
            self.sessionManager.requestSerializer = self.HTTPRequest;
            break;
    }
    
    NSString *methodType = @"";
    switch (requestType) {
        case HttpToolRequestTypeGet:
            methodType = @"GET";
            break;
        case HttpToolRequestTypePost:
            methodType = @"POST";
            break;
        case HttpToolRequestTypeDelete:
            methodType = @"DELETE";
            break;
        case HttpToolRequestTypePut:
            methodType = @"PUT";
            break;
        case HttpToolRequestTypePatch:
            methodType = @"PATCH";
            break;
    }
    
    [[self.sessionManager
      dataTaskWithHTTPMethod:methodType
      URLString:URLString
      parameters:parameters
      headers:nil
      uploadProgress:nil // 这里源码有if...else判断
      downloadProgress:progress
      success:success
      failure:failure]
     resume];
}

- (void)form:(NSString *)URLString
        type:(HttpToolRequestType)requestType
  parameters:(id)parameters
bodyConstructing:(void (^)(id<AFMultipartFormData> _Nonnull))block
    progress:(void (^)(NSProgress * _Nonnull))uploadProgress
     success:(void (^)(NSURLSessionDataTask * _Nonnull, id _Nullable))success
     failure:(void (^)(NSURLSessionDataTask * _Nullable, NSError * _Nonnull))failure {
    
    NSString *methodType = @"";
    switch (requestType) {
        case HttpToolRequestTypeGet:
            methodType = @"GET";
            break;
        case HttpToolRequestTypePost:
            methodType = @"POST";
            break;
        case HttpToolRequestTypeDelete:
            methodType = @"DELETE";
            break;
        case HttpToolRequestTypePut:
            methodType = @"PUT";
            break;
        case HttpToolRequestTypePatch:
            methodType = @"PATCH";
            break;
    }

    //多表单(multipart request):POST＆PUT
    NSMutableURLRequest *request =[self.sessionManager.requestSerializer
     multipartFormRequestWithMethod:methodType
     URLString:URLString
     parameters:parameters
     constructingBodyWithBlock:block
     error:nil];
    
    __block NSURLSessionDataTask *task = [self.sessionManager dataTaskWithRequest:request uploadProgress:nil downloadProgress:nil completionHandler:^(NSURLResponse * _Nonnull response, id  _Nullable responseObject, NSError * _Nullable error) {
            if (error) {
                failure(task, error);
                return;
        }else
            if (success) {
                success(task, responseObject);
            }
        }];
    
        [task resume];
    
//    [self.sessionManager
//     POST:URLString
//     parameters:parameters
//     headers:@{
//        @"App-Version" : @"100000"
//    }
//     constructingBodyWithBlock:block
//     progress:uploadProgress
//     success:success
//     failure:failure];
}

#pragma mark - Getter

- (AFJSONRequestSerializer *)defaultJSONRequest {
    if (_defaultJSONRequest == nil) {
        _defaultJSONRequest = AFJSONRequestSerializer.serializer;
        _defaultJSONRequest.timeoutInterval = 15;
        _defaultJSONRequest.HTTPMethodsEncodingParametersInURI = [NSSet setWithArray:@[]];
    }
    NSString *token = UserItem.defaultItem.token;
    if (token) {
        [_defaultJSONRequest setValue:[NSString stringWithFormat:@"Bearer %@",token] forHTTPHeaderField:@"authorization"];
    }
    return _defaultJSONRequest;
}

- (AFHTTPRequestSerializer *)HTTPRequest {
    if (_HTTPRequest == nil) {
        _HTTPRequest = AFHTTPRequestSerializer.serializer;
        _HTTPRequest.timeoutInterval = 15;
    }
    NSString *token = UserItem.defaultItem.token;
    if (token) {
        [_HTTPRequest setValue:[NSString stringWithFormat:@"Bearer %@",token] forHTTPHeaderField:@"authorization"];
    }
    return _HTTPRequest;
}

- (AFPropertyListRequestSerializer *)propertyListRequest {
    if (_propertyListRequest == nil) {
        _propertyListRequest = AFPropertyListRequestSerializer.serializer;
        _propertyListRequest.timeoutInterval = 15;
    }
    NSString *token = UserItem.defaultItem.token;
    if (token) {
        [_propertyListRequest setValue:[NSString stringWithFormat:@"Bearer %@",token] forHTTPHeaderField:@"authorization"];
    }
    return _propertyListRequest;
}

@end

@implementation HttpTool (WKWebView)

- (NSURLRequest *)URLRequestWithURL:(NSString *)url
                     bodyParameters:(id _Nullable)parameters {
    return [self.defaultJSONRequest
            requestWithMethod:@"GET"
            URLString:url
            parameters:parameters
            error:nil];
}

@end

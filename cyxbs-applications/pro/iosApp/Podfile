source 'https://github.com/CocoaPods/specs'
source 'https://github.com/aliyun/aliyun-specs.git'

platform :ios, '26.0'
use_frameworks!

# 是否为模拟器环境，真机/发版请改为 false
IS_SIMULATOR = false

inhibit_all_warnings!

pod 'Alamofire'  # 网络请求库
pod 'SwiftyJSON'  # JSON解析库

target 'CyxbsMobile2019_iOS' do
  pod 'ProgressHUD',      '13.7.2'  # 进度指示器库
  pod 'TZImagePickerController'  # 图片选择器库
  pod 'YBImageBrowser'  # 图片浏览器库
  pod 'NudeIn'  # 基于UITextView的富文本控件
  pod 'SDWebImage'  # 异步加载网络图片库
  pod 'AFNetworking'  # 网络请求库
  pod 'FMDB'  # SQLite数据库封装库
  pod 'MBProgressHUD', '~> 0.9.2'  # 提示框库
  pod 'YYKit'  # iOS开发工具集合
  pod 'YYImage'  # 图片处理库
  pod 'TOCropViewController'  # 图片裁剪库
  pod 'Masonry'  # 自动布局库
  pod 'MJRefresh'  # 下拉刷新库
  pod 'AMapLocation-NO-IDFA'  # 高德地图定位库
  pod 'AMap3DMap-NO-IDFA'  # 高德地图3D库
  pod 'MJExtension'  # 字典和模型之间的转换库
  pod 'SDCycleScrollView'  # 轮播图库
  pod 'FSCalendar'  # iOS日历库，兼容Objective-C和Swift
  
  pod 'UMCommon'  # 友盟统计库
  pod 'UMDevice'  # 友盟设备信息库
  pod 'UMVerify'  # 友盟一键登录库
  pod 'UMCCommonLog',           :configurations => ['Debug']  # 友盟日志库
  pod 'UMShare/Social/WeChat'  # 友盟微信分享库
  pod 'UMShare/Social/QQ'  # 友盟QQ分享库
  
  pod 'IQKeyboardManager'  # 键盘管理库
  pod 'Bugly'  # 腾讯Bugly崩溃日志上报库
  pod 'LookinServer',           :configurations => ['Debug']  # Lookin调试工具库

  pod 'FluentDarkModeKit'  # 暗黑模式适配库
  pod 'MarkdownKit'  # Markdown解析库
  pod 'AlicloudHTTPDNS', '2.2.0'  # 阿里云HTTPDNS解析库
  
  # pod 'RxSwift'
  pod 'RxCocoa'  # ReactiveX Cocoa扩展库
  pod 'SnapKit'  # 约束布局库
  
  pod 'JXPagingView'  # 分页视图库
  pod 'JXSegmentedView'  # 分段视图库
  pod 'JXBanner'  # 轮播图库
  
  pod 'RYTransitioningDelegateSwift'  # 自定义过渡动画库
  pod 'RYAngelWalker' 

  pod 'XBSBugly', :path => "Development Pods/"
end

target 'CyxbsWidgetExtension' do
  # 这里可以添加扩展目标的 Pods 配置
end

# 定义 M1 芯片相关的宏定义变量
M1_VALID_ARCHS = ['Pods-CyxbsMobile2019_iOS']
M1_EXCLUDED_ARCHS_iphonesimulator = ['YYKit', 'UMDevice']

post_install do |installer|
  installer.pods_project.targets.each do |target|
    # 打印目标名称，仅用于调试目的
    puts "target #{target}"
    
    target.build_configurations.each do |config|
      config.build_settings['ONLY_ACTIVE_ARCH'] = 'NO'  # 不仅仅针对当前激活的架构编译，适用于多架构编译
      config.build_settings['HEADER_SEARCH_PATHS'] = '$(PROJECT_DIR)/**'  # 头文件搜索路径
      config.build_settings['IPHONEOS_DEPLOYMENT_TARGET'] = '15.0'  # 部署目标版本注释补全的部分：

      config.build_settings['EXPANDED_CODE_SIGN_IDENTITY'] = ""  # 扩展代码签名标识
      config.build_settings['CODE_SIGNING_REQUIRED'] = "NO"  # 不要求代码签名
      config.build_settings['CODE_SIGNING_ALLOWED'] = "NO"  # 不允许代码签名
      
      #如果是模拟器运行
      if IS_SIMULATOR
        if M1_VALID_ARCHS.include?(target.name)
          config.build_settings['VALID_ARCHS'] = 'x86_64'  # 在模拟器上仅使用 x86_64 架构
        end
        
        if M1_EXCLUDED_ARCHS_iphonesimulator.include?(target.name)
          config.build_settings['EXCLUDED_ARCHS[sdk=iphonesimulator*]'] = 'arm64'  # 在模拟器上排除 arm64 架构
        end

      else
        # 在模拟器上排除 arm64 架构
        config.build_settings['EXCLUDED_ARCHS[sdk=iphonesimulator*]'] = 'arm64'  
      end
    end
  end
end

=begin
如果你是 M1 芯片:
每次 pod install 之前，删除 podfile.lock 后再执行
真机子/发版: 
修改 IS_Simulator 为 false
然后删除 podfile.lock 后再执行 pod install 就行，这样，你就可以正常在真机和发版的时候跑了

备注 XC 的 Ruby 来源：
https://www.rubydoc.info/github/CocoaPods/Xcodeproj/Xcodeproj/Project/Object/XCBuildConfiguration
这个文档对应的是下面这个 config 的属性

target.build_configurations.each do |config|
  ...
end

!!!: 适配指南

1. if target.name == 'Release'
  这个方法将导致 YYKit 内部判断出错

2. M1_EXCLUDED_ARCHS_iphonesimulator = [...]
  这个方法无法使 UMCommon 正常编译

3. M1_VALID_ARCHS = [...]
  这个方法只对 主.project 有用，并不对第三方库 Pods.project 有用

=end

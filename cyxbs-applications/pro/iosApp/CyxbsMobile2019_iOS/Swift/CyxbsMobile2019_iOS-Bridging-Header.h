//
//  Use this file to import your target's public headers that you would like to expose to Swift.
//


#pragma mark - 头文件

#import <AFNetworking.h>
#import <FluentDarkModeKit.h>
#import <UIImageView+WebCache.h>
#import <Masonry.h>
#import <MBProgressHUD.h>
#import <MJExtension.h>
#import "HttpClient.h"
#import "UITextView+Placeholder.h"
//#import "UserDefaultTool.h"
#import "UserItemTool.h"
#import "BaseViewController.h"
#import "NSDate+Timestamp.h"
#import "URLController.h"
#import "SDMask.h"
#import "CQUPTMapPlaceRect.h"   // 重邮地图里用的，很多文件都要导入这个，太麻烦了，直接写这里好了
#import "AESCipher.h"           // AES加密算法
#import "UserProtocolViewController.h"
#import "ByWordViewController.h"
#import "ByPasswordViewController.h"
#import "TodoSyncTool.h"
#import "UIColor+Color.h"
#import "HttpTool.h"
#import "NoteDataModel.h"

//#import "NewQAHud.h"
#import "RemindHud.h"
#import "UIColor+Color.h"
#import "SSRTopBarBaseView.h"
#import "HttpTool.h"
#import "NSObject+YYAdd.h"
#import "NSString+UILabel.h"
#import "SSRButton.h"
#import "AliyunConfig.h"
#import "UserItemTool.h"
#import "RisingRouter.h"

#pragma mark - Group共享

#define kAPPGroupID @"group.com.redrock.mobile"
#define kAppGroupShareNowDay @"nowDay"
#define kAppGroupShareThisWeekArray @"thisWeekArray"

#define kAPPUserDefaultLoginName @"name"
#define kAPPUserDefaultStudentID @"stuNum"

#define kWidthGrid  (SCREEN_WIDTH/7.5)
#define RGBColor(r,g,b,a) ([UIColor colorWithRed:r/255.0 green:g/255.0 blue:b/255.0 alpha:a])

#define ColorArray [[NSMutableArray alloc]initWithObjects:@"254,145,103",@"120,201,252",@"111,219,188",@"191,161,233",nil]

#define kPhotoImageViewW (SCREEN_WIDTH - 2 * 10 - 4) / 3

//gege
#define BACK_GRAY_COLOR [UIColor colorWithRed:236/255.0 green:236/255.0 blue:236/255.0 alpha:1]



#pragma mark - 友盟

// 友盟DeciveToken
#define kUMDeviceToken @"kUMDeviceToken"



#pragma mark - 屏幕适配相关

/* 小屏适配 */
#define IS_IPHONESE (SCREEN_WIDTH == 320.f && SCREEN_HEIGHT == 568.f)

/* 全面屏相关 */
#define IS_IPHONEX ((SCREEN_WIDTH == 375.f && SCREEN_HEIGHT == 812.f) || (SCREEN_WIDTH == 414.f && SCREEN_HEIGHT == 896.f)? YES : NO)
#define SAFE_AREA_BOTTOM (IS_IPHONEX ? (34.f) : (0.f))

/* 屏幕宽高 */
#define SCREEN_WIDTH ([UIScreen mainScreen].bounds.size.width)
#define SCREEN_HEIGHT ([UIScreen mainScreen].bounds.size.height)

/* TabBar高度 */
#define TABBARHEIGHT (IS_IPHONEX ? (49.f + SAFE_AREA_BOTTOM) : (49.f))

/* 状态栏高度 */
#define STATUSBARHEIGHT [[UIApplication sharedApplication] statusBarFrame].size.height

/* NavigationBar高度 */
#define NVGBARHEIGHT (44.f)

/* 顶部总高度 */
#define TOTAL_TOP_HEIGHT (STATUSBARHEIGHT + NVGBARHEIGHT)

#define kSegmentViewTitleHeight (SCREEN_HEIGHT * 50 / 667)

//学期开始时间
#define DateStart @"2021-03-01"


#pragma mark - 字体
#define PingFangSC @"PingFang SC"
//苹方-简 极细体
#define PingFangSCUltralight @"PingFangSC-Ultralight"
//苹方-简 纤细体
#define PingFangSCThin @"PingFangSC-Thin"
//苹方-简 细体
#define PingFangSCLight @"PingFangSC-Light"
//苹方-简 常规体
#define PingFangSCRegular @"PingFangSC-Regular"
//苹方-简 中黑体
#define PingFangSCMedium @"PingFangSC-Medium"
//苹方-简 中粗体
#define PingFangSCSemibold @"PingFangSC-Semibold"
//#define PingFangSCSemibold @"PingFangSC-Semibold"
/*
 下面这个for，可以打印出现有的所有字体
for(NSString *fontFamilyName in [UIFont familyNames]){
        NSLog(@"family:'%@'",fontFamilyName);
        for(NSString *fontName in [UIFont fontNamesForFamilyName:fontFamilyName]){
            NSLog(@"\tfont:'%@'",fontName);
        }
        NSLog(@"-------------");
}
*/




// Bahnschrift字体
#define BahnschriftBold @"Bahnschrift_Bold"



#define HEADERHEIGHT (STATUSBARHEIGHT+NVGBARHEIGHT)
#define MWIDTH ((SCREEN_WIDTH)/(DAY*2+1)) //monthLabel的宽度
#define MHEIGHT (1.6*MWIDTH) //monthLabel的高度
#define DAY 7
#define LESSON 12
#define WEEK 20
#define LONGLESSON (LESSON/2)
#define LESSONBTNSIDE (((SCREEN_WIDTH)-(MWIDTH))/DAY)
#define SEGMENT 2
#define autoSizeScaleX SCREEN_WIDTH/375.0
#define autoSizeScaleY SCREEN_HEIGHT/667.0
#define font(R) (R)*([UIScreen mainScreen].bounds.size.width)/375.0
#define SCREEN_RATE (667/[UIScreen mainScreen].bounds.size.height)
#define ZOOM(x) x / SCREEN_RATE



#pragma mark - “我的”接口

/// 登录接口
#define Mine_POST_logIn_API @"https://be-prod.redrock.cqupt.edu.cn/magipoke/token"

/// 刷新token
#define Mine_POST_refreshToken_API @"https://be-prod.redrock.cqupt.edu.cn/magipoke/token/refresh"

/// 上传头像
#define Mine_GET_upLoadProfile_API @"https://cyxbsmobile.redrock.team/app/index.php/Home/Photo/uploadArticle"
/// 上传用户信息
#define Mine_GET_upLoadUserInfo_API @"http://api-234.redrock.team/magipoke/Person/SetInfo"


/// 获取签到信息
#define Mine_POST_checkInInfo_API @"https://cyxbsmobile.redrock.team/app/index.php/QA/User/getScoreStatus"
/// 签到
#define Mine_POST_checkIn_API @"https://cyxbsmobile.redrock.team/app/index.php/QA/Integral/checkIn"
/// 积分商城
#define Mine_GET_integralStoreList_API @"https://cyxbsmobile.redrock.team/wxapi/magipoke-intergral/QA/Integral/getItemList"
/// 兑换商品
#define Mine_GET_integralStoreOrder_API @"https://cyxbsmobile.redrock.team/wxapi/magipoke-intergral/QA/Integral/order"
/// 我的商品
#define Mine_GET_myGoodsList_API @"https://cyxbsmobile.redrock.team/wxapi/magipoke-intergral/QA/Integral/myRepertory"


/// “我的”邮问数据
#define Mine_GET_mineQAData_API @"https://cyxbsmobile.redrock.team/app/index.php/QA/User/mine"
/// 我的提问
#define Mine_GET_myQuestions_API @"https://cyxbsmobile.redrock.team/app/index.php/QA/User/question"
/// 提问草稿箱
#define Mine_GET_myQuestionDraft_API @"https://cyxbsmobile.redrock.team/wxapi/magipoke-draft/User/getDraftQuestionList"
/// 我的回答
#define Mine_GET_myAnswers_API @"https://cyxbsmobile.redrock.team/app/index.php/QA/User/answer"
/// 回答草稿箱
#define Mine_GET_myAnswersDraft_API @"https://cyxbsmobile.redrock.team/wxapi/magipoke-draft/User/getDraftAnswerList"
/// 发出的评论
#define Mine_GET_myComment_API @"https://cyxbsmobile.redrock.team/app/index.php/QA/User/comment"
/// 收到的评论
#define Mine_GET_myRecomment_API @"https://cyxbsmobile.redrock.team/app/index.php/QA/User/reComment"
/// 删除草稿
#define Mine_GET_deleteDraft_API @"https://cyxbsmobile.redrock.team/wxapi/magipoke-draft/User/deleteItemInDraft"



#pragma mark - “发现”接口

/// 教务新闻列表
#define Discover_GET_newsList_API @"https://cyxbsmobile.redrock.team/234/newapi/jwNews/list"//参数page，方法Get
/// 教务新闻详情
#define Discover_GET_newsDetail_API @"https://cyxbsmobile.redrock.team/234/newapi/jwNews/content"
/// 教务新闻附件
#define Discover_GET_newsFile_API @"https://cyxbsmobile.redrock.team/234/newapi/jwNews/file"

/// 电费
#define Discover_POST_electricFee_API @"https://cyxbsmobile.redrock.team/MagicLoop/index.php?s=/addon/ElectricityQuery/ElectricityQuery/queryElecByRoom"
//#define Discover_POST_electricFee_API @"http://api-234.redrock.team/wxapi/magipoke-elecquery/getElectric"   // 这好像也是电费接口

/// 教务新闻列表
#define Discover_GET_newsList_API @"https://cyxbsmobile.redrock.team/234/newapi/jwNews/list"
/// 教务新闻详情
#define Discover_GET_newsDetail_API @"https://cyxbsmobile.redrock.team/234/newapi/jwNews/content"
/// 教务新闻附件下载
#define NEWSDOWNLOAD @"https://cyxbsmobile.redrock.team/234/newapi/jwNews/file"

///查询绩点需要先绑定ids
#define Discover_POST_idsBinding_API @"https://cyxbsmobile.redrock.team/wxapi/magipoke/ids/bind"
///绩点查询
//#define Discover_GET_GPA_API @"https://cyxbsmobile.redrock.team/wxapi/magipoke/gpa"

/// 考试安排接口
#define Discover_POST_examArrange_API @"https://cyxbsmobile.redrock.team/api/examSchedule"

/// 校车位置
#define Discover_POST_schoolBus_API @"https://cyxbsmobile.redrock.team/wxapi/schoolbus/status"

/// banner
//#define Discover_GET_bannerView_API @"http://api-234.redrock.team/magipoke-text/banner/get"

/// 志愿查询
#define Discover_POST_volunteerBind_API @"https://cyxbsmobile.redrock.team/wxapi/volunteer/binding"
#define Discover_POST_volunteerRequest_API @"https://cyxbsmobile.redrock.team/wxapi/volunteer/select"



#pragma mark - ”重邮地图“接口

/// 重邮地图历史记录偏好设置Key
#define Discover_cquptMapHistoryKey_String @"MapSearchHistory"

/// 重邮地图主页
#define Discover_GET_cquptMapBasicData_API @"https://cyxbsmobile.redrock.team/wxapi/magipoke-stumap/basic"
/// 重邮地图热搜
#define Discover_GET_cquptMapHotPlace_API @"https://cyxbsmobile.redrock.team/wxapi/magipoke-stumap/button"
/// 重邮地图：我的收藏
#define Discover_GET_cquptMapMyStar_API @"https://cyxbsmobile.redrock.team/wxapi/magipoke-stumap/rockmap/collect"
/// 重邮地图：搜索地点
#define Discover_POST_cquptMapSearch_API @"https://cyxbsmobile.redrock.team/wxapi/magipoke-stumap/placesearch"
/// 重邮地图：地点详情
#define Discover_POST_cquptMapPlaceDetail_API @"https://cyxbsmobile.redrock.team/wxapi/magipoke-stumap/detailsite"
/// 重邮地图：上传图片
#define Discover_POST_cquptMapUploadMage_API @"https://cyxbsmobile.redrock.team/wxapi/magipoke-stumap/rockmap/upload"
/// 重邮地图：添加收藏
#define Discover_PATCH_cquptMapAddCollect_API @"https://cyxbsmobile.redrock.team/wxapi/magipoke-stumap/rockmap/addkeep"
/// 重邮地图：删除收藏
#define Discover_POST_cquptMapDeleteCollect_API @"https://cyxbsmobile.redrock.team/wxapi/magipoke-stumap/rockmap/delete"



#pragma mark - “课表”、“备忘”接口

#define ClassSchedule_POST_keBiao_API @"https://cyxbsmobile.redrock.team/api/kebiao"


#define ClassSchedule_POST_addRemind_API @"https://cyxbsmobile.redrock.team/cyxbsMobile/index.php/Home/Person/addTransaction"


#define ClassSchedule_POST_getRemind_API @"https://cyxbsmobile.redrock.team/cyxbsMobile/index.php/Home/Person/getTransaction"


#define ClassSchedule_POST_editRemind_API @"https://cyxbsmobile.redrock.team/cyxbsMobile/index.php/Home/Person/editTransaction"


#define ClassSchedule_POST_deleteRemind_API @"https://cyxbsmobile.redrock.team/cyxbsMobile/index.php/Home/Person/deleteTransaction"


#define ClassSchedule_POST_teaKeBiao_API @"https://cyxbsmobile.redrock.team/wxapi/magipoke-teaKb/api/teaKb"
/// 空教室接口
#define ClassSchedule_POST_emptyClass_API @"https://cyxbsmobile.redrock.team/234/newapi/roomEmpty"

/// 同学课表之查找同学
#define ClassSchedule_GET_searchPeople_API @"https://cyxbsmobile.redrock.team/cyxbsMobile/index.php/home/searchPeople/peopleList"
/// 查找老师
#define ClassSchedule_POST_searchTeacher_API @"https://cyxbsmobile.redrock.team/wxapi/magipoke-teaKb/api/teaSearch"



typedef NS_ENUM(NSInteger, PeopleType) {
    /**代表传入的是学生相关*/
    PeopleTypeStudent,
    /**代表传入的是老师相关*/
    PeopleTypeTeacher
};

////data[i][j]代表（星期i+1）的（第j+1节课）是否有课，有课则为1，无课则为0
//typedef struct weekData{
//    int data[7][12];
//} weekData;

typedef NS_ENUM(NSInteger, ScheduleType) {
    /**代表是用户自己的课表*/
    ScheduleTypePersonal,
    /**代表是在查课表处显示的课表*/
    ScheduleTypeClassmate,
    /**代表是在没课约显示的课表*/
    ScheduleTypeWeDate,
};
//更新显示下节课内容的tabBar要用的协议
@protocol updateSchedulTabBarViewProtocol <NSObject>
//paramDict的3个key：
//classroomLabel：教室地点
//classTimeLabel：上课时间
//classLabel：课程名称
- (void)updateSchedulTabBarViewWithDic:(NSDictionary*)paramDict;
@end
//显示月份的view宽度；显示第几节课的竖条宽度
#define MONTH_ITEM_W (MAIN_SCREEN_W*0.088)
//显示月份、周几、日期的条内部item的间距；课表view和leftBar的距离
#define DAYBARVIEW_DISTANCE (MAIN_SCREEN_W*0.0075)
//0.00885

//记录最后一次登录的时间戳，类型是Double，用来避免后端出问题后的强制退出登录
#define LastLogInTimeKey_double @"LastLogInTimeKey_TimeInterval"


#import "UserItem.h"
#import "MineMessageVC.h"
#import "CheckInViewController.h"
#import "ScheduleInquiryViewController.h"
#import "SchoolBusVC.h"
#import "CalendarViewController.h"
#import "CQUPTMapViewController.h"
#import "ClassDetailModel.h"
#import "FinderToolViewController.h"
#import "FoodVC.h"
#import "AttitudeMainPageVC.h"
#import "EmptyClassViewController.h" // 空教室
#import "SportAttendanceViewController.h" // 体育打卡
#import "TestArrangeViewController.h" // 我的考试
#import "TodoDateTool.h"
#import "StampCenterVC.h" // 邮票中心
#import "FeedBackMainPageViewController.h" // 反馈中心
#import "MineSettingViewController.h" // 设置

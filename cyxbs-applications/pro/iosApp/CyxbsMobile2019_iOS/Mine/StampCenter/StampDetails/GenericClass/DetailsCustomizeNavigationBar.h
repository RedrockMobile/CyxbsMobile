//
//  DetailsCustomizeNavigationBar.h
//  Details
//
//  Created by Edioth Jin on 2021/8/5.
//

/**
 已经被废弃！！！
 */

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@class DetailsCustomizeNavigationBar;

@protocol CustomizeNavigationItemsDelegate <NSObject>
@required
/// 点击了返回按钮，控制器需要做的事情
- (void)DetailsCustomizeNavigationBarDidClickBack:(DetailsCustomizeNavigationBar *)DetailsCustomizeNavigationBar;

@end

/**
 * 自定义的导航栏
 * 注意在控制器中将导航栏设置为图层最上方
 * [self.view bringSubviewToFront:self.navBar];
 * 需要添加子视图可以继承
 */
@interface DetailsCustomizeNavigationBar : UIView

@property (nonatomic, weak) id <CustomizeNavigationItemsDelegate> delegate;
/// 是否隐藏分割线，default is NO
@property (nonatomic, assign, getter=isSplitLineHidden) BOOL splitLineHidden;
/// 标题
@property (nonatomic, strong) NSString * title;
/// 标题字体
@property (nonatomic, strong) UIFont * font;
/// 导航条高度
@property (nonatomic, assign) CGFloat height;

- (instancetype)initWithTitle:(NSString *)title;

@end

NS_ASSUME_NONNULL_END

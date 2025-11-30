//
//  DetailsBaseTableViewCell.h
//  Details
//
//  Created by Edioth Jin on 2021/8/7.
//

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * StampDetails 基类 - tableViewCell
 * 包含
 * 1. titleLabel: 标题
 * 2. sutitleLabel: 副标题，在标题的下方
 * 3. titleImgView: 标题右侧的图片
 * 4. rightTitleLabel: cell右侧的提示文字
 * 5. rightIndicatorImgView: cell右侧的小箭头，用以提示用户是否可以点击
 */
@interface DetailsBaseTableViewCell : UITableViewCell

/// 标题
@property (nonatomic, strong) UILabel * titleLabel;
/// 小标题
@property (nonatomic, strong) UILabel * subtitleLabel;
/// 右侧的小箭头
@property (nonatomic, strong) UIImageView * rightIndicatorImgView;
/// 是否隐藏右侧的小箭头 // default is NO
@property (nonatomic, assign, getter=isRightIndicatorHidden) BOOL rightIndicatorHidden;
/// 右侧的文字提示
@property (nonatomic, strong) UILabel * rightTitleLabel;
/// 标题右侧的图片
@property (nonatomic, strong) UIImageView * titleImgView;
/// 标题右侧的图片是否显示 // default is YES
@property (nonatomic, assign, getter=isTitleImgHidden) BOOL titleImgHidden;
/// separator
@property (nonatomic, strong) UIView * separateLine;
/// separator 的隐藏
@property (nonatomic, assign, getter=isSeparatorLineHidden) BOOL separatorLineHidden;

/// 复用标识
+ (NSString *)reuseIdentifier;

@end

NS_ASSUME_NONNULL_END

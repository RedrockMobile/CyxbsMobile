//
//  DetailsBaseTableViewCell.m
//  Details
//
//  Created by Edioth Jin on 2021/8/7.
//

#import "DetailsBaseTableViewCell.h"

@implementation DetailsBaseTableViewCell

#pragma mark - init

- (instancetype)initWithStyle:(UITableViewCellStyle)style
              reuseIdentifier:(NSString *)reuseIdentifier {
    self = [super initWithStyle:style reuseIdentifier:reuseIdentifier];
    if (self) {
        [self setupView];
        [self setupFrame];
    }
    return self;
}

- (void)setupView {
    [self.contentView addSubview:self.titleLabel];
    [self.contentView addSubview:self.subtitleLabel];
    [self.contentView addSubview:self.rightTitleLabel];
    [self.contentView addSubview:self.rightIndicatorImgView];
    [self.contentView addSubview:self.titleImgView];
    [self.contentView addSubview:self.separateLine];
}

- (void)setupFrame {
    for (UIView * view in self.contentView.subviews) {
        view.translatesAutoresizingMaskIntoConstraints = NO;
    }

    CGFloat space1 = (15.f / 375) * SCREEN_WIDTH; // H:space1-[content]-space1
    CGFloat space2 = (10.f / 375) * SCREEN_WIDTH; // H:[text]-space2-[rightIndicator].H:[title]-space2-[titleImg]
    CGFloat space3 = (6.f / 375) * SCREEN_WIDTH; // V:[title]-space3-[subtitle]
    
    // title label
    [self.contentView addConstraints:@[
        [NSLayoutConstraint constraintWithItem:_titleLabel
                                     attribute:(NSLayoutAttributeLeft)
                                     relatedBy:(NSLayoutRelationEqual)
                                        toItem:self.contentView
                                     attribute:(NSLayoutAttributeLeft)
                                    multiplier:1.f
                                      constant:space1],
        [NSLayoutConstraint constraintWithItem:_titleLabel
                                     attribute:(NSLayoutAttributeBottom)
                                     relatedBy:(NSLayoutRelationEqual)
                                        toItem:self.contentView
                                     attribute:(NSLayoutAttributeCenterY)
                                    multiplier:1.f
                                      constant:0]
    ]];
    
    // subtitle label
    [self.contentView addConstraints:@[
        [NSLayoutConstraint constraintWithItem:_subtitleLabel
                                     attribute:(NSLayoutAttributeLeft)
                                     relatedBy:(NSLayoutRelationEqual)
                                        toItem:_titleLabel
                                     attribute:(NSLayoutAttributeLeft)
                                    multiplier:1.f
                                      constant:0],
        [NSLayoutConstraint constraintWithItem:_subtitleLabel
                                     attribute:(NSLayoutAttributeTop)
                                     relatedBy:(NSLayoutRelationEqual)
                                        toItem:_titleLabel
                                     attribute:(NSLayoutAttributeBottom)
                                    multiplier:1.f
                                      constant:space3]
    ]];

    // right indicator imgView
    [self.contentView addConstraints:@[
        [NSLayoutConstraint constraintWithItem:_rightIndicatorImgView
                                     attribute:(NSLayoutAttributeCenterY)
                                     relatedBy:(NSLayoutRelationEqual)
                                        toItem:self.contentView
                                     attribute:(NSLayoutAttributeCenterY)
                                    multiplier:1.f
                                      constant:0],
        [NSLayoutConstraint constraintWithItem:_rightIndicatorImgView
                                     attribute:(NSLayoutAttributeRight)
                                     relatedBy:(NSLayoutRelationEqual)
                                        toItem:self.contentView
                                     attribute:(NSLayoutAttributeRight)
                                    multiplier:1.f
                                      constant:-space1]
    ]];
    
    // titleImgView
    [self.contentView addConstraints:@[
        [NSLayoutConstraint constraintWithItem:_titleImgView
                                     attribute:(NSLayoutAttributeCenterY)
                                     relatedBy:(NSLayoutRelationEqual)
                                        toItem:_titleLabel
                                     attribute:(NSLayoutAttributeCenterY)
                                    multiplier:1.f
                                      constant:0],
        [NSLayoutConstraint constraintWithItem:_titleImgView
                                     attribute:(NSLayoutAttributeLeft)
                                     relatedBy:(NSLayoutRelationEqual)
                                        toItem:_titleLabel
                                     attribute:(NSLayoutAttributeRight)
                                    multiplier:1.f
                                      constant:space2],
        [NSLayoutConstraint constraintWithItem:_titleImgView
                                     attribute:(NSLayoutAttributeHeight)
                                     relatedBy:(NSLayoutRelationEqual)
                                        toItem:_titleLabel
                                     attribute:(NSLayoutAttributeHeight)
                                    multiplier:1.f
                                      constant:0]
    ]];
    
    // right title label
    [self.contentView addConstraints:@[
        [NSLayoutConstraint constraintWithItem:_rightTitleLabel
                                     attribute:(NSLayoutAttributeCenterY)
                                     relatedBy:(NSLayoutRelationEqual)
                                        toItem:self.contentView
                                     attribute:(NSLayoutAttributeCenterY)
                                    multiplier:1.f
                                      constant:0],
        [NSLayoutConstraint constraintWithItem:_rightTitleLabel
                                     attribute:(NSLayoutAttributeRight)
                                     relatedBy:(NSLayoutRelationEqual)
                                        toItem:_rightIndicatorImgView
                                     attribute:(NSLayoutAttributeRight)
                                    multiplier:1.f
                                      constant:-space2]
    ]];
    
    // separate line
    [self.contentView addConstraints:@[
        [NSLayoutConstraint constraintWithItem:_separateLine
                                     attribute:(NSLayoutAttributeBottom)
                                     relatedBy:(NSLayoutRelationEqual)
                                        toItem:self.contentView
                                     attribute:(NSLayoutAttributeBottom)
                                    multiplier:1.f
                                      constant:0],
        [NSLayoutConstraint constraintWithItem:_separateLine
                                     attribute:(NSLayoutAttributeRight)
                                     relatedBy:(NSLayoutRelationEqual)
                                        toItem:self.contentView
                                     attribute:(NSLayoutAttributeRight)
                                    multiplier:1.f
                                      constant:0],
        [NSLayoutConstraint constraintWithItem:_separateLine
                                     attribute:(NSLayoutAttributeLeft)
                                     relatedBy:(NSLayoutRelationEqual)
                                        toItem:self.contentView
                                     attribute:(NSLayoutAttributeLeft)
                                    multiplier:1.0f
                                      constant:0],
        [NSLayoutConstraint constraintWithItem:_separateLine
                                     attribute:(NSLayoutAttributeHeight)
                                     relatedBy:(NSLayoutRelationEqual)
                                        toItem:nil
                                     attribute:(NSLayoutAttributeNotAnAttribute)
                                    multiplier:1.0f
                                      constant:1]
    ]];
    
    // configure
    self.rightIndicatorHidden = NO;
    self.titleImgHidden = YES;
    self.backgroundColor = [UIColor clearColor];
}

#pragma mark - setter

- (void)setRightIndicatorHidden:(BOOL)rightIndicatorHidden {
    _rightIndicatorHidden = rightIndicatorHidden;
    self.rightIndicatorImgView.hidden = rightIndicatorHidden;
}

- (void)setTitleImgHidden:(BOOL)titleImgHidden {
    _titleImgHidden = titleImgHidden;
    self.titleImgView.hidden = titleImgHidden;
}

- (void)setSeparatorLineHidden:(BOOL)separatorLineHidden {
    _separatorLineHidden = separatorLineHidden;
    self.separateLine.hidden = separatorLineHidden;
}

#pragma mark - getter

- (UILabel *)titleLabel {
    if (_titleLabel == nil) {
        _titleLabel = [[UILabel alloc] initWithFrame:(CGRectZero)];
        _titleLabel.font = [UIFont fontWithName:PingFangSCMedium size:16];
        _titleLabel.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#15315B" alpha:1] darkColor:[UIColor colorWithHexString:@"#F0F0F2" alpha:1]];
        [_titleLabel sizeToFit];
    }
    return _titleLabel;
}

- (UILabel *)subtitleLabel {
    if (_subtitleLabel == nil) {
        _subtitleLabel = [[UILabel alloc] initWithFrame:(CGRectZero)];
        _subtitleLabel.font = [UIFont fontWithName:PingFangSCMedium size:12];
        _subtitleLabel.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#15315B" alpha:0.4] darkColor:[UIColor colorWithHexString:@"#F0F0F2" alpha:0.4]];
    }
    return _subtitleLabel;
}

- (UILabel *)rightTitleLabel {
    if (_rightTitleLabel == nil) {
        _rightTitleLabel = [[UILabel alloc] initWithFrame:(CGRectZero)];
        _rightTitleLabel.font = [UIFont fontWithName:@"DIN-Medium" size:16];
        _rightTitleLabel.textColor = [UIColor blackColor];
        [_rightTitleLabel sizeToFit];
    }
    return _rightTitleLabel;
}

- (UIImageView *)rightIndicatorImgView {
    if (_rightIndicatorImgView == nil) {
        _rightIndicatorImgView = [[UIImageView alloc] initWithFrame:(CGRectZero)];
        _rightIndicatorImgView.image = [UIImage imageNamed:@"tableCell_rightIndicator"];
        
        [_rightIndicatorImgView sizeToFit];
    }
    return _rightIndicatorImgView;
}

- (UIImageView *)titleImgView {
    if (_titleImgView == nil) {
        _titleImgView = [[UIImageView alloc] initWithFrame:(CGRectZero)];
        _titleImgView.contentMode = UIViewContentModeCenter;
    }
    return _titleImgView;
}

- (UIView *)separateLine {
    if (_separateLine == nil) {
        _separateLine = [[UIView alloc] initWithFrame:(CGRectZero)];
        _separateLine.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#DDE6F4" alpha:1] darkColor:[UIColor colorWithHexString:@"#2B2C2D" alpha:0.8]];
    }
    return _separateLine;
}

#pragma mark - private

+ (NSString *)reuseIdentifier {
    return NSStringFromClass([self class]);
}

@end

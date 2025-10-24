//
//  DetailsCustomizeNavigationBar.m
//  Details
//
//  Created by Edioth Jin on 2021/8/5.
//

#import "DetailsCustomizeNavigationBar.h"

@interface DetailsCustomizeNavigationBar ()

/// 返回按钮
@property (nonatomic, strong) UIButton * backButton;
/// 分割线View
@property (nonatomic, strong) UIView * splitLine;
/// 标题
@property (nonatomic, strong) UILabel * titleLabel;

@end

@implementation DetailsCustomizeNavigationBar

#pragma mark - init

- (instancetype)initWithTitle:(NSString *)title {
    self = [super initWithFrame:(CGRectZero)];
    if (self) {
        [self setupView];
        self.title = title;
        self.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#F2F3F8" alpha:1] darkColor:[UIColor colorWithHexString:@"#1D1D1D" alpha:1]];
    }
    return self;
}

- (void)setupView {
    CGRect bounds = self.bounds;
    CGPoint origin = bounds.origin;
    origin.x = 0;
    origin.y = 0;
    CGSize size = bounds.size;
    // 适配不同机型的导航栏高度
    size.height = TOTAL_TOP_HEIGHT;
    size.width = SCREEN_WIDTH;
    
    // configure self
    self.frame = CGRectMake(0, 0, size.width, size.height);
    
    // configur splitLine
    [self addSubview:self.splitLine];
    CGRect f_splitLine = bounds;
    f_splitLine.size = CGSizeMake(size.width, 1);
    f_splitLine.origin = CGPointMake(0, size.height - 1);
    self.splitLine.frame = f_splitLine;
    
    // configure backButton
    [self addSubview:self.backButton];
    [self addConstraints:@[
        [NSLayoutConstraint constraintWithItem:_backButton
                                     attribute:(NSLayoutAttributeLeft)
                                     relatedBy:(NSLayoutRelationEqual)
                                        toItem:self
                                     attribute:(NSLayoutAttributeLeft)
                                    multiplier:1.0f
                                      constant:0],
        [NSLayoutConstraint constraintWithItem:_backButton
                                     attribute:(NSLayoutAttributeCenterY)
                                     relatedBy:(NSLayoutRelationEqual)
                                        toItem:self
                                     attribute:(NSLayoutAttributeTop)
                                    multiplier:1.0f
                                      constant:(size.height - 44) + 44 / 2],
        [NSLayoutConstraint constraintWithItem:_backButton
                                     attribute:(NSLayoutAttributeWidth)
                                     relatedBy:(NSLayoutRelationEqual)
                                        toItem:nil
                                     attribute:(NSLayoutAttributeNotAnAttribute)
                                    multiplier:1.f
                                      constant:44],
        [NSLayoutConstraint constraintWithItem:_backButton
                                     attribute:(NSLayoutAttributeHeight)
                                     relatedBy:(NSLayoutRelationEqual)
                                        toItem:nil
                                     attribute:(NSLayoutAttributeNotAnAttribute)
                                    multiplier:1.f
                                      constant:44]
    ]];
    
    
    // configure titleLabel
    [self addSubview:self.titleLabel];
    [self addConstraints:@[
        [NSLayoutConstraint constraintWithItem:_titleLabel
                                     attribute:(NSLayoutAttributeLeft)
                                     relatedBy:(NSLayoutRelationEqual)
                                        toItem:_backButton
                                     attribute:(NSLayoutAttributeRight)
                                    multiplier:1.f
                                      constant:10],
        [NSLayoutConstraint constraintWithItem:_titleLabel
                                     attribute:(NSLayoutAttributeCenterY)
                                     relatedBy:(NSLayoutRelationEqual)
                                        toItem:_backButton
                                     attribute:(NSLayoutAttributeCenterY)
                                    multiplier:1.f
                                      constant:0]
    ]];
}

#pragma mark - action

- (void)backDidClick {
    if ([self.delegate respondsToSelector:@selector(DetailsCustomizeNavigationBarDidClickBack:)]) {
        [self.delegate DetailsCustomizeNavigationBarDidClickBack:self];
        return;
    }
    NSLog(@"未设置点击事件");
}

#pragma mark - setter

- (void)setFont:(UIFont *)font {
    _font = font;
    self.titleLabel.font = font;
}

- (void)setTitle:(NSString *)title {
    _title = title;
    self.titleLabel.text = title;
}

- (void)setSplitLineHidden:(BOOL)splitLineHidden {
    _splitLineHidden = splitLineHidden;
    self.splitLine.hidden = _splitLineHidden;
}

#pragma mark - getter

- (CGFloat)height {
    return self.frame.size.height;
}

- (UILabel *)titleLabel {
    if (_titleLabel == nil) {
        _titleLabel = [[UILabel alloc] initWithFrame:(CGRectZero)];
        _titleLabel.translatesAutoresizingMaskIntoConstraints = NO;
        [_titleLabel sizeToFit];
        _titleLabel.font = [UIFont fontWithName:PingFangSCSemibold size:22];
    }
    return _titleLabel;
}

- (UIButton *)backButton {
    if (_backButton == nil) {
        _backButton = [[UIButton alloc] initWithFrame:(CGRectZero)];
        _backButton.translatesAutoresizingMaskIntoConstraints = NO;
        [_backButton setImage:[UIImage imageNamed:@"navBar_back"]
                     forState:(UIControlStateNormal)];
        [_backButton addTarget:self.delegate
                        action:@selector(backDidClick)
              forControlEvents:(UIControlEventTouchUpInside)];
        [_backButton sizeToFit];
    }
    return _backButton;
}

- (UIView *)splitLine {
    if (_splitLine == nil) {
        _splitLine = [[UIView alloc] initWithFrame:(CGRectZero)];
        _splitLine.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#2A4E84" alpha:0.1] darkColor:[UIColor colorWithHexString:@"#000000" alpha:1]];
        _splitLineHidden = NO;
    }
    return _splitLine;
}

@end

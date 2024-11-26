//
//  UserInfoView.m
//  CyxbsMobile2019_iOS
//
//  Created by 千千 on 2020/2/12.
//  Copyright © 2020 Redrock. All rights reserved.
//

#import "UserInfoView.h"

@interface UserInfoView()
@property (nonatomic, weak)UIImageView *userImage;//头像
@property (nonatomic, weak)UILabel *userNameLabel;//张树洞
@property (nonatomic, weak)UILabel *majorLabel;//软件工程学院
@property (nonatomic, weak)UILabel *idLabel;//2017******
@end
@implementation UserInfoView
- (instancetype)initWithFrame:(CGRect)frame
{
    self = [super initWithFrame:frame];
    if (self) {
        self.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#FFFFFF"] darkColor:[UIColor colorWithHexString:@"#1D1D1D"]];
      [self addImage];
      [self addName];
      [self addMajor];
      [self addIDLabel];
    }
    return self;
}

- (void)addImage {
    UIImageView *headerImageView = [[UIImageView alloc]initWithFrame:CGRectMake(16, 30, 40, 40)];
    [headerImageView.layer setCornerRadius:20];
    headerImageView.clipsToBounds = YES;
    [self addSubview:headerImageView];
    self.userImage = headerImageView;
    NSString *headImgUrl_str = [UserItemTool defaultItem].headImgUrl;
    NSURL *headImageUrl = [NSURL URLWithString:headImgUrl_str];
    [headerImageView sd_setImageWithURL:headImageUrl placeholderImage:[UIImage imageNamed:@"默认头像"] options:SDWebImageFromCacheOnly context:nil progress:nil completed:nil];
}
- (void)addName {
    UILabel *label = [[UILabel alloc]init];
    label.text = [UserItemTool defaultItem].realName;
    self.userNameLabel = label;
    label.font = [UIFont fontWithName:PingFangSCSemibold size:15];
    if (@available(iOS 11.0, *)) {
        [label setTextColor:[UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#15315B" alpha:1] darkColor:[UIColor colorWithHexString:@"#F0F0F2" alpha:1]]];
    } else {
        // Fallback on earlier versions
    }
    [self addSubview:label];
}
- (void)addMajor {
    UILabel *label = [[UILabel alloc]init];
    self.majorLabel = label;
    label.text = [UserItem defaultItem].college;
    if (@available(iOS 11.0, *)) {
        label.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#2A4E84" alpha:1] darkColor:[UIColor colorWithHexString:@"#DFDFE3" alpha:1]];
    } else {
        // Fallback on earlier versions
    }
    [label setAlpha:0.67];
    label.font = [UIFont fontWithName:PingFangSCRegular size:11];
    [self addSubview:label];
}

- (void)addIDLabel {
    UILabel *label = [[UILabel alloc]init];
    self.idLabel = label;
    if (@available(iOS 11.0, *)) {
        label.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#2A4E84" alpha:1] darkColor:[UIColor colorWithHexString:@"#DFDFE3" alpha:1]];
    } else {
        // Fallback on earlier versions
    }
    [label setAlpha:0.71];
    label.font = [UIFont fontWithName:PingFangSCRegular size:11];
    label.text = [NSUserDefaults.standardUserDefaults stringForKey:@"stuNum"];
    [self addSubview:label];
}
- (void)layoutSubviews {
    [self.userNameLabel mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self.userImage.mas_right).offset(14);
        make.top.equalTo(self.userImage);
    }];
    [self.idLabel mas_makeConstraints:^(MASConstraintMaker *make) {
        make.centerY.equalTo(self.userImage);
        make.right.equalTo(self).offset(-15);
    }];
    [self.majorLabel mas_makeConstraints:^(MASConstraintMaker *make) {
        make.left.equalTo(self.userNameLabel);
        make.top.equalTo(self.userNameLabel.mas_bottom).offset(4);
    }];
}
/*
// Only override drawRect: if you perform custom drawing.
// An empty implementation adversely affects performance during animation.
- (void)drawRect:(CGRect)rect {
    // Drawing code
}
*/

@end

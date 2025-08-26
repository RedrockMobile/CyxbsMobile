//
//  CQUPTDetailView.m
//  CyxbsMobile2019_iOS
//
//  Created by 方昱恒 on 2020/8/15.
//  Copyright © 2020 Redrock. All rights reserved.
//

#import "CQUPTMapDetailView.h"
#import "CQUPTMapPlaceItem.h"
#import "CQUPTMapPlaceDetailItem.h"
#import "CQUPTMapDetailTagsCollectionViewCell.h"
#import "CollectionViewSpaceLayout.h"
#import "CQUPTMapMoreImageViewController.h"
#import <TZImagePickerController.h>
#import "CQUPTMapDataItem.h"
#import "CQUPTMapModel.h"
#import "FYHCycleLabel.h"

@interface CQUPTMapDetailView () <UICollectionViewDelegate, UICollectionViewDataSource, TZImagePickerControllerDelegate>

@property (nonatomic, strong) CQUPTMapPlaceDetailItem *detailItem;

@property (nonatomic, weak) UIView *dragBar;
@property (nonatomic, weak) FYHCycleLabel *placeNameLabel;
@property (nonatomic, weak) UIButton *starButton;
@property (nonatomic, strong) NSMutableArray<UILabel *> *attributesLabelArray;
@property (nonatomic, weak) UILabel *detailLabel;
@property (nonatomic, weak) UIButton *showMoreButton;
@property (nonatomic, weak) UIImageView *showMoreImageView;
@property (nonatomic, weak) UIScrollView *imagesScrollView;
@property (nonatomic, strong) NSMutableArray<UIImageView *> *imagesArray;
@property (nonatomic, weak) UIImageView *shareImageView;
@property (nonatomic, weak) UIButton *shareButton;
@property (nonatomic, weak) UILabel *aboutHereLabel;
@property (nonatomic, weak) UICollectionView *tagsCollectionView;
@property (nonatomic, strong) UIButton *navigationBtn;

@end


@implementation CQUPTMapDetailView

- (instancetype)initWithPlaceItem:(CQUPTMapPlaceItem *)placeItem
{
    self = [super init];
    if (self) {
        self.attributesLabelArray = [NSMutableArray array];
        self.imagesArray = [NSMutableArray array];
        
        self.layer.cornerRadius = 20;
        if (@available(iOS 11.0, *)) {
            self.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#FFFFFF" alpha:1] darkColor:[UIColor colorWithHexString:@"#2D2D2D" alpha:1]];
        } else {
            self.backgroundColor = [UIColor whiteColor];
        }

        UIView *dragBar = [[UIView alloc] init];
        if (@available(iOS 11.0, *)) {
            dragBar.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#E1EDFB" alpha:1] darkColor:[UIColor colorWithHexString:@"#000101" alpha:1]];
        } else {
            dragBar.backgroundColor = [UIColor colorWithHexString:@"#E1EDFB"];
        }
        [self addSubview:dragBar];
        self.dragBar = dragBar;
        
        FYHCycleLabel *placeNameLabel = [[FYHCycleLabel alloc] initWithFrame:CGRectMake(15, 40, 280, 30)];
        if (@available(iOS 11.0, *)) {
            placeNameLabel.cycleLabel.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#15305C" alpha:1] darkColor:[UIColor colorWithHexString:@"#F0F0F2" alpha:1]];
        } else {
            placeNameLabel.cycleLabel.textColor = [UIColor colorWithHexString:@"#15305B"];
        }
        placeNameLabel.cycleLabel.font = [UIFont fontWithName:PingFangSCSemibold size:23];
        placeNameLabel.labelText = placeItem.placeName;
        [self addSubview:placeNameLabel];
        self.placeNameLabel = placeNameLabel;
        
        UIButton *starButton = [UIButton buttonWithType:UIButtonTypeCustom];
        [starButton setImage:[UIImage imageNamed:@"Map_StarButton"] forState:UIControlStateNormal];
        [starButton setImage:[UIImage imageNamed:@"Map_StarButtonOn"] forState:UIControlStateSelected];
        if ([placeItem isCollected]) {
            starButton.selected = YES;
        }
        [starButton addTarget:self action:@selector(starButtonTapped:) forControlEvents:UIControlEventTouchUpInside];
        [self addSubview:starButton];
        self.starButton = starButton;
        
        UILabel *detailLabel = [[UILabel alloc] init];
        detailLabel.text = @"详情";
        detailLabel.font = [UIFont fontWithName:PingFangSCSemibold size:17];
        if (@available(iOS 11.0, *)) {
            detailLabel.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#15305C" alpha:1] darkColor:[UIColor colorWithHexString:@"#F0F0F2" alpha:1]];
        } else {
            detailLabel.textColor = [UIColor colorWithHexString:@"#15305C"];
        }
        [self addSubview:detailLabel];
        self.detailLabel = detailLabel;
        
        UIButton *showMoreButton = [UIButton buttonWithType:UIButtonTypeSystem];
        [showMoreButton setTitle:@"查看更多" forState:UIControlStateNormal];
        showMoreButton.titleLabel.font = [UIFont fontWithName:PingFangSCMedium size:12];
        if (@available(iOS 11.0, *)) {
            [showMoreButton setTitleColor:[UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#ABBCD8" alpha:1] darkColor:[UIColor colorWithHexString:@"#979797" alpha:1]] forState:UIControlStateNormal];
        } else {
            [showMoreButton setTitleColor:[UIColor colorWithHexString:@"ABBCD8"] forState:UIControlStateNormal];
        }
        [showMoreButton addTarget:self action:@selector(showMoreButtonTapped) forControlEvents:UIControlEventTouchUpInside];
        [self addSubview:showMoreButton];
        self.showMoreButton = showMoreButton;
        
        UIImageView *showMoreImageView = [[UIImageView alloc] initWithImage:[UIImage imageNamed:@"Map_ShowMore"]];
        [self addSubview:showMoreImageView];
        self.showMoreImageView = showMoreImageView;
        
        UIScrollView *scrollView = [[UIScrollView alloc] init];
        scrollView.showsHorizontalScrollIndicator = NO;
        scrollView.contentInset = UIEdgeInsetsMake(0, 15, 0, 15);
        [self addSubview:scrollView];
        self.imagesScrollView = scrollView;
        
        UIImageView *shareImageView = [[UIImageView alloc] initWithImage:[UIImage imageNamed:@"Map_ShareImage"]];
        [self addSubview:shareImageView];
        self.shareImageView = shareImageView;
        
        UIButton *shareButton = [UIButton buttonWithType:UIButtonTypeSystem];
        [shareButton setTitle:@"与大家分享你拍摄的此地点" forState:UIControlStateNormal];
        shareButton.titleLabel.font = [UIFont fontWithName:PingFangSCSemibold size:13];
        if (@available(iOS 11.0, *)) {
            [shareButton setTitleColor:[UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#234780" alpha:1] darkColor:[UIColor colorWithHexString:@"#EFEFF1" alpha:1]] forState:UIControlStateNormal];
        } else {
            [shareButton setTitleColor:[UIColor colorWithHexString:@"234780"] forState:UIControlStateNormal];
        }
        [shareButton addTarget:self action:@selector(shareImageButtonTapped) forControlEvents:UIControlEventTouchUpInside];
        [self addSubview:shareButton];
        self.shareButton = shareButton;
        
        UILabel *aboutHereLabel = [[UILabel alloc] init];
        aboutHereLabel.text = @"关于该地点";
        aboutHereLabel.font = [UIFont fontWithName:PingFangSCSemibold size:17];
        aboutHereLabel.textColor = self.detailLabel.textColor;
        [self addSubview:aboutHereLabel];
        self.aboutHereLabel = aboutHereLabel;
        
        UICollectionView *tagsCollectionView = [[UICollectionView alloc] initWithFrame:CGRectZero collectionViewLayout:[[CollectionViewSpaceLayout alloc] init]];
        [tagsCollectionView registerClass:[CQUPTMapDetailTagsCollectionViewCell class] forCellWithReuseIdentifier:@"CQUPTMapDetailTagsCollectionViewCell"];
        tagsCollectionView.backgroundColor = [UIColor clearColor];
        tagsCollectionView.delegate = self;
        tagsCollectionView.dataSource = self;
        [self addSubview:tagsCollectionView];
        self.tagsCollectionView = tagsCollectionView;
        
        [self addSubview:self.navigationBtn];
    }
    return self;
}

- (void)layoutSubviews {
    [super layoutSubviews];
    
    [self.dragBar mas_makeConstraints:^(MASConstraintMaker *make) {
        make.top.equalTo(self).offset(11);
        make.centerX.equalTo(self);
        make.width.equalTo(@28);
        make.height.equalTo(@7);
    }];
    self.dragBar.layer.cornerRadius = 3.5;
    
    // placeNameLabel用的frame
    
    [self.starButton mas_makeConstraints:^(MASConstraintMaker *make) {
        make.trailing.equalTo(self).offset(-14);
        make.top.equalTo(self).offset(45);
        make.width.height.equalTo(@21);
    }];
    
    for (int i = 0; i < self.attributesLabelArray.count; i++) {
        if (i == 0) {
            [self.attributesLabelArray[i] mas_makeConstraints:^(MASConstraintMaker *make) {
                make.leading.equalTo(self).offset(15);
                make.top.equalTo(self.placeNameLabel.mas_bottom).offset(5);
                make.width.equalTo(@([self attributeLabelWidthText:self.attributesLabelArray[i].text] + 15));
                make.height.equalTo(@18);
            }];
        } else {
            [self.attributesLabelArray[i] mas_makeConstraints:^(MASConstraintMaker *make) {
                make.leading.equalTo(self.attributesLabelArray[i - 1].mas_trailing).offset(12);
                make.centerY.equalTo(self.attributesLabelArray[i - 1]);
                make.width.equalTo(@([self attributeLabelWidthText:self.attributesLabelArray[i].text] + 15));
                make.height.equalTo(@18);
            }];
        }
    }
    
    [self.detailLabel mas_makeConstraints:^(MASConstraintMaker *make) {
        make.leading.equalTo(self).offset(15);
        make.top.equalTo(self.placeNameLabel.mas_bottom).offset(40);
    }];

    [self.showMoreImageView mas_makeConstraints:^(MASConstraintMaker *make) {
        make.trailing.equalTo(self).offset(-15);
        make.centerY.equalTo(self.detailLabel);
        make.width.equalTo(@5);
        make.height.equalTo(@11);
    }];
    
    [self.showMoreButton mas_makeConstraints:^(MASConstraintMaker *make) {
        make.centerY.equalTo(self.detailLabel);
        make.trailing.equalTo(self.showMoreImageView.mas_leading).offset(-8);
        make.width.equalTo(@50);
        make.height.equalTo(@30);
    }];
    
    [self.imagesScrollView mas_makeConstraints:^(MASConstraintMaker *make) {
        make.leading.trailing.equalTo(self);
        make.top.equalTo(self.detailLabel.mas_bottom).offset(15);
        make.height.equalTo(@157);
    }];
    
    for (int i = 0; i < self.imagesArray.count; i++) {
        if (i == 0) {
            [self.imagesArray[i] mas_makeConstraints:^(MASConstraintMaker *make) {
                make.leading.equalTo(self.imagesScrollView);
                make.top.bottom.equalTo(self.imagesScrollView);
                make.height.equalTo(@157);
                make.width.equalTo(@280);
            }];
        } else if (i == self.imagesArray.count - 1) {
            [self.imagesArray[i] mas_makeConstraints:^(MASConstraintMaker *make) {
                make.leading.equalTo(self.imagesArray[i - 1].mas_trailing).offset(14);
                make.top.bottom.equalTo(self.imagesScrollView);
                make.height.equalTo(@157);
                make.width.equalTo(@280);
                make.trailing.equalTo(self.imagesScrollView);
            }];
        } else {
            [self.imagesArray[i] mas_makeConstraints:^(MASConstraintMaker *make) {
                make.leading.equalTo(self.imagesArray[i - 1].mas_trailing).offset(14);
                make.top.bottom.equalTo(self.imagesScrollView);
                make.height.equalTo(@157);
                make.width.equalTo(@280);
            }];
        }
    }
    
    [self.shareImageView mas_makeConstraints:^(MASConstraintMaker *make) {
        make.trailing.equalTo(self.shareButton.mas_leading).offset(-5);
        make.centerY.equalTo(self.shareButton);
        make.width.height.equalTo(@16);
    }];
    
    [self.shareButton mas_makeConstraints:^(MASConstraintMaker *make) {
        make.top.equalTo(self.imagesScrollView.mas_bottom).offset(8);
        make.trailing.equalTo(self).offset(-15);
    }];
    
    [self.aboutHereLabel mas_makeConstraints:^(MASConstraintMaker *make) {
        make.leading.equalTo(self).offset(15);
        make.top.equalTo(self.shareImageView.mas_bottom).offset(14);
    }];
    
    [self.tagsCollectionView mas_makeConstraints:^(MASConstraintMaker *make) {
        make.leading.equalTo(self).offset(15);
        make.top.equalTo(self.aboutHereLabel.mas_bottom).offset(15);
        make.trailing.equalTo(self).offset(-15);
        make.height.equalTo(@56);
    }];
    
    self.navigationBtn.frame = CGRectMake(SCREEN_WIDTH - 15.6 - 54, self.placeNameLabel.bottom + 5, 54, 18);
}

- (void)loadDataWithPlaceDetailItem:(CQUPTMapPlaceDetailItem *)detailItem {
    if (detailItem == nil || [detailItem isEqual:NSNull.null]) { return; }
    self.detailItem = detailItem;
    
    if(![detailItem.placeAttributesArray isEqual:[NSNull null]]){
        if (detailItem.placeAttributesArray.count > 0) {
            for (NSString *placeAttribute in detailItem.placeAttributesArray) {
                UILabel *attributeLabel = [[UILabel alloc] initWithFrame:CGRectZero];
                attributeLabel.text = placeAttribute;
                attributeLabel.font = [UIFont fontWithName:PingFangSCMedium size:12];
                attributeLabel.layer.cornerRadius = 9;
                attributeLabel.layer.borderWidth = 1;
                attributeLabel.textAlignment = NSTextAlignmentCenter;
                attributeLabel.alpha = 0;
                if (@available(iOS 11.0, *)) {
                    attributeLabel.textColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#778AA9" alpha:1]
                                                                     darkColor:[UIColor colorWithHexString:@"#A1A1A1" alpha:1]];
                } else {
                    attributeLabel.textColor = [UIColor colorWithHexString:@"778AA9"];
                }
                attributeLabel.layer.borderColor = attributeLabel.textColor.CGColor;
                [self addSubview:attributeLabel];
                [self.attributesLabelArray addObject:attributeLabel];
                
            }
        }
    }
    
    if([detailItem.imagesArray isEqual:[NSNull null]] || detailItem.imagesArray.count == 0){
        UIImageView *placeImageView = [[UIImageView alloc] init];
        placeImageView.layer.cornerRadius = 9;
        placeImageView.clipsToBounds = YES;
        placeImageView.image = [UIImage imageNamed:@"PlaceHolderImage"];
        placeImageView.contentMode = UIViewContentModeScaleAspectFill;
        [self.imagesScrollView addSubview:placeImageView];
        [self.imagesArray addObject:placeImageView];
    }
    
    if(![detailItem.imagesArray isEqual:[NSNull null]]){
        for (int i = 0; i < detailItem.imagesArray.count; i++) {
            UIImageView *placeImageView = [[UIImageView alloc] init];
            
            NSURL *placeUrl = [NSURL URLWithString:detailItem.imagesArray[i]];
            
            placeImageView.contentMode = UIViewContentModeScaleAspectFill;
            [placeImageView sd_setImageWithURL:placeUrl placeholderImage:[UIImage imageNamed:@"PlaceHolderImage"] options:SDWebImageScaleDownLargeImages completed:^(UIImage * _Nullable image, NSError * _Nullable error, SDImageCacheType cacheType, NSURL * _Nullable imageURL) {
                
            }];
            
            placeImageView.layer.cornerRadius = 9;
            placeImageView.clipsToBounds = YES;
            [self.imagesScrollView addSubview:placeImageView];
            [self.imagesArray addObject:placeImageView];
        }
    }
    
    [self.tagsCollectionView reloadData];
    
    [self layoutIfNeeded];
    
    [UIView animateWithDuration:0.25 animations:^{
        for (UILabel *attributeLabel in self.attributesLabelArray) {
            attributeLabel.alpha = 1;
        }
    }];
}

- (CGFloat)attributeLabelWidthText:(NSString *)text {
    NSDictionary *dic = @{NSFontAttributeName: [UIFont fontWithName:PingFangSCSemibold size:15]};

    CGRect rect = [text boundingRectWithSize:CGSizeMake(0, 10) options:NSStringDrawingUsesLineFragmentOrigin | NSStringDrawingUsesFontLeading attributes:dic context:nil];

    return rect.size.width;
}

- (NSInteger)numberOfSectionsInCollectionView:(UICollectionView *)collectionView {
    return 1;
}

- (NSInteger)collectionView:(UICollectionView *)collectionView numberOfItemsInSection:(NSInteger)section {
    if (![self.detailItem.tagsArray isEqual:[NSNull null]] && self.detailItem.tagsArray.count) {
        return self.detailItem.tagsArray.count;
    } else {
        return 1;
    }
}

- (UICollectionViewCell *)collectionView:(UICollectionView *)collectionView cellForItemAtIndexPath:(NSIndexPath *)indexPath {
    CQUPTMapDetailTagsCollectionViewCell *cell = [collectionView dequeueReusableCellWithReuseIdentifier:@"CQUPTMapDetailTagsCollectionViewCell" forIndexPath:indexPath];
    if ([self.detailItem.tagsArray isEqual:[NSNull null]] || self.detailItem.tagsArray.count == 0) {
        cell.tagLabel.text = @"暂无信息";
    } else {
        cell.tagLabel.text = self.detailItem.tagsArray[indexPath.item];
    }
    return cell;
}

- (CGSize)collectionView:(UICollectionView *)collectionView layout:(nonnull UICollectionViewLayout *)collectionViewLayout sizeForItemAtIndexPath:(nonnull NSIndexPath *)indexPath {
    if ([self.detailItem.tagsArray isEqual:[NSNull null]] || self.detailItem.tagsArray.count == 0) {
        return CGSizeMake([self attributeLabelWidthText:@"暂无信息"] + 5, 22);
    }
    return CGSizeMake([self attributeLabelWidthText:self.detailItem.tagsArray[indexPath.item]] + 5, 22);
}

// 垂直间距
- (CGFloat)collectionView:(UICollectionView *)collectionView layout:(nonnull UICollectionViewLayout *)collectionViewLayout minimumLineSpacingForSectionAtIndex:(NSInteger)section {
    return 12;
}

// 水平间距
- (CGFloat)collectionView:(UICollectionView *)collectionView layout:(nonnull UICollectionViewLayout *)collectionViewLayout minimumInteritemSpacingForSectionAtIndex:(NSInteger)section {
    return 1;
}

- (void)showMoreButtonTapped {
    if ([self.detailItem.imagesArray isEqual:[NSNull null]] || self.detailItem.imagesArray.count == 0) {
        MBProgressHUD *hud = [MBProgressHUD showHUDAddedTo:self.viewController.view animated:YES];
        hud.mode = MBProgressHUDModeText;
        hud.labelText = @"暂时还没有图片哦";
        [hud hide:YES afterDelay:1.2];
        
        return;
    }
    CQUPTMapMoreImageViewController *vc = [[CQUPTMapMoreImageViewController alloc] initWithPlaceDetailItem:self.detailItem];
    [self.viewController.navigationController pushViewController:vc animated:YES];
}

- (void)shareImageButtonTapped {
    UIAlertController *alertController = [UIAlertController alertControllerWithTitle:@"共享照片" message:@"在这里，与邮子们共同分享你们所拍的校园风景。上传你的照片，优质照片有机会在此展示。" preferredStyle:UIAlertControllerStyleAlert];
    
    UIAlertAction *cancelAction = [UIAlertAction actionWithTitle:@"取消" style:UIAlertActionStyleCancel handler:nil];
    
    UIAlertAction *certainAction = [UIAlertAction actionWithTitle:@"确定" style:UIAlertActionStyleDefault handler:^(UIAlertAction * _Nonnull action) {
        
        TZImagePickerController *imagePickerVc = [[TZImagePickerController alloc] initWithMaxImagesCount:10 delegate:self];
        [imagePickerVc setDidFinishPickingPhotosHandle:^(NSArray<UIImage *> *photos, NSArray *assets, BOOL isSelectOriginalPhoto) {
            [self.viewController dismissViewControllerAnimated:YES completion:^{
                
                UIAlertController *uploadAlertController = [UIAlertController alertControllerWithTitle:@"确认上传" message:@"确认后您选择的图片将被上传，审核通过后就可以展示啦。" preferredStyle:UIAlertControllerStyleAlert];
                UIAlertAction *uploadCancelAction = [UIAlertAction actionWithTitle:@"取消" style:UIAlertActionStyleCancel handler:nil];
                
                UIAlertAction *uploadCertainAction = [UIAlertAction actionWithTitle:@"确定" style:UIAlertActionStyleDefault handler:^(UIAlertAction * _Nonnull action) {
                    // 上传图片
                    
                    NSDictionary *params = @{
                        @"place_id": self.detailItem.placeID
                    };
                    
                    NSMutableArray *names = [@[] mutableCopy];
                    for (int i = 0; i < photos.count; i++) {
                        [names addObject:@"file"];
                    }
                    
                    [HttpTool.shareTool
                     form:Discover_POST_cquptMapUploadMage_API
                     type:HttpToolRequestTypePost
                     parameters:params
                     bodyConstructing:^(id<AFMultipartFormData>  _Nonnull body) {
                        for (int i = 0; i < photos.count; i++) {
                            UIImage *image = photos[i];
                            UIImage *image1 = [image cropEqualScaleImageToSize:image.size isScale:YES];
                            NSData *data = UIImageJPEGRepresentation(image1, 0.8);
                            NSString *fileName = [NSString stringWithFormat:@"%ld.png", [NSDate nowTimestamp]];
                            [body appendPartWithFileData:data name:names[i] fileName:fileName mimeType:@"image/png"];
                        }
                    }
                     progress:nil
                     success:^(NSURLSessionDataTask * _Nonnull task, id  _Nullable object) {
                        NSLog(@"图片上传成功");
                    }
                     failure:^(NSURLSessionDataTask * _Nullable task, NSError * _Nonnull error) {
                        NSLog(@"图片上传失败，错误信息：\n %@", error);
                    }];    
                }];
                
                [uploadAlertController addAction:uploadCancelAction];
                [uploadAlertController addAction:uploadCertainAction];
                
                [self.viewController presentViewController:uploadAlertController animated:YES completion:nil];
            }];
            
        }];
        imagePickerVc.modalPresentationStyle = UIModalPresentationFullScreen;
        [self.viewController presentViewController:imagePickerVc animated:YES completion:nil];
        
    }];
    
    [alertController addAction:cancelAction];
    [alertController addAction:certainAction];
    
    [self.viewController presentViewController:alertController animated:YES completion:nil];
}

- (void)starButtonTapped:(UIButton *)sender {
    if (!self.detailItem) { return; }
    
    if (sender.selected) {
        // 取消收藏
        [CQUPTMapModel deleteStarPlaceWithPlaceID:self.detailItem.placeID];
    } else {
        // 收藏
        [CQUPTMapModel starPlaceWithPlaceID:self.detailItem.placeID];
    }
    sender.selected = !sender.selected;
}

// 点击导航跳转百度地图
- (void)clickNaviBtn {
    NSString *destination = [@"重庆邮电大学" stringByAppendingString:self.placeNameLabel.labelText];
    NSString *urlString = [NSString stringWithFormat:@"baidumap://map/direction?destination=name:%@&mode=walking", destination];
    NSURL *url = [NSURL URLWithString:urlString];
    
    if ([[UIApplication sharedApplication] canOpenURL:url]) {
        [[UIApplication sharedApplication] openURL:url options:@{} completionHandler:nil];
    } else {
        NSString *encodedStr = [NSString stringWithFormat:@"%@",
                                [destination stringByAddingPercentEncodingWithAllowedCharacters:NSCharacterSet.URLQueryAllowedCharacterSet]];
        NSString *webUrlStr = [NSString stringWithFormat:@"http://api.map.baidu.com/geocoder?address=$%@&output=html&src=webapp.baidu.openAPIdemo", encodedStr];
        NSURL *webUrl = [NSURL URLWithString:webUrlStr];
        [[UIApplication sharedApplication] openURL:webUrl options:@{} completionHandler:nil];
    }
}

- (UIButton *)navigationBtn {
    if (!_navigationBtn) {
        _navigationBtn = [[UIButton alloc] init];
        [_navigationBtn setTitle:@"导航" forState:UIControlStateNormal];
        [_navigationBtn setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
        [_navigationBtn.titleLabel setFont:[UIFont systemFontOfSize:12]];
        _navigationBtn.layer.cornerRadius = 8.8;
        _navigationBtn.clipsToBounds = YES;
        _navigationBtn.backgroundColor = [UIColor dm_colorWithLightColor:[UIColor colorWithHexString:@"#3603EC" alpha:1] darkColor:[UIColor colorWithHexString:@"#3603EC" alpha:1]];
        [_navigationBtn addTarget:self action:@selector(clickNaviBtn) forControlEvents:UIControlEventTouchUpInside];
    }
    return _navigationBtn;
}

@end

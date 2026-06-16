//
//  EditMyInfoViewController.m
//  CyxbsMobile2019_iOS
//
//  Created by 方昱恒 on 2019/11/14.
//  Copyright © 2019 Redrock. All rights reserved.
//

#import "EditMyInfoViewController.h"
#import "EditMyInfoContentView.h"
#import "EditMyInfoPresenter.h"
#import "UserInformationIntorductionView.h"
#import "RemindHud.h"
#import "掌上重邮-Swift.h"

@interface EditMyInfoViewController ()
<
EditMyInfoContentViewDelegate,
UIScrollViewDelegate,
UINavigationControllerDelegate,
UIImagePickerControllerDelegate
>

@property (nonatomic, strong) EditMyInfoPresenter *presenter;

@property (nonatomic, assign) BOOL profileChanged;

@end

@implementation EditMyInfoViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    
    self.presenter = [[EditMyInfoPresenter alloc] init];
    [self.presenter attachView:self];
    
    self.titleColor = [UIColor dm_colorWithLightColor:RGBColor(21, 49, 91, 1) darkColor:KUIColorFromRGB(0xf0f0f2)];
    self.VCTitleStr = @"资料详情";
    self.view.backgroundColor = [UIColor dm_colorWithLightColor:UIColor.whiteColor darkColor:UIColor.blackColor];
    self.backBtnImage = [UIImage imageNamed:@"navBar_back"];
    
    EditMyInfoContentView *contentView = [[EditMyInfoContentView alloc] init];
    [self.view addSubview:contentView];
    self.contentView = contentView;
    contentView.delegate = self;
    contentView.contentScrollView.delegate = self;
    [self.contentView mas_makeConstraints:^(MASConstraintMaker *make) {
        make.bottom.leading.trailing.equalTo(self.view);
        make.top.mas_equalTo(self.topBarView.mas_bottom);
    }];
}

- (void)dealloc
{
    [self.presenter dettatchView];
}


#pragma mark - scrollView代理回调
- (void)scrollViewWillBeginDragging:(UIScrollView *)scrollView {
    [self.view endEditing:YES];
}

- (void)headerImageTapped:(UIImageView *)sender {
    UIImagePickerController *controller = [[UIImagePickerController alloc] init];
    controller.allowsEditing = YES;
    controller.delegate = self;
    [self presentViewController:controller animated:YES completion:nil];
}

#pragma mark - ImagePickerControllerDelegate

- (void)imagePickerController:(UIImagePickerController *)picker didFinishPickingMediaWithInfo:(NSDictionary< UIImagePickerControllerInfoKey,id> *)info {
    UIImage *image = info[UIImagePickerControllerEditedImage];
    self.contentView.headerImageView.image = image;
    self.profileChanged = YES;
    self.backBtn.enabled = NO;
    [self.presenter uploadProfile:self.contentView.headerImageView.image Success:^{
        self.backBtn.enabled = YES;
        [RemindHUD.shared showDefaultHUDWithText:@"头像上传成功" completion:^{
                    [self dismissViewControllerAnimated:YES completion:nil];
        }];
        [UpdatePersonModelTool.shared update];
    } failure:^(NSError * _Nonnull error) {
        self.backBtn.enabled = YES;
        [RemindHUD.shared showDefaultHUDWithText:@"头像上传失败" completion:^{
                    [self dismissViewControllerAnimated:YES completion:nil];
        }];
    }];
    
}

- (void)showUserInformationIntroduction:(UIButton *)sender {
    UserInformationIntorductionView *introductionView = [[UserInformationIntorductionView alloc] initWithFrame:CGRectMake(0, 0, self.contentView.width, self.contentView.height)];
    [self.contentView addSubview:introductionView];
    
    introductionView.alpha = 0;
    [UIView animateWithDuration:0.3 animations:^{
        introductionView.alpha = 1;
    }];
}

@end

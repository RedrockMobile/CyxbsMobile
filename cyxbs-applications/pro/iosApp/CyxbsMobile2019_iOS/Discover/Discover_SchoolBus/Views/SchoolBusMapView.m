//
//  SchoolBusMapView.m
//  CyxbsMobile2019_iOS
//
//  Created by Clutch Powers on 2022/4/7.
//  Copyright © 2022 Redrock. All rights reserved.
//

#import "SchoolBusMapView.h"
#import <Masonry/Masonry.h>
#import "BusMAPointAnnotation.h"
#import "StationMAPointAnnotation.h"
#import "CircleMAPointAnnotation.h"

@implementation SchoolBusMapView

- (instancetype)initWithFrame:(CGRect)frame
{
    self = [super initWithFrame:frame];
    if (self) {
        //更新App是否显示隐私弹窗的状态，隐私弹窗是否包含高德SDK隐私协议内容的状态
        [MAMapView updatePrivacyShow:AMapPrivacyShowStatusDidShow privacyInfo:AMapPrivacyInfoStatusDidContain];
        //更新用户授权高德SDK隐私协议状态
        [MAMapView updatePrivacyAgree:AMapPrivacyAgreeStatusDidAgree];
        [self addSubview:self.mapView];
        [self setLocationRepresentation];
        [self addSubview:self.backBtn];
        [self refreshSchoolBusData];
    }
    return self;
}

- (void)setSchoolBusPointArray:(NSArray *)schoolBusPointArray{
    
    if (_schoolBusPointArray) {
        [_mapView removeAnnotations:self.schoolBusPointArray];
    }
    
    _schoolBusPointArray = schoolBusPointArray;
    
    [_mapView addAnnotations:_schoolBusPointArray];
    
}


- (void)removeOldAnnotationsAndaddNew:(NSArray *)aStationAry {
    [_mapView removeAnnotations:self.stationPointArray];
    self.stationPointArray = aStationAry;
    [_mapView addAnnotations: self.stationPointArray];
}

- (MAMapView *)mapView{
    if (!_mapView) {
        
        [AMapServices sharedServices].apiKey = @"0de229ab86861128f7fec123538aa109";
        [[AMapServices sharedServices] setEnableHTTPS:YES];
        // remember
//        [MAMapView updatePrivacyShow:AMapPrivacyShowStatusDidShow privacyInfo:AMapPrivacyInfoStatusDidContain];
//        [MAMapView updatePrivacyAgree:AMapPrivacyAgreeStatusDidAgree];
        
        MAMapView *mapView = [[MAMapView alloc] initWithFrame:self.bounds];
        mapView.showsUserLocation = YES;
        mapView.userTrackingMode = MAUserTrackingModeFollow;
        mapView.zoomLevel = 17.4;
        //中心点位置
        mapView.centerCoordinate = CLLocationCoordinate2DMake(29.529332, 106.607517);
        mapView.scaleOrigin = CGPointMake(50, STATUSBARHEIGHT + 10);
        mapView.showsCompass = NO;
        mapView.delegate = self;
        
        if([CLLocationManager locationServicesEnabled]){
            AMapLocationManager *locationManager = [[AMapLocationManager alloc] init];

            [locationManager setDelegate:self];
            
            //是否允许后台定位。默认为NO。只在iOS 9.0及之后起作用。设置为YES的时候必须保证 Background Modes 中的 Location updates 处于选中状态，否则会抛出异常。由于iOS系统限制，需要在定位未开始之前或定位停止之后，修改该属性的值才会有效果。
            [locationManager setAllowsBackgroundLocationUpdates:NO];
            
            //指定定位是否会被系统自动暂停。默认为NO。
            [locationManager setPausesLocationUpdatesAutomatically:NO];
            
            //设定定位的最小更新距离。单位米，默认为 kCLDistanceFilterNone，表示只要检测到设备位置发生变化就会更新位置信息
            [locationManager setDistanceFilter:1];
            
            //设定期望的定位精度。单位米，默认为 kCLLocationAccuracyBest
            [locationManager setDesiredAccuracy:kCLLocationAccuracyBest];
            
            //开始定位服务
            [locationManager startUpdatingLocation];

        }
        _mapView = mapView;
        
    }
    return _mapView;
}


- (UIButton *)backBtn{
    if (!_backBtn) {
        UIButton *backBtn = [UIButton buttonWithType:UIButtonTypeSystem];
        backBtn.frame = CGRectMake(17, STATUSBARHEIGHT + 13, 19, 19);
        backBtn.imageEdgeInsets = UIEdgeInsetsMake(0, 0, 0, 10);
        [backBtn setImage:[UIImage imageNamed:@"Publish_backBtn"] forState:UIControlStateNormal];
        [backBtn setTintColor:[UIColor grayColor]];
        [backBtn addTarget:self.delegate action:@selector(backBtnClicked) forControlEvents:UIControlEventTouchUpInside];
        _backBtn = backBtn;
    }
    return _backBtn;
}



- (void)refreshSchoolBusData{

    
    if (!_timer) {
        self.timer = [NSTimer scheduledTimerWithTimeInterval:2 target:[YYWeakProxy proxyWithTarget:self] selector:@selector(setupSchoolBusData) userInfo:nil repeats:YES];
//        [[NSRunLoop mainRunLoop] addTimer:self.timer forMode:NSDefaultRunLoopMode];
    }

}

//将原始数据转换为地图上的标记点
- (void)setupSchoolBusData{
    [SchoolBusData SchoolBusDataWithSuccess:^(NSArray * _Nonnull array) {
        NSMutableArray *mArray = [NSMutableArray array];
        for (int i = 0; i < array.count; i++) {
            MAPointAnnotation *pointAnnotation = [[MAPointAnnotation alloc] init];
            SchoolBusData *data = array[i];
            pointAnnotation.coordinate = CLLocationCoordinate2DMake(data.latitude, data.longitude);
            pointAnnotation.title = [NSString stringWithFormat:@"BusID : %d",data.busID];
            pointAnnotation.subtitle = [NSString stringWithFormat:@"Line:%d",data.type+1];
            [mArray addObject:pointAnnotation];
        }
        self.schoolBusPointArray = mArray;
        } error:^{

        }];
}


- (MAAnnotationView *)mapView:(MAMapView *)mapView viewForAnnotation:(id<MAAnnotation>)annotation{
    static NSString *pointReuseIndentifier = @"pointReuseIndentifier";
    MAPinAnnotationView *annotationView = (MAPinAnnotationView*)[mapView dequeueReusableAnnotationViewWithIdentifier:pointReuseIndentifier];
    
    if (annotationView == nil)
    {
        annotationView = [[MAPinAnnotationView alloc] initWithAnnotation:annotation reuseIdentifier:pointReuseIndentifier];
    }
    if ([annotation isKindOfClass: [MAUserLocation class]]){
        return nil;
    }else {
        annotationView.canShowCallout= YES;       //设置气泡可以弹出，默认为NO
        annotationView.animatesDrop = NO;        //设置标注动画显示，默认为NO
        annotationView.draggable = NO;        //设置标注可以拖动，默认为NO
        
        if ([annotation isKindOfClass:[StationMAPointAnnotation class]] ) {
            
            annotationView.image = [UIImage imageNamed:@"station.purple"];
            return annotationView;
        }
        else if ([annotation isKindOfClass: [CircleMAPointAnnotation class]]) {
            
            if ([annotation.subtitle isEqual:@"1号线"]) {
                annotationView.image = [UIImage imageNamed:@"circle.pink"];
            }
            else if ([annotation.subtitle isEqual:@"2号线"]){
                annotationView.image = [UIImage imageNamed:@"circle.orange"];
            }
            else if ([annotation.subtitle isEqual:@"3号线"]){
                annotationView.image = [UIImage imageNamed:@"circle.blue"];
            }
            else if ([annotation.subtitle isEqual:@"4号线"]){
                annotationView.image = [UIImage imageNamed:@"circle.green"];
            }
            return annotationView;
        }
        else if ([annotation isKindOfClass:[MAPointAnnotation class]] && [annotation.subtitle  isEqual: @"1号线"] ) {
            
            annotationView.image = [UIImage imageNamed:@"station.pink"];
            
            return annotationView;
        }
        else if ([annotation isKindOfClass:[MAPointAnnotation class]] && [annotation.subtitle  isEqual: @"2号线"]) {
            annotationView.image = [UIImage imageNamed:@"station.orange"];
            return annotationView;
        }
        else if ([annotation isKindOfClass:[MAPointAnnotation class]] && [annotation.subtitle  isEqual: @"3号线"]) {
        
            annotationView.image = [UIImage imageNamed:@"station.blue"];
            return annotationView;
        }
        else if ([annotation isKindOfClass:[MAPointAnnotation class]] && [annotation.subtitle  isEqual: @"4号线"]) {
            
            annotationView.image = [UIImage imageNamed:@"station.green"];
            return annotationView;
        }
        else if ([annotation isKindOfClass:[MAPointAnnotation class]] && [annotation.subtitle isEqual:@"Line:1"])
        {
                    
                    annotationView.image = [UIImage imageNamed:@"schoolbus.pink"];
                    return annotationView;
        }else if ([annotation isKindOfClass:[MAPointAnnotation class]] && [annotation.subtitle isEqual:@"Line:2"])
        {
                    
                    annotationView.image = [UIImage imageNamed:@"schoolbus.orange"];
                    return annotationView;
        }else if ([annotation isKindOfClass:[MAPointAnnotation class]] && [annotation.subtitle isEqual:@"Line:3"])
        {
                    
                    annotationView.image = [UIImage imageNamed:@"schoolbus.blue"];
                    return annotationView;
        }else if ([annotation isKindOfClass:[MAPointAnnotation class]] && [annotation.subtitle isEqual:@"Line:4"])
        {
                    annotationView.image = [UIImage imageNamed:@"schoolbus.green"];
                    return annotationView;
        }
    }
    return nil;
}

- (void)mapView:(MAMapView *)mapView didSelectAnnotationView:(MAAnnotationView *)view {
    if (self.delegate) {
        [self.delegate schoolBusMapView:self didSelectedLinesWithAnnotationView:view];
    }
    
}

//设置myposion图片
- (void)setLocationRepresentation{
    MAUserLocationRepresentation *r = [[MAUserLocationRepresentation alloc] init];
    r.showsAccuracyRing = YES;///精度圈是否显示，默认YES
    r.image = [UIImage imageNamed:@"MyPosition"]; ///定位图标, 与蓝色原点互斥
    [_mapView updateUserLocationRepresentation:r];
}
    

@end

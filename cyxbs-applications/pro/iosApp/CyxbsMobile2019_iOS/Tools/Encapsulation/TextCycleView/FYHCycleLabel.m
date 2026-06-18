//
//  FYHScrollLabel.m
//  CyxbsMobile2019_iOS
//
//  Created by 方昱恒 on 2020/8/19.
//  Copyright © 2020 Redrock. All rights reserved.
//

#import "FYHCycleLabel.h"

@interface FYHCycleLabel ()

@property (nonatomic, strong) UILabel *secondLabel;
@property (nonatomic, strong) NSTimer *myTimer;

@end

@implementation FYHCycleLabel

- (instancetype)initWithFrame:(CGRect)frame{
    
    if (self = [super initWithFrame:frame]) {
        [self addSubview:self.cycleLabel];
        self.layer.masksToBounds = YES;
    }
    
    return self;
}

- (void)setLabelText:(NSString *)labelText{
    
    [self.myTimer invalidate];
    self.myTimer = nil;
    [self.secondLabel removeFromSuperview];
    
    self.cycleLabel.frame = CGRectMake(0, 0, self.frame.size.width, self.frame.size.height);
    
    _labelText = labelText;
    self.cycleLabel.text = _labelText;
    
    CGRect rect = [_labelText boundingRectWithSize:CGSizeMake(10000, self.frame.size.height) options:NSStringDrawingUsesLineFragmentOrigin attributes:@{NSFontAttributeName:self.cycleLabel.font} context:nil];
    
    if (rect.size.width > self.frame.size.width) {
        self.cycleLabel.frame = CGRectMake(0, 0, rect.size.width + 30, self.frame.size.height);
        [self createSecondLabel];
        self.myTimer = [NSTimer scheduledTimerWithTimeInterval:0.025 target:self selector:@selector(startFontCycle) userInfo:nil repeats:YES];
    }
}

- (void)startFontCycle{
    self.cycleLabel.frame = CGRectMake(self.cycleLabel.frame.origin.x - 0.5, 0, self.cycleLabel.frame.size.width, self.cycleLabel.frame.size.height);
    self.secondLabel.frame = CGRectMake(CGRectGetMaxX(self.cycleLabel.frame), 0, self.secondLabel.frame.size.width, self.secondLabel.frame.size.height);
    
    if (CGRectGetMaxX(self.cycleLabel.frame) < 0) {
        
        UILabel *tempLabel;
        tempLabel = self.cycleLabel;
        self.cycleLabel = self.secondLabel;
        self.secondLabel = tempLabel;
    }
}



- (void)createSecondLabel{
    
    if (self.secondLabel) {
        self.secondLabel = nil;
    }
    
    self.secondLabel = [[UILabel alloc] initWithFrame:CGRectMake(self.cycleLabel.frame.size.width, 0, self.cycleLabel.frame.size.width, self.frame.size.height)];
    self.secondLabel.text = self.cycleLabel.text;
    self.secondLabel.font = self.cycleLabel.font;
    self.secondLabel.textColor = self.cycleLabel.textColor;
    
    [self addSubview:self.secondLabel];
}

- (UILabel *)cycleLabel{
    
    if (!_cycleLabel) {
        _cycleLabel = [[UILabel alloc] initWithFrame:CGRectMake(0, 0, self.frame.size.width, self.frame.size.height)];
    }
    
    return _cycleLabel;
}


- (void)dealloc{
    [self.myTimer invalidate];
    self.myTimer = nil;
}

@end

//
//  CyxbsMobile2019_watchOS_Watch_AppUITestsLaunchTests.swift
//  CyxbsMobile2019_watchOS Watch AppUITests
//
//  Created by Holeon on 2025/11/6.
//  Copyright © 2025 Redrock. All rights reserved.
//

import XCTest

final class CyxbsMobile2019_watchOS_Watch_AppUITestsLaunchTests: XCTestCase {

    override class var runsForEachTargetApplicationUIConfiguration: Bool {
        true
    }

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testLaunch() throws {
        let app = XCUIApplication()
        app.launch()

        // Insert steps here to perform after app launch but before taking a screenshot,
        // such as logging into a test account or navigating somewhere in the app

        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = "Launch Screen"
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}

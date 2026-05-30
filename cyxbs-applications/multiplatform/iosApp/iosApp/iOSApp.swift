import SwiftUI
import CyxbsApplicationsMultiplatform

@main
struct iOSApp: App {

    init() {
        IOSAppKt.doInitApp(impl: KmpInterfaceImpl()) // Kotlin Multiplatform 工程初始化
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

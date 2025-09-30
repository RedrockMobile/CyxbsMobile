import SwiftUI
import CyxbsApplicationsTest

@main
struct iOSApp: App {

    init() {
        IOSAppKt.doInitApp(isDebug: isDebug()) // Kotlin Multiplatform 工程初始化
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
    
    func isDebug() -> Bool {
        #if DEBUG
        return true
        #else
        return false
        #endif
    }
}

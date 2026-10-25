import SwiftUI

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(FunnyMailAppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
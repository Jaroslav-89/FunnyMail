package com.jaroapps.funnymail

import platform.UIKit.UIDevice
import platform.Foundation.NSLocale

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun getDeviceLanguageCode(): String = NSLocale.currentLocale.languageCode
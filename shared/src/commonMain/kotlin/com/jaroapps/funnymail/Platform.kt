package com.jaroapps.funnymail

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

expect fun getDeviceLanguageCode(): String
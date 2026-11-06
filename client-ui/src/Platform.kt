package ru.haykor.sudv.client.ui

enum class Platform(val platformName: String) {
    ANDROID("Android"), WEB("Web")
}

expect fun getPlatform(): Platform
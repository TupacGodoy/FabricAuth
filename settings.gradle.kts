pluginManagement {
    repositories {
        maven ("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases")
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.6.1"
}

stonecutter {
    create(rootProject) {
        versions("1.20.5", "1.21", "1.21.2", "1.21.5", "1.21.6")
        vcsVersion = "1.21.6"
    }
}
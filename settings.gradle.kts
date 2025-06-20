pluginManagement {
    repositories {
        maven ( url = "https://maven.fabricmc.net/" )
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.6.1"
}

stonecutter {
    create(rootProject) {
        versions("1.21.2", "1.21.5", "1.21.6")
        vcsVersion = "1.21.6"
    }
}
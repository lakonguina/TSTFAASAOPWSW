pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.mozilla.org/maven2/") // GeckoView (le navigateur embarqué)
    }
}

rootProject.name = "ForceList"
include(":app")

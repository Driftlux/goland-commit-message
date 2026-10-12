val mirrors = listOf(
    "https://mirrors.cloud.tencent.com/nexus/repository/maven-public/",
    "https://maven.aliyun.com/repository/public",
)

allprojects {
    buildscript {
        repositories {
            mirrors.forEach { maven(it) }
            mavenCentral()
            gradlePluginPortal()
        }
    }
    repositories {
        mirrors.forEach { maven(it) }
        mavenCentral()
    }
}

settingsEvaluated {
    pluginManagement.repositories.apply {
        mirrors.forEach { maven(it) }
        mavenCentral()
        gradlePluginPortal()
    }
    dependencyResolutionManagement.repositories.apply {
        mirrors.forEach { maven(it) }
    }
}

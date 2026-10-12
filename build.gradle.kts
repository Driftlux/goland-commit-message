plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform")
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    intellijPlatform {
        local("/Applications/GoLand.app")
        bundledPlugin("Git4Idea")
        bundledModule("com.intellij.modules.vcs")
    }

    testImplementation(kotlin("test"))
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<Zip>("buildPlugin") {
    from("LICENSE") {
        into(rootProject.name)
    }
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.intellij.platform")
}

kotlin {
    // 本机只有 GoLand 自带的 JBR 25 可用于编译，产物仍按 IDE 运行时要求输出 Java 21 字节码。
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    intellijPlatform {
        val localIdePath = providers.gradleProperty("localIdePath").orNull
        if (localIdePath == null) {
            goland("2026.1.3")
        } else {
            local(localIdePath)
        }
        bundledPlugin("Git4Idea")
        bundledModule("com.intellij.modules.vcs")
    }

    testImplementation(kotlin("test"))
}

intellijPlatform {
    buildSearchableOptions = false
    instrumentCode = false
    publishing {
        token = providers.gradleProperty("intellijPlatformPublishingToken")
    }

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
    from("LICENSE")
}

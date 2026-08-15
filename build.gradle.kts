plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "org.huxerui"
version = "0.1.6"

val clionPath = providers.gradleProperty("clionPath")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        if (!clionPath.isPresent) {
            clion("2025.3")
        } else {
            local(clionPath.get())
        }
        bundledPlugin("com.intellij.cmake")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
        pluginVerifier()
    }
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

intellijPlatform {
    pluginConfiguration {
        id = "org.huxerui"
        name = "HuxerUI"
        version = project.version.toString()
        description = """
            CLion integration for creating, configuring, navigating, building, and running HuxerUI projects.
        """.trimIndent()
        ideaVersion {
            sinceBuild = "253"
            untilBuild = "262.*"
        }
        vendor {
            name = "HuxerUI"
            url = "https://github.com/HuxerUI"
        }
    }
    pluginVerification {
        ides {
            if (clionPath.isPresent) {
                local(clionPath)
            } else {
                recommended()
            }
        }
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 21
    }
    test {
        useJUnitPlatform()
        val testTemporaryDirectory = layout.buildDirectory.dir("tmp/test-jvm")
        doFirst {
            testTemporaryDirectory.get().asFile.mkdirs()
        }
        systemProperty("java.io.tmpdir", testTemporaryDirectory.get().asFile.absolutePath)
    }
}

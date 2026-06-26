plugins {
    kotlin("jvm") version "2.1.0"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.18"
    id("com.gradleup.shadow") version "8.3.5"
}

group = "com.k1wit.starlightbot"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")

    implementation(kotlin("stdlib"))

    implementation("net.dv8tion:JDA:5.2.2") {
        exclude(module = "opus-java")
    }

    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

// Kompiliere für Java 21, passend zu Paper 1.21.x und der lokalen Gradle-Konfiguration
kotlin {
    jvmToolchain(21)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks {
    assemble {
        dependsOn(shadowJar)
    }

    shadowJar {
        archiveClassifier.set("")
        archiveFileName.set("StarlightBot-${project.version}.jar")

        relocate("net.dv8tion.jda", "com.k1wit.starlightbot.libs.jda")
        relocate("okhttp3", "com.k1wit.starlightbot.libs.okhttp3")
        relocate("okio", "com.k1wit.starlightbot.libs.okio")
        relocate("org.sqlite", "com.k1wit.starlightbot.libs.sqlite")
        relocate("org.slf4j", "com.k1wit.starlightbot.libs.slf4j")
        relocate("com.neovisionaries", "com.k1wit.starlightbot.libs.neovisionaries")
        relocate("gnu.trove", "com.k1wit.starlightbot.libs.trove")
        relocate("com.fasterxml.jackson", "com.k1wit.starlightbot.libs.jackson")
        relocate("kotlinx.coroutines", "com.k1wit.starlightbot.libs.coroutines")
        relocate("org.json", "com.k1wit.starlightbot.libs.json")

        mergeServiceFiles()

        exclude("META-INF/*.SF")
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
    }

    processResources {
        filesMatching("paper-plugin.yml") {
            expand(mapOf("version" to project.version))
        }
    }
}
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

    implementation("net.dv8tion:JDA:5.5.1") {
        exclude(module = "opus-java")
    }

    implementation("org.slf4j:slf4j-simple:2.0.16")
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
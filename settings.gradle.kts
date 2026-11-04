pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.minecraftforge.net/") { name = "MinecraftForge" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
}

stonecutter {
    create(rootProject) {
        fun target(mc: String, vararg loaders: String) =
            loaders.forEach { version("$mc-$it", mc).buildscript = "build.$it.gradle.kts" }

        fun legacy(mc: String) = version("$mc-fabric", mc).apply { buildscript = "build.fabric-legacy.gradle.kts" }

        legacy("1.21.1")
        version("1.21.1-neoforge", "1.21.1").buildscript = "build.neoforge-legacy.gradle.kts"
        legacy("1.21.11")
        legacy("1.21.8")
        legacy("1.21.4")
        legacy("1.20.1")
        target("26.2", "fabric", "neoforge", "forge")
        target("26.3", "fabric", "neoforge", "forge")
        vcsVersion = "26.3-fabric"
    }
}

rootProject.name = "vanilla_timber"

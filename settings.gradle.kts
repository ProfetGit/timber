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

        target("26.2", "fabric", "neoforge", "forge")
        target("26.3", "fabric", "neoforge", "forge")
        vcsVersion = "26.3-fabric"
    }
}

rootProject.name = "vanilla_timber"

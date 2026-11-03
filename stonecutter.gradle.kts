plugins {
    id("dev.kikugie.stonecutter")
    id("net.fabricmc.fabric-loom") version "1.18.2" apply false
    id("net.neoforged.moddev") version "2.0.147" apply false
    id("net.minecraftforge.gradle") version "7.0.40" apply false
    id("net.fabricmc.fabric-loom-remap") version "1.18.2" apply false
}

stonecutter active "26.3-fabric"

stonecutter parameters {
    constants.match(current.project.substringAfterLast('-'), "fabric", "neoforge", "forge")
}

tasks.register<Copy>("dist") {
    group = "build"
    description = "Builds every target and copies the jars into dist/"
    stonecutter.versions.forEach { v ->
        val p = project(":${v.project}")
        dependsOn(p.tasks.named("build"))
        from(p.layout.buildDirectory.dir("libs")) {
            include("vanilla_timber-*.jar")
            exclude("*-sources.jar", "*-dev.jar")
        }
    }
    into(layout.projectDirectory.dir("dist"))
    doFirst { delete(layout.projectDirectory.dir("dist")) }
}

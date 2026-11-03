plugins {
    id("net.fabricmc.fabric-loom-remap")
}

val mc = stonecutter.current.version
val modId = property("mod.id") as String

version = "${property("mod.version")}+$mc-fabric"
group = property("mod.group") as String
base.archivesName = modId

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    mappings(loom.officialMojangMappings())
    annotationProcessor("net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7")
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    mixin.useLegacyMixinAp = true
    runs.named("server") {
        server()
        runDir = "run-server"
    }
    runs.named("client") {
        client()
        runDir = "run"
        programArgs("--username", "Builder")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "2000"))
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    exclude("META-INF/neoforge.mods.toml", "META-INF/mods.toml")
    val props = mapOf(
        "version" to project.version.toString(),
        "mc" to mc,
        "name" to project.property("mod.name"),
        "description" to project.property("mod.description"),
        "author" to project.property("mod.author"),
        "homepage" to project.property("mod.homepage"),
        "fabric_loader" to project.property("deps.fabric_loader"),
        "mc_range" to ((findProperty("deps.mc_range") as String?) ?: "~$mc"),
        "java" to "21",
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
    // the 1.3.6 sampler's trees, for the self-test scenes
    from(rootProject.file("dev/fixtures/trees")) { into("selftest/trees") }
}

tasks.named<Jar>("jar") {
    from(rootProject.file("LICENSE"))
}

extra["mcVersion"] = mc
apply(from = rootProject.file("../../tools/Backport/renames.gradle.kts"))

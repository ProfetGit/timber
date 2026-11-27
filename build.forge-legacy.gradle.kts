// Forge 1.21.x build (ForgeGradle 7; Forge 1.21.6+ needs it, FG6 cannot set up Forge 61): build.forge.gradle.kts plus Java 21 and the dev/renames.json pass.
plugins {
    id("net.minecraftforge.gradle")
}

val mc = stonecutter.current.version
val modId = property("mod.id") as String
val mcNext = mc.split(".").let { "${it[0]}.${it[1].toInt() + 1}" }

version = "${property("mod.version")}+$mc-forge"
group = property("mod.group") as String
base.archivesName = modId

minecraft {
    runs {
        configureEach {
            workingDir.convention(layout.projectDirectory.dir("run"))
            args("--mixin.config=$modId.mixins.json")
        }
        register("client") {
            args("--username", "Builder")
        }
    }
}

repositories {
    minecraft.mavenizer(this)
    maven(fg.forgeMaven)
    maven(fg.minecraftLibsMaven)
    mavenCentral()
}

dependencies {
    implementation(minecraft.dependency("net.minecraftforge:forge:$mc-${property("deps.forge")}"))
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    exclude("fabric.mod.json", "META-INF/neoforge.mods.toml")
    val props = mapOf(
        "version" to project.version.toString(),
        "mc" to mc,
        "mc_next" to mcNext,
        "name" to project.property("mod.name"),
        "description" to project.property("mod.description"),
        "author" to project.property("mod.author"),
        "homepage" to project.property("mod.homepage"),
    )
    inputs.properties(props)
    filesMatching("META-INF/mods.toml") { expand(props) }
    // the 1.3.6 sampler's trees, for the self-test scenes
    from(rootProject.file("dev/fixtures/trees")) { into("selftest/trees") }
}

tasks.named<Jar>("jar") {
    manifest {
        attributes["MixinConfigs"] = "$modId.mixins.json"
    }
    from(rootProject.file("LICENSE"))
}

extra["mcVersion"] = mc
apply(from = rootProject.file("../../tools/Backport/renames.gradle.kts"))

// The shared Profet settings screen, nested in the jar (tools/ProfetSettings/STATUS.md)
apply(from = rootProject.file("../../tools/ProfetSettings/mod/consumer.gradle.kts"))

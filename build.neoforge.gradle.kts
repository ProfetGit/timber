plugins {
    id("net.neoforged.moddev")
}

val mc = stonecutter.current.version
val modId = property("mod.id") as String
val mcNext = mc.split(".").let { "${it[0]}.${it[1].toInt() + 1}" }

version = "${property("mod.version")}+$mc-neoforge"
group = property("mod.group") as String
base.archivesName = modId

neoForge {
    version = property("deps.neoforge") as String

    runs {
        register("client") {
            client()
            gameDirectory = file("run/")
            programArguments.addAll("--username", "Builder")
        }
        register("server") {
            server()
            gameDirectory = file("run-server/")
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets["main"])
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // moddev keeps Minecraft's libraries off the test classpath; 26.x ships gson 2.14.0
    testImplementation("com.google.code.gson:gson:2.14.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    exclude("fabric.mod.json", "META-INF/mods.toml")
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
    filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
    // the 1.3.6 sampler's trees, for the self-test scenes
    from(rootProject.file("dev/fixtures/trees")) { into("selftest/trees") }
}

tasks.named<Jar>("jar") {
    from(rootProject.file("LICENSE"))
}

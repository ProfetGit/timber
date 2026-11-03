plugins {
    id("net.fabricmc.fabric-loom")
}

val mc = stonecutter.current.version
val modId = property("mod.id") as String

version = "${property("mod.version")}+$mc-fabric"
group = property("mod.group") as String
base.archivesName = modId

dependencies {
    minecraft("com.mojang:minecraft:$mc")
    implementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
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
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "mc" to mc,
        "name" to project.property("mod.name"),
        "description" to project.property("mod.description"),
        "author" to project.property("mod.author"),
        "homepage" to project.property("mod.homepage"),
        "fabric_loader" to project.property("deps.fabric_loader"),
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}

tasks.named<Jar>("jar") {
    from(rootProject.file("LICENSE"))
}

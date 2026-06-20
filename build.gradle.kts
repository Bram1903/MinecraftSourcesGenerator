plugins {
    java
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "FabricMC" }
}

dependencies {
    implementation(libs.gson)
    implementation(libs.tiny.remapper)
    implementation(libs.mapping.io)
    implementation(libs.asm)
    implementation(libs.asm.tree)
    implementation(libs.vineflower)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

val sourcesDir = layout.projectDirectory.dir("sources")
val workDir = layout.projectDirectory.dir("work")

fun JavaExec.configureGenerator() {
    group = "minecraft sources"
    mainClass = "com.deathmotion.mcsources.cli.Main"
    classpath = sourceSets["main"].runtimeClasspath
    maxHeapSize = "8g"
    systemProperty("mcsg.sourcesDir", sourcesDir.asFile.absolutePath)
    systemProperty("mcsg.workDir", workDir.asFile.absolutePath)
    systemProperty("mcsg.projectDir", layout.projectDirectory.asFile.absolutePath)
    listOf("force", "from", "to", "limit", "jobs", "vfThreads", "snapshots", "keepWork", "vfHeap", "eula",
            "noCache", "resetCache")
        .forEach { key -> (project.findProperty(key) as String?)?.let { systemProperty("mcsg.$key", it) } }
}

tasks.register<JavaExec>("listVersions") {
    description = "List every Minecraft release since 1.7.2 and the mapping source chosen for each."
    configureGenerator()
    args("list")
}

tasks.register<JavaExec>("generateVersion") {
    description = "Decompile one version or a from-to range. Usage: ./gradlew generateVersion -Pmc=1.16.5 (or -Pmc=1.16.5-1.18) -Peula=true"
    configureGenerator()
    args("one", (project.findProperty("mc") as String?) ?: "<unset>")
}

tasks.register<JavaExec>("generateAll") {
    description = "Decompile every Minecraft release since 1.7.2 in parallel. Resumable. Usage: ./gradlew generateAll -Peula=true"
    configureGenerator()
    args("all")
}

tasks.register<JavaExec>("ide") {
    description = "Build an IDE workspace for one version (generates it first if needed). Usage: ./gradlew ide -Pmc=26.2"
    configureGenerator()
    args("ide", (project.findProperty("mc") as String?) ?: "<unset>")
}

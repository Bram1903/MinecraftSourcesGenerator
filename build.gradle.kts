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
    implementation(libs.java.diff.utils)
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

val versionPropertyNames = listOf("mc", "old", "new", "from", "to")

@Suppress("UNCHECKED_CAST")
val splitVersionFragments = gradle.extra["splitVersionFragments"] as List<String>

val versionProperties: Map<String, String> = run {
    val given = versionPropertyNames.mapNotNull { name -> (findProperty(name) as String?)?.let { name to it } }.toMap()
    if (splitVersionFragments.isEmpty()) {
        return@run given
    }
    val truncated = given.filterValues { it.all(Char::isDigit) }
    if (splitVersionFragments.size != 1 || truncated.size != 1) {
        throw GradleException(
            "PowerShell split a version at its first dot, so Gradle received ${splitVersionFragments.joinToString()} " +
                "as task names. Quote each -P argument that holds a version, for example:\n\n" +
                "    ./gradlew generateVersion \"-Pmc=1.19.4\" -Peula=true\n" +
                "    ./gradlew diffVersions \"-Pold=26.2\" \"-Pnew=26.3\" -Peula=true\n\n" +
                "or run the command from cmd.exe, Git Bash or WSL, which pass it through unchanged."
        )
    }
    val (name, head) = truncated.entries.single()
    val repaired = head + splitVersionFragments.single()
    logger.lifecycle("PowerShell split -P$name=$repaired at its first dot, reading it as $repaired. " +
        "Quote the argument (\"-P$name=$repaired\") to avoid this.")
    given + (name to repaired)
}

fun versionProperty(name: String): String = versionProperties[name] ?: "<unset>"

fun JavaExec.configureGenerator() {
    group = "minecraft sources"
    mainClass = "com.deathmotion.mcsources.cli.Main"
    classpath = sourceSets["main"].runtimeClasspath
    maxHeapSize = "8g"
    systemProperty("mcsg.sourcesDir", sourcesDir.asFile.absolutePath)
    systemProperty("mcsg.workDir", workDir.asFile.absolutePath)
    systemProperty("mcsg.projectDir", layout.projectDirectory.asFile.absolutePath)
    listOf("force", "limit", "jobs", "vfThreads", "snapshots", "keepWork", "vfHeap", "eula",
            "noCache", "resetCache", "noReports")
        .forEach { key -> (project.findProperty(key) as String?)?.let { systemProperty("mcsg.$key", it) } }
    listOf("from", "to").forEach { key -> versionProperties[key]?.let { systemProperty("mcsg.$key", it) } }
}

tasks.register<JavaExec>("listVersions") {
    description = "List every Minecraft release since 1.7.2 and the mapping source chosen for each."
    configureGenerator()
    args("list")
}

tasks.register<JavaExec>("generateVersion") {
    description = "Decompile one version or a from-to range. Usage: ./gradlew generateVersion -Pmc=1.16.5 (or -Pmc=1.16.5-1.18) -Peula=true"
    configureGenerator()
    args("one", versionProperty("mc"))
}

tasks.register<JavaExec>("generateAll") {
    description = "Decompile every Minecraft release since 1.7.2 in parallel. Resumable. Usage: ./gradlew generateAll -Peula=true"
    configureGenerator()
    args("all")
}

tasks.register<JavaExec>("diffVersions") {
    description = "Compare two versions class by class and member by member, with resources, server reports and source diffs. Usage: ./gradlew diffVersions -Pold=26.2 -Pnew=26.3 -Peula=true"
    configureGenerator()
    args("diff", versionProperty("old"), versionProperty("new"))
}

tasks.register<JavaExec>("ide") {
    description = "Build an IDE workspace for one version (generates it first if needed). Usage: ./gradlew ide -Pmc=26.2"
    configureGenerator()
    args("ide", versionProperty("mc"))
}

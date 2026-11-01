plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "MinecraftSourcesGenerator"

val splitVersionFragment = Regex("""\.\d[\w.+\-]*""")
val splitVersionFragments = gradle.startParameter.taskNames.filter { splitVersionFragment.matches(it) }
if (splitVersionFragments.isNotEmpty()) {
    gradle.startParameter.setTaskNames(gradle.startParameter.taskNames.filterNot { it in splitVersionFragments })
}
gradle.extra["splitVersionFragments"] = splitVersionFragments

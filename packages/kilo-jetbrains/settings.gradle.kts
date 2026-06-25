rootProject.name = "kilo.jetbrains"

include("shared")
include("frontend")
include("backend")

pluginManagement {
    includeBuild("build-tasks")
    repositories {
        maven("https://central.sonatype.com/repository/maven-snapshots/")
        mavenCentral()
        gradlePluginPortal()
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/")
    }
}

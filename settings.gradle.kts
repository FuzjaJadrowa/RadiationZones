pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = providers.gradleProperty("rootProjectName").get()

include("common", "bukkit", "fabric", "neoforge")
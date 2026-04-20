plugins {
    base
}

group = providers.gradleProperty("groupId").get()
version = providers.gradleProperty("projectVersion").get()

subprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.enginehub.org/repo/")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.parchmentmc.org")
    }
}

val fabricJar by tasks.registering(Copy::class) {
    dependsOn(":fabric:build")
    from(project(":fabric").layout.buildDirectory.file("libs/${providers.gradleProperty("projectId").get()}-fabric-${project.version}.jar"))
    into(layout.buildDirectory.dir("multiloader"))
    rename { "${providers.gradleProperty("projectId").get()}-fabric-${project.version}.jar" }
}

val neoforgeJar by tasks.registering(Copy::class) {
    dependsOn(":neoforge:build")
    from(project(":neoforge").layout.buildDirectory.file("libs/${providers.gradleProperty("projectId").get()}-neoforge-${project.version}.jar"))
    into(layout.buildDirectory.dir("multiloader"))
    rename { "${providers.gradleProperty("projectId").get()}-neoforge-${project.version}.jar" }
}

tasks.register<Jar>("multiloaderJar") {
    group = "build"
    description = "Builds a single distribution archive with Fabric and NeoForge jars."
    dependsOn(fabricJar, neoforgeJar)
    archiveBaseName.set("${providers.gradleProperty("projectId").get()}-fabric-neoforge")
    archiveVersion.set(project.version.toString())
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    from(layout.buildDirectory.dir("multiloader"))
}

tasks.named("build") {
    dependsOn("multiloaderJar")
}
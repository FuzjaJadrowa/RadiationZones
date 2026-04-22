plugins {
    base
}

group = providers.gradleProperty("groupId").get()
version = providers.gradleProperty("projectVersion").get()
val projectId = providers.gradleProperty("projectId").get()

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

val fabricOutputJar = project(":fabric").layout.buildDirectory.file("libs/$projectId-fabric-${project.version}.jar")
val neoforgeOutputJar = project(":neoforge").layout.buildDirectory.file("libs/$projectId-neoforge-${project.version}.jar")

tasks.register<Jar>("multiloaderJar") {
    group = "build"
    description = "Builds a single universal jar for Fabric and NeoForge."
    dependsOn(":fabric:build", ":neoforge:build")
    archiveBaseName.set("${projectId}-fabric-neoforge")
    archiveVersion.set(project.version.toString())
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")

    from(zipTree(fabricOutputJar))
    from(zipTree(neoforgeOutputJar))
}

tasks.named("build") {
    dependsOn("multiloaderJar")
}
plugins {
    id("fabric-loom") version "1.11-SNAPSHOT"
}

base {
    archivesName.set("${providers.gradleProperty("projectId").get()}-fabric")
}

val minecraftVersion = providers.gradleProperty("minecraftVersion").get()
val fabricLoaderVersion = providers.gradleProperty("fabricLoaderVersion").get()
val fabricApiVersion = providers.gradleProperty("fabricApiVersion").get()

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$minecraftVersion+build.3:v2")
    modImplementation("net.fabricmc:fabric-loader:$fabricLoaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    modCompileOnly("com.terraformersmc:modmenu:11.0.3")
}

java {
    withSourcesJar()
    val javaVersion = providers.gradleProperty("javaVersion").map(String::toInt).get()
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
}

tasks.withType<JavaCompile>().configureEach {
    val javaVersion = providers.gradleProperty("javaVersion").map(String::toInt).get()
    options.release.set(javaVersion)
    options.encoding = "UTF-8"
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "mod_id" to providers.gradleProperty("modId").get(),
        "mod_name" to providers.gradleProperty("modName").get(),
        "mod_description" to providers.gradleProperty("modDescription").get(),
        "mod_author" to providers.gradleProperty("modAuthor").get()
    )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}
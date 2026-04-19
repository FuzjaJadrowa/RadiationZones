plugins {
    java
}

base {
    archivesName.set("${providers.gradleProperty("projectId").get()}-neoforge")
}

dependencies {
    compileOnly("net.neoforged:neoforge:${providers.gradleProperty("neoforgeVersion").get()}")
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
        "mod_author" to providers.gradleProperty("modAuthor").get(),
        "neoforge_version" to providers.gradleProperty("neoforgeVersion").get()
    )
    inputs.properties(props)
    filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
}
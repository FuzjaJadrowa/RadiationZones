plugins {
    java
}

base {
    archivesName.set("${providers.gradleProperty("projectId").get()}-bukkit")
}

dependencies {
    compileOnly("org.spigotmc:spigot-api:1.21.1-R0.1-SNAPSHOT")
}

java {
    val javaVersion = providers.gradleProperty("javaVersion").map(String::toInt).get()
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
}

tasks.withType<JavaCompile>().configureEach {
    val javaVersion = providers.gradleProperty("javaVersion").map(String::toInt).get()
    options.encoding = "UTF-8"
    options.release.set(javaVersion)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}
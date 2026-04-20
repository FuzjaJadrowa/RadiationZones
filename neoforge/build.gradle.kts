plugins {
    id("net.neoforged.moddev") version "1.0.11"
}

base {
    archivesName.set("${providers.gradleProperty("projectId").get()}-neoforge")
}

java {
    val javaVersion = providers.gradleProperty("javaVersion").map(String::toInt).get()
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
}

dependencies {
    implementation(project(":common"))
}

tasks.withType<JavaCompile>().configureEach {
    val javaVersion = providers.gradleProperty("javaVersion").map(String::toInt).get()
    options.release.set(javaVersion)
    options.encoding = "UTF-8"
}

neoForge {
    version = providers.gradleProperty("neoforgeVersion").get()

    runs {
        create("client") {
            client()
        }
        create("server") {
            server()
        }
    }

    mods {
        create(providers.gradleProperty("modId").get()) {
            sourceSet(sourceSets.main.get())
        }
    }
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

tasks.named<Jar>("jar") {
    dependsOn(":common:classes")
    from(project(":common").layout.buildDirectory.dir("classes/java/main"))
    from(project(":common").layout.buildDirectory.dir("resources/main"))
}
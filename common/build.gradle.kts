plugins {
    `java-library`
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
}

java {
    val javaVersion = providers.gradleProperty("javaVersion").map(String::toInt).get()
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
}

tasks.withType<JavaCompile>().configureEach {
    val javaVersion = providers.gradleProperty("javaVersion").map(String::toInt).get()
    options.release.set(javaVersion)
    options.encoding = "UTF-8"
}
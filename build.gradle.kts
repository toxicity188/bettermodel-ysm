plugins {
    java
    id("xyz.jpenilla.resource-factory-bukkit-convention") version "1.4.0"
    id("com.gradleup.shadow") version "9.6.1"
}

group = "io.github.toxicity188.bettermodelysm"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.pkg.github.com/toxicity188/BetterModel") {
        credentials {
            username = providers.gradleProperty("githubPackagesUsername")
                .orElse(providers.environmentVariable("GITHUB_USERNAME")).orNull
            password = providers.gradleProperty("githubPackagesPassword")
                .orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
        }
        content { includeGroup("io.github.toxicity188") }
        mavenContent { snapshotsOnly() }
    }
    maven("https://maven.pkg.github.com/toxicity188/ysm-converter") {
        credentials {
            username = providers.gradleProperty("githubPackagesUsername")
                .orElse(providers.environmentVariable("GITHUB_USERNAME")).orNull
            password = providers.gradleProperty("githubPackagesPassword")
                .orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
        }
        content { includeModule("io.github.toxicity188", "ysm-converter") }
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")
    compileOnly("io.github.toxicity188:bettermodel-bukkit-api:3.7.0-SNAPSHOT")
    compileOnly("io.github.toxicity188:ysm-converter:1.0.0")
    runtimeOnly("io.github.toxicity188:ysm-converter:1.0.0") { isTransitive = false }

    testImplementation("io.papermc.paper:paper-api:26.3.build.+")
    testImplementation("io.github.toxicity188:bettermodel-bukkit-api:3.7.0-SNAPSHOT")
    testImplementation("io.github.toxicity188:ysm-converter:1.0.0")
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

tasks.test {
    useJUnitPlatform()
    providers.gradleProperty("ysmTestModel").orNull?.let { systemProperty("ysmTestModel", it) }
}

bukkitPluginYaml {
    name = "BetterModelYsm"
    main = "$group.BetterModelYsmPlugin"
    version = project.version.toString()
    apiVersion = "1.21.4"
    author = "toxicity188"
    description = "YSM conversion adapter for BetterModel"
    depend = listOf("BetterModel")
    foliaSupported = true
    libraries = listOf(
        "com.github.luben:zstd-jni:1.5.7-25",
        "com.twelvemonkeys.imageio:imageio-core:3.15.3",
        "com.twelvemonkeys.imageio:imageio-jpeg:3.15.3",
        "com.twelvemonkeys.imageio:imageio-webp:3.15.3"
    )
}

tasks.jar { archiveClassifier = "plain" }
tasks.shadowJar {
    archiveClassifier = ""
    relocate("io.github.toxicity188.ysmconverter", "${project.group}.internal.ysmconverter")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "module-info.class", "META-INF/versions/**/module-info.class")
}
tasks.assemble { dependsOn(tasks.shadowJar) }

// Exercise the deployable adapter with libraries supplied by the server at runtime.
tasks.test {
    dependsOn(tasks.shadowJar)
    systemProperty("pluginJar", tasks.shadowJar.get().archiveFile.get().asFile.absolutePath)
}





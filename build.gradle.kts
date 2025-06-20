import com.matthewprenger.cursegradle.CurseArtifact
import com.matthewprenger.cursegradle.CurseProject
import com.matthewprenger.cursegradle.CurseRelation
import com.matthewprenger.cursegradle.Options

plugins {
    id("java")
    id("java-library")
    id("fabric-loom") version "1.10-SNAPSHOT"
    id("com.modrinth.minotaur") version "2.+"
    id("com.matthewprenger.cursegradle") version "1.4.0"
    id("com.gradleup.shadow") version "9.0.0-beta13"
}

repositories {
    maven(url = "https://maven.nucleoid.xyz")
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
    maven(url = "https://repo.opencollab.dev/main")
    maven(url = "https://api.modrinth.com/maven")
    //mavenLocal()
}

base.archivesName = "${property("mod_id")}-mc${property("minecraft_version")}"
version = "${property("mod_version")}"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

loom {
    accessWidenerPath = file("src/main/resources/easyauth.accesswidener")
    serverOnlyMinecraftJar()
    log4jConfigs.from(file("log4j.xml"))
}

dependencies {
    fun implementAndInclude(name: String) {
        implementation(name)
        include(name)
    }

    // Fabric
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")

    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    // Translations
    include("xyz.nucleoid:server-translations-api:${property("server_translations_version")}")
    modImplementation("xyz.nucleoid:server-translations-api:${property("server_translations_version")}")

    // Permissions
    modImplementation("me.lucko:fabric-permissions-api:${property("fabric_permissions_version")}")
    compileOnly("net.luckperms:api:${property("luckperms_version")}")

    // Mods
    modCompileOnly("org.geysermc.floodgate:api:${property("floodgate_api_version")}")
    modCompileOnly("maven.modrinth:vanish:${property("vanish_version")}")

    // Password hashing
    implementAndInclude("de.mkammerer:argon2-jvm:${property("argon2_version")}")
    implementAndInclude("de.mkammerer:argon2-jvm-nolibs:${property("argon2_version")}")

    implementAndInclude("at.favre.lib:bcrypt:${property("bcrypt_version")}")
    implementAndInclude("at.favre.lib:bytes:${property("bytes_version")}")

    // Storage
    implementAndInclude("org.iq80.leveldb:leveldb:${property("leveldb_version")}")
    implementAndInclude("org.iq80.leveldb:leveldb-api:${property("leveldb_version")}")

    implementAndInclude("org.mongodb:mongodb-driver-sync:${property("mongodb_version")}")
    implementAndInclude("org.mongodb:mongodb-driver-core:${property("mongodb_version")}")
    implementAndInclude("org.mongodb:bson:${property("mongodb_version")}")

    implementAndInclude("com.mysql:mysql-connector-j:${property("mysql_version")}")
    implementAndInclude("org.xerial:sqlite-jdbc:${property("sqlite_version")}")

    implementation("org.spongepowered:configurate-hocon:${property("hocon_version")}")
    shadow("org.spongepowered:configurate-hocon:${property("hocon_version")}")

    include("net.java.dev.jna:jna:${property("jna_version")}")
}

tasks.shadowJar {
    relocate("org.spongepowered.configurate", "xyz.nikitacartes.shadow.configurate")
    relocate("com.typesafe.config", "xyz.nikitacartes.shadow.config")
    relocate("io.leangen.geantyref", "xyz.nikitacartes.shadow.geantyref")
    relocate("net.kyori.option", "xyz.nikitacartes.shadow.option")

    minimize()
    configurations = listOf(project.configurations.shadow.get())
    from(sourceSets.main.get().output)
}

tasks.remapJar {
    dependsOn(tasks.shadowJar)
    inputFile.set(tasks.shadowJar.get().archiveFile)
}

tasks.jar {
    from("LICENCE")
}

tasks.processResources {
    inputs.property("id", property("mod_id"))
    inputs.property("name", property("mod_name"))
    inputs.property("version", property("version"))

    filesMatching("fabric.mod.json") {
        expand(
            mapOf(
                "id" to property("mod_id"),
                "name" to property("mod_name"),
                "version" to property("version")
            )
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

java {
    withSourcesJar()
}

modrinth {
    token.set(System.getenv("MODRINTH_TOKEN"))
    projectId.set("aZj58GfX")
    versionNumber.set("${property("version")}")
    versionName = "${property("display_name")} ${property("version")}"
    versionType = "release"
    changelog.set("Release notes:\nhttps://github.com/NikitaCartes/EasyAuth/releases/tag/${property("version")}\n\nChangelog:\nhttps://github.com/NikitaCartes/EasyAuth/tree/HEAD/CHANGELOG.md")
    uploadFile.set(tasks.remapJar)
    gameVersions.addAll(property("supported_versions").toString().split(","))
    loaders.add("fabric")
    dependencies {
        required.project("P7dR8mSH") // Fabric API
    }
}

curseforge {
    apiKey = System.getenv("CURSEFORGE_TOKEN") ?: ""
    curseforge.project(closureOf<CurseProject> {
        id = "503866"
        changelogType = "markdown"
        changelog = "Release notes:\nhttps://github.com/NikitaCartes/EasyAuth/releases/tag/${property("version")}\n\nChangelog:\nhttps://github.com/NikitaCartes/EasyAuth/tree/HEAD/CHANGELOG.md"
        releaseType = "release"

        addGameVersion("Fabric")

        addGameVersion("Java 21")

        property("supported_versions").toString().split(",").forEach { addGameVersion(it) }

        mainArtifact(tasks.remapJar, closureOf<CurseArtifact> {
            displayName = "${property("display_name")} ${property("version")}"
            relations(closureOf<CurseRelation> {
                requiredDependency("fabric-api")
                embeddedLibrary("server-translation-api")
            })
        })
    })
    options(closureOf<Options> {
        javaVersionAutoDetect = false
        javaIntegration = false
        forgeGradleIntegration = false
    })
}

tasks.register("publish") {
    dependsOn("modrinth")
    dependsOn("curseforge")
}
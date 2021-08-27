import com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.minecraftforge.gradle.user.UserExtension
import java.io.*

buildscript {
    repositories {
        mavenCentral()
        maven("http://files.minecraftforge.net/maven") { name = "Forge Maven" }
        maven("https://jitpack.io") { name = "JitPack Maven" }
    }
    dependencies {
        classpath("com.github.GTNH2:ForgeGradle:FG_1.2-SNAPSHOT")
    }
}
apply(plugin = "forge")
plugins {
    idea
    java
    id("com.palantir.git-version") version "0.12.3"
    id("com.github.johnrengelman.shadow") version "4.0.4"
}
// Downloads Javadocs and sources for dependencies
idea {
    module {
        isDownloadJavadoc = true
        isDownloadSources = true
    }
}
// Set Java 8 for compile
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
val projectName: String by project
val projectModId: String by project
val projectGroup: String by project
val projectMinecraftVersion: String by project
val projectForgeVersion: String by project
val projectReferenceClass: String by project
// Git tag based version setup
// Suggested reading: https://semver.org/
group = projectGroup
//Minecraft Blocks
configure<UserExtension> {
    version = projectForgeVersion
    // Replaces version inside the mod
    includes.addAll(arrayOf(projectReferenceClass))
    replacements.putAll(
        // Can be expanded to replace things like modid or mod name if it is something that changes frequently
        mapOf(
            Pair("@GRADLE_VERSION_TOKEN@", project.version)
        )
    )
}

val gitVersion: groovy.lang.Closure<String> by extra
version = projectMinecraftVersion + "-" + gitVersion()

repositories {
    mavenLocal()
    maven("https://gregtech.overminddl1.com/") { name = "Gregtech Maven" }
    maven("http://maven.ic2.player.to/") { name = "IC2 Maven" }
    maven("http://jenkins.usrv.eu:8081/nexus/content/repositories/releases/") { name = "UsrvDE/GTNH" }
    ivy {
        name = "GTNH_Ivy_Underscore"
        artifactPattern("http://downloads.gtnewhorizons.com/Mods_for_Jenkins/[module]_[revision].[ext]")
    }
    ivy {
        artifactPattern("http://downloads.gtnewhorizons.com/Mods_for_Jenkins/[module]-[revision].[ext]")
        name = "GTNH_Ivy_Dash"
    }
    ivy {
        artifactPattern("http://www.mod-buildcraft.com/releases/BuildCraft/[revision]/[module]-[revision](-[classifier]).[ext]")
        name = "BuildCraft_Ivy"
    }
    maven("http://maven.cil.li/") { name = "OpenComputers Maven" }
    maven("http://default.mobiusstrip.eu/maven") { name = "JABBA Maven" }
    maven("http://chickenbones.net/maven/") { name = "CodeChicken Maven" }
    maven("http://www.ryanliptak.com/maven/") { name = "appleCore Maven" }
    maven("https://jitpack.io") { name = "JitPack Maven" }
}
// Allows JitPack dependencies to be updated more frequently by checking more often.
configurations.all {
    resolutionStrategy.cacheDynamicVersionsFor(30, "seconds")
}
// Shadow implementations dependencies will be included inside the final jar
val shadowImplementation by configurations.creating
configurations["implementation"].extendsFrom(shadowImplementation)
dependencies {
    // Local libraries
    compile(fileTree("libs") { include("*.jar") })
    // Version loading
    val lombokVersion: String by project
    val manifoldVersion: String by project
    val codechickenlibVersion: String by project
    val codechickencoreVersion: String by project
    val neiVersion: String by project
    val wailaVersion: String by project

    // Java extensions
    compileOnly("org.projectlombok:lombok:$lombokVersion")
    annotationProcessor("org.projectlombok:lombok:$lombokVersion")
    implementation("systems.manifold:manifold-javadoc-agent:$manifoldVersion")
    shadowImplementation("systems.manifold:manifold-ext-rt:$manifoldVersion")
    annotationProcessor("systems.manifold:manifold-ext:$manifoldVersion")

    // Optional libraries for testing
    runtimeOnly("mcp.mobius.waila:Waila:$wailaVersion")
    runtimeOnly("codechicken:CodeChickenLib:$codechickenlibVersion:dev")
    runtimeOnly("codechicken:CodeChickenCore:$codechickencoreVersion:dev")
    runtimeOnly("codechicken:NotEnoughItems:$neiVersion:dev")
}
sourceSets.main {
    java {
        // Add extra sources here
        srcDir("src/main/java")
    }
    // Uncomment this if you get missing textures when debugging.
    // output.setResourcesDir(output.classesDirs.asPath)
}
tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xplugin:Manifold")
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }
    withType<Jar> {
        // Mark as outdated if versions change
        inputs.properties.plusAssign("version" to project.version)
        inputs.properties.plusAssign("mcversion" to projectMinecraftVersion)
        //Replace versions in mcmod.info
        filesMatching("/mcmod.info") {
            expand(
                mapOf(
                    "version" to project.version,
                    "mcversion" to projectMinecraftVersion
                )
            )
        }
    }
    val relocateShadowJar = register<ConfigureShadowRelocation>("relocateShadowJar")
    val shadowJarTask = named<ShadowJar>("shadowJar") {
        // Only shadows used classes
        minimize()
        // Loads shadow implementations
        configurations = listOf(shadowImplementation)
        // Enable package relocation in resulting shadow jar to prevent class overlap
        relocateShadowJar.get().apply { prefix = "$projectGroup.shadow"; target = this@named }
        dependsOn(relocateShadowJar)
    }
    // Makes shadowJarTask run in place of the jar task
    jar {
        dependsOn(shadowJarTask)
        enabled = false
    }
    val sourcesJar by creating(Jar::class) {
        from(sourceSets.main.get().allSource)
        archiveClassifier.set("sources")
    }
    val javadocJar by creating(Jar::class) {
        dependsOn.add(javadoc)
        archiveClassifier.set("javadoc")
        from(javadoc)
    }
    val devJar by creating(Jar::class) {
        from(sourceSets.main.get().output)
        archiveClassifier.set("dev")
    }
    artifacts {
        archives(sourcesJar)
        archives(javadocJar)
        archives(devJar)
    }
}

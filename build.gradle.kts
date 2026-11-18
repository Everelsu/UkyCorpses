
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

val ragdollSources by configurations.creating { isTransitive = false }
dependencies { ragdollSources("javax.vecmath:vecmath:1.5.2:sources") }

val jvmDowngrader by configurations.creating
val jvmDowngraderApi by configurations.creating { isTransitive = false }
val jvmDowngraderSources by configurations.creating { isTransitive = false }
repositories {
    maven("https://maven.wagyourtail.xyz/releases") {
        content { includeGroup("xyz.wagyourtail.jvmdowngrader") }
    }
}
dependencies {
    jvmDowngrader("xyz.wagyourtail.jvmdowngrader:jvmdowngrader:2.0.1")
    jvmDowngraderApi("xyz.wagyourtail.jvmdowngrader:jvmdowngrader-java-api:2.0.1")
    jvmDowngraderSources("xyz.wagyourtail.jvmdowngrader:jvmdowngrader-java-api:2.0.1:sources")
}
val downgradedJolt = layout.buildDirectory.file("java8/joltjni-downgraded.jar")
val downgradeJolt by tasks.registering(JavaExec::class) {
    classpath = jvmDowngrader
    mainClass.set("xyz.wagyourtail.jvmdg.cli.Main")
    inputs.file("libs/joltjni-local.jar")
    inputs.files(jvmDowngraderApi)
    outputs.file(downgradedJolt)
    args("--classVersion", "52", "--api", jvmDowngraderApi.singleFile.absolutePath,
        "downgrade", "--target", file("libs/joltjni-local.jar").absolutePath, "-",
        "shade", "--prefix", "com/uky/corpses/joltcompat", "--target", "-", downgradedJolt.get().asFile.absolutePath)
}
val joltCompat = sourceSets.create("joltCompat")
tasks.named<JavaCompile>(joltCompat.compileJavaTaskName) {
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(17)) })
    options.release.set(8)
}
val prepareJava8Jolt by tasks.registering(Jar::class) {
    dependsOn(downgradeJolt, joltCompat.classesTaskName)
    from(downgradedJolt.map { zipTree(it) }) {
        exclude("**/J_L_R_Cleaner*.class")
    }
    from(joltCompat.output)
    archiveFileName.set("joltjni-local.jar")
    destinationDirectory.set(layout.buildDirectory.dir("java8"))
}
val java8Jolt = prepareJava8Jolt.flatMap { it.archiveFile }

// The mod's icon, at the root of the jar under the two names that show one: pack.png for FML's mod list (it asks the
// mod's resource pack first), and mcmod.info's logoFile for the launchers. Made by art/generate_icon.py, as UkyUI's is.
tasks.named<ProcessResources>("processResources") {
    // Keep JNI and its Java 8 API backports together, outside Forge's annotation scanner.
    dependsOn(prepareJava8Jolt)
    from(java8Jolt) { into("META-INF/libraries") }
    from({ zipTree(jvmDowngraderApi.singleFile) }) {
        include("LICENSE.md", "license/**")
        into("META-INF/licenses/jvmdowngrader")
    }
    from(jvmDowngraderSources) { into("META-INF/licenses/jvmdowngrader") }
    from(joltCompat.allSource) { into("META-INF/licenses/jvmdowngrader/modified-sources") }
    from(ragdollSources) { into("META-INF/licenses/vecmath") }
    val icon = layout.projectDirectory.file("art/logo.png").asFile
    from(icon) { rename { "pack.png" } }
    from(icon) { rename { "ukycorpses_logo.png" } }
    doFirst {
        if (!icon.isFile) throw GradleException("art/logo.png is missing: run python art/generate_icon.py")
    }
}

val ragdollCheck = sourceSets.create("ragdollCheck") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().runtimeClasspath
}
configurations[ragdollCheck.annotationProcessorConfigurationName].extendsFrom(configurations.annotationProcessor.get())
val verifyRagdoll by tasks.registering(JavaExec::class) {
    dependsOn(ragdollCheck.classesTaskName)
    dependsOn(prepareJava8Jolt)
    classpath = ragdollCheck.runtimeClasspath + files(java8Jolt)
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) })
    mainClass.set("com.uky.corpses.corpse.RagdollCheck")
    enableAssertions = true
}
tasks.check { dependsOn(verifyRagdoll) }

// Separate local game harness: never packaged in the playable mod.
val gameCheck = sourceSets.create("gameCheck") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += sourceSets.main.get().runtimeClasspath
}
configurations[gameCheck.annotationProcessorConfigurationName].extendsFrom(configurations.annotationProcessor.get())
tasks.register<Jar>("gameCheckJar") {
    dependsOn(gameCheck.classesTaskName)
    from(gameCheck.output)
    archiveFileName.set("uky-port-gamecheck.jar")
    destinationDirectory.set(layout.buildDirectory.dir("gamecheck"))
}

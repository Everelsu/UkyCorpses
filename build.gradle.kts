
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// The mod's icon, at the root of the jar under the two names that show one: pack.png for FML's mod list (it asks the
// mod's resource pack first), and mcmod.info's logoFile for the launchers. Made by art/generate_icon.py, as UkyUI's is.
tasks.named<ProcessResources>("processResources") {
    val icon = layout.projectDirectory.file("art/logo.png").asFile
    from(icon) { rename { "pack.png" } }
    from(icon) { rename { "ukycorpses_logo.png" } }
    doFirst {
        if (!icon.isFile) throw GradleException("art/logo.png is missing: run python art/generate_icon.py")
    }
}

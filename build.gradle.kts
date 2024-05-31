
plugins {
    alias(libs.plugins.jvm)
    application
}

repositories {
    mavenCentral()
}

dependencies {
    // Intentionally empty (for now)
    implementation("com.miglayout:miglayout-swing:11.3")
}

application {
    mainClass = "zedit2.components.Main"
}


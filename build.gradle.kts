
plugins {
    alias(libs.plugins.jvm)
    application
}

repositories {
    mavenCentral()
}

dependencies {
    // Intentionally empty (for now)
}

application {
    mainClass = "zedit2.Main"
}


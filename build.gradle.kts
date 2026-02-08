plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.shadow)
    application
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Intentionally empty (for now)
    implementation("com.miglayout:miglayout-swing:11.3")
    implementation("com.github.weisj:jsvg:1.5.0")
    implementation("com.formdev:flatlaf:3.4.1")
}

application {
    mainClass.set("zedit2.components.Main")
}

tasks.shadowJar {
    // Remove the -all classifier from the shadow jar
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "zedit2.components.Main"
    }
}

tasks.jar {
    // Add -nodeps classifier to the standard jar
    archiveClassifier.set("nodeps")
}

version = "0.5.1"

distributions {
    main {
        distributionBaseName = "zloom2k"
        contents {
            from("src/readme")
        }
    }
}

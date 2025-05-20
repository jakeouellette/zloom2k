
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
    implementation("com.github.weisj:jsvg:1.5.0")
    implementation("com.formdev:flatlaf:3.4.1")
}

application {
    mainClass = "zedit2.components.Main"
}

version = "0.4.1"


distributions {
    main {
        distributionBaseName = "zloom2k"
        contents {
            from("src/readme")
        }
    }
}



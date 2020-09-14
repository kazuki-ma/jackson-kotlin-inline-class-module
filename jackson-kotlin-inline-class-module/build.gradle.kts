apply {
    plugin("kotlin")
}

dependencies {
    api("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}
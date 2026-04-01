plugins {
    java
    id("com.gradleup.shadow") version "8.3.5" apply false
}

allprojects {
    group = "space.blockway.waybucks"
    version = "1.0.0"

    apply(plugin = "java")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }
}

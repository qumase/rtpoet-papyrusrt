plugins {
    `java-library`
    kotlin("jvm") version "1.4.21"
    `maven-publish`
}

group = "ca.jahed"
version = "0.1"

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(group = "ca.jahed", name = "rtpoet", version = "0.1-SNAPSHOT")

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    testImplementation("junit", "junit", "4.12")
}

java {
    withJavadocJar()
    withSourcesJar()
}

tasks.jar {
    manifest {
        attributes(mapOf("Implementation-Title" to project.name,
            "Implementation-Version" to project.version))
    }

    from(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}

publishing {
    repositories {
        mavenLocal()
    }

    publications {
        create<MavenPublication>("maven") {
            groupId = "ca.jahed"
            artifactId = "rtpoet-papyrusrt"

            from(components["java"])

            versionMapping {
                usage("java-api") {
                    fromResolutionOf("runtimeClasspath")
                }
                usage("java-runtime") {
                    fromResolutionResult()
                }
            }

            pom {
                name.set("RTPoet For Papyrus-RT")
                description.set("Papyrus-RT plugin for the RTPoet library")
                url.set("https://github.com/kjahed/rtpoet-papyrusrt")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("kjahed")
                        name.set("Karim Jahed")
                        email.set("jahed@cs.queensu.ca")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/kjahed/rtpoet-papyrusrt.git")
                    developerConnection.set("scm:git:ssh://github.com/kjahed/rtpoet-papyrusrt.git")
                    url.set("https://github.com/kjahed/rtpoet-papyrusrt")
                }
            }
        }
    }
}

tasks.javadoc {
    if (JavaVersion.current().isJava9Compatible) {
        (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
    }
}
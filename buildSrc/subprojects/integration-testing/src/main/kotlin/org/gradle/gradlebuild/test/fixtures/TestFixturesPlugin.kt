/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.gradlebuild.test.fixtures

import accessors.groovy
import accessors.java
import library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import testLibraries
import testLibrary


/**
 * Test Fixtures Plugin.
 *
 * Configures the Project as a test fixtures producer if `src/testFixtures` is a directory:
 * - adds a new `testFixtures` source set which should contain utilities/fixtures to assist in unit testing
 *   classes from the main source set,
 * - the test fixtures are automatically made available to the test classpath.
 *
 * Configures the Project as a test fixtures consumer according to the `testFixtures` extension configuration.
 */
@Suppress("unused")
open class TestFixturesPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {

        apply(plugin = "java")

        extensions.create<TestFixturesExtension>("testFixtures")

        if (file("src/testFixtures").isDirectory) {
            configureAsProducer()
        }

        configureAsConsumer()
    }


    /**
     * This mimics what the java-library plugin does, but creating a library of test fixtures instead.
     */
    private
    fun Project.configureAsProducer() {
        val testFixtures by java.sourceSets.creating

        java.sourceSets.named<SourceSet>("test") {
            compileClasspath += testFixtures.output
            runtimeClasspath += testFixtures.output
        }

        val testFixturesJar by tasks.creating(Jar::class) {
            from(testFixtures.output)
            classifier = "test-fixtures"
        }

        configurations {
            val testFixturesCompile by getting {
                extendsFrom(configurations["compile"])
            }
            val testFixturesApi by creating {
                extendsFrom(testFixturesCompile)
            }
            val testFixturesImplementation by getting {
                extendsFrom(configurations["implementation"], testFixturesApi)
            }
            val testFixturesRuntime by getting {
                extendsFrom(configurations["runtime"])
            }
            val testFixturesRuntimeOnly by getting {
                extendsFrom(configurations["runtimeOnly"])
            }

            "testCompile" {
                extendsFrom(testFixturesCompile)
            }
            "testImplementation" {
                extendsFrom(testFixturesImplementation)
            }
            "testRuntime" {
                extendsFrom(testFixturesRuntime)
            }
            "testRuntimeOnly" {
                extendsFrom(testFixturesRuntimeOnly)
            }

            create("testFixturesApiElements") {
                extendsFrom(testFixturesApi, testFixturesRuntime)
                outgoing.artifact(testFixturesJar)
            }

            create("testFixturesRuntimeElements") {
                extendsFrom(testFixturesRuntimeOnly)
                outgoing.artifact(testFixturesJar)
            }
        }

        dependencies {
            val testFixturesApi by configurations

            testFixturesApi(project(path))
            testFixturesApi(library("junit"))
            testFixturesApi(testLibrary("spock"))
            testLibraries("jmock").forEach { testFixturesApi(it) }
        }

        plugins.withType<IdeaPlugin> {
            configure<IdeaModel> {
                module {
                    testSourceDirs = testSourceDirs + testFixtures.groovy.srcDirs + testFixtures.resources.srcDirs
                }
            }
        }
    }

    private
    fun Project.configureAsConsumer() = afterEvaluate {
        the<TestFixturesExtension>().origins.forEach { (projectPath, sourceSetName) ->
            val compileConfig = if (sourceSetName == "main") "compile" else "${sourceSetName}Compile"
            val runtimeConfig = if (sourceSetName == "main") "runtime" else "${sourceSetName}Runtime"

            dependencies {
                compileConfig(project(path = projectPath, configuration = "testFixturesApiElements"))
                compileConfig(project(":internalTesting"))
                runtimeConfig(project(path = projectPath, configuration = "testFixturesRuntimeElements"))
            }
        }
    }
}

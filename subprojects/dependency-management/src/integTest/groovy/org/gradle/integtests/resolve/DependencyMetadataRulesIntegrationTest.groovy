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
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import org.gradle.test.fixtures.maven.MavenFileRepository
import spock.lang.Unroll

import static org.gradle.util.GUtil.toCamelCase

@RequiredFeatures(
    @RequiredFeature(feature = GradleMetadataResolveRunner.EXPERIMENTAL_RESOLVE_BEHAVIOR, value = "true")
)
class DependencyMetadataRulesIntegrationTest extends AbstractModuleDependencyResolveTest {
    @Override
    String getTestConfiguration() { variantToTest }

    /**
     * Does the published metadata provide variants with attributes? Eventually all metadata should do that.
     * For Ivy and Maven POM metadata, the variants and attributes should be derived from configurations and scopes.
     */
    boolean getPublishedModulesHaveAttributes() { gradleMetadataEnabled }

    String getVariantToTest() {
        if (gradleMetadataEnabled || useIvy()) {
            'customVariant'
        } else {
            'runtime'
        }
    }

    def setup() {
        repository {
            'org.test:moduleA:1.0'() {
                variant 'customVariant', [format: 'custom']
            }
            'org.test:moduleB:1.0'()
        }

        buildFile << """
            configurations { $variantToTest { attributes { attribute(Attribute.of('format', String), 'custom') } } }
            
            dependencies {
                $variantToTest group: 'org.test', name: 'moduleA', version: '1.0' ${publishedModulesHaveAttributes ? "" : ", configuration: '$variantToTest'"}
            }
        """
    }

    @Unroll
    def "#thing can be added using #notation notation"() {
        when:
        buildFile << """
            dependencies {
                $variantToTest 'org.test:moduleB'
                components {
                    withModule('org.test:moduleA') {
                        withVariant("$variantToTest") { 
                            with${toCamelCase(thing)} {
                                add $declaration
                            }
                        }
                    }
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org.test:moduleB:', 'org.test:moduleB:1.0')
                module("org.test:moduleA:1.0:$expectedVariant") {
                    module('org.test:moduleB:1.0')
                }
            }
        }

        where:
        thing                    | notation | declaration
        "dependency constraints" | "string" | "'org.test:moduleB:1.0'"
        "dependency constraints" | "map"    | "group: 'org.test', name: 'moduleB', version: '1.0'"
        "dependencies"           | "string" | "'org.test:moduleB:1.0'"
        "dependencies"           | "map"    | "group: 'org.test', name: 'moduleB', version: '1.0'"
    }

    @Unroll
    def "#thing can be added and configured using #notation notation"() {
        when:
        buildFile << """
            dependencies {
                $variantToTest 'org.test:moduleB'
                components {
                    withModule('org.test:moduleA') {
                        withVariant("$variantToTest") {
                            with${toCamelCase(thing)} {
                                add($declaration) {
                                    it.version { strictly '1.0' }
                                }
                            }
                        }
                    }
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org.test:moduleB:', 'org.test:moduleB:1.0')
                module("org.test:moduleA:1.0:$expectedVariant") {
                    module('org.test:moduleB:1.0')
                }
            }
        }

        where:
        thing                    | notation | declaration
        "dependency constraints" | "string" | "'org.test:moduleB:1.0'"
        "dependency constraints" | "map"    | "group: 'org.test', name: 'moduleB', version: '1.0'"
        "dependencies"           | "string" | "'org.test:moduleB:1.0'"
        "dependencies"           | "map"    | "group: 'org.test', name: 'moduleB', version: '1.0'"
    }

    def "dependencies can be removed"() {
        given:
        repository {
            'org.test:moduleA:1.0' {
                dependsOn 'org.test:moduleB:1.0'
            }
        }

        when:
        buildFile << """
            dependencies {
                components {
                    withModule('org.test:moduleA') {
                        withVariant("$variantToTest") {
                            withDependencies {
                                removeAll { it.versionConstraint.preferredVersion == '1.0' }
                            }
                        }
                    }
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$expectedVariant")
            }
        }
    }

    def "dependency constraints can be removed"() {
        given:
        repository {
            'org.test:moduleA:1.0' {
                constraint 'org.test:moduleB:2.0'
            }
        }

        when:
        buildFile << """
            dependencies {
                $variantToTest 'org.test:moduleB:1.0'
                components {
                    withModule('org.test:moduleA') {
                        withVariant("$variantToTest") {
                            withDependencyConstraints {
                                removeAll { it.versionConstraint.preferredVersion == '2.0' }
                            }
                        }
                    }
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()

            }
            'org.test:moduleB'() {
                version('1.0') {
                    expectGetMetadata()
                    expectGetArtifact()
                }
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleB:1.0")
                module("org.test:moduleA:1.0:$expectedVariant")
            }
        }
    }

    @Unroll
    def "#thing modifications are visible in the next rule"() {
        when:
        buildFile << """
            dependencies {
                components {
                    withModule('org.test:moduleA') {
                        withVariant("$variantToTest") { 
                            with${toCamelCase(thing)} { d ->
                                assert d.size() == 0
                                d.add 'org.test:moduleB:1.0'
                            }
                        }
                    }
                    withModule('org.test:moduleA') {
                        withVariant("$variantToTest") {
                            with${toCamelCase(thing)} { d ->
                                assert d.size() == 1
                                d.removeAll { true }
                            }
                        }
                    }
                    all {
                        withVariant("$variantToTest") { 
                            with${toCamelCase(thing)} { d ->
                                assert d.size() == 0
                            }
                        }
                    }
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$expectedVariant")
            }
        }

        where:
        thing                    | _
        "dependencies"           | _
        "dependency constraints" | _
    }

    def "can set version on dependency"() {
        given:
        repository {
            'org.test:moduleA:1.0'() {
                dependsOn 'org.test:moduleB:2.0'
            }
        }

        when:
        buildFile << """
            dependencies {
                components {
                    withModule('org.test:moduleA') {
                        withVariant("$variantToTest") { 
                            withDependencies {
                                it.each {
                                    it.version { prefer '1.0' }
                                }
                            }
                        }
                    }
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$expectedVariant") {
                    module('org.test:moduleB:1.0')
                }
            }
        }
    }

    @RequiredFeatures(
        @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    )
    def "can set version on dependency constraint"() {
        given:
        repository {
            'org.test:moduleA:1.0'() {
                constraint 'org.test:moduleB:0.1'
            }
        }

        when:
        buildFile << """
            dependencies {
                $variantToTest 'org.test:moduleB'
                components {
                    withModule('org.test:moduleA') {
                        withVariant("$variantToTest") { 
                            withDependencyConstraints {
                                it.each {
                                    it.version { prefer '1.0' }
                                }
                            }
                        }
                    }
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org.test:moduleB:', 'org.test:moduleB:1.0')
                module("org.test:moduleA:1.0:$expectedVariant") {
                    module('org.test:moduleB:1.0')
                }
            }
        }
    }


    def "changing dependencies in one variant leaves other variants untouched"() {
        when:
        buildFile << """
            dependencies {
                components {
                    withModule('org.test:moduleA') {
                        withVariant("default") {
                            withDependencies {
                                add('org.test:moduleB:1.0')
                            }
                        }
                    }
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                variant("default", ['some':'other'])
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$expectedVariant")
            }
        }
    }

    def "can update all variants at once"() {
        when:
        buildFile << """
            dependencies {
                components {
                    withModule('org.test:moduleA') {
                        allVariants {
                            withDependencies {
                                add('org.test:moduleB:1.0')
                            }
                        }
                    }
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                variant("default", ['some':'other'])
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0' {
                expectResolve()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$expectedVariant") {
                    module('org.test:moduleB:1.0')
                }
            }
        }
    }

    @Unroll
    def "#thing of transitive dependencies can be changed"() {
        given:
        repository {
            'org.test:moduleA:1.0' {
                dependsOn 'org.test:moduleB:1.0'
            }
            'org.test:moduleB:1.0' {
                variant 'customVariant', [format: 'custom']
            }
            'org.test:moduleC:1.0'()
        }

        when:
        buildFile << """
            dependencies {
                $variantToTest 'org.test:moduleC'
                components {
                    withModule('org.test:moduleB') {
                        withVariant('$variantToTest') {
                            with${toCamelCase(thing)} { d ->
                                add('org.test:moduleC:1.0')
                            }
                        }
                    }
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleC:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                edge('org.test:moduleC:', 'org.test:moduleC:1.0')
                module("org.test:moduleA:1.0:$expectedVariant") {
                    module("org.test:moduleB:1.0") {
                        module('org.test:moduleC:1.0')
                    }
                }
            }
        }

        where:
        thing                    | _
        "dependencies"           | _
        "dependency constraints" | _
    }

    def "attribute matching is used to select a variant of the dependency's target if the dependency was added by a rule"() {
        given:
        repository {
            'org.test:moduleA:1.0' {
                dependsOn 'org.test:moduleB:1.0'
            }
            'org.test:moduleB:1.0' {
                variant 'customVariant', [format: 'custom']
            }
            'org.test:moduleD:1.0'()
        }

        def mavenGradleRepo = new MavenFileRepository(file("maven-gradle-repo"))
        buildFile << """
            repositories {
                maven {
                    url "$mavenGradleRepo.uri"
                }
            }
        """
        //All dependencies added by rules are of type GradleDependencyMetadata and thus attribute matching is used for selecting the variant/configuration of the dependency's target.
        //Here we add a module with Gradle metadata which defines a variant that uses the same attributes declared in the build script (format: "custom").
        //The dependency to this module is then added using the rule and thus is matched correctly.
        mavenGradleRepo.module("org.test", "moduleC").withModuleMetadata().variant("anotherVariantWithFormatCustom", [format: "custom"]).publish()

        when:
        buildFile << """
            dependencies {
                components {
                    withModule('org.test:moduleB') {
                        withVariant('$variantToTest') {
                            withDependencies {
                                add('org.test:moduleC:1.0')
                            }
                        }
                    }
                    withModule('org.test:moduleC') { //this second rule is here to test that the correct variant was chosen, which is the one adding the dependency to moduleD
                        withVariant('anotherVariantWithFormatCustom') {
                            withDependencies {
                                add('org.test:moduleD:1.0')
                            }
                        }
                    }
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleB:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
            'org.test:moduleC:1.0'() {
                expectGetMetadataMissing()
            }
            'org.test:moduleD:1.0'() {
                expectGetMetadata()
                expectGetArtifact()
            }
        }

        then:
        succeeds 'checkDep'
        def expectedVariant = variantToTest
        resolve.expectGraph {
            root(':', ':test:') {
                module("org.test:moduleA:1.0:$expectedVariant") {
                    module("org.test:moduleB:1.0") {
                        module('org.test:moduleC:1.0') {
                            module('org.test:moduleD:1.0')
                        }
                    }
                }
            }
        }
    }

    def "resolving one configuration does not influence the result of resolving another configuration."() {
        given:
        repository {
            'org.test:moduleA:1.0'() {
                dependsOn 'org.test:moduleB:1.0'
            }
        }

        when:
        buildFile << """
            configurations { anotherConfiguration { attributes { attribute(Attribute.of('format', String), 'custom') } } }
            
            dependencies {
                anotherConfiguration group: 'org.test', name: 'moduleA', version: '1.0' ${publishedModulesHaveAttributes ? "" : ", configuration: '$variantToTest'"}
            }

            dependencies {
                components {
                    withModule('org.test:moduleA') {
                        withVariant("$variantToTest") {
                            withDependencies {
                                //check that the dependency has not been removed already during resolution of the other configuration
                                assert it.size() == 1
                                removeAll { true }
                            }
                        }
                    }
                }
            }
        """
        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
            }
        }

        then:
        succeeds 'dependencies'
    }

    @Unroll
    def "can make #thing strict"() {
        given:
        repository {
            'org.test:moduleB:1.1'()
            'org.test:moduleA:1.0'() {
                if (defineAsConstraint) {
                    constraint 'org.test:moduleB:1.1'
                } else {
                    dependsOn 'org.test:moduleB:1.1'
                }
            }
        }

        when:
        buildFile << """
            dependencies {
                $variantToTest group: 'org.test', name: 'moduleB', version: '1.1' ${publishedModulesHaveAttributes ? "" : ", configuration: '$variantToTest'"}
 
                components {
                    withModule('org.test:moduleA') {
                        withVariant("$variantToTest") {
                            with${toCamelCase(thing)} { d ->
                                d.findAll { it.name == 'moduleB' }.each {
                                    it.version { strictly '1.0' }
                                }
                            }
                        }
                    }
                }
            }
        """
        if (defineAsConstraint && !gradleMetadataEnabled && useIvy()) {
            //in plain ivy, we do not have the constraint published. But we can add still add it.
            buildFile.text = buildFile.text.replace("d ->", "d -> d.add('org.test:moduleB:1.0')")
        }

        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
            }
            'org.test:moduleB:1.1'() {
                expectGetMetadata()
            }
        }

        then:
        fails 'checkDep'
        failure.assertHasCause """Cannot find a version of 'org.test:moduleB' that satisfies the version constraints: 
   Dependency path ':test:unspecified' --> 'org.test:moduleB' prefers '1.1'
   ${defineAsConstraint? 'Constraint' : 'Dependency'} path ':test:unspecified' --> 'org.test:moduleA:1.0' --> 'org.test:moduleB' prefers '1.0', rejects ']1.0,)'"""

        where:
        thing                    | defineAsConstraint
        "dependencies"           | false
        "dependency constraints" | true
    }

    @Unroll
    def "can add rejections to #thing"() {
        given:
        repository {
            'org.test:moduleB:1.1'()
            'org.test:moduleA:1.0'() {
                if (defineAsConstraint) {
                    constraint 'org.test:moduleB:1.1'
                } else {
                    dependsOn 'org.test:moduleB:1.1'
                }
            }
        }

        when:
        buildFile << """
            dependencies {
                $variantToTest group: 'org.test', name: 'moduleB', version: '1.1' ${publishedModulesHaveAttributes ? "" : ", configuration: '$variantToTest'"}
 
                components {
                    withModule('org.test:moduleA') {
                        withVariant("$variantToTest") {
                            with${toCamelCase(thing)} { d ->
                                d.findAll { it.name == 'moduleB' }.each {
                                    it.version { 
                                        prefer '1.0'
                                        reject '1.1', '1.2'
                                    }
                                }
                            }
                        }
                    }
                }
            }
        """
        if (defineAsConstraint && !gradleMetadataEnabled && useIvy()) {
            //in plain ivy, we do not have the constraint published. But we can add still add it.
            buildFile.text = buildFile.text.replace("d ->", "d -> d.add('org.test:moduleB') { version { prefer '1.0'; reject '1.1', '1.2' }}")
        }

        repositoryInteractions {
            'org.test:moduleA:1.0' {
                expectGetMetadata()
            }
            'org.test:moduleB:1.1'() {
                expectGetMetadata()
            }
        }

        then:
        fails 'checkDep'
        failure.assertHasCause """Cannot find a version of 'org.test:moduleB' that satisfies the version constraints: 
   Dependency path ':test:unspecified' --> 'org.test:moduleB' prefers '1.1'
   ${defineAsConstraint? 'Constraint' : 'Dependency'} path ':test:unspecified' --> 'org.test:moduleA:1.0' --> 'org.test:moduleB' prefers '1.0', rejects any of "'1.1', '1.2'\""""

        where:
        thing                    | defineAsConstraint
        "dependencies"           | false
        "dependency constraints" | true
    }
}
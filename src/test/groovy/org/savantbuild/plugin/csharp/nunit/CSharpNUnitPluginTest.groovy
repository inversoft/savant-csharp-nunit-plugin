/*
 * Copyright (c) 2016, Inversoft Inc., All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.savantbuild.plugin.csharp.nunit

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import org.savantbuild.dep.domain.Artifact
import org.savantbuild.dep.domain.ArtifactMetaData
import org.savantbuild.dep.domain.Dependencies
import org.savantbuild.dep.domain.DependencyGroup
import org.savantbuild.dep.domain.License
import org.savantbuild.dep.domain.Publication
import org.savantbuild.dep.domain.ReifiedArtifact
import org.savantbuild.dep.domain.Version
import org.savantbuild.dep.workflow.FetchWorkflow
import org.savantbuild.dep.workflow.PublishWorkflow
import org.savantbuild.dep.workflow.Workflow
import org.savantbuild.dep.workflow.process.CacheProcess
import org.savantbuild.dep.workflow.process.URLProcess
import org.savantbuild.domain.Project
import org.savantbuild.io.FileTools
import org.savantbuild.output.Output
import org.savantbuild.output.SystemOutOutput
import org.savantbuild.runtime.RuntimeConfiguration
import org.savantbuild.util.MapBuilder
import org.testng.annotations.BeforeMethod
import org.testng.annotations.BeforeSuite
import org.testng.annotations.Test

import static org.testng.Assert.assertFalse
import static org.testng.Assert.assertTrue

/**
 * Tests the CSharp NUnit plugin.
 *
 * @author Brian Pontarelli
 */
class CSharpNUnitPluginTest {
  public static Path projectDir

  Output output

  Project project

  @BeforeSuite
  public void beforeSuite() {
    projectDir = Paths.get("")
    if (!Files.isRegularFile(projectDir.resolve("LICENSE"))) {
      projectDir = Paths.get("../csharp-nunit-plugin")
    }
  }

  @BeforeMethod
  public void beforeMethod() {
    FileTools.prune(projectDir.resolve("build/cache"))
    FileTools.prune(projectDir.resolve("test-project/build/test"))

    output = new SystemOutOutput(true)
    output.enableDebug()

    project = new Project(projectDir.resolve("test-project"), output)
    project.group = "org.savantbuild.test"
    project.name = "test-project"
    project.version = new Version("1.0")
    project.licenses.put(License.ApacheV2_0, null)

    project.publications.add("main", new Publication(new ReifiedArtifact("org.savantbuild.test:test-project:1.0.0:dll", MapBuilder.simpleMap(License.Commercial, null)), new ArtifactMetaData(null, MapBuilder.simpleMap(License.Commercial, null)),
        Paths.get("build/jars/test-project-1.0.0.dll"), null))
    project.publications.add("test", new Publication(new ReifiedArtifact("org.savantbuild.test:test-project:test-project-test:1.0.0:dll", MapBuilder.simpleMap(License.Commercial, null)), new ArtifactMetaData(null, MapBuilder.simpleMap(License.Commercial, null)),
        Paths.get("build/jars/test-project-test-1.0.0.dll"), null))

    Path repositoryPath = Paths.get(System.getProperty("user.home"), "dev/inversoft/repositories/savant")
    project.dependencies = new Dependencies(
        new DependencyGroup("compile", false, new Artifact("org.nlog-project:NLog:2.1.0:dll", false)),
        new DependencyGroup("test-compile", false, new Artifact("org.nunit:nunit.framework:2.6.3:dll", false))
    )
    project.workflow = new Workflow(
        new FetchWorkflow(output,
            new CacheProcess(output, projectDir.resolve("build/cache").toString()),
            new URLProcess(output, repositoryPath.toUri().toString(), null, null)
        ),
        new PublishWorkflow(
            new CacheProcess(output, projectDir.resolve("build/cache").toString())
        )
    )
  }

  @Test
  public void test() throws Exception {
    CSharpNUnitPlugin plugin = new CSharpNUnitPlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.sdkVersion = "4.5"
    plugin.settings.nunitVersion = "3.4.1"

    plugin.test()
    assertTestsRan("Org.SavantBuild.Test.MyClassTest", "Org.SavantBuild.Test.MyClassIntegrationTest")

    plugin.test(null)
    assertTestsRan("Org.SavantBuild.Test.MyClassTest", "Org.SavantBuild.Test.MyClassIntegrationTest")
  }

  @Test
  public void skipTests() throws Exception {
    RuntimeConfiguration runtimeConfiguration = new RuntimeConfiguration()
    runtimeConfiguration.switches.booleanSwitches.add("skipTests")

    CSharpNUnitPlugin plugin = new CSharpNUnitPlugin(project, runtimeConfiguration, output)
    plugin.settings.sdkVersion = "4.5"
    plugin.settings.nunitVersion = "3.4.1"

    plugin.test()
    assertFalse(Files.isDirectory(projectDir.resolve("test-project/build/test")))
  }

  @Test
  public void withWhere() throws Exception {
    CSharpNUnitPlugin plugin = new CSharpNUnitPlugin(project, new RuntimeConfiguration(), output)
    plugin.settings.sdkVersion = "4.5"
    plugin.settings.nunitVersion = "3.4.1"

    plugin.test(where: "cat == Unit")
    assertTestsRan("Org.SavantBuild.Test.MyClassTest")

    plugin.test(where: "cat == Integration")
    assertTestsRan("Org.SavantBuild.Test.MyClassIntegrationTest")
  }

  static void assertTestsRan(String... classNames) {
    assertTrue(Files.isDirectory(projectDir.resolve("test-project/build/test")))
    assertTrue(Files.isReadable(projectDir.resolve("test-project/build/test/test-report.xml")))

    String xml = projectDir.resolve("test-project/build/test/test-report.xml").toFile().getText()
    classNames.each { className -> assertTrue(xml.contains(className), "Missing test result for ${className}") }
  }
}

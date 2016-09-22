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

import org.savantbuild.dep.domain.ArtifactID
import org.savantbuild.domain.Project
import org.savantbuild.output.Output
import org.savantbuild.plugin.dep.DependencyPlugin
import org.savantbuild.plugin.file.FilePlugin
import org.savantbuild.plugin.groovy.BaseGroovyPlugin
import org.savantbuild.runtime.RuntimeConfiguration

/**
 * The C# NUnit plugin. The public methods on this class define the features of the plugin.
 */
class CSharpNUnitPlugin extends BaseGroovyPlugin {
  public final String CSHARP_ERROR_MESSAGE = "You must create the file [~/.savant/plugins/org.savantbuild.plugin.csharp.properties] " +
      "that contains the system configuration for the Mono/C# system. This file should include the location of the C# compiler " +
      "(msc or csc) by Platform version. These properties look like this:\n\n" +
      "  2.0=/Library/Frameworks/Mono.framework/Versions/2.6.7\n" +
      "  4.0=/Library/Frameworks/Mono.framework/Versions/4.4.2\n"

  public final String NUNIT_ERROR_MESSAGE = "You must create the file [~/.savant/plugins/org.savantbuild.plugin.csharp-nunit.properties] " +
      "that contains the location of the NUnit installation that will be used to run the tests. These properties look like this:\n\n" +
      "  2.6=~/dev/nunit-2.6.3\n" +
      "  3.4=~/dev/nunit-3.4.0\n"

  Properties csharpProperties

  DependencyPlugin dependencyPlugin

  FilePlugin filePlugin

  Path monoPath

  Path nunitConsolePath

  Properties nunitProperties

  CSharpNUnitSettings settings = new CSharpNUnitSettings()

  CSharpNUnitPlugin(Project project, RuntimeConfiguration runtimeConfiguration, Output output) {
    super(project, runtimeConfiguration, output)
    csharpProperties = loadConfiguration(new ArtifactID("org.savantbuild.plugin", "csharp", "csharp", "dll"), CSHARP_ERROR_MESSAGE)
    nunitProperties = loadConfiguration(new ArtifactID("org.savantbuild.plugin", "csharp", "csharp-nunit", "dll"), NUNIT_ERROR_MESSAGE)
    dependencyPlugin = new DependencyPlugin(project, runtimeConfiguration, output)
    filePlugin = new FilePlugin(project, runtimeConfiguration, output)
  }

  /**
   * Runs the NUnit tests. The tests where clause is optional, but if it is specified, only the tests that match the
   * where clause are run. Otherwise, all the tests are run. Here is an example calling this method:
   * <p>
   * <pre>
   *   csharpNUnit.test(where: ["cat == Unit"])
   * </pre>
   *
   * @param attributes The named attributes.
   */
  void test(Map<String, Object> attributes) {
    if (runtimeConfiguration.switches.booleanSwitches.contains("skipTests")) {
      output.infoln("Skipping tests")
      return
    }

    initialize()

    // Initialize the attributes if they are null
    if (!attributes) {
      attributes = [:]
    }

    // Copy everything to the test dir
    dependencyPlugin.copy(to: "build/test", removeVersion: true) {
      settings.dependencies.each { d -> dependencies(d) }
    }

    filePlugin.copy(to: "build/test") {
      fileSet(dir: "build/dlls", includePatterns: [/.+.dll/])
    }

    filePlugin.mkdir(dir: project.directory.resolve(settings.xmlReportFile).getParent())

    List<String> command = [monoPath.toString(), nunitConsolePath.toString()]

    if (settings.nunitArguments != "") {
      command << settings.nunitArguments
    }

    command << "--result=${project.directory.resolve(settings.xmlReportFile).toAbsolutePath().toString()}"

    if (runtimeConfiguration.switches.valueSwitches.containsKey("test")) {
      command << "--test=${runtimeConfiguration.switches.valueSwitches.get("test")}"
    }

    if (runtimeConfiguration.switches.valueSwitches.containsKey("where")) {
      command << "--where" << runtimeConfiguration.switches.valueSwitches.get("where")
    }

    if (attributes.containsKey("where")) {
      command << "--where" << attributes.get("where")
    }

    command << "${project.name}.Test.dll"
    output.debugln("Running command [%s]", command)

    Process process = command.execute(null, project.directory.resolve("build/test").toFile())
    process.consumeProcessOutput(System.out, System.err)

    int result = process.waitFor()
    if (result != 0) {
      fail("Build failed.")
    }
  }

  private void initialize() {
    if (nunitConsolePath) {
      return
    }

    if (!settings.sdkVersion) {
      fail("You must configure the SDK/Mono/.Net version to use with the settings object. It will look something like this:\n\n" +
          "  csharp.settings.sdkVersion=\"2.0\"")
    }

    if (!settings.nunitVersion) {
      fail("You must configure the NUnit version to use with the settings object. It will look something like this:\n\n" +
          "  csharp.settings.nunitVersion=\"2.6\"")
    }

    String csharpHome = csharpProperties.getProperty(settings.sdkVersion)
    if (!csharpHome) {
      fail("No SDK/Mono/.Net platform is configured for version [%s].\n\n[%s]", settings.sdkVersion, CSHARP_ERROR_MESSAGE)
    }

    monoPath = Paths.get(csharpHome, "bin/mono")
    if (!Files.isRegularFile(monoPath)) {
      fail("The Mono executable [%s] does not exist.", monoPath.toAbsolutePath())
    }
    if (!Files.isExecutable(monoPath)) {
      fail("The Mono executable [%s] is not executable.", monoPath.toAbsolutePath())
    }

    String nunitHome = nunitProperties.getProperty(settings.nunitVersion)
    if (!nunitHome) {
      fail("No NUnit installation is configured for version [%s].\n\n[%s]", settings.sdkVersion, NUNIT_ERROR_MESSAGE)
    }

    // Try 2.x
    nunitConsolePath = Paths.get(nunitHome, "bin/nunit-console.exe")
    if (!Files.isRegularFile(nunitConsolePath)) {
      nunitConsolePath = Paths.get(nunitHome, "bin/nunit3-console.exe")
    }

    if (!Files.isRegularFile(nunitConsolePath)) {
      fail("The NUnit executable [%s] does not exist.", nunitConsolePath.toAbsolutePath())
    }
  }
}

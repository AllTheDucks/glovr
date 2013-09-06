package com.wileyfuller

import com.google.gson.Gson
import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.tasks.JavaExec

class GlovrPlugin implements Plugin<Project> {
    String userHome = System.getProperty("user.home")
    String plovrJarName = "plovr-81ed862.jar";
    File plovrHome = new File("$userHome/plovr")
    File plovrJar = new File("$plovrHome/$plovrJarName")
    File tmpDir = new File(System.getProperty('java.io.tmpdir'))


    void apply(Project project) {
//        project.extensions.create("greeting", GreetingPluginExtension)
        project.task('plovrServe') << {
            checkPlovrJar(project)
            String configFileName = generatePlovrServeConfig(project)
            project.javaexec {
                main = 'org.plovr.cli.Main'
                classpath = project.files(plovrJar)
                args 'serve', tmpDir.absolutePath + '/' + configFileName
                systemProperty 'java.net.preferIPv4Stack', 'true'
            }
        }


        project.task('plovrBuild') << {
            checkPlovrJar(project)
            String configFileName = generatePlovrBuildConfig(project)
            project.javaexec {
                main = 'org.plovr.cli.Main'
                classpath = project.files(plovrJar)
                args 'build', tmpDir.absolutePath + '/' + configFileName
                systemProperty 'java.net.preferIPv4Stack', 'true'
            }

        }

        project.war.dependsOn project.plovrBuild

    }

    void checkPlovrJar(Project project) {

        if (!plovrJar.exists()) {
            project.ant.get(
                    src: "https://plovr.googlecode.com/files/$plovrJarName",
                    dest: plovrHome
            )
        } else {
            //TODO Change this to an INFO level log statement
            //println "Plovr jar already downloaded"

        }
    }

    String generatePlovrServeConfig(Project project) {
        String fileName = project.name + "-plovr-SERVE.js"
        File configFile = new File(tmpDir, fileName)
        configFile.withWriter { out ->
            out.write("{\"id\": \"$project.name\", \"paths\": \".\"," +
                    "  \"inputs\": \"main.js\"}")
        }
        return fileName;
    }

    String generatePlovrBuildConfig(Project project) {
        String fileName = project.name + "-plovr-BUILD.js"
        File configFile = new File(tmpDir, fileName)
        def configMap = [id: project.name, paths: new String("$project.projectDir/src/main/javascript"),
                inputs: new String("$project.projectDir/src/main/javascript/main.js"),
                'output-file': new String("$project.buildDir/plovr/compiled/main.js")]
        Gson gson = new Gson();
        configFile.withWriter { out ->
            out.write(gson.toJson(configMap))
//            out.write("{\"id\": \"$project.name\", \"paths\": \"$project.projectDir/src/main/javascript\"," +
//                    "  \"inputs\": \"$project.projectDir/src/main/javascript/main.js\"," +
//                    "\"output-file\":\"$project.buildDir/plovr/compiled/main.js\"}")
        }
        project.war.from "$project.buildDir/plovr/compiled/main.js"
        return fileName;
    }
}


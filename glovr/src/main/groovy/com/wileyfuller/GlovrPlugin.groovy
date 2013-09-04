package com.wileyfuller

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
            String configFileName = generatePlovrConfig(project)
            project.javaexec {
                main = 'org.plovr.cli.Main'
                classpath = project.files(plovrJar)
                args 'serve', tmpDir.absolutePath + '/' + configFileName
                systemProperty 'java.net.preferIPv4Stack', 'true'
            }
        }


        project.task('plovrBuild') << {

        }


    }

    void checkPlovrJar(Project project) {

        if (!plovrJar.exists()) {
            project.ant.get(
                    src: "https://plovr.googlecode.com/files/$plovrJarName",
                    dest: plovrHome
            )
        } else {
            println "Don't need plovr jar"

        }
    }

    String generatePlovrConfig(Project project) {
        String fileName = project.name + "-plovr-SERVE.js"
        File configFile = new File(tmpDir, fileName)
        configFile.withWriter { out ->
            out.write("{\"id\": \"sample\", \"paths\": \".\"," +
                    "  \"inputs\": \"main.js\"}")
        }
        return fileName;
    }
}


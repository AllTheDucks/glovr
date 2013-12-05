package com.wileyfuller

import com.google.gson.Gson
import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskInstantiationException

class GlovrPlugin implements Plugin<Project> {
    String userHome = System.getProperty("user.home")
    String plovrJarName = "plovr-81ed862.jar";
    File plovrHome = new File("$userHome/plovr")
    File plovrJar = new File("$plovrHome/$plovrJarName")
    File tmpDir = new File(System.getProperty('java.io.tmpdir'))



    void apply(Project project) {
        project.extensions.create("glovr", GlovrPluginExtension)

        String serveMode = project.glovr.serveMode ? project.glovr.serveMode : project.glovr.mode
        String buildMode = project.glovr.buildMode ? project.glovr.buildMode : project.glovr.mode

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


        project.task('plovrBuild') {
//        task plovrBuild  {
            inputs.dir getJsRootAbsolute(project)
            outputs.dir new File("$project.buildDir/plovr/compiled/$project.glovr.jsOutputDir")
            doLast {
                checkPlovrJar(project)
                String configFileName = generatePlovrBuildConfig(project)
                project.javaexec {
                    main = 'org.plovr.cli.Main'
                    classpath = project.files(plovrJar)
                    args 'build', tmpDir.absolutePath + '/' + configFileName
                    systemProperty 'java.net.preferIPv4Stack', 'true'
                }
            }

        }

        if (project.plugins.hasPlugin("war")) {
            project.war.from "$project.buildDir/plovr/compiled"
            project.war.dependsOn project.plovrBuild
        }

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
        String mode = project.glovr.serveMode ? project.glovr.serveMode : project.glovr.mode;
        String fileName = generatePlovrConfig(project, 'SERVE', mode)
        return fileName;
    }

    String generatePlovrBuildConfig(Project project) {
        String mode = project.glovr.buildMode ? project.glovr.buildMode : project.glovr.mode;
        String fileName = generatePlovrConfig(project, 'BUILD', mode)

        return fileName;
    }

    String generatePlovrConfig(Project project, String configName, String mode) {
        String fileName = project.name + "-plovr-" + configName + ".js"
        File configFile = new File(tmpDir, fileName)
        String jsRoot = getJsRootAbsolute(project).absolutePath

        def configMap = [id: project.name,
                paths: new String("$jsRoot"),
                inputs: new String("$jsRoot/$project.glovr.mainJs"),
                mode: mode,
                externs: getExterns(project),
                'output-file': new String("$project.buildDir/plovr/compiled/$project.glovr.jsOutputDir/$project.glovr.mainJs"),
                'checks': ["externsValidation": "OFF"] ]

        Gson gson = new Gson();
        configFile.withWriter { out ->
            out.write(gson.toJson(configMap))
        }

        return fileName;
    }

    List<String> getExterns(Project project) {
        List<String> externs = new ArrayList<String>()

        if(project.glovr.externs) {
            for(extern in project.glovr.externs) {
                File externFile = new File(project.projectDir, extern)
                externs.add(externFile.absolutePath)
            }
        } else {
            //TODO: auto discovery
        }

        return externs
    }

    File getJsRootAbsolute(Project project) {
        return new File(project.projectDir, project.glovr.jsRoot)
    }
}


class GlovrPluginExtension {
    def mainJs = "main.js"
    def jsRoot = "/src/main/javascript/"
    def jsOutputDir = "js"
    def mode = "SIMPLE"
    def serveMode = null
    def buildMode = null
    def externs = null
}
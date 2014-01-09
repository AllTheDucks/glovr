package com.alltheducks

import com.google.gson.Gson
import org.gradle.api.Project
import org.gradle.api.Plugin

class GlovrPlugin implements Plugin<Project> {
    File glovrHome
    File plovrJar

    void apply(Project project) {
        project.extensions.create("glovr", GlovrPluginExtension)

        glovrHome = new File(project.rootProject.projectDir, ".glovr")
        glovrHome.mkdirs();
        plovrJar = new File(glovrHome,project.glovr.jarName)

        project.task('plovrServe') << {
            checkPlovrJar(project)
            String configFileName = generatePlovrServeConfig(project)
            project.javaexec {
                main = 'org.plovr.cli.Main'
                classpath = project.files(plovrJar)
                args 'serve', glovrHome.absolutePath + '/' + configFileName
                systemProperty 'java.net.preferIPv4Stack', 'true'
            }
        }


        project.task('plovrBuild') {
            inputs.dir getJsRootAbsolute(project)
            outputs.dir new File("$project.buildDir/plovr/compiled/$project.glovr.jsOutputDir")
            doLast {
                checkPlovrJar(project)
                String configFileName = generatePlovrBuildConfig(project)
                project.javaexec {
                    main = 'org.plovr.cli.Main'
                    classpath = project.files(plovrJar)
                    args 'build', glovrHome.absolutePath + '/' + configFileName
                    systemProperty 'java.net.preferIPv4Stack', 'true'
                }
            }

        }

        project.task('gjslint') {
            inputs.dir getJsRootAbsolute(project)
            outputs.upToDateWhen( { return true } );
            doLast {
                def command = getLinterCommand('gjslint', project);
                logger.info(command);
                def proc
                try {
                    proc = command.execute()
                } catch (IOException ioex) {
                    println 'Google Closure Linter is not installed. If you install it, your javascript will be automatically linted.'
                    return
                }

                proc.waitFor()

                switch(proc.exitValue()) {
                    case 0:
                        logger.info(proc.text);
                    break

                    case 1:
                        println proc.text;
                        throw new Exception("Javascript linting errors found; please fix them. Some may be automatically fixed using the fixjsstyle task.")
                    break

                    default:
                        println proc.text;
                        throw new Exception("Unknown exit code: ${ proc.exitValue() }")
                    break
                }
            }
        }

        project.task('fixjsstyle') {
            inputs.dir getJsRootAbsolute(project)
            outputs.upToDateWhen( { return true } );
            doLast {
                def command = getLinterCommand('fixjsstyle', project);
                logger.info(command);
                def proc
                try {
                    proc = command.execute()
                } catch (IOException ioex) {
                    println 'Google Closure Linter is not installed. If you install it, your javascript will have some linting errors automatically fixed.'
                    return
                }

                proc.waitFor()

                println proc.text;
                if(proc.exitValue() != 0) {
                    throw new Exception("Unknown exit code: ${ proc.exitValue() }")
                }
            }
        }

        if(project.glovr.autoLint) {
            project.plovrBuild.dependsOn project.gjslint
        }

        if (project.plugins.hasPlugin("war")) {
            project.war.from "$project.buildDir/plovr/compiled"
            project.war.dependsOn project.plovrBuild
        }

    }

    void checkPlovrJar(Project project) {

        if (!plovrJar.exists()) {
            project.ant.get(
                    src: "https://plovr.googlecode.com/files/$project.glovr.jarName",
                    dest: glovrHome
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
        File configFile = new File(glovrHome, fileName)
        String jsRoot = getJsRootAbsolute(project).absolutePath

        def configMap = [id: project.name,
                paths: getPaths(project),
                inputs: new String("$jsRoot/$project.glovr.mainJs"),
                mode: mode,
                externs: getExterns(project),
                'output-file': new String("$project.buildDir/plovr/compiled/$project.glovr.jsOutputDir/$project.glovr.mainJs"),
                'checks': ["externsValidation": "OFF"] ]

        configMap.putAll(project.glovr.options)

        Gson gson = new Gson();
        configFile.withWriter { out ->
            out.write(gson.toJson(configMap))
        }

        return fileName;
    }

    String getLinterCommand(String command, Project project) {
        def path
        if(project.glovr.lintPaths) {
            path = getPaths(project).join(" ")
        } else {
            path = getJsRootAbsolute(project).absolutePath
        }

        def strict = project.glovr.strictLint ? "--strict " : ""
        return "$command $strict-r $path";
    }

    List<String> getPaths(Project project) {
        String jsRoot = getJsRootAbsolute(project).absolutePath

        List<String> paths = new ArrayList<String>();
        paths.add(new String("$jsRoot"));

        if (project.glovr.paths instanceof Collection<? extends String>) {
            paths.addAll(project.glovr.paths);
        } else {
            paths.add(project.glovr.paths.toString());
        }
        return paths
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
    def jarName = "plovr-81ed862.jar"
    def mainJs = "main.js"
    def jsRoot = "/src/main/javascript/"
    def jsOutputDir = "js"
    def mode = "SIMPLE"
    def serveMode = null
    def buildMode = null
    def externs = null
    def paths = null
    def options = null

    def autoLint = true
    def lintPaths = true
    def strictLint = true

}

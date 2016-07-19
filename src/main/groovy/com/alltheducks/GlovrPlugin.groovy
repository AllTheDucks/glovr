package com.alltheducks

import com.google.gson.Gson
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.gradle.api.Plugin

class GlovrPlugin implements Plugin<Project> {
    File glovrHome
    File plovrJar
    File stylesheetsJar

    void apply(Project project) {
        project.extensions.create("glovr", GlovrPluginExtension)

        glovrHome = new File(project.rootProject.projectDir, ".glovr")
        glovrHome.mkdirs();
        plovrJar = new File(glovrHome,project.glovr.jarName)
        stylesheetsJar = new File(glovrHome,project.glovr.stylesheetsJarName)


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
            inputs.files getJsPaths(project).plus(getCssPaths(project))
            outputs.files(new File("$project.buildDir/plovr/compiled/$project.glovr.jsOutputDir"),
                    new File("$project.buildDir/plovr/compiled/$project.glovr.cssOutputDir"))
            doLast {
                checkPlovrJar(project)
                def cssInputs = getCssPaths(project)
                if (cssInputs && cssInputs.size() > 0) {
                    processCss(project, cssInputs)
                }
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
                    src: "https://plovr.googlecode.com/files/${project.glovr.jarName}",
                    dest: glovrHome
            )
        } else {
            //TODO Change this to an INFO level log statement
            //println "Plovr jar already downloaded"

        }

        if (!stylesheetsJar.exists()) {
            project.ant.get(
                    src: "https://closure-stylesheets.googlecode.com/files/${project.glovr.stylesheetsJarName}",
                    dest: glovrHome
            )
        } else {
            //TODO Change this to an INFO level log statement
            //println "closure stylesheets jar already downloaded"

        }
    }

    String generatePlovrServeConfig(Project project) {
        String mode = project.glovr.serveMode ? project.glovr.serveMode : project.glovr.mode;
        String fileName = generatePlovrConfig(project, 'SERVE', mode, false)
        return fileName;
    }

    String generatePlovrBuildConfig(Project project) {
        String mode = project.glovr.buildMode ? project.glovr.buildMode : project.glovr.mode;
        String fileName = generatePlovrConfig(project, 'BUILD', mode, project.glovr.cssRename)
        return fileName;
    }

    String generatePlovrConfig(Project project, String configName, String mode, boolean cssRename) {
        String fileName = project.name + "-plovr-" + configName + ".js"
        File configFile = new File(glovrHome, fileName)
        String jsRoot = getJsRootAbsolute(project).absolutePath


        def cssDir = new File("$project.buildDir/plovr/compiled/$project.glovr.cssOutputDir");
        cssDir.mkdirs()

        def inputs = new ArrayList()
        if(cssRename) {
            inputs.add(new String(glovrHome.absolutePath + '/cssRename.js'));
        }
        inputs.add(new String("$jsRoot/$project.glovr.mainJs"));

        def configMap = [id: project.name,
                paths: getJsPaths(project),
                inputs: inputs,
                mode: mode,
                externs: getExterns(project),
                'output-file': new String("$project.buildDir/plovr/compiled/$project.glovr.jsOutputDir/$project.glovr.mainJs"),
                'checks': ["externsValidation": "OFF"],
                'css-inputs': getCssPaths(project)]

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
            path = getJsPaths(project).join(" ")
        } else {
            path = getJsRootAbsolute(project).absolutePath
        }

        def strict = project.glovr.strictLint ? "--strict " : ""
        return "$command $strict-r $path";
    }

    Collection<String> getCssPaths(Project project) {
        Collection<File> cssFiles = FileUtils.listFiles(new File(project.projectDir, project.glovr.cssRoot), ['css','gss'] as String[],true)
        Collection<String> cssPaths = cssFiles*.getAbsolutePath();
        return cssPaths
    }


    void processCss(Project project, Collection<String> cssPaths) {

        def cssDir = new File("$project.buildDir/plovr/compiled/$project.glovr.cssOutputDir");
        cssDir.mkdirs()

        String cssOutputFile = new File(cssDir, project.glovr.mainCss).getAbsolutePath();

        List<String> inputArgs = new ArrayList();
        def allowedFunctions = project.glovr.options['css-allowed-non-standard-functions']
        def allowedProps = project.glovr.options['css-allowed-unrecognized-properties']

        inputArgs.addAll(interpolateList('--allowed-non-standard-function', allowedFunctions));
        inputArgs.addAll(interpolateList('--allowed-unrecognized-property', allowedProps));

        inputArgs.add('--output-file');
        inputArgs.add(cssOutputFile)

        if(project.glovr.cssDefines) {
            inputArgs.addAll(interpolateList('--define', project.glovr.cssDefines))
        }

        if(project.glovr.cssRename) {
            inputArgs.add('--output-renaming-map-format')
            inputArgs.add('CLOSURE_COMPILED')

            inputArgs.add('--rename')
            inputArgs.add('CLOSURE')

            inputArgs.add('--output-renaming-map')
            inputArgs.add(glovrHome.absolutePath + '/cssRename.js')
        }

        inputArgs.addAll(cssPaths)

//        logger.info("Closure Stylesheets Args: " + inputArgs.join(" "))

        project.javaexec {
            main = 'com.google.common.css.compiler.commandline.ClosureCommandLineCompiler'
            classpath = project.files(stylesheetsJar)
            args inputArgs.toArray()
        }
    }


    List<String> interpolateList(String interpolationValue, List<String> inputList) {
        List<String> outputList = new ArrayList<String>();
        for (val in inputList) {
            outputList.add(interpolationValue);
            outputList.add(val);
        }
        return outputList;
    }


    List<String> getJsPaths(Project project) {
        String jsRoot = getJsRootAbsolute(project).absolutePath


        List<String> paths = new ArrayList<String>();
        paths.add(new String("$jsRoot"));

        if (project.glovr.paths instanceof Collection<? extends String>) {
            paths.addAll(project.glovr.paths);
        } else if(project.glovr.paths != null) {
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
    def stylesheetsJarName = "closure-stylesheets-20111230.jar"
    def mainJs = "main.js"
    def jsRoot = "/src/main/javascript/"
    def jsOutputDir = "js"
    def mainCss = "main.css"
    def cssRoot = "/src/main/css"
    def cssOutputDir = "css"
    def mode = "SIMPLE"
    def serveMode = null
    def buildMode = null
    def externs = null
    def paths = null
    def options = null
    def cssDefines = null
    def cssRename = false

    def autoLint = true
    def lintPaths = true
    def strictLint = true

}

//
// This script is executed by Grails after plugin was installed to project.
// This script is a Gant script so you can use all special variables provided
// by Gant (such as 'baseDir' which points on project base dir). You can
// use 'ant' to access a global instance of AntBuilder
//
// For example you can create directory under project tree:
//
//    ant.mkdir(dir:"${basedir}/grails-app/jobs")
//


ant.copy(todir:"${basedir}/grails-app/conf", file:"${appEnginePluginDir}/src/templates/jdoconfig.xml")
ant.copy(todir:"${basedir}/grails-app/conf", file:"${appEnginePluginDir}/src/templates/datastore-indexes.xml")
println "Installed JDO config to ${basedir}/grails-app/conf"
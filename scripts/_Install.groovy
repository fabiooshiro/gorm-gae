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



ant.copy(todir:"${basedir}/grails-app/conf", file:"${appEnginePluginDir}/src/templates/datastore-indexes.xml")
ant.mkdir(dir:"${basedir}/src/templates")


uninstallPluginForName "hibernate"
persistenceProvider = "jdo"
if(isInteractive) {
	ant.input(message:"Do you want to use JPA or JDO for persistence?",validargs:"jpa,jdo", addproperty:"persistence.provider")
	persistenceProvider = ant.antProject.properties["persistence.provider"]
}

if(persistenceProvider == 'jdo') {
	ant.copy(todir:"${basedir}/grails-app/conf", file:"${appEnginePluginDir}/src/templates/jdoconfig.xml")
}
else if(persistenceProvider == 'jpa') {
	ant.copy(todir:"${basedir}/grails-app/conf", file:"${appEnginePluginDir}/src/templates/persistence.xml")	
}
metadata['appengine.persistence'] = persistenceProvider
metadata.persist()
ant.copy(todir:"${basedir}/src/templates") {
	fileset(dir:"${appEnginePluginDir}/src/templates/$persistenceProvider")
}
ant.mkdir(dir:"${basedir}/src/templates/war")
ant.copy(file:"${appEnginePluginDir}/src/templates/war/web.xml",todir:"${basedir}/src/templates/war", overwrite:true) 	
println "Installed JDO config to ${basedir}/grails-app/conf"


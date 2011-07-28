appEngineSDK=System.getenv("APPENGINE_HOME")
if(!appEngineSDK && buildConfig.google.appengine.sdk) {
	appEngineSDK = buildConfig.google.appengine.sdk
}
if(!appEngineSDK) {
	println "No Google AppEngine SDK specified. Either set APPENGINE_HOME in your environment or specify google.appengine.sdk in your grails-app/conf/BuildConfig.groovy file"
	System.exit(1)
}
target(doInstallGormGaePlugin: 'Install the plugin'){
//def doInstallGormGaePlugin(){
	ant.copy(todir:"${basedir}/grails-app/conf", file:"${gormGaePluginDir}/src/templates/datastore-indexes.xml")
	//ant.mkdir(dir:"${basedir}/src/templates")
	ant.mkdir(dir:"${basedir}/src/groovy/org/grails/appengine")
	ant.mkdir(dir:"${basedir}/test/unit/org/grails/appengine/")

	if(pluginSettings.getPluginDirForName("hibernate")?.exists())
		uninstallPluginForName "hibernate"
	if(pluginSettings.getPluginDirForName("tomcat")?.exists())	
		uninstallPluginForName "tomcat"
	persistenceProvider = "jdo"
	if(isInteractive) {
		ant.input(message:"Do you want to use JPA or JDO for persistence?",validargs:"jpa,jdo", addproperty:"persistence.provider")
		persistenceProvider = ant.antProject.properties["persistence.provider"]
	}

	if(persistenceProvider == 'jdo') {
		ant.copy(todir:"${basedir}/grails-app/conf", file:"${gormGaePluginDir}/src/templates/jdoconfig.xml")

	}
	else if(persistenceProvider == 'jpa') {
		ant.copy(todir:"${basedir}/grails-app/conf", file:"${gormGaePluginDir}/src/templates/persistence.xml")
		installPluginForName "gorm-jpa"
	}


	metadata['appengine.persistence'] = persistenceProvider
	metadata.persist()
	/*
	ant.copy(todir:"${basedir}/src/templates") {
		fileset(dir:"${appEnginePluginDir}/src/templates/$persistenceProvider")
	}
	*/
	ant.mkdir(dir:"${basedir}/src/templates/war")
	ant.copy(file:"${gormGaePluginDir}/src/templates/war/web.xml",todir:"${basedir}/src/templates/war", overwrite:true)
	ant.copy(file:"${gormGaePluginDir}/src/templates/war/appengine-web.xml",todir:"${basedir}/web-app/WEB-INF")

	// cleanup
	ant.delete(dir:"${basedir}/web-app/WEB-INF/lib", failonerror:false)
	ant.delete(dir:"${basedir}/web-app/WEB-INF/classes", failonerror:false)
	println "Installed [$persistenceProvider] config to ${basedir}/grails-app/conf"

}

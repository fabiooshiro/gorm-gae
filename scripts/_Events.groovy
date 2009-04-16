includeTargets << new File("${appEnginePluginDir}/scripts/_AppEngineCommon.groovy")

eventCreateWarStart = { warLocation, stagingDir ->
	def appVersion = metadata.'app.version'
	def appName = config.google.appengine.application ?: grailsAppName

	
	println "Generating appengine-web.xml file for application [$appName]"	
	new File("$stagingDir/WEB-INF/appengine-web.xml").write """<?xml version=\"1.0\" encoding=\"utf-8\"?>
<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">
    <application>${appName}</application>
    <version>${appVersion}</version>
</appengine-web-app>	
"""	

	println "Packaging AppEngine jar files"
	ant.copy(todir:"$stagingDir/WEB-INF/lib", flatten:true) {
		fileset(dir:"${appEngineSDK}/lib") {
			include(name:"user/*.jar")
			include(name:"user/orm/*.jar")			
		}		
	}
	
	println "Configuring JDO for AppEngine"
	if(new File("${basedir}/grails-app/conf/jdoconfig.xml").exists()) {
		ant.mkdir(dir:"$stagingDir/WEB-INF/classes/META-INF")
		ant.copy(file:"${basedir}/grails-app/conf/jdoconfig.xml",todir:"$stagingDir/WEB-INF/classes/META-INF")
	}
	if(new File("${basedir}/grails-app/conf/datastore-indexes.xml").exists()) {
		ant.copy(file:"${basedir}/grails-app/conf/datastore-indexes.xml",todir:"$stagingDir/WEB-INF")
	}
	if(new File("${projectWorkDir}/appengine-generated").exists()) {
		ant.mkdir(dir:"$stagingDir/WEB-INF/appengine-generated")
		ant.copy(todir:"$stagingDir/WEB-INF/appengine-generated", failonerror:false) {
			fileset(dir:"${projectWorkDir}/appengine-generated") 
		}
	}
	
	println "Enhancing JDO classes"
	ant.'import'(file:"${appEngineSDK}/config/user/ant-macros.xml")
	ant.enhance_war(war:stagingDir)	
}

eventSetClasspath = {
	classpathSet = false
	
	def appEngineJars = ant.fileScanner {
		fileset(dir:"${appEngineSDK}/lib") {
			include(name:"user/**/*.jar")
		}
	}.collect { it }
	
	grailsSettings.compileDependencies.addAll appEngineJars
	grailsSettings.runtimeDependencies.addAll appEngineJars	
	grailsSettings.testDependencies.addAll appEngineJars	
	classpath()
}
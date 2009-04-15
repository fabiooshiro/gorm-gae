includeTargets << new File("${appEnginePluginDir}/scripts/_AppEngineCommon.groovy")

eventCreateWarStart = { warLocation, stagingDir ->
	def appVersion = metadata.'app.version'
	def appName = config.google.appengine.application ?: grailsAppName


	new File("$stagingDir/WEB-INF/appengine-web.xml").write """<?xml version=\"1.0\" encoding=\"utf-8\"?>
<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">
    <application>${grailsAppName}</application>
    <version>${appVersion}</version>
</appengine-web-app>	
"""	

	ant.copy(todir:"$stagingDir/WEB-INF/lib") {
		fileset(dir:"${appEngineSDK}/lib") {
			include(name:"user/**/*.jar")
		}		
	}
}

eventCompileStart = {
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
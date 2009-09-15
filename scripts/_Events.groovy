includeTargets << new File("${appEnginePluginDir}/scripts/_AppEngineCommon.groovy")

eventCreateWarStart = { warLocation, stagingDir ->
	def appVersion = metadata.'app.version'
	def appName = config.google.appengine.application ?: grailsAppName

	def enableSessions = config.google.appengine.sessionEnabled?:true
	def enableSsl = config.google.appengine.enableSsl?:true
	
	println "Generating appengine-web.xml file for application [$appName]"	
	new File("$stagingDir/WEB-INF/appengine-web.xml").write """<?xml version=\"1.0\" encoding=\"utf-8\"?>
<appengine-web-app xmlns=\"http://appengine.google.com/ns/1.0\">
    <application>${appName}</application>
    <version>${appVersion}</version>
    <sessions-enabled>${enableSessions}</sessions-enabled>
    <ssl-enabled>${enableSsl}</ssl-enabled>
	<system-properties>
	    <!-- this property should be necessary. it avoids the report of an abnormal and unexpected situation!
	        <property name=\"appengine.orm.disable.duplicate.emf.exception\" value=\"true\" />
	     -->
    </system-properties>

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
	if(new File("${basedir}/grails-app/conf/persistence.xml").exists()) {
		ant.mkdir(dir:"$stagingDir/WEB-INF/classes/META-INF")
		ant.copy(file:"${basedir}/grails-app/conf/persistence.xml",todir:"$stagingDir/WEB-INF/classes/META-INF")
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
	
	def devAppEngineJars = ant.fileScanner {
		fileset(dir:"${appEngineSDK}/lib") {
			include(name:"user/**/*.jar")
		}
	}.collect { it }
	
	grailsSettings.compileDependencies.addAll devAppEngineJars
	grailsSettings.runtimeDependencies.addAll devAppEngineJars	
	grailsSettings.testDependencies.addAll devAppEngineJars
	
    // needed to unit tests appengine related classes without adding files to lib dir
	def testAppEngineJars = ant.fileScanner {
	    fileset(dir:"${appEngineSDK}/lib") {
	        include(name:"impl/appengine-local-runtime.jar")
	    }
	}.collect { it }
	grailsSettings.testDependencies.addAll testAppEngineJars

	classpath()
}

eventTestSuiteStart = { String type ->    
    if (type.equalsIgnoreCase("unit")) {
        
        // as grails change the classloader between compile and run phases
        // we need to manually add the app engine jars to the class loader.
	    def appEngineJarsNeededAtUnitTesting = ant.fileScanner {
		    fileset(dir:"${appEngineSDK}/lib") {
			    include(name:"user/**/*.jar")
	            include(name:"impl/appengine-local-runtime.jar")
	        }
	    }.collect { it.toURL() }

	    for (jar in appEngineJarsNeededAtUnitTesting) {
        	rootLoader?.addURL( jar )
	    }
	    
	    println "Enhancing classes"
	    ant.'import'(file:"${appEngineSDK}/config/user/ant-macros.xml")
	    	            	    
        try {
            ant.copy(todir:"${basedir}/web-app/META-INF", file:"${basedir}/grails-app/conf/persistence.xml")
	        def appengineEnhancers = ant.path {
		        fileset(dir:"${appEngineSDK}/lib") {
	                include(name:"tools/**/*.jar")
			        include(name:"*.jar")
	            }
	            pathelement(location:"${basedir}/web-app/")
	        }
            ant.enhance(
                failonerror:true,
                classpath:appengineEnhancers,
                persistenceUnit:"transactions-optional",
                dir:"${projectWorkDir}/classes")
        } finally {
            ant.delete(file:"${projectWorkDir}/classes/persistence.xml")
        }
    }
}


eventRunAppStart = {
	println "The command 'grails run-app' is not supported with AppEngine. Use 'grails app-engine' to start the application"
	exit(1)
}

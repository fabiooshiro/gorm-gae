import grails.util.BuildSettings

includeTargets << new File("${appEnginePluginDir}/scripts/_AppEngineCommon.groovy")
includeTargets << grailsScript("_GrailsWar")

eventGenerateWebXmlEnd = {
	packagePluginsForWar("${basedir}/web-app")
    ant.copy(file:webXmlFile, todir:"${basedir}/web-app/WEB-INF", overwrite:true)
	System.setProperty("grails.server.factory", "org.grails.appengine.AppEngineJettyServerFactory")
	defaultWarDependencies.curry(ant)
	def libDir = "${basedir}/web-app/WEB-INF/lib"
	ant.mkdir(dir:libDir)
	ant.copy(todir:libDir, defaultWarDependencies)
  
	ant.replace(file:"${basedir}/web-app/WEB-INF/applicationContext.xml",
                token:"classpath*:", value:"/WEB-INF/" )

	stagingDir = "${basedir}/web-app"

    packagePersistenceDescriptors(stagingDir, basedir)
	createDescriptor()
	warPlugins()
	packagePluginsForWar(stagingDir)
    packageAppEngineJars(stagingDir)
  
	ant.mkdir(dir:"${stagingDir}/WEB-INF/grails-app")	
    ant.copy(todir:"${stagingDir}/WEB-INF/grails-app", overwrite:true) {
        fileset(dir:"${resourcesDirPath}/grails-app", includes:"i18n/**")
    }
	
	println "Enhancing JDO classes"
	ant.'import'(file:"${appEngineSDK}/config/user/ant-macros.xml")
	ant.enhance_war(war:stagingDir)	
	
}

eventCreateWarStart = { warLocation, stagingDir ->
	def appVersion = metadata.'app.version'
	def appName = config.google.appengine.application ?: grailsAppName


    String targetAppEngineWebXml = "${stagingDir}/WEB-INF/appengine-web.xml"
    ant.copy(file:"${basedir}/web-app/WEB-INF/appengine-web.xml", tofile:targetAppEngineWebXml, overwrite:true)
    ant.replace(file:targetAppEngineWebXml,
              token:"@app.name@", value:appName )
    ant.replace(file:targetAppEngineWebXml,
              token:"@app.version@", value:appVersion )


	packageAppEngineJars(stagingDir)
	


	if(new File("${basedir}/grails-app/conf/datastore-indexes.xml").exists()) {
		ant.copy(file:"${basedir}/grails-app/conf/datastore-indexes.xml",todir:"$stagingDir/WEB-INF")
	}
	if(new File("${projectWorkDir}/appengine-generated").exists()) {
		ant.mkdir(dir:"$stagingDir/WEB-INF/appengine-generated")
		ant.copy(todir:"$stagingDir/WEB-INF/appengine-generated", failonerror:false) {
			fileset(dir:"${projectWorkDir}/appengine-generated") 
		}
	}

}

private packageAppEngineJars(stagingDir) {
    println "Packaging AppEngine jar files"
	ant.copy(todir:"$stagingDir/WEB-INF/lib", flatten:true) {
		fileset(dir:"${appEngineSDK}/lib") {
			include(name:"user/*.jar")
			include(name:"user/orm/*.jar")
		}
	}
}

private packagePersistenceDescriptors (stagingDir, basedir) {
  println "Configuring persistence for AppEngine"
  
  if(new File("${basedir}/grails-app/conf/jdoconfig.xml").exists()) {
      ant.mkdir(dir:"$stagingDir/WEB-INF/classes/META-INF")
      ant.copy(file:"${basedir}/grails-app/conf/jdoconfig.xml",todir:"$stagingDir/WEB-INF/classes/META-INF")
  }
  if(new File("${basedir}/grails-app/conf/persistence.xml").exists()) {
      ant.mkdir(dir:"$stagingDir/WEB-INF/classes/META-INF")
      ant.copy(file:"${basedir}/grails-app/conf/persistence.xml",todir:"$stagingDir/WEB-INF/classes/META-INF")
  }

}


eventSetClasspath = {
	classpathSet = false
    BuildSettings buildSettings = grailsSettings

    classesDir = new File("${basedir}/web-app/WEB-INF/classes")
    classesDirPath = classesDir.path
    buildSettings.classesDir = classesDir
	
	def devAppEngineJars = ant.fileScanner {
		fileset(dir:"${appEngineSDK}/lib") {
			include(name:"user/**/*.jar")
		}
		fileset(dir:"${appEngineSDK}/lib") {
			include(name:"appengine-tools-api.jar")
		}
	}.collect { it }
	
	buildSettings.compileDependencies.addAll devAppEngineJars
	buildSettings.runtimeDependencies.addAll devAppEngineJars
	buildSettings.testDependencies.addAll devAppEngineJars

    for(jar in devAppEngineJars) {
        rootLoader.addURL(jar.toURL())
    }
	
    // needed to unit tests appengine related classes without adding files to lib dir
	def testAppEngineJars = ant.fileScanner {
	    fileset(dir:"${appEngineSDK}/lib") {
	        include(name:"impl/appengine-local-runtime.jar")
	    }
	}.collect { it }
	buildSettings.testDependencies.addAll testAppEngineJars

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



import grails.util.*

includeTargets << grailsScript("_GrailsWar")

scriptEnv = "dev"

target(cleanUpAfterWar:"override to avoid cleanup") { 
	// do nothing
}
target(main: "Runs a Grails application in the AppEngine development environment") {
	depends(parseArguments)
	
	ant.'import'(file:"${appEngineSDK}/config/user/ant-macros.xml")
	ant.delete(dir:"${grailsSettings.projectWorkDir}/staging")
	ant.mkdir dir:"${basedir}/target"	
	def cmd = argsMap.params ? argsMap.params[0] : 'run'
	def debug = argsMap.debug ?: false
	argsMap.params.clear()	
	
	if(!buildConfig.grails.war.destFile) {
		buildConfig.grails.war.destFile="${basedir}/target/${grailsAppName}-${metadata.getApplicationVersion()}.war"		
	}
	war()
	
	
	switch(cmd) {
		case 'package':
			def targetDir = "${basedir}/target/war"
			ant.delete dir:targetDir
			ant.mkdir dir:targetDir			
			ant.copy(todir:targetDir) {
				fileset(dir:"${grailsSettings.projectWorkDir}/stage")
			}
			println "Created distribution at $targetDir"
		break
		case 'run':
			startDevServer(debug)		
			break
		case ~/(update|deploy)/:
			ant.appcfg(action:"update", war:stagingDir); break
		case ~/(update_indexes)/:
			ant.appcfg(action:"update_indexes", war:stagingDir); break			
		case 'rollback':
			ant.appcfg(action:"rollback", war:stagingDir); break		
		case 'logs':
			def days = argsMap.days ?: 5
			def file = argsMap.file ?: 'logs.txt'
			
			ant.appcfg(action:"logs", war:stagingDir) {
				options {
					arg value:"--num_days=$days"
				}
				args {
					arg value:file
				}
			}
		
		break
		default: startDevServer(debug)
	}
}

setDefaultTarget(main)

private startDevServer(boolean debug) {
	startAppEngineGeneratedThread()	
	startAppEngineReloadThread()
	ant.dev_appserver(war:stagingDir) {
		options {
			arg value:"--jvm_flag=-D--enable_all_permissions=true"
			arg value:"--jvm_flag=-Dgrails.env=${grailsEnv}"
			if(Environment.current == Environment.DEVELOPMENT) {
				arg value:"--jvm_flag=-Dgrails.reload.enabled=true"						
				arg value:"--jvm_flag=-Dgrails.reload.location=${basedir}"												
			}					
			if(debug) {
				arg value:"--jvm_flag=-Xdebug"
				arg value:"--jvm_flag=-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=9999"
			}
		}
	}	
}
// starts a thread to monitor for AppEngine generated to content
private startAppEngineGeneratedThread() {
	println "Starting AppEngine generated indices thread."
	ant.mkdir(dir:"${projectWorkDir}/appengine-generated")
	Thread.start {	

		while(true) {
			if(new File("${stagingDir}/WEB-INF/appengine-generated/datastore-indexes-auto.xml").exists() && new File("${stagingDir}/WEB-INF/appengine-generated/datastore-indexes-auto.xml").text) {
				def localAnt = new AntBuilder()							
				localAnt.copy(todir:"${projectWorkDir}/appengine-generated") {
					fileset(dir:"${stagingDir}/WEB-INF/appengine-generated") 
				}			
			}			
			sleep(4000)			
		}

	}
}

// enables reloading of the AppEngine server
private startAppEngineReloadThread() {
	println "Starting reload monitor thread."
	Thread.start {
		while(true) {
			try {
				new URL("http://localhost:8080/appEngineReload/index").text
			}
			catch(e) {
				// ignore
			}
			sleep 3000
		}
	}
}
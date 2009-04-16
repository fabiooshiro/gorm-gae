includeTargets << grailsScript("_GrailsWar")
target(cleanUpAfterWar:"override to avoid cleanup") { 
	// do nothing
}
target(main: "Runs a Grails application in the AppEngine development environment") {
	depends(parseArguments)
	
	ant.'import'(file:"${appEngineSDK}/config/user/ant-macros.xml")
	ant.delete(dir:"${grailsSettings.projectWorkDir}/staging")
	war()
	
	def cmd = argsMap.params ? argsMap.params[0] : 'run'
	def debug = argsMap.debug ?: false
	
	switch(cmd) {
		case 'run':
			startAppEngineGeneratedThread()		
			ant.dev_appserver(war:stagingDir) {
				options {
					arg value:"--jvm_flag=-D--enable_all_permissions=true"
					if(debug) {
						arg value:"--jvm_flag=-Xdebug"
						arg value:"--jvm_flag=-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=9999"
					}
				}
			}
		
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
			
			ant.appcfg(action:"rollback", war:stagingDir) {
				options {
					arg value:"--num_days=$days"
				}
				args {
					arg value:file
				}
			}
		
		break
		default: ant.dev_appserver(war:stagingDir);
	}
}

setDefaultTarget(main)

// starts a thread to monitor for AppEngine generated to content
private startAppEngineGeneratedThread() {
	ant.mkdir(dir:"${projectWorkDir}/appengine-generated")
	Thread.start {	
		def localAnt = new AntBuilder()
		while(true) {
			if(new File("${stagingDir}/WEB-INF/appengine-generated/datastore-indexes-auto.xml").exists() && new File("${stagingDir}/WEB-INF/appengine-generated/datastore-indexes-auto.xml").text) {
			//	println "updating AppEngine generated indices"				
				localAnt.copy(todir:"${projectWorkDir}/appengine-generated") {
					fileset(dir:"${stagingDir}/WEB-INF/appengine-generated") 
				}			
			}			
		}
		sleep(4000)
	}
}
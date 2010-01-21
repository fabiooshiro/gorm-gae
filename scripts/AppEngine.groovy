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
	
	def warFile = buildConfig.grails.war.destFile
    if(!warFile) {
        warFile = "${basedir}/target/${grailsAppName}-${metadata.getApplicationVersion()}.war"
		buildConfig.grails.war.destFile = warFile
	}
	if(cmd!='run')
		grailsEnv = Environment.PRODUCTION.name

    ant.delete(dir:"${grailsSettings.projectWorkDir}/stage", failonerror:false)
    ant.delete(file: warFile, failonerror:false)
	war()
	
	switch(cmd) {
		case 'package':
			def targetDir = "${basedir}/target/war"
			ant.delete dir:targetDir
			ant.mkdir dir:targetDir
            ant.unzip(src:warFile, dest:targetDir)
			ant.copy(todir:targetDir) {
				fileset(dir:"${grailsSettings.projectWorkDir}/stage") 
			}
			ant.delete(failonerror:false) {
				fileset(dir:targetDir) {
					include(name:"**/appengine-generated/**.bin")					
					include(name:"stacktrace.log")										
				}
			}
			println "Created distribution at $targetDir"
		break
		case 'run':
			startDevServer(debug)		
			break
		case ~/(update|deploy)/:
			appcfg("update", stagingDir); 
			break
		case ~/(update_indexes)/:
			appcfg("update_indexes", stagingDir); 
			break			
		case 'rollback':
			appcfg("rollback", stagingDir); break		
		case 'logs':			
			appcfg("logs", stagingDir); break
		break
		default: startDevServer(debug)
	}
}

// calls the jar file directly instead of using google's macro
private appcfg( action, war ){
	
	//def read = System.in.newReader().&readLine 
	def email = config.google.appengine.email
	def password = config.google.appengine.password

	if( !email ){
		ant.input(message:"Enter your Google App Engine email address", addproperty:"appengine.email")
		email = ant.antProject.properties['appengine.email']
	}

	if(!password){
		ant.input(message:"Enter your Google App Engine password", addproperty:"appengine.password")
		password = ant.antProject.properties['appengine.password']
	}
	
	// this is essentially what's in the app engine macro, except it takes in a pw + email 
	ant.property( name:"appengine.sdk.home", location: "${appEngineSDK}")
	
	ant.java(
		classname:"com.google.appengine.tools.admin.AppCfg",
		classpath:"${appEngineSDK}/lib/appengine-tools-api.jar",
		fork:true,
		inputstring: password+'\n'
	){
		arg(value:"--email=${email}");
		arg(value:"--passin");

		if(action=="logs") {
			def days = argsMap.days ?: 5
			def file = argsMap.file ?: 'logs.txt'		
			
			arg value:"--num_days=$days"			
			arg value:"request_logs"
			arg value:war				
			arg value:file						
		}
		else {
			arg(value:action)
			arg(value:war)			
		}

	}
	
}

setDefaultTarget(main)

private startDevServer(boolean debug) {
	startAppEngineGeneratedThread()	
	startAppEngineReloadThread()
	ant.dev_appserver(war:stagingDir) {
		options {
			if(Environment.current == Environment.DEVELOPMENT) {
				arg value:"--jvm_flag=-D--enable_all_permissions=true"				
			}

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
	if(Environment.current == Environment.DEVELOPMENT) {
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
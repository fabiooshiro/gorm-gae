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
			appcfg("update", stagingDir); 
			break
		case ~/(update_indexes)/:
			appcfg("update_indexes", stagingDir); 
			break			
		case 'rollback':
			appcfg("rollback", stagingDir); break		
		case 'logs':
			def days = argsMap.days ?: 5
			def file = argsMap.file ?: 'logs.txt'
			
			appcfg("logs", stagingDir) {
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

// calls the jar file directly instead of using google's macro
private appcfg( action, war ){
	
	def read = System.in.newReader().&readLine 
	def email = config.google.appengine.email
	def password = config.google.appengine.password

	if( !email ){
		println "Enter your Google App Engine email address:"
		email = read()
	}

	if(!password){
		println "Enter your Google App Engine password:"
		password = read() // System.console.password?
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
		arg(value:action);
		arg(value:war)
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
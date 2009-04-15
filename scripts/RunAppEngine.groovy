includeTargets << grailsScript("Init")
includeTargets << new File("${appEnginePluginDir}/scripts/_AppEngineCommon.groovy")

target(main: "Runs a Grails application in the AppEngine development environment") {

	ant.'import'(file:"${appEngineSDK}/config/user/ant-macros.xml")
	
	println "Sorry I don't work yet!"
}

setDefaultTarget(main)

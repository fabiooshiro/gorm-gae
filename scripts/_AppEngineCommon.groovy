appEngineSDK=System.getenv("APPENGINE_HOME")
if(!appEngineSDK && buildConfig.google.appengine.sdk) {
	appEngineSDK = buildConfig.google.appengine.sdk
}
if(!appEngineSDK) {
	println "No Google AppEngine SDK specified. Either set APPENGINE_HOME in your environment or specify google.appengine.sdk in your grails-app/conf/BuildConfig.groovy file"
	exit 1
}
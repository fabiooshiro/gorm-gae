class AppEngineGrailsPlugin {
    // the plugin version
    def version = "0.5"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.1 > *"
	def evict = ['hibernate']
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp",
            "grails-app/controllers/TestController.groovy"
    ]

    def author = "Graeme Rocher"
    def authorEmail = "graeme.rocher@springsource.com"
    def title = "Grails AppEngine plugin"
    def description = '''\\
A plugin that integrates the AppEngine development runtime and deployment tools with Grails.
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/AppEngine+Plugin"

	def doWithSpring = {
		def persistenceEngine = application.config.google.appengine.persistence ?: "jdo"
		if(persistenceEngine?.equalsIgnoreCase("JDO")) {
			persistenceManagerFactory(org.springframework.beans.factory.config.MethodInvokingFactoryBean) {
				targetClass = "javax.jdo.JDOHelper"
				targetMethod = "getPersistenceManagerFactory"
				arguments = ["transactions-optional"]
			}
			
			persistenceManager(getClass().classLoader.loadClass('javax.jdo.PersistenceManager')) { bean ->
				bean.scope = "request"
				bean.factoryBean = "persistenceManagerFactory"
				bean.factoryMethod = "getPersistenceManager"
				bean.destroyMethod = "close"
			}
		}
	}

}

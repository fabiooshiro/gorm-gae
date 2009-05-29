class AppEngineGrailsPlugin {
    // the plugin version
    def version = "0.8.1"
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
		def persistenceEngine = application.config.google.appengine.persistence ?: null
		if(!persistenceEngine) {
			persistenceEngine = application.metadata['appengine.persistence'] ?: 'jdo'
		}
		
		if(persistenceEngine?.equalsIgnoreCase("JDO")) {
			log.info "Configuring JDO PersistenceManager"			
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
			transactionManager(org.springframework.orm.jdo.JdoTransactionManager) {
				persistenceManagerFactory = persistenceManagerFactory
			}
			transactionTemplate(org.springframework.transaction.support.TransactionTemplate) {
				transactionManager = transactionManager
			}
			jdoTemplate(org.springframework.orm.jdo.JdoTemplate) {
				persistenceManagerFactory = persistenceManagerFactory
			}
		}
		
		else if(persistenceEngine?.equalsIgnoreCase("JPA")) {
			log.info "Configuring JPA EntityManager"
			entityManagerFactory(org.springframework.beans.factory.config.MethodInvokingFactoryBean) {
				targetClass = "javax.persistence.Persistence"
				targetMethod = "createEntityManagerFactory"
				arguments = ["transactions-optional"]				
			}
			transactionManager(org.springframework.orm.jpa.JpaTransactionManager) {
				entityManagerFactory = entityManagerFactory
			}
			transactionTemplate(org.springframework.transaction.support.TransactionTemplate) {
				transactionManager = transactionManager
			}			
			jpaTemplate(org.springframework.orm.jpa.JpaTemplate) {
				entityManagerFactory = entityManagerFactory
			}
			entityManager(getClass().classLoader.loadClass('javax.persistence.EntityManager')) { bean ->
				bean.scope = "request"
				bean.factoryBean = "entityManagerFactory"
				bean.factoryMethod = "createEntityManager"
				bean.destroyMethod = "close"				
			}
		}
	}

}

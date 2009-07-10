import org.codehaus.groovy.grails.commons.ConfigurationHolder

class AppEngineGrailsPlugin {
    // the plugin version
    def version = "0.8.3"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.1 > *"
	def evict = ['hibernate']
	def loadAfter = ['gorm-jpa']
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
    def documentation = "http://grails.org/plugin/app-engine"

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
			
			persistenceManager(org.springframework.beans.factory.config.MethodInvokingFactoryBean) { bean ->
				bean.scope = "request"
				targetClass = "org.springframework.orm.jdo.PersistenceManagerFactoryUtils"
				targetMethod = "getPersistenceManager"
				arguments = [persistenceManagerFactory,false]				
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
            if (manager?.hasGrailsPlugin("controllers")) {
                openPersistenceManagerInViewInterceptor(org.springframework.orm.jdo.support.OpenPersistenceManagerInViewInterceptor) {
                    persistenceManagerFactory = persistenceManagerFactory
                }
                if(getSpringConfig().containsBean("grailsUrlHandlerMapping")) {                    
                    grailsUrlHandlerMapping.interceptors << openPersistenceManagerInViewInterceptor
                }
            }
			
		}
		
		else if(persistenceEngine?.equalsIgnoreCase("JPA")) {
			log.info "Configuring JPA EntityManager"
			// need to replace this bean because the default implementation relies on JNDI
		    "org.springframework.context.annotation.internalPersistenceAnnotationProcessor"(DummyPersistenceContextPostProcessor)
		    
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
			entityManager(org.springframework.beans.factory.config.MethodInvokingFactoryBean) { bean ->
				bean.scope = "request"
				targetClass = "org.springframework.orm.jpa.EntityManagerFactoryUtils"
				targetMethod = "getTransactionalEntityManager"
				arguments = [entityManagerFactory]				
			}			
            if (manager?.hasGrailsPlugin("controllers")) {
                openEntityManagerInViewInterceptor(org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor) {
                    entityManagerFactory = entityManagerFactory
                }
                if(getSpringConfig().containsBean("grailsUrlHandlerMapping")) {                    
                    grailsUrlHandlerMapping.interceptors << openEntityManagerInViewInterceptor
                }
            }
			
		}
	}

	def doWithWebDescriptor = { webXml ->
  	   def mappingElement = webXml.'servlet-mapping'	
	   def config = ConfigurationHolder.config
	   def patterns = [] 
	   def requireLoginMap = []
	   def requireAdminMap = []
	   def secureMap = []
	
 	   if( config.google.appengine.security.requireLogin ){
	 	  patterns += config.google.appengine.security.requireLogin
	      requireLoginMap +=  config.google.appengine.security.requireLogin 
	   }
 	   if( config.google.appengine.security.requireAdmin ){
	 	  patterns += config.google.appengine.security.requireAdmin
	  	  requireAdminMap += config.google.appengine.security.requireAdmin
	   }
	
	   if( config.google.appengine.security.useHttps ){
	  	  patterns += config.google.appengine.security.useHttps
	      secureMap +=  config.google.appengine.security.useHttps
       }

	   patterns.each { pattern -> 
		  def isAdmin = requireAdminMap.contains( pattern )
		  def isBasic = requireLoginMap.contains( pattern ) 
		  def isSecure = secureMap.contains( pattern )

	      mappingElement[0] + {
		  'security-constraint' {
			'web-resource-collection'{
				'url-pattern'( pattern as String )
			}
			if( isAdmin || isBasic ){
				'auth-constraint'{
					'role-name'( isAdmin ? "admin" : "*" )
				}				
			}
			if( isSecure ){
				'user-data-constraint'{
					'transport-guarantee'("CONFIDENTIAL")
				}
			}
	      }
	   }
	}
  }
}
class DummyPersistenceContextPostProcessor {}

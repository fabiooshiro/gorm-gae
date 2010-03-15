import org.codehaus.groovy.grails.commons.ConfigurationHolder
import org.codehaus.groovy.grails.web.pages.GroovyPageResourceLoader
import grails.util.Environment
import org.springframework.context.ApplicationContext
import org.springframework.core.io.FileSystemResource
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import com.google.appengine.api.datastore.Key
import com.google.appengine.api.datastore.KeyFactory
import org.grails.appengine.AppEnginePropertyEditorRegistrar
import org.apache.commons.logging.LogFactory
import org.apache.log4j.LogManager
import org.codehaus.groovy.grails.plugins.logging.Log4jConfig
import grails.util.GrailsNameUtils

class AppEngineGrailsPlugin {
    // the plugin version
    def version = "0.8.9"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.2 > *"
	def evict = ['hibernate', 'logging']
	def loadAfter = ['gorm-jpa']
    def observe = ['*']	
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

    def doWithDynamicMethods = {applicationContext ->
        for(handler in application.artefactHandlers) {
            for( artefact in application."${handler.type}Classes" ) {
                addLogMethod(artefact.clazz, handler)
            }
        }
    }

    def onConfigChange = {event ->
        def log4jConfig = event.source.log4j
        if (log4jConfig instanceof Closure) {
            LogManager.resetConfiguration()
            new Log4jConfig().configure(log4jConfig)
        }
    }

    def onChange = {event ->
        if (event.source instanceof Class) {
            log.debug "Adding log method to modified artefact [${event.source}]"
            def handler = application.artefactHandlers.find {it.isArtefact(event.source)}
            if (handler) {
                addLogMethod(event.source, handler)
            }
        }
    }


    def addLogMethod(artefactClass, handler) {
        // Formulate a name of the form grails.<artefactType>.classname
        // Do it here so not calculated in every getLog call :)
        def type = GrailsNameUtils.getPropertyNameRepresentation(handler.type)
        def logName = "grails.app.${type}.${artefactClass.name}".toString()

        def log = LogFactory.getLog(logName)

        artefactClass.metaClass.getLog << {-> log}
    }

	def doWithSpring = {
		def persistenceEngine = application.config.google.appengine.persistence ?: null
		if(!persistenceEngine) {
			persistenceEngine = application.metadata['appengine.persistence'] ?: null
		}

        appEnginePropertyEditorRegistrar(AppEnginePropertyEditorRegistrar)
		
		if(persistenceEngine?.equalsIgnoreCase("JDO")) {
			log.info "Configuring JDO PersistenceManager"
			
			persistenceManagerFactory(org.grails.appengine.AppEnginePersistenceManagerFactory) { bean ->
	    		bean.factoryMethod = "get"
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
		    
			entityManagerFactory(org.grails.appengine.AppEngineEntityManagerFactory) { bean ->
	    		bean.factoryMethod = "get"
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

    def doWithApplicationContext = { ApplicationContext ctx ->
        if(Environment.current == Environment.DEVELOPMENT) {
          if(ctx.containsBean("groovyPageResourceLoader")) {            
            GroovyPageResourceLoader loader = ctx.getBean("groovyPageResourceLoader")
            String path = System.getProperty("grails.reload.location")
            if(!path.endsWith('/')) path = "$path/"
            loader.baseResource = new FileSystemResource(path)
          }
        }

        for(GrailsDomainClass domain in application.domainClasses) {
            GrailsDomainClass currentDomain = domain
            if(Key.isAssignableFrom(currentDomain.identifier.type)) {
              Class c = domain.clazz
              MetaClass mc = c.metaClass
              def getMethod = mc.getStaticMetaMethod("get", [Serializable] as Class[])
              if(getMethod && getMethod?.isStatic()) {
                mc.static.get = { Serializable id ->
                   if(!(id instanceof Key)) {
                      try {
                        Long l = Long.valueOf(id.toString())
                        id = KeyFactory.createKey(c.simpleName, l)
                      } catch (e) {
                        id = KeyFactory.createKey(c.simpleName, id.toString())
                      }
                   }
                   getMethod.invoke(delegate, id)
                }
              }
            }
        }

    }

	def doWithWebDescriptor = { webXml ->
		// change log4j listener
		def listener = webXml.listener.find { it.'listener-class'.text().contains('Log4jConfigListener') }
		listener.'listener-class' = 'org.grails.appengine.Log4jConfigListener'
		// google security
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

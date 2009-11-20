package org.grails.appengine

import grails.web.container.EmbeddableServer
import grails.util.*
/**
 * An AppEngine Grails server implementation
 * 
 * @author Graeme Rocher
 * @since 1.1
 */

public class AppEngineJettyServer implements EmbeddableServer{

  File base
  File warFile
  def buildConfig 
  BuildSettings buildSettings
  def ant = new AntBuilder()

  AppEngineJettyServer(File warFile) {
    this.warFile = warFile
    init()
  }
  AppEngineJettyServer(String basedir, ClassLoader classLoader) {
     base = new File(basedir)
     init()
  }

  private init() {
    buildSettings = BuildSettingsHolder.settings
    buildConfig = buildSettings.config
    def appEngineSDK = System.getenv("APPENGINE_HOME")
    if (!appEngineSDK && buildConfig.google.appengine.sdk) {
      appEngineSDK = buildConfig.google.appengine.sdk
    }
    ant.'import'(file: "${appEngineSDK}/config/user/ant-macros.xml")
  }

  public void start() {
    start("localhost", 8080)

  }

  public void start(int port) {
    start("localhost",port)
  }

  public void start(String host, int port) {
	 startDevServer(host, port)
  }

  public void startSecure() {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  public void startSecure(int i) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  public void startSecure(String s, int i, int i1) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  public void stop() {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  public void restart() {
    //To change body of implemented methods use File | Settings | File Templates.
  }

	private startDevServer(String host, int port) {
        if(warFile) {
            def target = "${buildSettings.baseDir}/target/war"
            ant.delete(dir:target, failonerror:false)          
            ant.mkdir(dir:target)
            ant.unzip(src:warFile, dest:target)
            ant.dev_appserver(war:target, address:host ?: 'localhost', port: port ?: 8080) {
                options {
                    arg value:"--jvm_flag=-Dgrails.env=${System.getProperty('grails.env')}"
                    arg value:"--jvm_flag=-Dstringchararrayaccessor.disabled=true"
                    if(Boolean.getBoolean("appengine.debug")) {
                        arg value:"--jvm_flag=-Xdebug"
                        arg value:"--jvm_flag=-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=9999"
                    }
                }
            }
        }
        else {
          startAppEngineGeneratedThread()
          startAppEngineReloadThread()
          try {
            ant.dev_appserver(war:"${base}", address:host ?: 'localhost', port: port ?: 8080) {
                options {
                    arg value:"--jvm_flag=-Dstringchararrayaccessor.disabled=true"
                    if(Environment.current == Environment.DEVELOPMENT) {
                        arg value:"--jvm_flag=-D--enable_all_permissions=true"
                    }

                    arg value:"--jvm_flag=-Dgrails.env=${System.getProperty('grails.env')}"
                    if(Environment.current == Environment.DEVELOPMENT) {
                        arg value:"--jvm_flag=-Dgrails.reload.enabled=true"
                        arg value:"--jvm_flag=-Dgrails.reload.location=${buildSettings.baseDir.absolutePath}"
                    }
                    if(Boolean.getBoolean("appengine.debug")) {
                        arg value:"--jvm_flag=-Xdebug"
                        arg value:"--jvm_flag=-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=9999"
                    }
                }
            }
          }
          catch(e) {
            println "Error occurred starting or stopping AppEngine container: ${e.message}"
          }
        }
	}
	// starts a thread to monitor for AppEngine generated to content
	private startAppEngineGeneratedThread() {
		if(Environment.current == Environment.DEVELOPMENT) {
			println "Starting AppEngine generated indices thread."
			ant.mkdir(dir:"${buildSettings.projectWorkDir}/appengine-generated")
			Thread.start {	

				while(true) {
					if(new File("${base}/WEB-INF/appengine-generated/datastore-indexes-auto.xml").exists() && new File("${base}/WEB-INF/appengine-generated/datastore-indexes-auto.xml").text) {
						def localAnt = new AntBuilder()							
						localAnt.copy(todir:"${buildSettings.projectWorkDir}/appengine-generated") {
							fileset(dir:"${base}/WEB-INF/appengine-generated") 
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
}
package org.grails.appengine

import grails.web.container.EmbeddableServerFactory
import grails.web.container.EmbeddableServer

/**
 * @author Graeme Rocher
 * @since 1.1
 */

public class AppEngineJettyServerFactory implements EmbeddableServerFactory{

  public EmbeddableServer createInline(String basedir, String webXml, String contextPath, ClassLoader classLoader) {
    return new AppEngineJettyServer(basedir, classLoader)
  }

  public EmbeddableServer createForWAR(String warPath, String contextPath) {
    return new AppEngineJettyServer(new File(warPath))    
  }

}
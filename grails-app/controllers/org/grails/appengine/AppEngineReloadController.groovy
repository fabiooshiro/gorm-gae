package org.grails.appengine

import grails.util.*

class AppEngineReloadController {

	def pluginManager
	def index = {
		if(Environment.current.isReloadEnabled()) {
			synchronized(pluginManager) {
				pluginManager.checkForChanges()
			}			
		}
		render "done."
	}

}
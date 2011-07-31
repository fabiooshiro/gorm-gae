package oshiro.gorm.gae

import grails.util.GrailsNameUtils
import org.springframework.beans.*;
import org.springframework.context.ApplicationContext

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.PreparedQuery;

import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler;
import static com.google.appengine.api.datastore.FetchOptions.Builder.*;

import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;
import org.apache.commons.beanutils.PropertyUtils;

import java.beans.Introspector
import java.lang.reflect.Field;

class GaePluginSupportService {
	
	static transactional = false
	
	static managedCascadeList = [:]
	
	static final COMPARATORS = Collections.unmodifiableList([
		"IsNull",
		"IsNotNull",
		"LessThan",
		"LessThanEquals",
		"GreaterThan",
		"GreaterThanEquals",
		"NotEqual",
		"Like",
		"Ilike",
		"InList",
		"NotInList",
		"NotBetween",
		"Between"
	])
	static final COMPARATORS_RE = COMPARATORS.join("|")
	static final DYNAMIC_FINDER_RE = /(\w+?)(${COMPARATORS_RE})?((And|Or)(\w+?)(${COMPARATORS_RE})?)?/

	static doWithApplicationContext = { ApplicationContext applicationContext ->
		return
		def typeConverter = new SimpleTypeConverter()
		applicationContext.getBeansOfType(PropertyEditorRegistrar).each { key, val ->
			val.registerCustomEditors typeConverter
		}
		application.domainClasses.each{ domainClass ->
			Class entityClass = domainClass.clazz
			def logicalName = GrailsNameUtils.getLogicalPropertyName(entityClass.name,'')

			entityClass.mixin(SaveDeleteMethod)
			
			// dynamic finder handling with methodMissing
			entityClass.metaClass.static.methodMissing = { method, args ->
				def m = method =~ /^find(All)?By${DYNAMIC_FINDER_RE}$/
				if (m) {
					throw new MissingMethodException(method, delegate, args)
				} else {
					throw new MissingMethodException(method, delegate, args)
				}
			}
		}
	}
	
	def remove( datastore, ownerClassName, ownerId){
		//println "ownerClassName = ${ownerClassName} ownerId = ${ownerId}"
		managedCascadeList.get(ownerClassName).each{ owned -> // Cascade: apagar todos os que belongsTo esta classe
			//println "owned = ${owned}"
			Query q = new Query(owned.className)//.setKeysOnly();
			q.addFilter(owned.prop.substring(2), Query.FilterOperator.EQUAL, ownerId) //misterio
			PreparedQuery pq = datastore.prepare(q)
			for (Entity result : pq.asIterable()) {
				datastore.delete(result.getKey())
				remove(datastore, owned.className, result.getKey().getId())
			}
		}
	}
		
	def setStaticGet(clazz){
		//['acme.Author' : [[className:'acme.Book', prop: '__authorId'], [className: 'acme.Owned', prop: '__ownedId']]
		managedCascadeList.put(clazz.name, [])
		
		def metaClazz = clazz.metaClass
		def propertyNames = metaClass.properties*.name
		def relationsPropCreated = []		
		metaClass.id = null
		
		def bindProps = { r, entity ->
			r.id = entity.getKey().getId()
			entity.getProperties().each{ k, v ->
				try{
					if(k.startsWith('__') && k.endsWith('Id')) return
					if(k != '$const$0' && r.metaClass.hasProperty(r, k)){
						if(PropertyUtils.getPropertyType(r, k)?.name == 'java.math.BigDecimal'){
							println "set bigDecimal = ${v}"
							r."$k" = new BigDecimal(v)
						}else{
							r."$k" = v
						}
					} else {
						println "dont has prop ${k}"
					}
				}catch(Throwable e){
					println "Error setting prop '${k}' = '${v}'"
					e.printStackTrace()
				}
			}
			return r
		}
		
		def getLong = { Long id ->
			try{
				DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
				def entity = datastore.get(KeyFactory.createKey(clazz.name, id));
				return bindProps(clazz.newInstance(), entity)
			}catch(Throwable e){
				println "GORM-GAE error: " + e.message
				return null
			}
		}
		
		def createFkProp = { k, v ->
			def fkPropName = "__${k}Id"
			metaClazz."${fkPropName}" = null
			metaClazz."${k}" = null
			metaClazz."get${k[0].toUpperCase()}${k.substring(1)}" = {
				if(delegate.@"$k" == null && delegate."${fkPropName}" != null){
					delegate.@"$k" = v.get(delegate."${fkPropName}")
				}
				//println "get ${k}: ${delegate.@"$k"}, fk: ${fkPropName}, class: ${v}, fk: ${delegate."${fkPropName}"}, ${k}Id: ${delegate."${k}Id"}"
				return delegate.@"$k"
			}
			metaClazz."set${k[0].toUpperCase()}${k.substring(1)}Id" = { id ->
				println "set ${fkPropName} = ${id} | ${id.class}"
				if(id instanceof String){
					delegate."${fkPropName}" = Long.valueOf(id)
				}else{
					delegate."${fkPropName}" = id
				}
				//println fkPropName + ' = ' + delegate."${fkPropName}" 
			}
			return fkPropName
		}
		
		println 'chk belongs to'
		if(propertyNames.contains('belongsTo')){
			clazz.belongsTo.each{ k, v ->
				println clazz.name + ' belongs to ' + v.name
				relationsPropCreated.add(k)
				def fkPropName = createFkProp(k, v)
				managedCascadeList.get(v.name, []).add([className: clazz.name, prop: fkPropName])
			}
		}
		
		clazz.getDeclaredFields().each{ Field field ->
			if(DomainClassArtefactHandler.isDomainClass(field.getType()) && !relationsPropCreated.contains(field.name)){
				println clazz.name + ' possui attr ' + field.name + ' do tipo ' + field.getType().name
				createFkProp(field.name, field.getType())
			}
		}
		
		metaClazz.static.list = { p ->
			def authorInstanceList = []
			def total = 0
			// Get a handle on the datastore itself
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
			Query query = new Query(clazz.name);
			def rs
			if(p?.offset != null && p?.max != null){
				rs = datastore.prepare(query).asIterable(withLimit(Integer.valueOf(p.max)).offset(Integer.valueOf(p.offset)))
			}else if(p?.max != null){
				rs = datastore.prepare(query).asIterable(withLimit(Integer.valueOf(p.max)))
			}else{
				rs = datastore.prepare(query).asIterable()
			}
			for (Entity entity : rs) {
				authorInstanceList.add(bindProps(clazz.newInstance(), entity))
				total ++
			}
			authorInstanceList
		}
		
		metaClazz.static.count = {
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
			Query query = new Query(clazz.name);
			return (Long)datastore.prepare(query).countEntities(withDefaults())
		}
		
		metaClazz.static.get = { Serializable id ->
			getLong(Long.valueOf(id))
		}
		
		metaClazz.static.get = { Long id ->
			getLong(id)
		}
		
		metaClazz.static.withTransaction = { Closure clos ->
			clos()
		}

		metaClazz.delete = { Map opts = [:] ->
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
			datastore.delete(KeyFactory.createKey(clazz.name, delegate.id))
			remove(datastore, clazz.name, delegate.id)
		}
		
		metaClazz.save = { Map opts = [:] ->
			DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
			
			if(propertyNames.contains('constraints')){//TODO: tratar threads
				//verificar unique
				clazz.constraints.each{ k, v ->
					if(v.getAppliedConstraint('unique')){
						Query q = new Query(clazz.name)//.setKeysOnly();
						q.addFilter(k, Query.FilterOperator.EQUAL, delegate."$k")
						if(datastore.prepare(q).countEntities(withDefaults())>0){
							throw new RuntimeException("not unique exception $k")
						}
					}
				}
			}
			
			Entity entity
			if(delegate.id){
				entity = datastore.get(KeyFactory.createKey(clazz.name, delegate.id));
			}else{
				def newKey = datastore.allocateIds(clazz.name, 1L).getStart()
				entity = new Entity(newKey)
				delegate.id = newKey.getId()
			}
			
			def keys = delegate.properties.keySet() - ['id', 'metaClass', 'class', 'errors', 'constraints', 'belongsTo', 'hasMany', 'log']
			keys.each{ k ->
				if(k.startsWith('__') && k.endsWith('Id')) return
				def value = delegate."$k"
				println "saving ${k} = ${value}"
				if(value instanceof String 
					|| value instanceof Long
					|| value instanceof Integer
					|| value instanceof Double
					|| value instanceof Date
					|| value instanceof Boolean
					){
					entity.setProperty(k, value)
				} else if(value instanceof BigDecimal){
					entity.setProperty(k, value.toString())
				} else {
					println "not saving: ${delegate.class.simpleName}.${k} = ${value}"
				}
			}
			datastore.put(entity)
			 
			return delegate
		}
	}
	
	private static int getArgCountForComparator(String comparator) {
		if (comparator == "Between") {
			return 2
		}
		else if (["IsNull", "IsNotNull"].contains(comparator)) {
			return 0
		}
		else {
			return 1
		}
	}
}

package oshiro.gorm.gae;

import org.codehaus.groovy.grails.validation.AbstractConstraint
import org.springframework.validation.Errors

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.PreparedQuery;
import com.google.appengine.api.datastore.Query;
import static com.google.appengine.api.datastore.FetchOptions.Builder.*;

class GaeUniqueConstraint extends AbstractConstraint {

	void setParameter(Object constraintParameter){
		super.setParameter(constraintParameter)
	}
	
	public void processValidate(Object target, Object propertyValue, Errors errors){
		println "Validando '${constraintOwningClass}'"
		DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
		Query q = new Query(target.class.name)//.setKeysOnly();
		q.addFilter(constraintPropertyName, Query.FilterOperator.EQUAL, propertyValue)
		if(target.id){
			q.addFilter('id', Query.FilterOperator.NOT_EQUAL, target.id)
		}
		if(datastore.prepare(q).countEntities(withDefaults())>0){
			println "unique error ${constraintPropertyName} = '${propertyValue}'"
			def args = (Object[]) [constraintPropertyName, constraintOwningClass, propertyValue]
			super.rejectValue(target, errors, 'default.not.unique.message', "not unique value ${propertyValue}", args);	
		}else{
			println "unique ok"
		}
	}
	
	public String getName(){
		'unique'
	}
	
	boolean supports(Class type){
		true
	}
}


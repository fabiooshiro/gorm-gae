package oshiro.gorm.gae;

import org.codehaus.groovy.grails.validation.AbstractConstraint
import org.springframework.validation.Errors

class GaeUniqueConstraint extends AbstractConstraint {

	void setParameter(Object constraintParameter){
		super.setParameter(constraintParameter)
	}
	
	public void processValidate(Object target, Object propertyValue, Errors errors){
		
	}
	
	public String getName(){
		'unique'
	}
	
	boolean supports(Class type){
		true
	}
}


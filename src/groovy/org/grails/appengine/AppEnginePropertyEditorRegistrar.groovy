package org.grails.appengine

import org.springframework.beans.PropertyEditorRegistrar
import org.springframework.beans.PropertyEditorRegistry
import com.google.appengine.api.datastore.Key
import java.beans.PropertyEditorSupport

/**
 * @author Graeme Rocher
 * @since 1.1
 */

public class AppEnginePropertyEditorRegistrar implements PropertyEditorRegistrar{

  public void registerCustomEditors(PropertyEditorRegistry propertyEditorRegistry) {
    propertyEditorRegistry.registerCustomEditor(Key, new GoogleKeyPropertyEditor())
  }

}
class GoogleKeyPropertyEditor extends java.beans.PropertyEditorSupport {

  public String getAsText() {
    Object value = getValue()
    if(value!=null) {
      Key k = value
      return String.valueOf(k.id)
    }
    return "";
  }

}
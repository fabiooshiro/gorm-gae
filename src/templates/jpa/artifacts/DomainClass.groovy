@artifact.package@

import javax.persistence.*;
// import com.google.appengine.api.datastore.Key;

@Entity
class @artifact.name@ {

    @Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	Long id

    static constraints = {
    	id visible:false
	}
}

import com.google.appengine.api.users.*
// dummy controller to test out compilation
class TestController {
	def index = {
		UserService service = UserServiceFactory.getUserService()
	}
}
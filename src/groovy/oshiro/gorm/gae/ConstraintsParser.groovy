package oshiro.gorm.gae;
class ConstraintsParser {
	def uniques = []
	def methodMissing(String name, args) {
		def map = [:]
		if(args) map = args[0]
		
		if(map['unique']) uniques << name
		println "$name = $args"
	}
}


import groovy.json.JsonOutput
import groovy.json.JsonSlurper

def jsonSlurper = new JsonSlurper()
def content = jsonSlurper.parseText(request.content)

content.each { entry -> assert entry != null }

return JsonOutput.toJson(content);


import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def jsonSlurper = new JsonSlurper()
def content = jsonSlurper.parseText(request.content)
content[0].firstname = 'Hacked ' + content[0].firstname
content[0].country = 'US'
return JsonOutput.toJson(content)
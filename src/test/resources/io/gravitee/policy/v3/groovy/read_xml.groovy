
import groovy.util.XmlSlurper
import groovy.json.JsonOutput

def xmlSlurper = new XmlSlurper()
def content = xmlSlurper.parseText(request.content)
attr = content.getAt(0).children()
return JsonOutput.toJson([
        age: attr.get(0).text(),
        firstname: attr.get(1).text(),
        lastname: attr.get(2).text()
])
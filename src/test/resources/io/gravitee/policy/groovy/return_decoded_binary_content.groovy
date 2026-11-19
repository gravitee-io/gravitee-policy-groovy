import groovy.json.JsonSlurper

def envelope = new JsonSlurper().parseText(response.content)
response.headers.'Content-Type' = envelope.headers.'Content-Type'
return envelope.body.decodeBase64()

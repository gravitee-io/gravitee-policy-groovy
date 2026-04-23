def value = message.headers['X-Incoming']
message.attribute('echoedHeader', value == null ? null : value[0])

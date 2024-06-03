message.attributes.wronglyBase64EncodedContent = message.content.bytes.encodeBase64().toString()  // final value is not well encoded because of Charset transformation
message.attributes.goodBase64Content = message.contentAsBase64
message.attributes.byteArray = message.contentAsByteArray.toString()

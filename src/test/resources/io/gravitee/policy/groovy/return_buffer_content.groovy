import io.gravitee.gateway.api.buffer.Buffer

return Buffer.buffer('BIN:').appendBuffer(Buffer.buffer(request.contentAsByteArray))

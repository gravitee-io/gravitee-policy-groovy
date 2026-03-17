## Overview

You can use the `groovy` policy to execute Groovy scripts at any stage of Kafka native API processing within the Gateway.

This policy supports the following Kafka flow phases:

* **INTERACT** — Runs on each Kafka request/response at the connection level (e.g., Produce, Fetch). Use this phase to inspect or modify connection-level context attributes.
* **PUBLISH** — Runs on each individual Kafka message being produced (sent to the broker).
* **SUBSCRIBE** — Runs on each individual Kafka message being consumed (received from the broker).

Several variables are automatically bound to the Groovy script depending on the active phase.

### Script selection

| Configuration field | Used in phase(s)         | Notes                                           |
|---------------------|--------------------------|-------------------------------------------------|
| `script`            | INTERACT, PUBLISH, SUBSCRIBE | Fallback for all phases when phase-specific script is blank |
| `onRequestScript`   | INTERACT (request)       | Overrides `script` for INTERACT request if set  |
| `onResponseScript`  | INTERACT (response)      | Overrides `script` for INTERACT response if set |

### Available variables by phase

| Variable  | INTERACT | PUBLISH | SUBSCRIBE | Description                                        |
|-----------|----------|---------|-----------|----------------------------------------------------|
| `context` | ✅        | ✅       | ✅         | Execution context — read/write attributes          |
| `result`  | ✅        | ✅       | ✅         | Signal a failure to interrupt processing           |
| `message` | —         | ✅       | ✅         | The Kafka message being processed                  |

> **Note:** `context.metrics` is not available in Kafka contexts and will throw an exception if accessed.




## Usage

### Interrupt processing with a failure

Set `result.state` to `State.FAILURE` to stop processing. For connection-level phases (INTERACT), this terminates the entire Kafka request. For message phases (PUBLISH/SUBSCRIBE), this terminates the current message batch.

```groovy
import io.gravitee.policy.groovy.PolicyResult.State

if (!context.attributes.'authorized') {
    result.state = State.FAILURE
    result.error = 'Unauthorized Kafka client'
}
```

> **Note:** For Kafka, `result.code`, `result.key`, and `result.contentType` are not forwarded to the client — Kafka uses its own protocol-level error codes. Setting `result.state = State.FAILURE` triggers a `UNKNOWN_SERVER_ERROR` Kafka protocol error.

### Set a context attribute (INTERACT phase)

```groovy
context.setAttribute('my-attribute', 'my-value')
```

### Filter a message (PUBLISH / SUBSCRIBE phase)

Use `result.state = State.FAILURE` to stop a specific message batch, or return `null` to pass the message through without modification.

```groovy
import io.gravitee.policy.groovy.PolicyResult.State

// Reject messages from a specific topic
if (message.topic == 'forbidden-topic') {
    result.state = State.FAILURE
    result.error = 'Topic not allowed'
}
```

### Enrich message attributes (PUBLISH / SUBSCRIBE phase)

```groovy
message.attributes.topic = message.topic
message.attributes.partition = message.partition
```

### Override message content (PUBLISH / SUBSCRIBE phase)

Enable **Override message content** in the policy configuration, then return the new content from your script:

```groovy
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def json = new JsonSlurper().parseText(message.content)
json.enriched = true
return JsonOutput.toJson(json)
```

### Access record headers (PUBLISH / SUBSCRIBE phase)

```groovy
// As strings (recommended)
def traceId = message.recordHeadersAsStrings.'X-Trace-Id'

// As raw buffers
def rawHeader = message.recordHeaders.'X-Trace-Id'
```




## Bound object properties and methods

### `message` (PUBLISH / SUBSCRIBE phases)

`message.<property>`

| Property                  | Type                  | Mutable | Description                                                                    |
|---------------------------|-----------------------|---------|--------------------------------------------------------------------------------|
| `content`                 | `String`              | ✅ *    | Message body as a string.                                                      |
| `contentAsBase64`         | `String`              |         | Message body as a Base64-encoded string.                                       |
| `contentAsByteArray`      | `byte[]`              |         | Message body as a raw byte array.                                              |
| `key`                     | `String`              |         | Kafka message key as a string.                                                 |
| `keyAsBuffer`             | `Buffer`              |         | Kafka message key as a raw buffer (binary access).                             |
| `topic`                   | `String`              |         | Topic the message was published to or consumed from.                           |
| `offset`                  | `long`                |         | Message offset within its partition.                                           |
| `sequence`                | `int`                 |         | Sequence number within the batch.                                              |
| `partition`               | `int`                 |         | Partition index.                                                               |
| `sizeInBytes`             | `int`                 |         | Total size of the message in bytes.                                            |
| `recordHeaders`           | `Map<String, Buffer>` | ✅       | Kafka wire-level record headers with raw `Buffer` values.                      |
| `recordHeadersAsStrings`  | `Map<String, String>` |         | Kafka record headers with values converted to strings (recommended).           |
| `headers`                 | `Map<String, String>` | ✅       | Gravitee message headers. For methods, refer to [Headers methods](#headers-methods). |
| `correlationId`           | `String`              |         | Correlation ID for tracking.                                                   |
| `parentCorrelationId`     | `String`              |         | Parent correlation ID.                                                         |
| `timestamp`               | `long`                |         | Epoch (ms) timestamp.                                                          |
| `error`                   | `boolean`             |         | Whether this message represents an error.                                      |
| `metadata`                | `Map<String, Object>` | ✅       | Message metadata.                                                              |
| `attributes`              | `Map<String, Object>` | ✅       | Message attributes. For methods, refer to [Message attributes methods](#message-attributes-methods). |

\* Mutable only when **Override message content** is enabled. Return the new content string from your script.

**Message attributes methods**

`message.attributes.<method>`

| Method          | Arguments (type) | Return type   | Description                                |
|-----------------|------------------|---------------|--------------------------------------------|
| `remove`        | key (`Object`)   |               | Remove an attribute.                       |
| `containsKey`   | key (`Object`)   | `boolean`     | Check if an attribute exists.              |
| `containsValue` | value (`Object`) | `boolean`     | Check if any attribute holds this value.   |
| `empty`         |                  | `boolean`     | `true` when attributes map is empty.       |
| `size`          |                  | `int`         | Number of attributes.                      |
| `keySet`        |                  | `Set<String>` | All attribute names.                       |


### `context`

| Property          | Type                | Mutable | Description                  |
|-------------------|---------------------|---------|------------------------------|
| `attributes`      | `Map<String, Object>` | ✅     | Context attributes as a map. |
| `attributeNames`  | `Set<String>`       |         | All attribute names.         |
| `attributeAsList` | `List<Object>`      |         | All attribute values.        |

> `context.metrics` is **not available** in Kafka contexts and will throw `UnsupportedOperationException` if called.

**Context attributes methods**

`context.attributes.<method>`

| Method        | Arguments (type)                  | Return type | Description                           |
|---------------|-----------------------------------|-------------|---------------------------------------|
| `get`         | key (`Object`)                    | `Object`    | Get an attribute by key.              |
| `containsKey` | key (`Object`)                    | `boolean`   | Check if an attribute exists.         |
| `put`         | key (`String`), value (`Object`)  |             | Set an attribute value.               |


### `result`

| Attribute | Type               | Description                                                           |
|-----------|--------------------|-----------------------------------------------------------------------|
| `state`   | `PolicyResult.State` | Set to `State.FAILURE` to interrupt processing with a Kafka error.  |
| `error`   | `String`           | Human-readable error message (logged by the Gateway).                 |

> `result.code`, `result.key`, and `result.contentType` are accepted but ignored for Kafka flows.


### Common objects

#### Headers methods

Applicable to `message.headers`.

| Method        | Arguments (type) | Return type   | Description              |
|---------------|------------------|---------------|--------------------------|
| `remove`      | key (`Object`)   |               | Remove a header.         |
| `containsKey` | key (`Object`)   | `boolean`     | Check if a header exists.|
| `clear`       |                  |               | Remove all headers.      |
| `empty`       |                  | `boolean`     | `true` when no headers.  |
| `size`        |                  | `int`         | Header count.            |
| `keySet`      |                  | `Set<String>` | All header names.        |




## Errors

When `result.state = State.FAILURE` is set, the Gateway terminates processing with a Kafka protocol-level `UNKNOWN_SERVER_ERROR`. For connection-level phases (INTERACT), this fails the entire Kafka request. For message phases (PUBLISH/SUBSCRIBE), this fails the current message batch.

Script compilation or runtime errors also result in a `UNKNOWN_SERVER_ERROR` being returned to the Kafka client.

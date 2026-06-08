You can use the `groovy` policy to execute Groovy scripts at any stage of request processing within the Gateway.

This policy is applicable to the following API types:

* v2 APIs
* v4 HTTP proxy APIs 
* v4 message APIs
* v4 LLM proxy APIs
* v4 MCP proxy APIs
* v4 A2A proxy APIs
* v4 Native Kafka APIs

**Note:** The Groovy policy is not supported by v4 TCP APIs.

Several variables are automatically bound to the Groovy script. These let you read, and potentially modify, their values to define the behavior of the policy.

### Request/response

| Variable   | Description                                                                 |
|------------|-----------------------------------------------------------------------------|
| `request`  | Inbound HTTP request                                                        |
| `response` | Outbound HTTP response                                                      | 
| `message`  | Message transiting the Gateway                                              | 
| `context`  | Context usable to access external components such as services and resources |
| `result`   | Object to return to alter the outcome of the request/response      |

See the [Usage](#usage) section for object attributes and methods.

### Content

| Variable           | Description                      |
|--------------------|----------------------------------|
| `request.content`  | When "Read content" is enabled   |
| `response.content` | When "Read content" is enabled   |
| `message.content`  | Always available                 |

### Native Kafka APIs

On v4 Native Kafka APIs the policy runs Groovy scripts across the Kafka flow phases:

* **INTERACT** — runs on each Kafka request/response at the connection level (e.g. Produce, Fetch).
* **PUBLISH** — runs on each individual Kafka message being produced.
* **SUBSCRIBE** — runs on each individual Kafka message being consumed.

The `context`, `result`, and (for `PUBLISH`/`SUBSCRIBE`) `message` variables are bound to the script. Note that `context.metrics` is not available in Kafka contexts, and `result.code`, `result.key`, and `result.contentType` are not forwarded to the client — Kafka uses its own protocol-level error codes.



You can use the `groovy` policy to execute Groovy scripts at any stage of request processing within the Gateway.

This policy is applicable to the following API types:

* v2 APIs
* v4 HTTP proxy APIs 
* v4 message APIs

**Note:** The Groovy policy is not supported by v4 TCP or Native APIs.

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



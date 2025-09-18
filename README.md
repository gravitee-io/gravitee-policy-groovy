
<!-- GENERATED CODE - DO NOT ALTER THIS OR THE FOLLOWING LINES -->
# Groovy

[![Gravitee.io](https://img.shields.io/static/v1?label=Available%20at&message=Gravitee.io&color=1EC9D2)](https://download.gravitee.io/#graviteeio-apim/plugins/policies/gravitee-policy-groovy/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/gravitee-io/gravitee-policy-groovy/blob/master/LICENSE.txt)
[![Releases](https://img.shields.io/badge/semantic--release-conventional%20commits-e10079?logo=semantic-release)](https://github.com/gravitee-io/gravitee-policy-groovy/releases)
[![CircleCI](https://circleci.com/gh/gravitee-io/gravitee-policy-groovy.svg?style=svg)](https://circleci.com/gh/gravitee-io/gravitee-policy-groovy)

## Overview
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





## Usage

### Change the outcome of a proxy API


To change the outcome of the request or response to access the `result` object, use the following properties in your script:

| Attribute | Type               | Description                    |
|-----------|--------------------|--------------------------------|
| `state`   | PolicyResult.State | To indicate a failure          |
| `code`    | integer            | An HTTP status code            |
| `error`   | string             | The error message              |
| `key`     | string             | The key of a response template |

Here is an example on the request phase:

```groovy
import io.gravitee.policy.groovy.PolicyResult.State

if (request.headers.containsKey('X-Gravitee-Break')) {
    result.key = 'RESPONSE_TEMPLATE_KEY'
    result.state = State.FAILURE
    result.code = 500
    result.error = 'Stop request processing due to X-Gravitee-Break header'
} else {
    request.headers.'X-Groovy-Policy' = 'ok'
}
```

### Override content

To override content in a proxy API, you must enable "Override content." "Override content" is always enabled for message APIs.

To override existing content, make your script the expected content.

**Input body content**

```json
{
"age": 32,
"firstname": "John",
"lastname": "Doe"
}
```

**Groovy script**

_You must enable "Read content" for this to work_

```groovy
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def jsonSlurper = new JsonSlurper()
def content = jsonSlurper.parseText(response.content)
content.firstname = 'Hacked ' + content.firstname
content.country = 'US'
return JsonOutput.toJson(content)
```

**Output body content**

```json
{
  "age": 32,
  "firstname": "Hacked John",
  "lastname": "Doe",
  "country": "US"
}
```

## Bound objects properties and methods

### Request

`request.<property>`

| Property           | Type                         | Mutable | Description                                                                                                                                              |
|--------------------|------------------------------|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `content`          | `String`                     |         | Body of the request.                                                                                                                                     |
| `transactionId`    | `String`                     |         | Unique identifier for the transaction.                                                                                                                   |
| `clientIdentifier` | `String`                     |         | Identifies the client that made the request.                                                                                                             |
| `uri`              | `String`                     |         | The complete request URI.                                                                                                                                |
| `host`             | `String`                     |         | Host from the incoming request.                                                                                                                          |
| `originalHost`     | `String`                     |         | Host as originally received before any internal rewriting.                                                                                               |
| `contextPath`      | `String`                     |         | API context path.                                                                                                                                         |
| `pathInfo`         | `String`                     |         | Path beyond the context path.                                                                                                                             |
| `path`             | `String`                     |         | The full path component of the request URI.                                                                                                              |
| `parameters`       | `Map<String, <List<String>>` | ✅️      | Query parameters as a multi-value map. For methods, refer to [Multimap methods](#multimap-methods).                                                           |
| `pathParameters`   | `Map<String, <List<String>>` |         | Parameters extracted from path templates. For methods, refer to [Multimap methods](#multimap-methods). Note that altering method are useless in this context. |
| `headers`          | `Map<String, String>`        | ✅       | HTTP headers. For methods, refer to [Headers methods](#headers-methods).                                                                                     |
| `method`           | `HttpMethod` (enum)          |         | HTTP method used in the request (e.g., GET, POST).                                                                                                       |
| `scheme`           | `String`                     |         | The scheme (HTTP or HTTPS) used by the request.                                                                                                          |
| `version`          | `HttpVersion` (enum)         |         | HTTP protocol version: `HTTP_1_0`,  `HTTP_1_1`,  `HTTP_2`.                                                                                               |
| `timestamp`        | `long`                       |         | Epoch timestamp of when the request was received.                                                                                                        |
| `remoteAddress`    | `String`                     |         | IP address of the client.                                                                                                                                 |
| `localAddress`     | `String`                     |         | Local IP address of the server handling the request.                                                                                                          |

### Response

`response.<property>`

| Property  | Type                  | Mutable | Description                                                                                       |
|-----------|-----------------------|---------|---------------------------------------------------------------------------------------------------|
| `content` | `String`              |         | Body of the response.                                                                              |
| `status`  | `int`                 |         | Response status code.                                                                              |
| `reason`  | `String`              |         | Reason for the status.                                                                              |
| `headers` | `Map<String, String>` | ✅       | HTTP headers wrapped in a bindable object. For methods, refer to [Headers methods](#headers-methods). |



### Message
`message.<property>`

| Property              | Type                | Mutable | Description                                                              |
|-----------------------|---------------------|---------|--------------------------------------------------------------------------|
| `correlationId`       | String              |         | Correlation ID to track the message.                                      |
| `parentCorrelationId` | String              |         | Parent correlation ID.                                                    |
| `timestamp`           | long                |         | Epoch (ms) timestamp.                                                     |
| `error`               | boolean             |         | Message is an error message.                                              |
| `metadata`            | Map<String, Object> | ✅       | Message metadata. Dependent on the messaging system.                        |
| `headers`             | Map<String, String> | ✅       | Message headers. For methods, refer to [Headers methods](#headers-methods).  |
| `content`             | String              |         | Message body as a string.                                                 |
| `contentAsBase64`     | String              |         | Message body bytes as a basic base64 string.                                |
| `contentAsByteArray`  | byte[]              |         | Message body bytes.                                                       |
| `attributes`          | Map<String, Object> | ✅       | Message attributes wrapped in a bindable object. For methods, see below. |

**Message attributes methods**

`message.attributes.<method>`

| Method          | Arguments (type) | Return type   | Description                                |
|-----------------|------------------|---------------|--------------------------------------------|
| `remove`        | key (`Object`)   |               | Remove an attribute.                       |
| `containsKey`   | key (`Object`)   | `boolean`     | Check if an attribute exists.               |
| `containsValue` | key (`Object`)   | `boolean`     | Check if one of the attributes contains the value. |
| `empty`         |                  | `boolean`     | `true` when the attribute exists.           |
| `size`          |                  | `int`         | Return an attribute count.                  |
| `keySet`        |                  | `Set<String>` | All attribute names.                        |

### Context

| Property          | Type                | Mutable | Description                  | 
|-------------------|---------------------|---------|------------------------------|
| `attributes`      | Map<String, Object> | ✅       | Context attributes as a map. |
| `attributeNames`  | Set<String>         |         | All attribute names.         |
| `attributeAsList` | List<Object>        |         | All attribute values.         |

**Context attributes methods**

`context.attributes.<method>`

Use a prefix to get an attribute or verify that it exists.

For example, `context.attributes.'my-specific-attribute'` returns an attribute map entry with the key `gravitee.attribute.my-specific-attribute`.

Refer to the Gravitee documentation for available attributes. 

**Note: This rule only applies when reading an attribute. You must explicitly add `gravitee.attribute.<key>` to alter an attribute.**


| Method        | Arguments (type)                | Return type | Description                                                                                                                                  |
|---------------|---------------------------------|-------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `get`         | key (`Object`)                  | `Object`    | Get a Gravitee attribute starting with `gravitee.attribute.`. Works with Groovy `context.attributes.'key'` syntax using the same prefix rule. |
| `containsKey` | key (`Object`)                  | `boolean`   | Check if a Gravitee attribute starting with `gravitee.attribute.` exists.                                                                       |
| `put`         | key (`String`) value (`Object`) |             | Equivalent of `context.attributes.'key' = value`.                                                                                            |

Other methods of `java.util.Map` are accessible, such as `remove` and `size`.


### Common objects

#### Headers methods

Applicable to `request.headers`, `response.headers`, `message.headers`.

| Method          | Arguments (type) | Return type    | Description               |
|-----------------|------------------|----------------|---------------------------|
| `remove`        | key (`Object`)   |                | Remove a header.          |
| `containsKey`   | key (`Object`)   | `boolean`      | Check if a header exists.  |
| `clear`         |                  |                | Remove all headers.         |
| `empty`         |                  | `boolean`      | `true` when header exists. |
| `size`          |                  | `int`          | Return header count.      |
| `keySet`        |                  | `Set<String>`  | All header names.         |


#### Multimap methods

Multimap lets you use several values for a single map entry without pre-initializing a collection.

Multimap is applicable to `request.parameters` and `request.pathParameters`.

All methods of the Gravitee [MultiValueMap](https://github.com/gravitee-io/gravitee-common/blob/master/src/main/java/io/gravitee/common/util/MultiValueMap.java) implementation: `getFirst`, `add`, `set`, `setAll`, `toSingleValueMap`, `containsAllKeys`.

All `java.util.Map` are also available.




## Errors
These templates are defined at the API level, in the "Entrypoint" section for v4 APIs, or in "Response Templates" for v2 APIs.
The error keys sent by this policy are as follows:

| Key |
| ---  |
| GROOVY_EXECUTION_FAILURE |



## Phases
The `groovy` policy can be applied to the following API types and flow phases.

### Compatible API types

* `PROXY`
* `MESSAGE`

### Supported flow phases:

* Request
* Response
* Publish
* Subscribe

## Compatibility matrix
Strikethrough text indicates that a version is deprecated.

| Plugin version| APIM |
| --- | ---  |
|4.x|4.9.x and above |
|3.x|4.6.x to 4.8.x |
|2.x|4.5.x and below |



## Configuration
### Gateway configuration
### Whitelist sandbox

The `groovy` policy includes a native sandbox feature, which lets you safely run Groovy scripts. The sandbox is based on a predefined list of allowed methods, fields, constructors, and annotations.

The complete whitelist can be found here: [gravitee groovy whitelist](https://gh.gravitee.io/gravitee-io/gravitee-policy-groovy/master/src/main/resources/groovy-whitelist).

This whitelist should address the majority of possible use cases. If you have specific needs which are not satisfied by the built-in whitelist, you can extend, or even replace, the list with your own declarations. To modify the whitelist, configure the `gravitee.yml` file to specify:

* `groovy.whitelist.mode`: `append` or `replace`. This lets you append whitelisted definitions to the built-in list, or completely replace it. We recommend selecting `append` to avoid unintended behaviors.
* `groovy.whitelist.list`: This lets you declare other methods, constructors, fields, or annotations in the whitelist.
    * Start with `method` to allow a specific method (complete signature)
    * Start with `class` to allow a complete class. All methods, constructors, and fields of the class are then accessible.
    * Start with `new` to allow a specific constructor (complete signature)
    * Start with `field` to allow access to a specific field of a class
    * Start with `annotation` to allow use of a specific annotation


gravitee.yml
```YAML
groovy:
  whitelist:
    mode: append
    list:
      - method com.acme.common.Strings toTitleCase java.lang.String
      - class com.acme.common.Strings
```
Environment variables (Helm)
```YAML
gateway:
  env:
    - name: GRAVITEE_GROOVY_WHITELIST_MODE
      value: append
    - name: GRAVITEE_GROOVY_WHITELIST_LIST_0
      value: "method com.acme.common.Strings toTitleCase java.lang.String"
    - name: GRAVITEE_GROOVY_WHITELIST_LIST_1
      value: "class com.acme.common.Strings"
```

**Security implications**

Exercise care when using classes or methods. In some cases, giving access to all methods of a class may make unwanted methods accessible via transitivity and risk security breaches.


### Configuration options


#### 
| Name <br>`json name`  | Type <br>`constraint`  | Mandatory  | Default  | Description  |
|:----------------------|:-----------------------|:----------:|:---------|:-------------|
| Override content<br>`overrideContent`| boolean|  | | Enable to override the content of the request or response with the value returned by your script.|
| Read content<br>`readContent`| boolean|  | | Enable if your script needs to access the content of the HTTP request or response in your script.|
| Script<br>`script`| string|  | | Groovy script to evaluate.|




## Examples

*Proxy API on Request phase*
```json
{
  "api": {
    "definitionVersion": "V4",
    "type": "PROXY",
    "name": "Groovy example API",
    "flows": [
      {
        "name": "Common Flow",
        "enabled": true,
        "selectors": [
          {
            "type": "HTTP",
            "path": "/",
            "pathOperator": "STARTS_WITH"
          }
        ],
        "request": [
          {
            "name": "Groovy",
            "enabled": true,
            "policy": "groovy",
            "configuration":
              {
                  "readContent": false,
                  "overrideContent": false,
                  "script": "response.headers.remove 'X-Powered-By'\nresponse.headers.'X-Gravitee-Gateway-Version' = '0.14.0'"
              }
          }
        ]
      }
    ]
  }
}

```
*Message API CRD*
```yaml
apiVersion: "gravitee.io/v1alpha1"
kind: "ApiV4Definition"
metadata:
    name: "groovy-message-api-crd"
spec:
    name: "Groovy example"
    type: "MESSAGE"
    flows:
      - name: "Common Flow"
        enabled: true
        selectors:
            matchRequired: false
            mode: "DEFAULT"
        request:
          - name: "Groovy"
            enabled: true
            policy: "groovy"
            configuration:
              overrideContent: false
              readContent: false
              script: |-
                  response.headers.remove 'X-Powered-By'
                  response.headers.'X-Gravitee-Gateway-Version' = '0.14.0'

```


## Changelog

### [4.0.0](https://github.com/gravitee-io/gravitee-policy-groovy/compare/3.0.3...4.0.0) (2025-09-18)


##### Features

* include cause throwable in the execution failure ([1cf1409](https://github.com/gravitee-io/gravitee-policy-groovy/commit/1cf140916aa5e69d36f2bfd4bb78013a3895728d))


##### BREAKING CHANGES

* requires APIM version 4.9.0 or later

#### [3.0.3](https://github.com/gravitee-io/gravitee-policy-groovy/compare/3.0.2...3.0.3) (2025-08-08)


##### Bug Fixes

* **deps:** update dependency org.apache.commons:commons-lang3 to v3.18.0 [security] ([7913dc6](https://github.com/gravitee-io/gravitee-policy-groovy/commit/7913dc60a528a5b17ffa326a27ab8ebb19192986))

#### [3.0.2](https://github.com/gravitee-io/gravitee-policy-groovy/compare/3.0.1...3.0.2) (2025-06-18)


##### Bug Fixes

* doc gen ([24bde45](https://github.com/gravitee-io/gravitee-policy-groovy/commit/24bde452c0b94ed1fad55169d8afe1cea00b35cf))

#### [3.0.1](https://github.com/gravitee-io/gravitee-policy-groovy/compare/3.0.0...3.0.1) (2025-01-15)


##### Bug Fixes

* **deps:** upgrade groovy-sandbox to 1.30 ([32fba8f](https://github.com/gravitee-io/gravitee-policy-groovy/commit/32fba8f5f3cd8b89b16bb1be5b9535048eb43612))

### [3.0.0](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.6.3...3.0.0) (2024-12-30)


##### Bug Fixes

* **deps:** bump apim version ([4eb775a](https://github.com/gravitee-io/gravitee-policy-groovy/commit/4eb775a8c3e81921e2f5ca68199902e501b3bda8))
* use latest node and apim version ([fb3706b](https://github.com/gravitee-io/gravitee-policy-groovy/commit/fb3706b1a7016f6229de992026a85362293f2cb0))
* warning messages ([5b3b334](https://github.com/gravitee-io/gravitee-policy-groovy/commit/5b3b334924b84d2dae870fefd778288d82768ba2))


##### Features

* support new OpenTelemetry feature ([14ca260](https://github.com/gravitee-io/gravitee-policy-groovy/commit/14ca2604e7ad08f3340885b1f67119c43a7cf02e))


##### BREAKING CHANGES

* Tracer interface is not more available through tracer-api module
* tracer and components are no longer allowed for groovy context

### [3.0.0-alpha.3](https://github.com/gravitee-io/gravitee-policy-groovy/compare/3.0.0-alpha.2...3.0.0-alpha.3) (2024-12-30)


##### Bug Fixes

* **deps:** bump apim version ([4eb775a](https://github.com/gravitee-io/gravitee-policy-groovy/commit/4eb775a8c3e81921e2f5ca68199902e501b3bda8))

### [3.0.0-alpha.2](https://github.com/gravitee-io/gravitee-policy-groovy/compare/3.0.0-alpha.1...3.0.0-alpha.2) (2024-12-16)


##### Bug Fixes

* warning messages ([5b3b334](https://github.com/gravitee-io/gravitee-policy-groovy/commit/5b3b334924b84d2dae870fefd778288d82768ba2))

### [3.0.0-alpha.1](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.6.2...3.0.0-alpha.1) (2024-11-06)


##### Bug Fixes

* use latest node and apim version ([fb3706b](https://github.com/gravitee-io/gravitee-policy-groovy/commit/fb3706b1a7016f6229de992026a85362293f2cb0))


##### Features

* support new OpenTelemetry feature ([14ca260](https://github.com/gravitee-io/gravitee-policy-groovy/commit/14ca2604e7ad08f3340885b1f67119c43a7cf02e))


##### BREAKING CHANGES

* Tracer interface is not more available through tracer-api module
* tracer and components are no longer allowed for groovy context

#### [2.6.3](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.6.2...2.6.3) (2024-12-13)


##### Bug Fixes

* warning messages in logs for groovy classes ([612f554](https://github.com/gravitee-io/gravitee-policy-groovy/commit/612f5542a14fb13f100408f0c616ccf86ec9df53))

#### [2.6.2](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.6.1...2.6.2) (2024-10-02)


##### Bug Fixes

* avoid blocking eventloop when compiling ([5bccbe2](https://github.com/gravitee-io/gravitee-policy-groovy/commit/5bccbe21442b73e6936bb62f6cc5cd4bc03dfa3c))

#### [2.6.1](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.6.0...2.6.1) (2024-08-21)


##### Bug Fixes

* add missing dateutil extension ([f6ab32d](https://github.com/gravitee-io/gravitee-policy-groovy/commit/f6ab32d778088bcaf9a154318ca491de98ec2a85))
* add missing java.time classes to whitelist ([c1f7456](https://github.com/gravitee-io/gravitee-policy-groovy/commit/c1f74563a03e4d1b5137d19cb8426c46e460bbc6))

### [2.6.0](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.5.2...2.6.0) (2024-06-03)


##### Features

* add methods for binary content of messages ([707519e](https://github.com/gravitee-io/gravitee-policy-groovy/commit/707519e220256f9d00386fec1d0525c7d37309be))

#### [2.5.2](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.5.1...2.5.2) (2023-11-09)


##### Bug Fixes

* do not write body if onRequest/Response only ([62f692c](https://github.com/gravitee-io/gravitee-policy-groovy/commit/62f692c5c685a4afaf537958fa61fef77be7c215))

#### [2.5.1](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.5.0...2.5.1) (2023-10-05)


##### Bug Fixes

* add request and response to message phases ([154db98](https://github.com/gravitee-io/gravitee-policy-groovy/commit/154db98744d3614f2f8d085ad8029b8f452afe15))

### [2.5.0](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.4.2...2.5.0) (2023-10-02)


##### Features

* add message level support to policy ([632813e](https://github.com/gravitee-io/gravitee-policy-groovy/commit/632813e1ab7496a58fae8b3918889beac0420d31))

#### [2.4.2](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.4.1...2.4.2) (2023-07-20)


##### Bug Fixes

* update policy description ([b0e00a0](https://github.com/gravitee-io/gravitee-policy-groovy/commit/b0e00a0b44c8d7fcffb1cc6d80f55fdf1a948976))

#### [2.4.1](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.4.0...2.4.1) (2023-06-27)


##### Bug Fixes

* add policy result key to readme ([f8b6774](https://github.com/gravitee-io/gravitee-policy-groovy/commit/f8b677474eac47758946a2d5be831a0686b866fc))

### [2.4.0](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.3.0...2.4.0) (2023-06-27)


##### Features

* allow to add response template key in policy result ([b26046e](https://github.com/gravitee-io/gravitee-policy-groovy/commit/b26046ee229cd9fe0225c90798f24f2533a047b9))

### [2.3.0](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.2.2...2.3.0) (2023-04-12)


##### Bug Fixes

* properly return the scheme with `scheme()` and `getScheme()` methods ([2a827b9](https://github.com/gravitee-io/gravitee-policy-groovy/commit/2a827b9154664800032543429d2bfba0e4db58de))


##### Features

* add a `getHost()` method so that "request.host" expression is correctly resolved ([12a3a04](https://github.com/gravitee-io/gravitee-policy-groovy/commit/12a3a04bff982fdfa0eac96d110bf59892046c86))

#### [2.2.2](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.2.1...2.2.2) (2022-06-24)


##### Bug Fixes

* whitelist gateway-api HttpHeaders ([f4bd528](https://github.com/gravitee-io/gravitee-policy-groovy/commit/f4bd5280544310548037560dcf74b12a2b29df13))

#### [2.2.1](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.2.0...2.2.1) (2022-06-15)


##### Bug Fixes

* make header accessor return an iterable instead of a string ([46774f2](https://github.com/gravitee-io/gravitee-policy-groovy/commit/46774f2b817cfe21c732aeb7cbd637af995aee48))

### [2.2.0](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.1.0...2.2.0) (2022-03-17)


##### Bug Fixes

* resolve GStringImpl.trim() ([4ff3390](https://github.com/gravitee-io/gravitee-policy-groovy/commit/4ff3390c4b5fdaa226b27ae49eaa945854da885b))
* resolve iteration on map ([d8fd8e5](https://github.com/gravitee-io/gravitee-policy-groovy/commit/d8fd8e5f18802373663adaf4e8080a9397276dd8)), closes [gravitee-io/issues#7302](https://github.com/gravitee-io/issues/issues/7302)


##### Features

* add EncodingGroovyMethods to whitelist ([2ba4f27](https://github.com/gravitee-io/gravitee-policy-groovy/commit/2ba4f27a781a5304da58d3980e1c826165dce010))

### [2.1.0](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.0.0...2.1.0) (2022-01-24)


##### Features

* **headers:** Internal rework and introduce HTTP Headers API ([3a3aa33](https://github.com/gravitee-io/gravitee-policy-groovy/commit/3a3aa334cac522d354e94e77fe7f3ffb0eed1de6)), closes [gravitee-io/issues#6772](https://github.com/gravitee-io/issues/issues/6772)
* **perf:** adapt policy for new classloader system ([08c3aea](https://github.com/gravitee-io/gravitee-policy-groovy/commit/08c3aeab9b283181c84baf487f21184d2bc97f86)), closes [gravitee-io/issues#6758](https://github.com/gravitee-io/issues/issues/6758)


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

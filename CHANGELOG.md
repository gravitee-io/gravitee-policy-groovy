# [3.0.0-alpha.3](https://github.com/gravitee-io/gravitee-policy-groovy/compare/3.0.0-alpha.2...3.0.0-alpha.3) (2024-12-30)


### Bug Fixes

* **deps:** bump apim version ([4eb775a](https://github.com/gravitee-io/gravitee-policy-groovy/commit/4eb775a8c3e81921e2f5ca68199902e501b3bda8))

# [3.0.0-alpha.2](https://github.com/gravitee-io/gravitee-policy-groovy/compare/3.0.0-alpha.1...3.0.0-alpha.2) (2024-12-16)


### Bug Fixes

* warning messages ([5b3b334](https://github.com/gravitee-io/gravitee-policy-groovy/commit/5b3b334924b84d2dae870fefd778288d82768ba2))

# [3.0.0-alpha.1](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.6.2...3.0.0-alpha.1) (2024-11-06)


### Bug Fixes

* use latest node and apim version ([fb3706b](https://github.com/gravitee-io/gravitee-policy-groovy/commit/fb3706b1a7016f6229de992026a85362293f2cb0))


### Features

* support new OpenTelemetry feature ([14ca260](https://github.com/gravitee-io/gravitee-policy-groovy/commit/14ca2604e7ad08f3340885b1f67119c43a7cf02e))


### BREAKING CHANGES

* Tracer interface is not more available through tracer-api module
* tracer and components are no longer allowed for groovy context

## [2.6.2](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.6.1...2.6.2) (2024-10-02)


### Bug Fixes

* avoid blocking eventloop when compiling ([5bccbe2](https://github.com/gravitee-io/gravitee-policy-groovy/commit/5bccbe21442b73e6936bb62f6cc5cd4bc03dfa3c))

## [2.6.1](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.6.0...2.6.1) (2024-08-21)


### Bug Fixes

* add missing dateutil extension ([f6ab32d](https://github.com/gravitee-io/gravitee-policy-groovy/commit/f6ab32d778088bcaf9a154318ca491de98ec2a85))
* add missing java.time classes to whitelist ([c1f7456](https://github.com/gravitee-io/gravitee-policy-groovy/commit/c1f74563a03e4d1b5137d19cb8426c46e460bbc6))

# [2.6.0](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.5.2...2.6.0) (2024-06-03)


### Features

* add methods for binary content of messages ([707519e](https://github.com/gravitee-io/gravitee-policy-groovy/commit/707519e220256f9d00386fec1d0525c7d37309be))

## [2.5.2](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.5.1...2.5.2) (2023-11-09)


### Bug Fixes

* do not write body if onRequest/Response only ([62f692c](https://github.com/gravitee-io/gravitee-policy-groovy/commit/62f692c5c685a4afaf537958fa61fef77be7c215))

## [2.5.1](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.5.0...2.5.1) (2023-10-05)


### Bug Fixes

* add request and response to message phases ([154db98](https://github.com/gravitee-io/gravitee-policy-groovy/commit/154db98744d3614f2f8d085ad8029b8f452afe15))

# [2.5.0](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.4.2...2.5.0) (2023-10-02)


### Features

* add message level support to policy ([632813e](https://github.com/gravitee-io/gravitee-policy-groovy/commit/632813e1ab7496a58fae8b3918889beac0420d31))

## [2.4.2](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.4.1...2.4.2) (2023-07-20)


### Bug Fixes

* update policy description ([b0e00a0](https://github.com/gravitee-io/gravitee-policy-groovy/commit/b0e00a0b44c8d7fcffb1cc6d80f55fdf1a948976))

## [2.4.1](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.4.0...2.4.1) (2023-06-27)


### Bug Fixes

* add policy result key to readme ([f8b6774](https://github.com/gravitee-io/gravitee-policy-groovy/commit/f8b677474eac47758946a2d5be831a0686b866fc))

# [2.4.0](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.3.0...2.4.0) (2023-06-27)


### Features

* allow to add response template key in policy result ([b26046e](https://github.com/gravitee-io/gravitee-policy-groovy/commit/b26046ee229cd9fe0225c90798f24f2533a047b9))

# [2.3.0](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.2.2...2.3.0) (2023-04-12)


### Bug Fixes

* properly return the scheme with `scheme()` and `getScheme()` methods ([2a827b9](https://github.com/gravitee-io/gravitee-policy-groovy/commit/2a827b9154664800032543429d2bfba0e4db58de))


### Features

* add a `getHost()` method so that "request.host" expression is correctly resolved ([12a3a04](https://github.com/gravitee-io/gravitee-policy-groovy/commit/12a3a04bff982fdfa0eac96d110bf59892046c86))

## [2.2.2](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.2.1...2.2.2) (2022-06-24)


### Bug Fixes

* whitelist gateway-api HttpHeaders ([f4bd528](https://github.com/gravitee-io/gravitee-policy-groovy/commit/f4bd5280544310548037560dcf74b12a2b29df13))

## [2.2.1](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.2.0...2.2.1) (2022-06-15)


### Bug Fixes

* make header accessor return an iterable instead of a string ([46774f2](https://github.com/gravitee-io/gravitee-policy-groovy/commit/46774f2b817cfe21c732aeb7cbd637af995aee48))

# [2.2.0](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.1.0...2.2.0) (2022-03-17)


### Bug Fixes

* resolve GStringImpl.trim() ([4ff3390](https://github.com/gravitee-io/gravitee-policy-groovy/commit/4ff3390c4b5fdaa226b27ae49eaa945854da885b))
* resolve iteration on map ([d8fd8e5](https://github.com/gravitee-io/gravitee-policy-groovy/commit/d8fd8e5f18802373663adaf4e8080a9397276dd8)), closes [gravitee-io/issues#7302](https://github.com/gravitee-io/issues/issues/7302)


### Features

* add EncodingGroovyMethods to whitelist ([2ba4f27](https://github.com/gravitee-io/gravitee-policy-groovy/commit/2ba4f27a781a5304da58d3980e1c826165dce010))

# [2.1.0](https://github.com/gravitee-io/gravitee-policy-groovy/compare/2.0.0...2.1.0) (2022-01-24)


### Features

* **headers:** Internal rework and introduce HTTP Headers API ([3a3aa33](https://github.com/gravitee-io/gravitee-policy-groovy/commit/3a3aa334cac522d354e94e77fe7f3ffb0eed1de6)), closes [gravitee-io/issues#6772](https://github.com/gravitee-io/issues/issues/6772)
* **perf:** adapt policy for new classloader system ([08c3aea](https://github.com/gravitee-io/gravitee-policy-groovy/commit/08c3aeab9b283181c84baf487f21184d2bc97f86)), closes [gravitee-io/issues#6758](https://github.com/gravitee-io/issues/issues/6758)

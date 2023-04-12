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

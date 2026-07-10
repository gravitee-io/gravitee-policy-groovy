
**Security implications**

Exercise care when using classes or methods. In some cases, giving access to all methods of a class may make unwanted methods accessible via transitivity and risk security breaches.
### Script execution timeout

Groovy scripts are interrupted if they run longer than the configured timeout, to protect the gateway from long-running or never-ending scripts. By default, the timeout instrumentation skips interfaces so that scripts declaring Groovy interfaces (or annotations) keep compiling; the `strictExecutionTimeout` option of the policy rejects such scripts instead, guaranteeing that no part of the script can escape the timeout.

| System property | Default | Description |
| --- | --- | --- |
| `gravitee.policy.groovy.script.timeout.seconds` | `5` | Maximum script execution time in seconds, clamped between `1` and `30`. |

Example:

```
-Dgravitee.policy.groovy.script.timeout.seconds=10
```

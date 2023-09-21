import io.gravitee.policy.groovy.PolicyResult.State

result.state = State.FAILURE
result.key = 'GROOVY_FAILED_ON_PURPOSE'
result.code = 400
result.error = 'Rejected Request'

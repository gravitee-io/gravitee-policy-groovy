interface Operators {
    Map OPERATORS = [eq: '=']
}

if (Operators.OPERATORS['eq'] == '=') {
    request.headers.remove 'x-context'
}

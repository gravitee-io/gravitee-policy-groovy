/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.groovy.sandbox;

import groovy.lang.Binding;
import io.gravitee.el.TemplateEngine;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ConcurrentHashMap;

import static io.gravitee.policy.groovy.sandbox.SecuredResolver.WHITELIST_LIST_KEY;
import static io.gravitee.policy.groovy.sandbox.SecuredResolver.WHITELIST_MODE_KEY;
import static org.junit.Assert.assertEquals;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SecuredGroovyShellTest {

    private final SecuredGroovyShell securedGroovyShell = new SecuredGroovyShell();

    @BeforeClass
    public static void beforeClass() {
        SecuredResolver.initialize(null);
    }

    @Before
    public void init() {

        SecuredResolver.destroy();
        SecuredResolver.initialize(null);
    }

    @Test
    public void patternOperator() {

        String script = "import java.util.regex.Pattern \n " +
                "def p = ~/foo/\n" +
                "assert p instanceof Pattern";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void findOperator() {

        String script = "import java.util.regex.Matcher                            \n" +
                "def text = \"some text to match\"                                 \n" +
                "def m = text =~ /match/                                           \n" +
                "assert m instanceof Matcher                                       \n" +
                "if (!m) {                                                         \n" +
                "    throw new RuntimeException(\"Oops, text not found!\")\n" +
                "}";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void spreadOperator() {

        String script = "class Car {\n" +
                "    String make\n" +
                "    String model\n" +
                "}\n" +
                "def cars = [\n" +
                "       new Car(make: 'Peugeot', model: '508'),        \n" +
                "       new Car(make: 'Renault', model: 'Clio')]       \n" +
                "def makes = cars*.make                                \n" +
                "assert makes == ['Peugeot', 'Renault']";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void rangeOperator() {

        String script = "def range = 0..5                            \n" +
                "assert (0..5).collect() == [0, 1, 2, 3, 4, 5]       \n" +
                "assert (0..<5).collect() == [0, 1, 2, 3, 4]         \n" +
                "assert (0..5) instanceof List                       \n" +
                "assert (0..5).size() == 6 ";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void spaceshipOperator() {

        String script = "assert (1 <=> 1) == 0\n" +
                "assert (1 <=> 2) == -1       \n" +
                "assert (2 <=> 1) == 1        \n" +
                "assert ('a' <=> 'z') == -1";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void callOperator() {

        String script =
                "class MyCallable {\n" +
                        "    int call(int x) {           \n" +
                        "        2*x\n" +
                        "    }\n" +
                        "}\n" +
                        "\n" +
                        "def mc = new MyCallable()\n" +
                        "assert mc.call(2) == 4          \n" +
                        "assert mc(2) == 4";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void getAtOperator() {

        String script = "class User {\n" +
                "    Long id\n" +
                "    String name\n" +
                "    def getAt(int i) {                                             \n" +
                "        switch (i) {\n" +
                "            case 0: return id\n" +
                "            case 1: return name\n" +
                "        }\n" +
                "        throw new IllegalArgumentException(\"No such element $i\")\n" +
                "    }\n" +
                "    void putAt(int i, def value) {                                 \n" +
                "        switch (i) {\n" +
                "            case 0: id = value; return\n" +
                "            case 1: name = value; return\n" +
                "        }\n" +
                "        throw new IllegalArgumentException(\"No such element $i\")\n" +
                "    }\n" +
                "}\n" +
                "def user = new User(id: 1, name: 'Alex')                           \n" +
                "assert user[0] == 1                                                \n" +
                "assert user[1] == 'Alex'                                           \n" +
                "user[1] = 'Bob'                                                    \n" +
                "assert user.name == 'Bob'";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void overloadingOperator() {

        String script =
                "class Bucket {\n" +
                        "    int size\n" +
                        "    Bucket(int size) { this.size = size }        \n" +
                        "    Bucket plus(Bucket other) {                  \n" +
                        "        return new Bucket(this.size + other.size)\n" +
                        "    }\n" +
                        "}\n" +
                        "def b1 = new Bucket(4) \n" +
                        "def b2 = new Bucket(11)\n" +
                        "assert (b1 + b2).size == 15";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void closure() {

        String script = "def code = { 123 } \n" +
                "assert code() == 123       \n" +
                "assert code.call() == 123";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void closureParameters() {

        String script = "def closureWithOneArg = { str -> str.toUpperCase() }\n" +
                "assert closureWithOneArg('groovy') == 'GROOVY'\n" +
                "\n" +
                "def closureWithOneArgAndExplicitType = { String str -> str.toUpperCase() }\n" +
                "assert closureWithOneArgAndExplicitType('groovy') == 'GROOVY'\n" +
                "\n" +
                "def closureWithTwoArgs = { a,b -> a+b }\n" +
                "assert closureWithTwoArgs(1,2) == 3\n" +
                "\n" +
                "def closureWithTwoArgsAndExplicitTypes = { int a, int b -> a+b }\n" +
                "assert closureWithTwoArgsAndExplicitTypes(1,2) == 3\n" +
                "\n" +
                "def closureWithTwoArgsAndOptionalTypes = { a, int b -> a+b }\n" +
                "assert closureWithTwoArgsAndOptionalTypes(1,2) == 3\n" +
                "\n" +
                "def closureWithTwoArgAndDefaultValue = { int a, int b=2 -> a+b }\n" +
                "assert closureWithTwoArgAndDefaultValue(1) == 3";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void closureImplicitParameter() {

        String script = "def greeting = { \"Hello, $it!\" }\n" +
                "assert greeting('Patrick') == 'Hello, Patrick!'";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void closureVarargs() {

        String script = "def concat1 = { String... args -> args.join('') }   \n" +
                "assert concat1('abc','def') == 'abcdef'                     \n" +
                "def concat2 = { String[] args -> args.join('') }            \n" +
                "assert concat2('abc', 'def') == 'abcdef'                    \n" +
                "                                                            \n" +
                "def multiConcat = { int n, String... args ->                \n" +
                "    args.join('')*n                                         \n" +
                "}                                                           \n" +
                "assert multiConcat(2, 'abc','def') == 'abcdefabcdef'";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void closureGString() {

        String script = "def x = 1\n" +
                "def gs = \"x = ${x}\"\n" +
                "assert gs == 'x = 1'";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void closureMemoization() {

        String script = "def fib      \n" +
                "fib = { long n -> n<2?n:fib(n-1)+fib(n-2) }.memoize()\n" +
                "assert fib(25) == 75025";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void jsonSlurper() {

        String script = "import java.util.Map \n" +
                "import groovy.json.JsonSlurper \n" +
                "def jsonSlurper = new JsonSlurper()\n" +
                "def object = jsonSlurper.parseText('{ \"name\": \"John Doe\" } /* some comment */')\n" +
                "\n" +
                "assert object instanceof Map\n" +
                "assert object.name == 'John Doe'";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void jsonOutput() {

        String script = "import groovy.json.JsonOutput \n" +
                "class Person { String name }\n" +
                "\n" +
                "def json = JsonOutput.toJson([ new Person(name: 'John'), new Person(name: 'Max') ])\n" +
                "\n" +
                "assert json == '[{\"name\":\"John\"},{\"name\":\"Max\"}]'";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void list() {

        String script = "def numbers = [1, 2, 3] \n" +
                "\n" +
                "assert numbers instanceof List  \n" +
                "assert numbers.size() == 3";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void listOperators() {

        String script = "def letters = ['a', 'b', 'c', 'd']\n" +
                "\n" +
                "assert letters[0] == 'a'     \n" +
                "assert letters[1] == 'b'\n" +
                "\n" +
                "assert letters[-1] == 'd'    \n" +
                "assert letters[-2] == 'c'\n" +
                "\n" +
                "letters[2] = 'C'             \n" +
                "assert letters[2] == 'C'\n" +
                "\n" +
                "letters << 'e'               \n" +
                "assert letters[ 4] == 'e'\n" +
                "assert letters[-1] == 'e'\n" +
                "\n" +
                "assert letters[1, 3] == ['b', 'd']         \n" +
                "assert letters[2..4] == ['C', 'd', 'e']";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void listSpreadOperator() {

        String script = "def items = [4,5]              \n" +
                "def list = [1,2,3,*items,6]            \n" +
                "assert list == [1,2,3,4,5,6] ";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void array() {

        String script = "String[] arrStr = ['Ananas', 'Banana', 'Kiwi']  \n" +
                "\n" +
                "assert arrStr instanceof String[]    \n" +
                "assert !(arrStr instanceof List)     \n" +
                "assert arrStr[1] == 'Banana'         \n";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void arrayOfInts() {

        String script = "def numArr = [1, 2, 3] as int[]      \n" +
                "\n" +
                "assert numArr instanceof int[]       \n" +
                "assert numArr.size() == 3";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void map() {

        String script = "def colors = [red: '#FF0000', green: '#00FF00', blue: '#0000FF']   \n" +
                "\n" +
                "assert colors['red'] == '#FF0000'    \n" +
                "assert colors.green  == '#00FF00'    \n" +
                "\n" +
                "colors['pink'] = '#FF00FF'           \n" +
                "colors.yellow  = '#FFFF00'           \n" +
                "\n" +
                "assert colors.pink == '#FF00FF'      \n" +
                "assert colors['yellow'] == '#FFFF00' \n" +
                "assert colors.unknown == null        \n" +
                "assert colors instanceof java.util.LinkedHashMap";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void mapQuoteSyntax() {

        String script = "def map = [:]\n" +
                "map.'single quote'\n" +
                "map.\"double quote\"\n" +
                "map.'''triple single quote'''\n" +
                "map.\"\"\"triple double quote\"\"\"\n" +
                "map./slashy string/\n" +
                "map.$/dollar slashy string/$";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void mapSpreadOperator() {

        String script = "def m1 = [c:3, d:4]           \n" +
                "def map = [a:1, b:2, *:m1, d: 8]      \n" +
                "assert map == [a:1, b:2, c:3, d:8]";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void function() {

        String script = "String myFunction(String arg) { \n" +
                "    return arg.toUpperCase()\n" +
                "}\n" +
                "\n" +
                "def result = myFunction('test')            \n" +
                "assert result == 'TEST'";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void propertyAccess() {

        String script = "class X {int x = 99}; new X().x";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void directPropertyGet() {

        String script = "class User {                           \n" +
                "    public final String name                   \n" +
                "    User(String name) { this.name = name}      \n" +
                "    String getName() { 'Name: ' + this.@name } \n" +
                "}\n" +
                "def user = new User('Bob')\n" +
                "assert user.name == 'Name: Bob' \n" +
                "assert user.@name == 'Bob'";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void directPropertyGetNotAllowed() {

        String script = "this.@binding";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void directPropertySet() {

        String script = "class User {                      \n" +
                "    public String name                    \n" +
                "    User(String name) { this.name = name} \n" +
                "}\n" +
                "def user = new User('Bob')\n" +
                "user.@name = 'Name: Bob' \n" +
                "assert user.@name == 'Name: Bob'";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void directPropertySetNotAllowed() {

        String script = "this.@binding = null";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void getBindingNotAllowed() {

        String script = "this.binding";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void getMetaclassNotAllowed() {

        String script = "this.metaClass";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void setBindingNotAllowed() {

        String script = "this.binding = this.binding";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void setMetaclassNotAllowed() {

        String script = "this.metaClass = this.metaClass";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void getBindingValue() {

        String script = "assert testVar == 'testValue'";

        Binding binding = new Binding();
        binding.setVariable("testVar", "testValue");
        securedGroovyShell.evaluate(script, binding);
    }

    @Test
    public void setBindingValue() {

        String script = "testVar = 'newValue' \n" +
                "assert testVar == 'newValue'";

        Binding binding = new Binding();
        binding.setVariable("testVar", "testValue");
        securedGroovyShell.evaluate(script, binding);
    }

    @Test
    public void methodPointer() {

        String script = "def str = 'example of method reference'    \n" +
                "def fun = str.&toUpperCase                         \n" +
                "def upper = fun()                                  \n" +
                "assert upper == str.toUpperCase() ";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void stringInterpolation() {

        String script = "def name = 'Test' // a plain string\n" +
                "def greeting = \"Hello ${name}\"\n" +
                "\n" +
                "assert greeting.toString() == 'Hello Test'";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void gStringCast() {

        String script = "String takeString(String message) { \n" +
                "    assert message instanceof String        \n" +
                "    return message\n" +
                "}\n" +
                "\n" +
                "def message = \"The message is ${'hello'}\" \n" +
                "assert message instanceof GString           \n" +
                "\n" +
                "def result = takeString(message)            \n" +
                "assert result instanceof String\n" +
                "assert result == 'The message is hello'";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void gStringHashCode() {

        String script = "assert \"one: ${1}\".hashCode() != \"one: 1\".hashCode()";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void tripleDoubleQuotedString() {

        String script = "def name = 'Groovy'\n" +
                "def template = \"\"\"\n" +
                "    Hello ${name},\n" +
                "    By\n" +
                "\"\"\"\n" +
                "\n" +
                "assert template.toString().contains('Groovy')";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void primitiveNumbers() {

        String script = "byte  b = 1\n" +
                "char  c = 2\n" +
                "short s = 3\n" +
                "int   i = 4\n" +
                "long  l = 5\n" +
                "float  f = 1.234\n" +
                "double d = 2.345";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void numbers() {

        String script = "assert 42I == new Integer('42')\n" +
                "assert 42i == new Integer('42') // lowercase i more readable\n" +
                "assert 123L == new Long(\"123\") // uppercase L more readable\n" +
                "assert 2147483648 == new Long('2147483648') // Long type used, value too large for an Integer\n" +
                "assert 456G == new BigInteger('456')\n" +
                "assert 456g == new BigInteger('456')\n" +
                "assert 123.45 == new BigDecimal('123.45') // default BigDecimal type used\n" +
                "assert 1.200065D == new Double('1.200065')\n" +
                "assert 1.234F == new Float('1.234')\n" +
                "assert 1.23E23D == new Double('1.23E23')";

        securedGroovyShell.evaluate(script, new Binding());
    }


    @Test(expected = SecurityException.class)
    public void packageNotAllowed() throws Exception {

        try {
            String script = "package io.gravitee \n" +
                    "assert (1 <=> 1) == 0";

            securedGroovyShell.evaluate(script, new Binding());
        } catch (MultipleCompilationErrorsException e) {
            throw e.getErrorCollector().getException(0);
        }
    }

    @Test(expected = SecurityException.class)
    public void annotationNotAllowed() throws Exception {

        try {
            String script = "import groovy.transformASTTest\n" +
                    "@transformASTTest\n" +
                    "def a =1";

            securedGroovyShell.evaluate(script, new Binding());
        } catch (MultipleCompilationErrorsException e) {
            throw e.getErrorCollector().getException(0);
        }
    }

    @Test(expected = SecurityException.class)
    public void annotationWorkAround1NotAllowed() throws Exception {

        try {
            String script = "@groovy.transformASTTest\n" +
                    "def a =1";

            securedGroovyShell.evaluate(script, new Binding());
        } catch (MultipleCompilationErrorsException e) {
            throw e.getErrorCollector().getException(0);
        }
    }

    @Test(expected = SecurityException.class)
    public void annotationWorkAround2NotAllowed() throws Exception {

        try {
            String script = "import groovy.transformASTTest as Custom\n" +
                    "@Custom\n" +
                    "def a =1";

            securedGroovyShell.evaluate(script, new Binding());
        } catch (MultipleCompilationErrorsException e) {
            throw e.getErrorCollector().getException(0);
        }
    }

    @Test(expected = SecurityException.class)
    public void superMethodNotAllowed() {

        String script = "super.getBinding()";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void superMethodAllowed() {

        String script = "class MyJsonSlurper extends groovy.json.JsonSlurper { \n" +
                "   int myMethod() { super.getMaxSizeForInMemory() } \n" +
                "}\n" +
                "def mySlurper = new MyJsonSlurper() \n" +
                "mySlurper.myMethod()";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void superConstructorNotAllowed() {

        String script = "class MyEnv extends org.springframework.core.env.StandardEnvironment { \n" +
                "   MyEnv() { super() } \n" +
                "}\n" +
                "def myEnv = new MyEnv()";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void superConstructorAllowed() {

        String script = "class MyJsonSlurper extends groovy.json.JsonSlurper { \n" +
                "   MyJsonSlurper() { super() } \n" +
                "}\n" +
                "def mySlurper = new MyJsonSlurper()";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void printNotAllowed() {

        String script = "println 'hello'";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void systemOutPrintNotAllowed() {

        String script = "java.lang.System.out.println('hello')";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void systemOutNotAllowed() {

        String script = "def outPs = java.lang.System.out";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void systemInNotAllowed() {

        String script = "def inPs = java.lang.System.in";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void systemPropertiesNotAllowed() {

        String script = "java.lang.System.getProperties()";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void systemEnvNotAllowed() {

        String script = "java.lang.System.getenv()";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void systemExitNotAllowed() {

        String script = "java.lang.System.exit(-1)";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void systemExitWorkAround1NotAllowed() {

        String script = "('java.lang.System' as Class).exit(-1)";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void systemExitWorkAround2NotAllowed() {

        String script = "def c = System\n" +
                "c.exit(-1)";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void systemExitWorkAround3NotAllowed() {

        String script = "import static java.lang.System.exit\n" +
                "exit(-1)";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void systemExitWorkAround4NotAllowed() {

        String script = "((Object)System).exit(-1)";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void systemExitWorkAround5NotAllowed() {

        String script = "def var = \"${java.lang.System.exit(-1)}\"";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void systemExitWorkAround6NotAllowed() {

        String script = "def var = \"${-> java.lang.System.exit(-1)}\" \n" +
                "assert var == 'test' // Force evaluation of GString closure";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void systemExitWorkAround7NotAllowed() {
        String script = "def var = java.lang.System.&exit \n" +
                "var(-1)";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void classFornameNotAllowed() {

        String script = "java.lang.Class.forname('java.lang.System')";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void getPropertyNotAllowed() {

        String script = SecuredResolver.class.getCanonicalName() + ".WHITELIST_MODE";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void getPropertyAllowed() {

        String script = Integer.class.getCanonicalName() + ".MAX_VALUE";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void setPropertyNotAllowed() {

        String script = SecuredResolver.class.getCanonicalName() + ".WHITELIST_MODE = 'test'";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void setPropertyAllowed() {

        String script = "import groovy.json.JsonSlurper \n" +
                "import groovy.json.JsonParserType \n" +
                "def jsonSlurper = new JsonSlurper()\n" +
                "jsonSlurper.type = JsonParserType.LAX";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void classResourceNotAllowed() {

        String script = "def clazz = this.class.getResource(\"/groovy-whitelist\")";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void fileNotAllowed() {

        String script = "def file = new File(\"test.txt\")";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void finalizeOverrideNotAllowed() throws Exception {

        try {
            String script = "class Person { public void finalize() { println 'test' } }\n" +
                    "new Person()";

            securedGroovyShell.evaluate(script, new Binding());
        } catch (MultipleCompilationErrorsException e) {
            throw e.getErrorCollector().getException(0);
        }
    }

    @Test(expected = SecurityException.class)
    public void constructorNotAllowed() {

        String script = "def e = new Exception()";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void constructorAllowed() {

        String script = "def d = new BigDecimal(\"5.4\")";

        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test
    public void shouldIgnoreUnknownWhitelistedElements() {

        ConfigurableEnvironment environment = new MockEnvironment()
                .withProperty(WHITELIST_MODE_KEY, "append")
                .withProperty(WHITELIST_LIST_KEY + "[O]", "class io.gravitee.Unknown")
                .withProperty(WHITELIST_LIST_KEY + "[1]", "method java.lang.Math unknown")
                .withProperty(WHITELIST_LIST_KEY + "[2]", "new java.lang.Math unknown")
                .withProperty(WHITELIST_LIST_KEY + "[3]", "annotation java.lang.Math")
                .withProperty(WHITELIST_LIST_KEY + "[4]", "field java.lang.Math unknown");

        SecuredResolver.destroy();
        SecuredResolver.initialize(environment);

        String script = "assert java.lang.Math.abs(60) == 60";
        securedGroovyShell.evaluate(script, new Binding());
    }

    @Test(expected = SecurityException.class)
    public void shouldNotAllowMethodWhenBuiltInWhitelistHasBeenReplace() {

        ConfigurableEnvironment environment = new MockEnvironment()
                .withProperty(WHITELIST_MODE_KEY, "replace") // The configured whitelist replaces the built-in (doesn't contains Math.abs(int) method).
                .withProperty(WHITELIST_LIST_KEY + "[O]", "class java.lang.String");

        SecuredResolver.destroy();
        SecuredResolver.initialize(environment);

        String script = "assert java.lang.Math.abs(60) == 60";
        securedGroovyShell.evaluate(script, new Binding());
    }
}
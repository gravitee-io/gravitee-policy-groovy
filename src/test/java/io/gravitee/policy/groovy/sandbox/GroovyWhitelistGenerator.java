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

import java.io.*;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GroovyWhitelistGenerator {

    private static final String WHITELIST_PATH = "src/main/resources/groovy-whitelist";

    private static final String[] SUPPORTED_CLASS = {
        "groovy.json.JsonOutput",
        "groovy.json.JsonParserType",
        "groovy.json.JsonSlurper",
        "groovy.util.slurpersupport.Node",
        "groovy.util.slurpersupport.NodeChild",
        "groovy.util.XmlSlurper",
        "io.gravitee.am.model.safe.ClientProperties",
        "io.gravitee.am.model.safe.DomainProperties",
        "io.gravitee.am.model.safe.UserProperties",
        "io.gravitee.am.model.uma.PermissionRequest",
        "io.gravitee.common.http.GraviteeHttpHeader",
        "io.gravitee.common.http.HttpHeader",
        "io.gravitee.common.http.HttpHeaders",
        "io.gravitee.common.http.HttpHeadersValues",
        "io.gravitee.common.http.HttpMethod",
        "io.gravitee.common.http.HttpStatusCode",
        "io.gravitee.common.http.HttpVersion",
        "io.gravitee.common.http.MediaType",
        "io.gravitee.common.util.BlockingArrayQueue",
        "io.gravitee.common.util.LinkedCaseInsensitiveMap",
        "io.gravitee.common.util.LinkedCaseInsensitiveSet",
        "io.gravitee.common.util.LinkedMultiValueMap",
        "io.gravitee.common.util.ListReverser",
        "io.gravitee.common.util.Maps",
        "io.gravitee.common.util.MultiValueMap",
        "io.gravitee.common.util.URIUtils",
        "io.gravitee.gateway.api.ExecutionContext",
        "io.gravitee.policy.groovy.model.ContentAwareRequest",
        "io.gravitee.policy.groovy.model.ContentAwareResponse",
        "io.gravitee.policy.groovy.PolicyResult",
        "io.gravitee.policy.groovy.PolicyResult$State",
        "io.gravitee.policy.groovy.utils.AttributesBasedExecutionContext",
        "java.lang.Double",
        "java.lang.Float",
        "java.lang.Integer",
        "java.lang.Long",
        "java.lang.Math",
        "java.lang.Short",
        "java.lang.String",
        "java.math.BigDecimal",
        "java.math.BigInteger",
        "java.net.URLDecoder",
        "java.net.URLEncoder",
        "java.time.format.DateTimeFormatter",
        "java.time.LocalDate",
        "java.time.LocalDateTime",
        "java.time.ZonedDateTime",
        "java.util.Calendar",
        "java.util.Collection",
        "java.util.Collections",
        "java.util.Date",
        "java.util.Deque",
        "java.util.List",
        "java.util.Map",
        "java.util.Queue",
        "java.util.Random",
    };

    private static final Map<String, List<String>> EXPLICIT_SUPPORTED_CLASS_METHODS = Map.of(
        "java.lang.System",
        List.of("currentTimeMillis", "nanoTime")
    );

    private static final String[] SUPPORTED_CLASS_METHODS = {
        "java.lang.Object",
        "java.util.Comparator",
        "java.util.Map$Entry",
        "java.util.TimeZone",
        "java.util.UUID",
        "java.lang.Throwable",
        "java.lang.Comparable",
        "java.lang.Enum",
        "groovy.lang.Closure",
        "groovy.lang.GString",
        "groovy.lang.Range",
        "groovy.json.JsonSlurper",
        "org.codehaus.groovy.runtime.DefaultGroovyMethods",
        "org.codehaus.groovy.runtime.InvokerHelper",
        "org.codehaus.groovy.runtime.ScriptBytecodeAdapter",
        "org.codehaus.groovy.runtime.StringGroovyMethods",
        "org.codehaus.groovy.runtime.GStringImpl",
        "org.codehaus.groovy.runtime.EncodingGroovyMethods",
    };

    private static final Map<String, List<String>> EXPLICIT_EXCLUDED_CLASS_METHODS = Map.of(
        "org.codehaus.groovy.runtime.DefaultGroovyMethods",
        List.of("getProperties")
    );

    private static final String[] EXCLUDED_METHODS_PREFIX = {
        "execute",
        "getClass",
        "getMetaClass",
        "invoke",
        "new",
        "notify",
        "print",
        "run",
        "wait",
    };

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        new GroovyWhitelistGenerator().run();
    }

    public void run() throws IOException, ClassNotFoundException {
        File file = new File(WHITELIST_PATH);
        if (file.exists()) {
            file.delete();
        }
        file.createNewFile();

        FileWriter fileWriter = new FileWriter(file);

        PrintWriter printWriter = new PrintWriter(fileWriter);
        printWriter.print("# Allows by class (all methods, fields and constructors)");
        printWriter.println();
        for (String supportedClass : SUPPORTED_CLASS) {
            printWriter.printf("class %s", supportedClass);
            printWriter.println();
        }

        printWriter.println();
        printWriter.print("# Allows method signatures");
        printWriter.println();

        List<String> methods = new ArrayList<>();
        for (String className : EXPLICIT_SUPPORTED_CLASS_METHODS.keySet()) {
            EXPLICIT_SUPPORTED_CLASS_METHODS
                .get(className)
                .forEach(methodName -> methods.add(String.format("method %s %s", className, methodName)));
        }

        for (String supportedMethod : SUPPORTED_CLASS_METHODS) {
            Class<?> aClass = Class.forName(supportedMethod);
            Arrays
                .stream(aClass.getMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .filter(method -> !Arrays.stream(EXCLUDED_METHODS_PREFIX).anyMatch(prefix -> method.getName().startsWith(prefix)))
                .filter(
                    method ->
                        !(
                            EXPLICIT_EXCLUDED_CLASS_METHODS.containsKey(aClass.getCanonicalName()) &&
                            EXPLICIT_EXCLUDED_CLASS_METHODS.get(aClass.getCanonicalName()).contains(method.getName())
                        )
                )
                .filter(method -> Arrays.stream(method.getParameters()).filter(p -> p.isVarArgs()).findFirst().isEmpty())
                .forEach(
                    method -> {
                        String params = Arrays
                            .stream(method.getParameters())
                            .map(p -> p.getType().getCanonicalName())
                            .collect(Collectors.joining(" "));
                        methods.add(String.format("method %s %s %s", aClass.getCanonicalName(), method.getName(), params).trim());
                    }
                );
        }

        methods
            .stream()
            .filter(
                method -> {
                    try {
                        SecuredResolver.parseMethod(method);
                        return true;
                    } catch (Exception e) {
                        return false;
                    }
                }
            )
            .sorted(String::compareToIgnoreCase)
            .forEach(
                method -> {
                    printWriter.printf(method);
                    printWriter.println();
                }
            );

        printWriter.close();
    }
}

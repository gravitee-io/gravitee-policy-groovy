/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.groovy.sandbox;

import static java.util.Collections.emptyList;

import groovy.lang.GString;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import io.gravitee.common.util.EnvironmentUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.codehaus.groovy.runtime.DateGroovyMethods;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.EncodingGroovyMethods;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * The {@link SecuredResolver} is a thread-safe singleton class which can be used by any {@link org.kohsuke.groovy.sandbox.GroovyInterceptor} to determine if a method, field, constructor, ... is allowed.
 * <p/>
 * By default, the whitelisted methods, constructors, fields and annotations are loaded from groovy-whitelist file located in the classpath.
 * The list can be either replaced or completed specifying a 'groovy.whitelist.list' configuration (array).
 *
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class SecuredResolver {

    static final String WHITELIST_MODE = "append";
    static final String WHITELIST_MODE_KEY = "groovy.whitelist.mode";
    static final String WHITELIST_LIST_KEY = "groovy.whitelist.list";
    static final String WHITELIST_METHOD_PREFIX = "method ";
    static final String WHITELIST_FIELD_PREFIX = "field ";
    static final String WHITELIST_CLASS_PREFIX = "class ";
    static final String WHITELIST_CONSTRUCTOR_PREFIX = "new ";
    static final String WHITELIST_ANNOTATION_PREFIX = "annotation ";

    // Specific groovy methods on numbers.
    private static final Set<String> NUMBER_MATH_METHOD_NAMES = new HashSet<>(
        Arrays.asList(
            "plus",
            "minus",
            "multiply",
            "div",
            "compareTo",
            "or",
            "and",
            "xor",
            "intdiv",
            "mod",
            "leftShift",
            "rightShift",
            "rightShiftUnsigned"
        )
    );

    // Default groovy method classes.
    private static final Class<?>[] DGM_CLASSES = {
        DefaultGroovyMethods.class,
        StringGroovyMethods.class,
        EncodingGroovyMethods.class,
        DateGroovyMethods.class,
    };

    private static final List<String> ALLOWED_ARRAY_NATIVE_METHODS = Arrays.asList("getAt", "putAt", "getLength");

    private static Map<Class<?>, List<Method>> methodsByType;
    private static Map<Class<?>, List<Field>> fieldsByType;
    private static Map<Class<?>, List<Constructor<?>>> constructorsByType;
    private static Set<Class<?>> annotations;

    private final Map<Class<?>, List<Method>> methodsByTypeAndSuperTypes;
    private final Map<String, Boolean> resolved;

    private static SecuredResolver instance;

    public static synchronized void initialize(@Nullable Environment environment) {
        if (!isInitialized()) {
            loadWhitelist(environment);
            instance = new SecuredResolver();
        }
    }

    public static synchronized boolean isInitialized() {
        return instance != null;
    }

    public static synchronized void destroy() {
        if (isInitialized()) {
            methodsByType.clear();
            fieldsByType.clear();
            constructorsByType.clear();
            annotations.clear();
            instance = null;
        }
    }

    public static SecuredResolver getInstance() {
        if (!isInitialized()) {
            initialize(null);
        }

        return instance;
    }

    private SecuredResolver() {
        resolved = new ConcurrentHashMap<>();
        methodsByTypeAndSuperTypes = new ConcurrentHashMap<>();
    }

    public boolean isAnnotationAllowed(String name) {
        // We only have an annotation name. Just try to find corresponding annotation testing all combinations.
        return annotations
            .stream()
            .anyMatch(aClass ->
                aClass.getCanonicalName().equals(name) ||
                aClass.getName().equals(name) ||
                aClass.getSimpleName().equals(name) ||
                aClass.getTypeName().equals(name)
            );
    }

    public boolean isConstructorAllowed(Class<?> clazz, Object... constructorArgs) {
        String key = getKey(clazz, "<init>", constructorArgs);

        if (resolved.containsKey(key)) {
            return resolved.get(key);
        }

        if (isGroovyScriptDefinedClass(clazz)) {
            resolved.put(key, true);
            return true;
        }

        Class<?>[] argumentClasses = getClasses(constructorArgs);
        Constructor<?> constructor = ConstructorUtils.getMatchingAccessibleConstructor(clazz, argumentClasses);

        boolean constructorAllowed = constructorsByType.getOrDefault(clazz, emptyList()).contains(constructor);
        resolved.put(key, constructorAllowed);

        return constructorAllowed;
    }

    public boolean isGetPropertyAllowed(Object object, String propertyName) {
        Class<?> objectClass = object instanceof Class ? (Class<?>) object : object.getClass();
        String key = getKey(object, propertyName, new Object[0]);

        if (resolved.containsKey(key)) {
            return resolved.get(key);
        }

        if (isGroovyScriptDefinedClass(objectClass)) {
            resolved.put(key, true);
            return true;
        }

        // Try to find 'get' or 'is' method.
        String[] getPrefixes = { "get", "is" };

        for (String prefix : getPrefixes) {
            String getter = prefix + StringUtils.capitalize(propertyName);
            if (isMethodAllowed(object, getter)) {
                resolved.put(key, true);
                return true;
            }
        }

        // Try to find accessible class property.
        Field field = FieldUtils.getDeclaredField(objectClass, propertyName);

        if (field != null && getAllowedFields(objectClass).contains(field)) {
            resolved.put(key, true);
            return true;
        }

        return false;
    }

    public boolean isSetPropertyAllowed(Object object, String propertyName, Object propertyValue) {
        Class<?> objectClass = object instanceof Class ? (Class<?>) object : object.getClass();
        String key = getKey(object, propertyName, new Object[0]);

        if (resolved.containsKey(key)) {
            return resolved.get(key);
        }

        if (isGroovyScriptDefinedClass(objectClass)) {
            resolved.put(key, true);
            return true;
        }

        // Try to find 'set' method.
        String getter = "set" + StringUtils.capitalize(propertyName);

        if (isMethodAllowed(object, getter, propertyValue)) {
            resolved.put(key, true);
            return true;
        }

        // Try to find accessible class property.
        Field field = FieldUtils.getDeclaredField(objectClass, propertyName);

        if (field != null && getAllowedFields(objectClass).contains(field)) {
            resolved.put(key, true);
            return true;
        }

        return false;
    }

    public boolean isMethodAllowed(Object object, String methodName, Object... methodArgs) {
        Class<?> objectClass = object instanceof Class ? (Class<?>) object : object.getClass();
        String key = getKey(objectClass, methodName, methodArgs);

        if (resolved.containsKey(key)) {
            return resolved.get(key);
        }

        if (object instanceof Number && NUMBER_MATH_METHOD_NAMES.contains(methodName)) {
            // Synthetic methods like Integer.plus(Integer).
            resolved.put(key, true);
            return true;
        }

        Class<?>[] argumentClasses = getClasses(methodArgs);

        boolean methodAllowed =
            isMethodAllowed(objectClass, methodName, argumentClasses) || isDGMAllowed(objectClass, methodName, argumentClasses);
        resolved.put(key, methodAllowed);

        return methodAllowed;
    }

    private boolean isMethodAllowed(Class<?> clazz, String methodName, Class<?>[] argumentClasses) {
        if (clazz == null) {
            return false;
        }

        Method method = getMatchingAccessibleMethod(clazz, methodName, argumentClasses);

        if (method != null && (isGroovyScriptDefinedMethod(method) || getAllowedMethods(clazz).contains(method))) {
            // Allow method if directly defined in the script or if the method is explicitly allowed.
            return true;
        }

        // Try to find allowed method from super type.
        if (isMethodAllowed(clazz.getSuperclass(), methodName, argumentClasses)) {
            return true;
        }

        // Try to find allowed method from all interfaces.
        Class<?>[] interfaces = ClassUtils.getAllInterfacesForClass(clazz);
        for (Class<?> c : interfaces) {
            if (c != clazz && isMethodAllowed(c, methodName, argumentClasses)) {
                return true;
            }
        }

        return false;
    }

    private Method getMatchingAccessibleMethod(Class<?> clazz, String methodName, Class<?>[] argumentClasses) {
        try {
            return MethodUtils.getMatchingAccessibleMethod(clazz, methodName, argumentClasses);
        } catch (InaccessibleObjectException e) {
            return getMatchingAccessibleMethod(clazz.getSuperclass(), methodName, argumentClasses);
        }
    }

    private boolean isDGMAllowed(Class<?> clazz, String methodName, Class<?>[] argumentClasses) {
        Class<?>[] selfArgs = new Class[argumentClasses.length + 1];
        selfArgs[0] = clazz;
        System.arraycopy(argumentClasses, 0, selfArgs, 1, argumentClasses.length);

        if (clazz.isArray() && ALLOWED_ARRAY_NATIVE_METHODS.contains(methodName)) {
            // Groovy allows to call getAt(int) on an array (not a list which is handled by DGM classes).
            // array.getAt(0) is equivalent to array[0], so we mut allow the call.
            // Ex: "a b c".split(" ").getAt(2) -> split call return an array !
            return true;
        }

        // Try to find allowed method from default groovy methods.
        for (Class<?> dgmClass : DGM_CLASSES) {
            Method method = getMatchingAccessibleMethod(dgmClass, methodName, selfArgs);

            if (method != null && getAllowedMethods(dgmClass).contains(method)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Indicates if the specified class has been defined in a Groovy script.
     * For that, class must be loaded from Groovy class loader and must not be assignable to {@link Script} class (which means the class is the compiled script himself).
     *
     * For example, the following Groovy script will contains a <code>User</code> class which will be considered as a Groovy script defined class:
     * <pre>
     *     class User {
     *         String name
     *     }
     *
     *     def user = new User()
     * </pre>
     *
     * @param clazz the class to check.
     * @return <code>true</code> if the class has been declared in a Groovy script, <code>false</code> else.
     */
    private boolean isGroovyScriptDefinedClass(Class<?> clazz) {
        return clazz.getClassLoader() instanceof GroovyClassLoader && !Script.class.isAssignableFrom(clazz);
    }

    /**
     * Indicates if the method has been defined in a Groovy script.
     *
     * For example, the following Groovy script declares a method <code>myMethod</code> and will be considered as well:
     * <pre>
     *     def myMethod() {
     *         assert true
     *     }
     *
     *     myMethod()
     * </pre>
     *
     * @param method the method to check.
     * @return <code>true</code> if the method has been declared in a Groovy script, <code>false</code> else.
     */
    private boolean isGroovyScriptDefinedMethod(Method method) {
        Class<?> clazz = method.getDeclaringClass();
        return clazz.getClassLoader() instanceof GroovyClassLoader && clazz != Script.class;
    }

    /**
     * Returns all allowed methods for a given class.
     *
     * @param clazz the class.
     * @return the list of all allowed methods for the specified class. If no method is allowed, an empty list will be returned.
     */
    private List<Method> getAllowedMethods(Class<?> clazz) {
        if (methodsByTypeAndSuperTypes.containsKey(clazz)) {
            return methodsByTypeAndSuperTypes.get(clazz);
        }

        List<Method> methods = methodsByType.getOrDefault(clazz, emptyList());

        if (clazz.getSuperclass() != null) {
            methods = Stream.concat(methods.stream(), getAllowedMethods(clazz.getSuperclass()).stream()).collect(Collectors.toList());
        }

        for (Class<?> anInterface : clazz.getInterfaces()) {
            methods = Stream.concat(methods.stream(), getAllowedMethods(anInterface).stream()).collect(Collectors.toList());
        }

        methodsByTypeAndSuperTypes.put(clazz, methods);

        return methods;
    }

    /**
     * Returns all allowed fields for a given class.
     *
     * @param clazz the class.
     * @return the list of all allowed fields for the specified class. If no field is allowed, an empty list will be returned.
     */
    private List<Field> getAllowedFields(Class<?> clazz) {
        return fieldsByType.getOrDefault(clazz, emptyList());
    }

    private String getKey(Object object, String methodName, Object[] methodArgs) {
        Class<?>[] argumentClasses = getClasses(methodArgs);

        return (object instanceof Class<?> ? object : object.getClass()) + "#" + methodName + Arrays.toString(argumentClasses);
    }

    /**
     * Transform a given array of objects to an array of corresponding object classes.
     * Note: {@link GString} class will be considered as {@link String} class.
     *
     * @param objects the array of objects.
     * @return the array of corresponding classes.
     */
    private Class<?>[] getClasses(Object[] objects) {
        Class<?>[] argumentClasses = new Class<?>[objects.length];

        for (int i = 0; i < objects.length; i++) {
            if (objects[i] instanceof GString) {
                // Groovy String must be considered as String to resolve methods and handle automatic Groovy cast to String.class.
                argumentClasses[i] = String.class;
            } else {
                argumentClasses[i] = objects[i] != null ? objects[i].getClass() : Object.class;
            }
        }

        return argumentClasses;
    }

    private static void loadWhitelist(Environment environment) {
        List<Method> methods = new ArrayList<>();
        List<Field> fields = new ArrayList<>();
        List<Constructor<?>> constructors = new ArrayList<>();
        List<Class<?>> annotationClasses = new ArrayList<>();
        boolean loadBuiltInWhitelist = true;

        // Load groovy-whitelist from configuration.
        if (environment != null) {
            // Built-in groovy-whitelist will not be loaded if mode is not 'append' (ie: set to 'replace').
            loadBuiltInWhitelist = WHITELIST_MODE.equals(environment.getProperty(WHITELIST_MODE_KEY, WHITELIST_MODE));

            Collection<Object> configWhitelist = EnvironmentUtils
                .getPropertiesStartingWith((ConfigurableEnvironment) environment, WHITELIST_LIST_KEY)
                .values();

            for (Object declaration : configWhitelist) {
                parseDeclaration(String.valueOf(declaration), methods, fields, constructors, annotationClasses);
            }
        }

        // Load built-in groovy-whitelist if required.
        if (loadBuiltInWhitelist) {
            InputStream input = SecuredResolver.class.getResourceAsStream("/groovy-whitelist");
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));

            String declaration;

            try {
                while ((declaration = reader.readLine()) != null) {
                    parseDeclaration(declaration, methods, fields, constructors, annotationClasses);
                }
            } catch (IOException ioe) {
                log.error("Unable to read Groovy built-in groovy-whitelist", ioe);
            }
        }

        methodsByType = methods.stream().collect(Collectors.groupingBy(Method::getDeclaringClass));

        fieldsByType = fields.stream().collect(Collectors.groupingBy(Field::getDeclaringClass));

        constructorsByType = constructors.stream().collect(Collectors.groupingBy(Constructor::getDeclaringClass));

        annotations = new HashSet<>(annotationClasses);
    }

    private static void parseDeclaration(
        String declaration,
        List<Method> methods,
        List<Field> fields,
        List<Constructor<?>> constructors,
        List<Class<?>> annotations
    ) {
        try {
            if (declaration.startsWith(WHITELIST_METHOD_PREFIX)) {
                methods.add(parseMethod(declaration));
            } else if (declaration.startsWith(WHITELIST_FIELD_PREFIX)) {
                fields.add(parseField(declaration));
            } else if (declaration.startsWith(WHITELIST_CONSTRUCTOR_PREFIX)) {
                constructors.add(parseConstructor(declaration));
            } else if (declaration.startsWith(WHITELIST_ANNOTATION_PREFIX)) {
                annotations.add(parseAnnotation(declaration));
            } else if (declaration.startsWith(WHITELIST_CLASS_PREFIX)) {
                methods.addAll(parseAllMethods(declaration));
                fields.addAll(parseAllFields(declaration));
                constructors.addAll(parseAllConstructors(declaration));
            }
        } catch (Exception e) {
            log.warn("The Groovy whitelisted declaration [{}] cannot be loaded. Message is [{}]", declaration, e.toString());
        }
    }

    private static Method parseMethod(String declaration) throws ClassNotFoundException, NoSuchMethodException {
        String[] split = declaration.split(" ");
        String clazzName = split[1];
        String methodName = split[2];
        String[] methodArgs = {};

        if (split.length > 3) {
            methodArgs = Arrays.copyOfRange(split, 3, split.length);
        }

        Class<?>[] argumentClasses = getArgumentClasses(methodArgs);

        Class<?> clazz = ClassUtils.forName(clazzName, SecuredResolver.class.getClassLoader());

        return clazz.getDeclaredMethod(methodName, argumentClasses);
    }

    private static List<Method> parseAllMethods(String declaration) throws ClassNotFoundException {
        String[] split = declaration.split(" ");
        String clazzName = split[1];
        Class<?> clazz = ClassUtils.forName(clazzName, SecuredResolver.class.getClassLoader());

        return Arrays.asList(clazz.getDeclaredMethods());
    }

    private static Field parseField(String declaration) throws ClassNotFoundException, NoSuchFieldException {
        String[] split = declaration.split(" ");
        String clazzName = split[1];
        String fieldName = split[2];
        Class<?> clazz = ClassUtils.forName(clazzName, SecuredResolver.class.getClassLoader());

        return clazz.getDeclaredField(fieldName);
    }

    private static List<Field> parseAllFields(String declaration) throws ClassNotFoundException {
        String[] split = declaration.split(" ");
        String clazzName = split[1];
        Class<?> clazz = ClassUtils.forName(clazzName, SecuredResolver.class.getClassLoader());

        return Arrays.asList(clazz.getDeclaredFields());
    }

    private static Constructor<?> parseConstructor(String declaration) throws ClassNotFoundException, NoSuchMethodException {
        String[] split = declaration.split(" ");
        String clazzName = split[1];
        String[] args = {};

        if (split.length > 2) {
            args = Arrays.copyOfRange(split, 2, split.length);
        }

        Class<?>[] argumentClasses = getArgumentClasses(args);

        Class<?> clazz = ClassUtils.forName(clazzName, SecuredResolver.class.getClassLoader());

        return clazz.getDeclaredConstructor(argumentClasses);
    }

    private static Class<?>[] getArgumentClasses(String[] args) throws ClassNotFoundException {
        Class<?>[] argumentClasses = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            argumentClasses[i] = ClassUtils.forName(args[i], SecuredResolver.class.getClassLoader());
        }
        return argumentClasses;
    }

    private static List<Constructor<?>> parseAllConstructors(String declaration) throws ClassNotFoundException {
        String[] split = declaration.split(" ");
        String clazzName = split[1];
        Class<?> clazz = ClassUtils.forName(clazzName, SecuredResolver.class.getClassLoader());

        return Arrays.asList(clazz.getDeclaredConstructors());
    }

    private static Class<?> parseAnnotation(String declaration) throws ClassNotFoundException {
        String[] split = declaration.split(" ");
        String clazzName = split[1];

        return ClassUtils.forName(clazzName, SecuredResolver.class.getClassLoader());
    }
}

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

import groovy.lang.GString;
import groovy.lang.GroovyClassLoader;
import groovy.lang.Script;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.ConstructorUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.apache.groovy.dateutil.extensions.DateUtilExtensions;
import org.apache.groovy.dateutil.extensions.DateUtilStaticExtensions;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.EncodingGroovyMethods;
import org.codehaus.groovy.runtime.StringGroovyMethods;
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
        DateUtilExtensions.class,
        DateUtilStaticExtensions.class,
    };

    private static final List<String> ALLOWED_ARRAY_NATIVE_METHODS = Arrays.asList("getAt", "putAt", "getLength");

    private static final Class<?>[] NO_ARGUMENTS = {};

    private final Whitelist whitelist;
    /**
     * Decisions already taken, keyed first by the receiving class itself. A ClassValue keys by identity, so two classes
     * sharing a name cannot inherit each other's decisions, and it holds nothing strongly, so a redeployed classloader
     * is still collected.
     */
    private final ClassValue<Map<String, Boolean>> resolved = new ClassValue<>() {
        @Override
        protected Map<String, Boolean> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    private final SandboxDiagnostics diagnostics;

    /**
     * Read on every sandbox check without holding the lock, so it must be volatile: together with the final fields of
     * the instance it publishes, this guarantees that a thread seeing a resolver sees its whitelist fully built.
     */
    private static volatile SecuredResolver instance;

    /**
     * The environment the current whitelist was built from. Kept so that a resolver rebuilt after a {@link #destroy()}
     * still honours the configured 'groovy.whitelist.list' instead of silently falling back to the built-in whitelist
     * only. It is the single source of truth: whatever the live resolver was built from is what this names.
     */
    private static volatile Environment configuredEnvironment;

    /**
     * Installs a whitelist built from the given environment. Handing over a different environment rebuilds the
     * whitelist rather than recording a configuration that would only take effect at the next {@link #destroy()}.
     * Handing over the same one again is a no-op, so repeated activations do not pay for a rebuild.
     */
    public static synchronized void initialize(@Nullable Environment environment) {
        if (instance != null && environment == configuredEnvironment) {
            return;
        }

        configuredEnvironment = environment;
        instance = new SecuredResolver(WhitelistLoader.load(environment));
    }

    public static synchronized boolean isInitialized() {
        return instance != null;
    }

    /**
     * Drops the current resolver. Nothing is cleared: a thread that already holds one keeps working against a whitelist
     * that stays complete, and the discarded resolver is collected once the last of them is done with it.
     */
    public static synchronized void destroy() {
        if (instance != null) {
            SandboxDiagnostics.destroyed(instance.diagnostics.generation());
            instance = null;
        }
    }

    public static SecuredResolver getInstance() {
        SecuredResolver current = instance;

        return current != null ? current : initializeAndGet();
    }

    private static synchronized SecuredResolver initializeAndGet() {
        if (instance == null) {
            instance = new SecuredResolver(WhitelistLoader.load(configuredEnvironment));
            SandboxDiagnostics.lazyInitialization(configuredEnvironment);
        }

        return instance;
    }

    private SecuredResolver(Whitelist whitelist) {
        this.whitelist = whitelist;
        this.diagnostics = new SandboxDiagnostics(whitelist);
    }

    public boolean isAnnotationAllowed(String name) {
        return whitelist.allowsAnnotation(name);
    }

    public boolean isConstructorAllowed(Class<?> clazz, Object... constructorArgs) {
        Map<String, Boolean> decisions = resolved.get(clazz);
        Class<?>[] argumentClasses = getClasses(constructorArgs);
        String key = memberKey("<init>", argumentClasses);
        Boolean cachedDecision = decisions.get(key);

        if (cachedDecision != null) {
            return cachedDecision;
        }

        boolean allowed =
            isGroovyScriptDefinedClass(clazz) ||
            whitelist.allows(ConstructorUtils.getMatchingAccessibleConstructor(clazz, argumentClasses));

        return remember(decisions, key, allowed);
    }

    public boolean isGetPropertyAllowed(Object object, String propertyName) {
        Class<?> objectClass = receiverClass(object);
        Map<String, Boolean> decisions = resolved.get(objectClass);
        String key = memberKey(propertyName, NO_ARGUMENTS);
        Boolean cachedDecision = decisions.get(key);

        if (cachedDecision != null) {
            return cachedDecision;
        }

        boolean allowed =
            isGroovyScriptDefinedClass(objectClass) ||
            isReadableThroughGetter(object, propertyName) ||
            whitelist.allows(FieldUtils.getDeclaredField(objectClass, propertyName));

        return remember(decisions, key, allowed);
    }

    public boolean isSetPropertyAllowed(Object object, String propertyName, Object propertyValue) {
        Class<?> objectClass = receiverClass(object);
        Map<String, Boolean> decisions = resolved.get(objectClass);
        String key = memberKey(propertyName, NO_ARGUMENTS);
        Boolean cachedDecision = decisions.get(key);

        if (cachedDecision != null) {
            return cachedDecision;
        }

        boolean allowed =
            isGroovyScriptDefinedClass(objectClass) ||
            isMethodAllowed(object, "set" + StringUtils.capitalize(propertyName), propertyValue) ||
            whitelist.allows(FieldUtils.getDeclaredField(objectClass, propertyName));

        return remember(decisions, key, allowed);
    }

    public boolean isMethodAllowed(Object object, String methodName, Object... methodArgs) {
        Class<?> objectClass = receiverClass(object);
        Map<String, Boolean> decisions = resolved.get(objectClass);
        Class<?>[] argumentClasses = getClasses(methodArgs);
        String key = memberKey(methodName, argumentClasses);
        Boolean cachedDecision = decisions.get(key);

        if (cachedDecision != null) {
            return cachedDecision;
        }

        boolean allowed =
            // Synthetic methods like Integer.plus(Integer).
            (object instanceof Number && NUMBER_MATH_METHOD_NAMES.contains(methodName)) ||
            isMethodAllowed(objectClass, methodName, argumentClasses) ||
            isDGMAllowed(objectClass, methodName, argumentClasses);

        return remember(decisions, key, allowed);
    }

    /**
     * A property is readable when either of the two getter shapes Groovy recognises is allowed.
     */
    private boolean isReadableThroughGetter(Object object, String propertyName) {
        String capitalized = StringUtils.capitalize(propertyName);

        return isMethodAllowed(object, "get" + capitalized) || isMethodAllowed(object, "is" + capitalized);
    }

    private boolean isMethodAllowed(Class<?> clazz, String methodName, Class<?>[] argumentClasses) {
        if (clazz == null) {
            return false;
        }

        Method method = getMatchingAccessibleMethod(clazz, methodName, argumentClasses);

        if (method != null && (isGroovyScriptDefinedMethod(method) || whitelist.allows(method))) {
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

            if (whitelist.allows(method)) {
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
     * Identifies a member within one class. The receiving class is the ClassValue key, so it deliberately does not
     * appear here.
     */
    private static String memberKey(String memberName, Class<?>[] argumentClasses) {
        return memberName + Arrays.toString(argumentClasses);
    }

    /**
     * The class a check applies to: a static access carries the class itself, anything else its instance.
     */
    private static Class<?> receiverClass(Object object) {
        return object instanceof Class<?> clazz ? clazz : object.getClass();
    }

    /**
     * Stores a decision and returns it, so a check can end on a single statement.
     * <p/>
     * Deliberately a get() followed by a put() rather than a computeIfAbsent(): resolving a property re-enters through
     * isMethodAllowed() on the same receiving class, hence on this very map, and a nested computeIfAbsent() on a
     * ConcurrentHashMap is rejected as a recursive update.
     */
    private static boolean remember(Map<String, Boolean> decisions, String key, boolean decision) {
        decisions.put(key, decision);

        return decision;
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
}

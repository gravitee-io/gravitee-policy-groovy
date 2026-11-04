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

import groovy.lang.GroovyRuntimeException;
import groovy.lang.MetaClass;
import groovy.lang.Script;
import io.gravitee.common.util.MultiValueMap;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.MethodClosure;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.kohsuke.groovy.sandbox.GroovyInterceptor;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SecuredInterceptor extends GroovyInterceptor {

    @Override
    public Object onMethodCall(Invoker invoker, Object receiver, String method, Object... args) throws Throwable {
        // The whitelisted Closure.call(..) would let a MethodClosure invoke an arbitrary target method, so re-check
        // the wrapped target. A raw MethodClosure is only reachable via asType (guarded below), so guarding the direct
        // call suffices; revisit if that stops holding.
        if (receiver instanceof MethodClosure methodClosure && isClosureInvocation(method)) {
            Object target = methodClosure.getOwner();
            String targetMethod = methodClosure.getMethod();

            if (SecuredResolver.getInstance().isMethodAllowed(target, targetMethod, args)) {
                return super.onMethodCall(invoker, receiver, method, args);
            }

            throw new SecurityException("Failed to resolve method [ " + prettyPrint(target, targetMethod, args) + " ]");
        }

        // asType(target) reaches the target constructor through trusted Groovy code, bypassing onNewInstance.
        if (isAsTypeToClass(method, args)) {
            guardAsTypeConstruction(receiver, (Class<?>) args[0]);
        }

        // Special case to handle HttpHeaders.set(Object, Object). Fallback to original method if generic 'set' method is not allowed (or found).
        if (
            receiver instanceof MultiValueMap &&
            method.equals("put") &&
            args.length == 2 &&
            SecuredResolver.getInstance().isMethodAllowed(receiver, "set", args)
        ) {
            return super.onMethodCall(invoker, receiver, "set", args);
        }

        if (SecuredResolver.getInstance().isMethodAllowed(receiver, method, args)) {
            return super.onMethodCall(invoker, receiver, method, args);
        }

        throw new SecurityException("Failed to resolve method [ " + prettyPrint(receiver, method, args) + " ]");
    }

    @Override
    public Object onStaticCall(Invoker invoker, Class receiver, String method, Object... args) throws Throwable {
        // Defence in depth for the static DefaultGroovyMethods.asType(source, target) form.
        if (method.equals("asType") && args.length == 2 && args[1] instanceof Class<?> target) {
            guardAsTypeConstruction(args[0], target);
        }

        if (SecuredResolver.getInstance().isMethodAllowed(receiver, method, args)) {
            return super.onStaticCall(invoker, receiver, method, args);
        }

        throw new SecurityException("Failed to resolve method [ " + prettyPrint(receiver, method, args) + " ]");
    }

    @Override
    public Object onNewInstance(Invoker invoker, Class receiver, Object... args) throws Throwable {
        if (SecuredResolver.getInstance().isConstructorAllowed(receiver, args)) {
            return super.onNewInstance(invoker, receiver, args);
        }

        throw new SecurityException("Failed to resolve constructor [" + prettyPrint(receiver, "<init>", args) + "]");
    }

    @Override
    public Object onSuperCall(Invoker invoker, Class senderType, Object receiver, String method, Object... args) throws Throwable {
        if (SecuredResolver.getInstance().isMethodAllowed(receiver, method, args)) {
            // groovy-sandbox's default super-call dispatch resolves the method against
            // senderType.getSuperclass(). Since Groovy 4 the meta method index for super calls became
            // strict (methodsForSuper(sender) only holds sender's super methods), so passing the
            // superclass fails to resolve inherited methods (MissingMethodException). Invoke the super
            // method ourselves using senderType so resolution starts at the proper superclass.
            try {
                MetaClass metaClass = InvokerHelper.getMetaClass(receiver.getClass());
                return metaClass.invokeMethod(senderType, receiver, method, args, true, true);
            } catch (GroovyRuntimeException gre) {
                throw ScriptBytecodeAdapter.unwrap(gre);
            }
        }

        throw new SecurityException("Failed to resolve method [ " + prettyPrint(receiver, method, args) + " ]");
    }

    @Override
    public void onSuperConstructor(Invoker invoker, Class receiver, Object... args) throws Throwable {
        if (SecuredResolver.getInstance().isConstructorAllowed(receiver, args)) {
            super.onSuperConstructor(invoker, receiver, args);
        } else {
            throw new SecurityException("Failed to resolve constructor [" + prettyPrint(receiver, "<init>", args) + "]");
        }
    }

    @Override
    public Object onGetProperty(Invoker invoker, Object receiver, String property) throws Throwable {
        if (receiver instanceof Script && !property.equals("binding") && !property.equals("metaClass")) {
            return super.onGetProperty(invoker, receiver, property);
        }

        if (SecuredResolver.getInstance().isGetPropertyAllowed(receiver, property)) {
            return super.onGetProperty(invoker, receiver, property);
        }

        throw new SecurityException("Failed to resolve getter method or field [" + prettyPrint(receiver, property) + "]");
    }

    @Override
    public Object onSetProperty(Invoker invoker, Object receiver, String property, Object value) throws Throwable {
        if (receiver instanceof Script && !property.equals("binding") && !property.equals("metaClass")) {
            return super.onSetProperty(invoker, receiver, property, value);
        }

        if (SecuredResolver.getInstance().isSetPropertyAllowed(receiver, property, value)) {
            return super.onSetProperty(invoker, receiver, property, value);
        }

        throw new SecurityException("Failed to resolve setter method or field [" + prettyPrint(receiver, property, value) + "]");
    }

    @Override
    public Object onGetAttribute(Invoker invoker, Object receiver, String attribute) throws Throwable {
        if (SecuredResolver.getInstance().isGetPropertyAllowed(receiver, attribute)) {
            return super.onGetAttribute(invoker, receiver, attribute);
        }

        throw new SecurityException("Failed to get field [" + prettyPrint(receiver, attribute) + "]");
    }

    @Override
    public Object onSetAttribute(Invoker invoker, Object receiver, String attribute, Object value) throws Throwable {
        if (SecuredResolver.getInstance().isSetPropertyAllowed(receiver, attribute, value)) {
            return super.onSetAttribute(invoker, receiver, attribute, value);
        }

        throw new SecurityException("Failed to set field [" + prettyPrint(receiver, attribute, value) + "]");
    }

    @Override
    public Object onGetArray(Invoker invoker, Object receiver, Object index) throws Throwable {
        if (isArrayAccess(receiver, index)) {
            return super.onGetArray(invoker, receiver, index);
        }

        return onMethodCall(invoker, receiver, "getAt", index);
    }

    @Override
    public Object onSetArray(Invoker invoker, Object receiver, Object index, Object value) throws Throwable {
        if (isArrayAccess(receiver, index)) {
            return super.onSetArray(invoker, receiver, index, value);
        }

        return onMethodCall(invoker, receiver, "putAt", index, value);
    }

    private String prettyPrint(Object receiver, String method, Object... args) {
        return (
            (receiver instanceof Class<?> ? receiver : receiver.getClass()) +
            " " +
            method +
            " " +
            Arrays.asList(args)
                .stream()
                .map(arg -> arg == null ? Object.class.getCanonicalName() : arg.getClass().getCanonicalName())
                .collect(Collectors.joining(" "))
        );
    }

    private boolean isArrayAccess(Object receiver, Object index) {
        return (receiver.getClass().isArray() || List.class.isAssignableFrom(receiver.getClass())) && index instanceof Integer;
    }

    private static boolean isClosureInvocation(String method) {
        return method.equals("call") || method.equals("doCall");
    }

    private static boolean isAsTypeToClass(String method, Object[] args) {
        return method.equals("asType") && args.length == 1 && args[0] instanceof Class<?>;
    }

    /**
     * Rejects an {@code asType} conversion that would instantiate a non-whitelisted target. Conversions that do not
     * reach an arbitrary constructor are left alone: a source already matching the target, coercion to an interface,
     * array, primitive, enum or Number / CharSequence value type, and the standard {@code java.util} collection and
     * map targets Groovy knows how to build. Any other concrete target is authorised like a {@code new}.
     */
    private void guardAsTypeConstruction(Object source, Class<?> target) {
        // No construction happens when the source already matches the target (castToType returns it unchanged).
        if (target.isInstance(source)) {
            return;
        }

        if (reachesConstructorViaAsType(target) && !SecuredResolver.getInstance().isAsTypeConstructionAllowed(target)) {
            throw new SecurityException("Failed to resolve constructor [" + prettyPrint(target, "<init>", source) + "]");
        }
    }

    private static boolean reachesConstructorViaAsType(Class<?> target) {
        return !(
            target == null ||
            target.isInterface() ||
            target.isArray() ||
            target.isPrimitive() ||
            target.isEnum() ||
            Number.class.isAssignableFrom(target) ||
            CharSequence.class.isAssignableFrom(target) ||
            Boolean.class == target ||
            Character.class == target ||
            isStandardJdkCollection(target)
        );
    }

    private static boolean isStandardJdkCollection(Class<?> target) {
        return (
            (Collection.class.isAssignableFrom(target) || Map.class.isAssignableFrom(target)) && "java.util".equals(target.getPackageName())
        );
    }
}

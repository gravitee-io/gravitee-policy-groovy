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

import groovy.lang.Script;
import io.gravitee.common.util.MultiValueMap;
import org.kohsuke.groovy.sandbox.GroovyInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SecuredInterceptor extends GroovyInterceptor {


    @Override
    public Object onMethodCall(Invoker invoker, Object receiver, String method, Object... args) throws Throwable {

        // Special case to handle HttpHeaders.set(Object, Object). Fallback to original method if generic 'set' method is not allowed (or found).
        if (receiver instanceof MultiValueMap && method.equals("put") && args.length == 2
                && SecuredResolver.getInstance().isMethodAllowed(receiver, "set", args)) {
            return super.onMethodCall(invoker, receiver, "set", args);
        }

        if (SecuredResolver.getInstance().isMethodAllowed(receiver, method, args)) {
            return super.onMethodCall(invoker, receiver, method, args);
        }

        throw new SecurityException("Failed to resolve method [ " + prettyPrint(receiver, method, args) + " ]");
    }

    @Override
    public Object onStaticCall(Invoker invoker, Class receiver, String method, Object... args) throws Throwable {

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
            return super.onSuperCall(invoker, senderType, receiver, method, args);
        }

        throw new SecurityException("Failed to resolve method [ " + prettyPrint(receiver, method, args) + " ]");
    }

    @Override
    public void onSuperConstructor(Invoker invoker, Class receiver, Object... args) throws Throwable {

        if (SecuredResolver.getInstance().isConstructorAllowed(receiver, args)) {
            super.onSuperConstructor(invoker, receiver, args);
        }else {
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

        return (receiver instanceof Class<?> ? receiver : receiver.getClass()) + " " + method + " " + Arrays.asList(args).stream().map(arg -> arg == null ? Object.class.getCanonicalName() : arg.getClass().getCanonicalName()).collect(Collectors.joining(" "));
    }

    private boolean isArrayAccess(Object receiver, Object index) {

        return (receiver.getClass().isArray() || List.class.isAssignableFrom(receiver.getClass())) && index instanceof Integer;
    }
}

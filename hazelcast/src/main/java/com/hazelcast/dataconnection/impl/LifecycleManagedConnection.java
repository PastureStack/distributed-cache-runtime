/*
 * Copyright (c) 2008-2026, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.dataconnection.impl;

import com.hazelcast.core.HazelcastException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adds a lifecycle callback to a standard JDBC connection without duplicating
 * every JDBC method in a forwarding class. Query text remains owned by the
 * original caller, so static analysis can report an unsafe construction at its
 * real source instead of at an unrelated forwarding boundary.
 */
final class LifecycleManagedConnection {

    private LifecycleManagedConnection() {
    }

    static Connection wrap(Connection delegate, Runnable releaseAction) {
        AtomicBoolean released = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("equals".equals(name) && method.getParameterCount() == 1) {
                        return proxy == args[0];
                    }
                    if ("hashCode".equals(name) && method.getParameterCount() == 0) {
                        return System.identityHashCode(proxy);
                    }
                    if ("close".equals(name) && method.getParameterCount() == 0) {
                        if (!released.compareAndSet(false, true)) {
                            return null;
                        }
                        try {
                            return invoke(delegate, method, args);
                        } finally {
                            releaseAction.run();
                        }
                    }
                    return invoke(delegate, method, args);
                });
    }

    private static Object invoke(Connection delegate, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause == null) {
                throw new HazelcastException("Could not invoke JDBC connection method", e);
            }
            throw cause;
        }
    }
}

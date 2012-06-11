/*
 * Copyright 2012 samson.ph.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ph.samson.seppugu;

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.util.Types;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import javax.xml.ws.Service;
import javax.xml.ws.WebEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the binding for a JAX-WS Service Endpoint Interface.
 *
 * @author Edward Samson <https://github.com/esamson>
 */
public class SeiProxyModule extends AbstractModule {

    private static final Logger log =
            LoggerFactory.getLogger(SeiProxyModule.class);
    private final Class<? extends Service> serviceClass;

    /**
     * 
     * @param serviceClass the {@link Service} type that will be used as a
     *      factory for SEI proxy instances.
     */
    public SeiProxyModule(Class<? extends Service> serviceClass) {
        this.serviceClass = serviceClass;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void configure() {
        log.debug("Configuring JAX-WS client binding for {}", serviceClass);

        LinkedHashSet<Class<?>> proxyTypes = new LinkedHashSet<Class<?>>();
        for (Method m : serviceClass.getMethods()) {
            if (m.isAnnotationPresent(WebEndpoint.class)) {
                Class<?> type = m.getReturnType();
                log.debug("WebEndpoint method {} returns type {}", m, type);
                proxyTypes.add(type);
            }
        }

        if (proxyTypes.isEmpty()) {
            addError("No WebEndpoint methods in service %s", serviceClass);
            return;
        }

        if (proxyTypes.size() > 1) {
            /*
             * This is considered an error scenario for now bacause I have not
             * seen a valid example of two WebEndpoint methods returning
             * different types. If there is such a case, then we'll have to
             * cover it by giving the user a configuration option.
             */
            addError("%s returns multiple proxy types: %s",
                    serviceClass, proxyTypes);
            return;
        }

        @SuppressWarnings("rawtypes")
        Class proxyType = proxyTypes.iterator().next();
        bind(proxyType).toProvider(Key.get(Types.newParameterizedType(
                SeiProxyProvider.class, serviceClass, proxyType)));
    }
}

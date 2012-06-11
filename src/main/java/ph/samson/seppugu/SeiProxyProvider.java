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

import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;
import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Service;
import javax.xml.ws.WebEndpoint;
import javax.xml.ws.WebServiceClient;
import javax.xml.ws.WebServiceFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.inject.name.Names.named;
import static javax.xml.ws.BindingProvider.*;

/**
 * A Guice provider that uses reflection to create SEI instances.
 *
 * @author Edward Samson <https://github.com/esamson>
 */
class SeiProxyProvider<S extends Service, P> implements Provider<P> {

    private static final Logger log =
            LoggerFactory.getLogger(SeiProxyProvider.class);
    private final Class<S> serviceClass;
    private final Class<P> proxyClass;
    private final URL wsdlLocation;
    private final WebServiceFeature[] features;
    private final QName serviceName; // TODO: implement case for using QName
    private final String endpointName;
    private final String endpointUrl;

    @Inject
    public SeiProxyProvider(TypeLiteral<S> service, TypeLiteral<P> proxy,
            Injector injector) {
        @SuppressWarnings("unchecked")
        Class<S> sClass = (Class<S>) service.getRawType();
        this.serviceClass = sClass;
        if (!serviceClass.isAnnotationPresent(WebServiceClient.class)) {
            throw new IllegalArgumentException(serviceClass
                    + " is not annotated with @" + WebServiceClient.class);
        }
        @SuppressWarnings("unchecked")
        Class<P> pClass = (Class<P>) proxy.getRawType();
        this.proxyClass = pClass;
        if (!proxyClass.isAnnotationPresent(WebService.class)) {
            throw new IllegalArgumentException(proxyClass
                    + " is not annotated with @" + WebService.class);
        }

        Binding<URL> urlBinding = injector.getExistingBinding(Key.get(
                URL.class, named(getWsdlLocationProperty(serviceClass))));
        wsdlLocation = urlBinding != null ? urlBinding.getProvider().get() : null;

        Binding<WebServiceFeature[]> wsfBinding = injector.getExistingBinding(
                Key.get(WebServiceFeature[].class, named(serviceClass.getName())));
        features = wsfBinding != null ? wsfBinding.getProvider().get() : null;

        Binding<QName> qnBinding = injector.getExistingBinding(
                Key.get(QName.class, named(serviceClass.getName())));
        serviceName = qnBinding != null ? qnBinding.getProvider().get() : null;

        Binding<String> endpointNameB = injector.getExistingBinding(
                Key.get(String.class, named(getEndpointNameProperty(serviceClass))));
        endpointName = endpointNameB != null ? endpointNameB.getProvider().get() : null;

        Binding<String> endpointUrlB = injector.getExistingBinding(
                Key.get(String.class, named(getEndpointUrlProperty(serviceClass))));
        endpointUrl = endpointUrlB != null ? endpointUrlB.getProvider().get() : null;
    }

    static <S extends Service> String getWsdlLocationProperty(Class<S> sClass) {
        return sClass.getName() + ".wsdl";
    }

    static <S extends Service> String getEndpointNameProperty(Class<S> sClass) {
        return sClass.getName() + ".endpoint.name";
    }

    static <S extends Service> String getEndpointUrlProperty(Class<S> sClass) {
        return sClass.getName() + ".endpoint.url";
    }

    @Override
    public P get() {
        S service = getService();
        Method factory = getProxyFactoryMethod();

        P proxy;
        try {
            if (factory.getParameterTypes().length == 0) {
                @SuppressWarnings("unchecked")
                P p = (P) factory.invoke(service);
                proxy = p;
            } else {
                @SuppressWarnings("unchecked")
                P p = (P) factory.invoke(service, (Object) features);
                proxy = p;
            }
        } catch (IllegalAccessException ex) {
            throw new ProvisionException(
                    "Could not access factory method " + factory, ex);
        } catch (IllegalArgumentException ex) {
            throw new ProvisionException(
                    "Illegal factory method argument.", ex);
        } catch (InvocationTargetException ex) {
            throw new ProvisionException(
                    "Factory method threw an exception.", ex);
        }

        Map<String, Object> ctx = ((BindingProvider) proxy).getRequestContext();
        if (endpointUrl != null) {
            log.info("{} endpoint URL set to {}", proxyClass, endpointUrl);
            ctx.put(ENDPOINT_ADDRESS_PROPERTY, endpointUrl);
        } else {
            log.info("No endpoint URL defined for {}. Using default: {}",
                    proxyClass, ctx.get(ENDPOINT_ADDRESS_PROPERTY));
        }

        return proxy;
    }

    S getService() {
        return getServiceUsingUrlAndFeaturesConstructor();
    }

    S getServiceUsingDefaultConstructor() {
        Constructor<S> constructor;
        try {
            constructor = serviceClass.getConstructor();
        } catch (NoSuchMethodException ex) {
            throw new ProvisionException(
                    "No default constructor for " + serviceClass, ex);
        } catch (SecurityException ex) {
            throw new ProvisionException(
                    "Could not access default constructor for " + serviceClass,
                    ex);
        }

        try {
            return constructor.newInstance();
        } catch (InstantiationException ex) {
            throw new ProvisionException(
                    serviceClass + " is an abstract class", ex);
        } catch (IllegalAccessException ex) {
            throw new ProvisionException("Could not access" + constructor, ex);
        } catch (IllegalArgumentException ex) {
            throw new ProvisionException("Illegal constructor argument", ex);
        } catch (InvocationTargetException ex) {
            throw new ProvisionException(
                    constructor + " threw an exception", ex);
        }
    }

    S getServiceUsingFeaturesConstructor() {
        if (features == null) {
            return getServiceUsingDefaultConstructor();
        }

        Constructor<S> constructor;
        try {
            constructor = serviceClass.getConstructor(
                    WebServiceFeature[].class);
        } catch (NoSuchMethodException ex) {
            log.warn("No {}(WebServiceFeature... features) constructor."
                    + " Using default constructor.", serviceClass, ex);
            return getServiceUsingDefaultConstructor();
        } catch (SecurityException ex) {
            throw new ProvisionException(
                    "Could not access default constructor for " + serviceClass,
                    ex);
        }

        try {
            return constructor.newInstance((Object) features);
        } catch (InstantiationException ex) {
            throw new ProvisionException(
                    serviceClass + " is an abstract class", ex);
        } catch (IllegalAccessException ex) {
            throw new ProvisionException("Could not access" + constructor, ex);
        } catch (IllegalArgumentException ex) {
            throw new ProvisionException("Illegal constructor argument", ex);
        } catch (InvocationTargetException ex) {
            throw new ProvisionException(
                    constructor + " threw an exception", ex);
        }
    }

    S getServiceUsingUrlConstructor() {
        if (wsdlLocation == null) {
            return getServiceUsingDefaultConstructor();
        }

        Constructor<S> constructor;
        try {
            constructor = serviceClass.getConstructor(URL.class);
        } catch (NoSuchMethodException ex) {
            log.warn("No {}(URL wsdlLocation) constructor."
                    + " Using default constructor.", serviceClass, ex);
            return getServiceUsingDefaultConstructor();
        } catch (SecurityException ex) {
            throw new ProvisionException(
                    "Could not access default constructor for " + serviceClass,
                    ex);
        }

        try {
            return constructor.newInstance(wsdlLocation);
        } catch (InstantiationException ex) {
            throw new ProvisionException(
                    serviceClass + " is an abstract class", ex);
        } catch (IllegalAccessException ex) {
            throw new ProvisionException("Could not access" + constructor, ex);
        } catch (IllegalArgumentException ex) {
            throw new ProvisionException("Illegal constructor argument", ex);
        } catch (InvocationTargetException ex) {
            throw new ProvisionException(
                    constructor + " threw an exception", ex);
        }
    }

    S getServiceUsingUrlAndFeaturesConstructor() {
        if (wsdlLocation == null && features == null) {
            return getServiceUsingDefaultConstructor();
        } else if (wsdlLocation == null) {
            return getServiceUsingFeaturesConstructor();
        } else if (features == null) {
            return getServiceUsingUrlConstructor();
        }

        Constructor<S> constructor;
        try {
            constructor = serviceClass.getConstructor(URL.class,
                    WebServiceFeature[].class);
        } catch (NoSuchMethodException ex) {
            log.warn("No {}(URL wsdlLocation, WebServiceFeature... features)"
                    + " constructor."
                    + " Using default constructor.", serviceClass, ex);
            return getServiceUsingDefaultConstructor();
        } catch (SecurityException ex) {
            throw new ProvisionException(
                    "Could not access default constructor for " + serviceClass,
                    ex);
        }

        try {
            return constructor.newInstance(wsdlLocation, features);
        } catch (InstantiationException ex) {
            throw new ProvisionException(
                    serviceClass + " is an abstract class", ex);
        } catch (IllegalAccessException ex) {
            throw new ProvisionException("Could not access" + constructor, ex);
        } catch (IllegalArgumentException ex) {
            throw new ProvisionException("Illegal constructor argument", ex);
        } catch (InvocationTargetException ex) {
            throw new ProvisionException(
                    constructor + " threw an exception", ex);
        }
    }

    QName getServiceName() {
        WebServiceClient webServiceClient =
                serviceClass.getAnnotation(WebServiceClient.class);
        String targetNamespace = webServiceClient.targetNamespace();
        String clientName = webServiceClient.name();
        return new QName(targetNamespace, clientName);
    }

    Method getProxyFactoryMethod() {
        Method method = null;

        ArrayList<Method> factories = new ArrayList<Method>();
        for (Method m : serviceClass.getMethods()) {
            if (m.isAnnotationPresent(WebEndpoint.class)) {
                if (endpointName == null) {
                    factories.add(m);
                } else {
                    WebEndpoint we = m.getAnnotation(WebEndpoint.class);
                    if (endpointName.equals(we.name())) {
                        factories.add(m);
                    }
                }
            }
        }

        if (factories.isEmpty()) {
            if (endpointName == null) {
                throw new ProvisionException("No methods in " + serviceClass
                        + " annotated with @" + WebEndpoint.class);
            }

            throw new ProvisionException("No methods in " + serviceClass
                    + " annotated with @" + WebEndpoint.class
                    + "(name = \"" + endpointName + "\")");
        }

        if (features != null) {
            /*
             * look for factory method which accepts 
             * (WebServiceFeature... features)
             * 
             */
            for (Method factory : factories) {
                Class<?>[] params = factory.getParameterTypes();
                if (params.length == 1
                        && WebServiceFeature[].class.equals(params[0])) {
                    method = factory;
                    break;
                }
            }
        }

        if (method == null) {
            /*
             * look for factory method with no arguments
             */
            for (Method factory : factories) {
                if (factory.getParameterTypes().length == 0) {
                    method = factory;
                    break;
                }
            }
        }

        if (method == null) {
            throw new ProvisionException("No proxy factory method found in "
                    + serviceClass);
        }

        Class<?> returnType = method.getReturnType();
        if (!proxyClass.isAssignableFrom(returnType)) {
            throw new ProvisionException(serviceClass + " factory method ("
                    + method + ") does not return proxy objects of type "
                    + proxyClass);
        }

        log.debug("proxy factory method: {}", method);
        return method;
    }
}

# SePPuGu

## A JAX-WS Service Endpoint Interface Provider for Guice

*SePPuGu* allows you to use [Guice](http://code.google.com/p/google-guice/) to
easily inject JAX-WS Service Endpoint Interface proxy instances.

### Getting Started

Before you start using *SePPuGu*, you must already have generated your JAX-WS
artifacts with the
 [wsimport](http://jax-ws.java.net/2.2.6-2/docs/ch04.html#tools-wsimport) tool.
If you haven't, yet, go do so now. I'll wait. (There's also a
 [maven plugin](http://jax-ws-commons.java.net/jaxws-maven-plugin/wsimport-mojo.html)
available to do the same).

Once you've done that, **wsimport** should have generated for you:

* [Service](http://jax-ws.java.net/nonav/jaxws-api/2.2/javax/xml/ws/Service.html)
* Service Endpoint Interface (SEI)

and a few other things like JAXB beans, etc. Now we can begin.

### Usage

The main purpose of *SePPuGu* is to avoid directly using **Service** instances
as **SEI** proxy factories in your code. Instead, you will have Guice inject
**SEI** proxy instances for you.

#### Before

Without *SePPuGu*, you will usually use your generated JAX-WS artifacts like so:

    public class MyWsClient {

        public void doCall() {
            MyWebService service = new MyWebService();
            IWebService sei = service.getPortSei();

            // now we have an SEI proxy instance we can use
            sei.call();
        }
    }

#### After

Using *SePPuGu*, Guice will inject the **SEI** proxy instance for you.

    public class MyWsClient {

        @Inject
        private IWebService sei;

        public void doCall() {
            // now we have an SEI instance we can use
            sei.call();
        }
    }

How does Guice know how to create **SEI** proxy instances? You do so using
`SeiProxyModule` when initializing the Guice `Injector`.

    public class App {

        public static void main(String[] args) {
            Injector i = Guice.createInjector(
                    new SeiProxyModule(MyWebService.class));

            MyWsClient client = i.getInstance(MyWsClient.class);
            client.doCall();
        }
    }

This, of course, applies to any other usual place where Guice initialization
goes. If you aren't familiar with Guice,
 [get started there](http://code.google.com/p/google-guice/wiki/Motivation).

### Compatibility

*SePPuGu* has been tested with JAX-WS artifacts generated using the
 [JAX-WS Maven Plugin](http://jax-ws-commons.java.net/jaxws-maven-plugin/) but
I imagine it should work just as well for artifacts from any other JAX-WS
implementation. If it doesn't work for you, please do file a bug.


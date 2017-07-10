package io.vertx.openshift.http2;

import io.grpc.ManagedChannel;
import io.grpc.examples.helloworld.GreeterGrpc;
import io.grpc.examples.helloworld.HelloRequest;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.grpc.VertxChannelBuilder;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class EdgeVerticle extends AbstractVerticle {


  private WebClient client;

  @Override
  public void start() throws Exception {

    client = WebClient.create(vertx, new WebClientOptions().setProtocolVersion(HttpVersion.HTTP_2));

    Router router = Router.router(vertx);
    router.get("/").handler(this::hello);
    router.get("/health").handler(rc -> rc.response().end("OK"));
    router.get("/aloha").handler(this::front);
    router.get("/hello").handler(this::grpc);

    HttpServer server =
      vertx.createHttpServer(new HttpServerOptions());

    server.requestHandler(router::accept)
      .listen(8081);
  }

  private void grpc(RoutingContext rc) {
    ManagedChannel channel = VertxChannelBuilder.forAddress(vertx, "hello", 80)
      .useSsl(options -> {
          options
            .setSsl(true)
            .setUseAlpn(true)
            .setTrustAll(true);
        }
      )
      .build();

    GreeterGrpc.GreeterVertxStub stub = GreeterGrpc.newVertxStub(channel);
    HelloRequest request = HelloRequest.newBuilder().setName("OpenShift").build();
    System.out.println("Sending request...");
    stub.sayHello(request, asyncResponse -> {
      System.out.println("Got result");
      if (asyncResponse.succeeded()) {
        System.out.println("Succeeded " + asyncResponse.result().getMessage());
        rc.response().end(asyncResponse.result().getMessage());
      } else {
        rc.fail(asyncResponse.cause());
      }
    });
  }

  private void hello(RoutingContext rc) {
    rc.response().end("Aloha " + rc.request().version());
  }

  private void front(RoutingContext rc) {
    client.get(80, "aloha", "/")
      .send(resp -> {
        if (resp.succeeded()) {
          rc.response().end(resp.result().body() + " " + rc.request().version());
        } else {
          rc.fail(resp.cause());
        }
      });
  }
}
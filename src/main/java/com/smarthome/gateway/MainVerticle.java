package com.smarthome.gateway;

import discovery.DiscoveryManager;
import http.DevicesHealth;
import http.Registration;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.rest.ServiceDiscoveryRestEndpoint;
import mqtt.MqttManager;

public class MainVerticle extends AbstractVerticle {
  private final Logger logger = LoggerFactory.getLogger(MainVerticle.class);
  private ServiceDiscovery serviceDiscovery;
  private Registration registration;
  private DevicesHealth devicesHealth;
  private MqttManager mqttManager;

  @Override
  public void stop(Promise<Void> stopPromise) throws Exception {
    stopPromise.complete();
    serviceDiscovery.close();
    mqttManager.getMqttClient().disconnect();
  }

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    serviceDiscovery = DiscoveryManager.initializeServiceDiscovery(vertx);
    registration = new Registration(serviceDiscovery);
    mqttManager = new MqttManager();
    mqttManager.startAndConnectMqttClient(vertx);
    devicesHealth = new DevicesHealth(serviceDiscovery, WebClient.create(vertx), mqttManager.getMqttClient());

    vertx.setPeriodic(5000, devicesHealth::checkServices);

    Router router = Router.router(vertx);
    vertx.createHttpServer()
      .requestHandler(router)
      .listen(9090);

    router.post("/register")
      .consumes("application/json")
      .handler(registration::jwtHandler)
      .handler(BodyHandler.create())
      .handler(registration::register);

    ServiceDiscoveryRestEndpoint.create(router, serviceDiscovery);
  }

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new MainVerticle());
  }
}

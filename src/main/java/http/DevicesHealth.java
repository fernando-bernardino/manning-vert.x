package http;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.mqtt.MqttClient;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;

import java.util.Optional;

public class DevicesHealth {
  private final Logger logger = LoggerFactory.getLogger(DevicesHealth.class);

  private final ServiceDiscovery discovery;
  private final String mqttTopic;
  private final WebClient webClient;
  private final MqttClient mqttClient;

  public DevicesHealth(ServiceDiscovery discovery, WebClient webClient, MqttClient mqttClient) {
    this.discovery = discovery;
    this.webClient = webClient;
    this.mqttClient = mqttClient;
    this.mqttTopic = Optional.ofNullable(System.getenv("MQTT_TOPIC")).orElse("house");
  }

  public void checkServices(Long aLong) {
    discovery.getRecords(r -> true, ar -> {
      if (ar.succeeded()) {
        logger.info("Getting reading for " + ar.result().size() + " services");
        ar.result().forEach(this::getStatus);
      } else {
        logger.error("Lookup failed", ar.cause());
      }
    });
  }

  private void getStatus(Record record) {
    JsonObject location = record.getLocation();
    webClient.get(location.getInteger("port"), location.getString("host"), location.getString("root"))
    .ssl(false)
    .putHeader("Accept", "application/json")
    .as(BodyCodec.string())
    .send()
      .onSuccess(r -> {
        logger.info("Successfully got response from " + record.getName());
        updateStatus(r.body());
      })
      .onFailure(r -> {
        logger.warn("Failed to reach device " + record.getName(), r.getCause());
        unregister(record);
      });
  }

  private void updateStatus(String object) {
    mqttClient.publish(mqttTopic, Buffer.buffer(object), MqttQoS.AT_LEAST_ONCE, false, false);
  }

  private void unregister(Record record) {
    discovery.unpublish(record.getRegistration());
  }
}

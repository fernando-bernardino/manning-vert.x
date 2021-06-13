package mqtt;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.messages.MqttConnAckMessage;

import java.util.Optional;

public class MqttManager {
  private final Logger logger = LoggerFactory.getLogger(MqttManager.class);

  private MqttClient mqttClient;
  private CircuitBreaker breaker;

  public MqttClient getMqttClient() {
    return mqttClient;
  }

  // get a circuit breaker
  private CircuitBreaker getBreaker(Vertx vertx) {
    if(breaker == null) {
      breaker =  CircuitBreaker
        .create("mqtt-client-breaker", vertx, circuitBreakerOptions())
        .retryPolicy(retryCount -> retryCount * 100L);

    }
    return breaker;
  }

  // create and connect the MQTT client "in" a Circuit Breaker
  public Future<MqttConnAckMessage> startAndConnectMqttClient(Vertx vertx) {
    var mqttClientId = Optional.ofNullable(System.getenv("MQTT_CLIENT_ID")).orElse("gateway");
    MqttClientOptions options = new MqttClientOptions();
    options.setClientId(mqttClientId);
    options.setSsl(false);

    var mqttPort = Integer.parseInt(Optional.ofNullable(System.getenv("MQTT_PORT")).orElse("1883"));
    var mqttHost = Optional.ofNullable(System.getenv("MQTT_HOST")).orElse("10.134.122.223");

    return getBreaker(vertx).execute(promise -> {
      mqttClient = MqttClient.create(vertx, options);
      // connect the mqttClient
      mqttClient.connect(mqttPort, mqttHost, s -> {
        if (s.succeeded()) {
          logger.info("Successfully connect to MQTT server");
          promise.complete();
        } else {
          logger.warn("Failed to connect to MQTT server", s.cause());
          promise.fail(s.cause());
        }
      });
    });
  }

  private CircuitBreakerOptions circuitBreakerOptions() {
    return new CircuitBreakerOptions()
      .setMaxFailures(5)
      .setMaxRetries(0)
      .setTimeout(5000)
      .setResetTimeout(10_000);
  }
}


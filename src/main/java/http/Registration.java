package http;

import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.servicediscovery.Record;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.types.HttpEndpoint;

import java.util.Optional;

public class Registration {
  private final Logger logger = LoggerFactory.getLogger(Registration.class);

  private final ServiceDiscovery discovery;


  public Registration(ServiceDiscovery discovery) {
    this.discovery = discovery;
  }

  public void jwtHandler(RoutingContext routingContext) {
    String token = routingContext.request().getHeader("smart-token");
    if ("smart.home".equals(token)) {
      routingContext.next();
    } else {
      routingContext.response().setStatusCode(403).end();
    }
  }
  public Optional<RegistrationData> parseMessage(RoutingContext routingContext) {
    JsonObject payload = routingContext.getBodyAsJson();
    String id = payload.getString("id");
    String host = payload.getString("host");
    Integer port = payload.getInteger("port");

    if (id == null || host == null || port == null) {
      return Optional.empty();
    }

    RegistrationData data = new RegistrationData();
    data.setId(id);
    data.setHost(host);
    data.setPort(port);
    data.setCategory(payload.getString("category"));
    data.setPosition(payload.getString("position"));

    return Optional.of(data);
  }

  public void register(RoutingContext routingContext) {
    parseMessage(routingContext)
      .map(this::toRecord)
      .ifPresentOrElse(record ->
        publishDevice(record,
          () -> routingContext.response().end("Device registered successfully"),
          () -> routingContext.response().setStatusCode(500).end("Failed to register device")),
        () -> routingContext.response().setStatusCode(400).end("Id, host and port are required for registration"));
  }

  private void publishDevice(Record record, Runnable success, Runnable failure) {
    discovery.publish(record, ar -> {
      if (ar.succeeded()) {
        // publication succeeded
        Record publishedRecord = ar.result();
        success.run();
      } else {
        // publication failed
        logger.error("Failed to register device", ar.cause());
        failure.run();
      }
    });
  }

  private Record toRecord(RegistrationData data) {
    return HttpEndpoint.createRecord(
      data.getId(),
      data.getHost(),
      data.getPort(),
      "/",
      new JsonObject()
        .put("category", data.getCategory())
        .put("position", data.getPosition()));
  }

}

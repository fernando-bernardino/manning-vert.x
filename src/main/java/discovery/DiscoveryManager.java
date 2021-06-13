package discovery;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.servicediscovery.ServiceDiscovery;
import io.vertx.servicediscovery.ServiceDiscoveryOptions;

import java.util.Optional;

public class DiscoveryManager {
  public static ServiceDiscovery initializeServiceDiscovery(Vertx vertx) {
    var redisHost = Optional.ofNullable(System.getenv("REDIS_HOST")).orElse("localhost");
    return ServiceDiscovery
      .create(vertx, new ServiceDiscoveryOptions()
        .setBackendConfiguration(
          new JsonObject()
            .put("connectionString", String.format("redis://%s:6379", redisHost))
            .put("key", "records")
        ));
  }
}

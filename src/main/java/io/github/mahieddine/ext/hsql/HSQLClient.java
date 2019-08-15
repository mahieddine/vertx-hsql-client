package io.github.mahieddine.ext.hsql;

import io.github.mahieddine.ext.hsql.impl.HSQLClientImpl;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.sql.Connection;
import java.util.UUID;

public interface HSQLClient extends AutoCloseable {

    /**
     * The name of the default data source
     */
    String DEFAULT_DS_NAME = "DEFAULT_HSQL_DS";

    /**
     * Create a JDBC client which maintains its own data source.
     *
     * @param vertx  the Vert.x instance
     * @param config the configuration
     * @return {@link HSQLClient}
     */
    static HSQLClient createNonShared(Vertx vertx, JsonObject config) {
        return new HSQLClientImpl(vertx, config, UUID.randomUUID().toString());
    }

    /**
     * Create a JDBC client which shares its data source with any other JDBC clients created with the same
     * data source name
     *
     * @param vertx          the Vert.x instance
     * @param config         the configuration
     * @param dataSourceName the datasource name
     * @return {@link HSQLClient}
     */
    static HSQLClient createShared(Vertx vertx, JsonObject config, String dataSourceName) {
        return new HSQLClientImpl(vertx, config, dataSourceName);
    }

    /**
     * Like {@link #createShared(Vertx, JsonObject, String)} but with the default data source name
     *
     * @param vertx  the Vert.x instance
     * @param config the configuration
     * @return {@link HSQLClient}
     */
    static HSQLClient createShared(Vertx vertx, JsonObject config) {
        return new HSQLClientImpl(vertx, config, DEFAULT_DS_NAME);
    }

    /**
     * @param handler the handler to call once the connection retrieved
     * @return {@link HSQLClient}
     */
    HSQLClient getConnection(Handler<AsyncResult<Connection>> handler);

    /**
     * Close the client and release all resources.
     * Call the handler when close is complete.
     *
     * @param handler the handler that will be called when close is complete
     */
    void close(Handler<AsyncResult<Void>> handler);

    /**
     * Close the client
     */
    void close();

}

package io.github.mahieddine.ext.hsql.impl;

import io.github.mahieddine.ext.hsql.HSQLClient;
import io.vertx.core.*;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.Shareable;
import io.vertx.core.spi.metrics.PoolMetrics;
import io.vertx.ext.jdbc.spi.DataSourceProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class HSQLClientImpl implements HSQLClient {

    private static final String DS_LOCAL_MAP_NAME = "__vertx.HSQLClient.hsql.datasources";

    private final Vertx vertx;
    private final ConnectionHolder holder;
    private final ExecutorService exec;
    private final JsonObject config;

    public HSQLClientImpl(Vertx vertx, JsonObject config, String datasourceName) {
        Objects.requireNonNull(vertx);
        Objects.requireNonNull(config);
        Objects.requireNonNull(datasourceName);
        this.vertx = vertx;
        this.holder = lookupHolder(datasourceName, config);
        this.exec = holder.exec();
        this.config = config;
        setupCloseHook();
    }

    private void setupCloseHook() {
        Context ctx = Vertx.currentContext();
        if (ctx != null && ctx.owner() == vertx) {
            ctx.addCloseHook(holder::close);
        }
    }

    @Override
    public HSQLClient getConnection(Handler<AsyncResult<Connection>> handler) {
        Context ctx = vertx.getOrCreateContext();
        exec.execute(() -> {
            Future<Connection> res = Future.future();
            try {
                Connection connection = DriverManager.getConnection("jdbc:hsqldb:" + config.getString("url"),
                        config.getString("username"),
                        config.getString("password"));
                res.complete(connection);
            } catch (SQLException e) {
                res.fail(e);
            }

            ctx.runOnContext(v -> res.setHandler(handler));
        });
        return this;
    }

    private ConnectionHolder lookupHolder(String datasourceName, JsonObject config) {
        synchronized (vertx) {
            LocalMap<String, ConnectionHolder> map = vertx.sharedData().getLocalMap(DS_LOCAL_MAP_NAME);
            ConnectionHolder theHolder = map.get(datasourceName);
            if (theHolder == null) {
                theHolder = new ConnectionHolder((VertxInternal) vertx, config, map, datasourceName);
            } else {
                theHolder.incRefCount();
            }
            return theHolder;
        }
    }

    @Override
    public void close(Handler<AsyncResult<Void>> completionHandler) {
        holder.close(completionHandler);
    }


    @Override
    public void close() {
        holder.close(null);
    }

    private class ConnectionHolder implements Shareable {

        private final VertxInternal vertx;
        private final LocalMap<String, ConnectionHolder> map;
        DataSourceProvider provider;
        JsonObject config;
        DataSource ds;
        PoolMetrics metrics;
        ExecutorService exec;
        private int refCount = 1;
        private final String name;

        ConnectionHolder(VertxInternal vertx, JsonObject config, LocalMap<String, ConnectionHolder> map, String name) {
            this.config = config;
            this.map = map;
            this.vertx = vertx;
            this.name = name;

            map.put(name, this);
        }

        synchronized DataSource ds() {
            if (ds == null) {

                String providerClass = "org.hsqldb.jdbc.JDBCDriver";

                if (Thread.currentThread().getContextClassLoader() != null) {
                    try {
                        // Try with the TCCL
                        Class clazz = Thread.currentThread().getContextClassLoader().loadClass(providerClass);
                        provider = (DataSourceProvider) clazz.newInstance();
                        ds = provider.getDataSource(config);
                        int poolSize = provider.maximumPoolSize(ds, config);
                        metrics = vertx.metricsSPI().createMetrics(ds, "datasource", name, poolSize);
                        return ds;
                    } catch (ClassNotFoundException e) {
                        // Next try.
                    } catch (InstantiationException | SQLException | IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }

                try {
                    // Try with the classloader of the current class.
                    Class clazz = this.getClass().getClassLoader().loadClass(providerClass);
                    provider = (DataSourceProvider) clazz.newInstance();
                    ds = provider.getDataSource(config);
                    int poolSize = provider.maximumPoolSize(ds, config);
                    metrics = vertx.metricsSPI().createMetrics(ds, "datasource", name, poolSize);
                    return ds;
                } catch (ClassNotFoundException | InstantiationException | SQLException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }

            return ds;
        }

        synchronized ExecutorService exec() {
            if (exec == null) {
                exec = new ThreadPoolExecutor(1, 1,
                        1000L, TimeUnit.MILLISECONDS,
                        new LinkedBlockingQueue<>(),
                        (r -> new Thread(r, "vertx-jdbc-service-get-connection-thread")));
            }
            return exec;
        }

        void incRefCount() {
            refCount++;
        }

        void close(Handler<AsyncResult<Void>> completionHandler) {
            synchronized (vertx) {
                if (--refCount == 0) {
                    if (metrics != null) {
                        metrics.close();
                    }
                    Future<Void> f1 = Future.future();
                    Future<Void> f2 = Future.future();
                    if (completionHandler != null) {
                        CompositeFuture.all(f1, f2).<Void>map(f -> null).setHandler(completionHandler);
                    }
                    if (provider != null) {
                        vertx.executeBlocking(future -> {
                            try {
                                provider.close(ds);
                                future.complete();
                            } catch (SQLException e) {
                                future.fail(e);
                            }
                        }, f2.completer());
                    } else {
                        f2.complete();
                    }
                    try {
                        if (exec != null) {
                            exec.shutdown();
                        }
                        if (map != null) {
                            map.remove(name);
                            if (map.isEmpty()) {
                                map.close();
                            }
                        }
                        f1.complete();
                    } catch (Throwable t) {
                        f1.fail(t);
                    }
                } else {
                    if (completionHandler != null) {
                        completionHandler.handle(Future.succeededFuture());
                    }
                }
            }
        }
    }
}

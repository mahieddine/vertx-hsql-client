# Vert.x HSQL Client  
 
HSQL Client is a Vert.x ext that provides a HSQL connector compatible with jooq, it expose the native SQL.Connection interface rather than the Vertx' own SQLConnection

# Usage
```java
# Init HSQL Connection
HSQLClient hsqlClient = HSQLClient.createShared(vertx, new JsonObject()
                .put("url", DATABASE_URL)
                .put("username", DATABASE_USERNAME)
                .put("password", DATABASE_PASSWORD)
);
Configuration jooqConf = new DefaultConfiguration().set(SQLDialect.HSQLDB);
...

# Retrieve a connection handler
hsqlClient.getConnection(connectionAsyncResult -> {
                if (connectionAsyncResult.succeeded()) {
                    jooqConf.set(connectionAsyncResult.result());
                    cf.complete(null);
                } else {
                    cf.completeExceptionally(connectionAsyncResult.cause());
                }
});

# After that you can use the jooqConf object to init your JOOQ DAO and perform your requests normally :) 

```

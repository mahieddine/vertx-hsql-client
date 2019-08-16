# Vert.x HSQL Client  
[![Build Status](https://travis-ci.org/mahieddine/vertx-hsql-client.svg?branch=master)](https://travis-ci.org/mahieddine/vertx-hsql-client)
[![MIT license](https://img.shields.io/badge/License-MIT-blue.svg)](https://lbesson.mit-license.org/)
[![Maintenance](https://img.shields.io/badge/Maintained%3F-yes-green.svg)](https://github.com/mahieddine/vertx-hsql-client/graphs/commit-activity)    

HSQL Client is a Vert.x ext that provides a HSQL connector compatible with jooq, it expose the native SQL.Connection interface rather than the Vertx' own SQLConnection

# Maven

```xml
<dependency>
  <groupId>io.github.mahieddine</groupId>
  <artifactId>vertx-hsql-client</artifactId>
  <version>3.4.2</version>
</dependency>
```

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

# Vert.x Web Scala Template

<img src='https://raw.githubusercontent.com/mslinn/quill-powered-vertx/gh-pages/images/redHat.jpg' align='right' width='20%'>

[![Build Status](https://travis-ci.org/mslinn/quill-powered-vertx.svg?branch=master)](https://travis-ci.org/mslinn/quill-powered-vertx)
[![GitHub version](https://badge.fury.io/gh/mslinn%2Fquill-powered-vertx.svg)](https://badge.fury.io/gh/mslinn%2Fquill-powered-vertx)

This example webapp demonstrates persistence with Scala, vert.x and [quill-cache](https://github.com/mslinn/quill-cache/).
Authentication is enabled by [vert.x](http://vertx.io/docs/vertx-jdbc-client/scala/)'s 
[JSON web tokens](http://vertx.io/docs/vertx-auth-jwt/scala/).

This project is built with Scala 2.12, which requires Java 8+.

Vert.x is polyglot by nature, which is important to me, however the approach taken for persistence 
(as I currently understand it) makes me uncomfortable.
From the [documentation](http://vertx.io/docs/vertx-jdbc-client/scala/#_data_types): 
*Due to the fact that Vert.x uses JSON as its standard message format there will be many limitations to the data types accepted by the client.*
For example, integers, long integers and floats are not supported, just doubles.
I minimize the use of Vert.x's persistence mechanism as a result.
It is possible, or even likely, that as my understanding of vert.x increases I may adopt a different opinion.

## Running the Program
Start the program, then point a web browser at [http://localhost:8080/products](http://localhost:8080/products)
```
$ sbt run
[info] Loading global plugins from /home/mslinn/.sbt/0.13/plugins
[info] Loading project definition from /mnt/_/work/experiments/vertx/quill-powered-vertx/project
[info] Set current project to quill-powered-vertx-server (in build file:/mnt/_/work/experiments/vertx/quill-powered-vertx/)
WARN  persistence - Got 1 down lines from evolutions/1.sql:
<<<<<<< HEAD

DROP TABLE IF EXISTS "products" CASCADE;
WARN  persistence - Got 1 up lines from evolutions/1.sql:
=======
DROP TABLE IF EXISTS "products" CASCADE;
WARN  persistence - Got 0 up lines from evolutions/1.sql
>>>>>>> wip

Dec 19, 2017 2:22:25 PM io.vertx.core.spi.resolver.ResolverProvider
INFO: Using the default address resolver as the dns resolver could not be loaded
```

Your web browser should show the following when pointed to [http://localhost:8080/products](http://localhost:8080/products):
```
[ {
  "ID" : 1,
  "NAME" : "Egg Whisk",
  "PRICE" : 3.99,
  "WEIGHT" : 150
}, {
  "ID" : 2,
  "NAME" : "Tea Cosy",
  "PRICE" : 5.99,
  "WEIGHT" : 100
}, {
  "ID" : 3,
  "NAME" : "Spatula",
  "PRICE" : 1.0,
  "WEIGHT" : 80
} ]
```

### Make a Fat Jar and Run It
The `bin/run` Bash script assembles this project into a fat jar and runs it.
Sample usage, which runs the `Main` entry point in `com/micronautics/vertx/QuillPoweredServer.scala`:

```
$ bin/run 
```

The `-j` option forces a rebuild of the fat jar. 
Use it after modifying the source code.

```
$ bin/run -j 
```

---
title: 003 - Elasticsearch Connector
description: Elasticsearch Connector (source and sink)
---

*Since*: 4.2

## Current implementation

The connector uses Elastic's supported Java API Client and its REST 5
transport. The Maven artifactId and source directory retain the historical
`elasticsearch-7` suffix for coordinate compatibility only; the runtime does
not contain the Elasticsearch 7 High Level REST Client.

The current client line requires Java 17 and targets Elasticsearch 9. The
client version is declared by `elasticsearch.java.version` in the connector
POM so the implementation and test container remain aligned.

## Choice of client

The Java API Client provides typed, immutable request and response objects,
while the REST 5 transport owns HTTP communication. This keeps the connector
on Elastic's supported API instead of maintaining a separate JSON protocol
implementation. The `_cat/nodes` and `_cat/shards` calls needed for
co-location are issued through the REST 5 low-level client because they are
not part of the typed client surface used by the connector.

## Factory methods and builders

The factory methods cover common cases. For example, a source that reads one
index and maps each hit to a JSON string is:

```java
BatchSource<String> source = ElasticSources.elastic(
    () -> ElasticClients.client("localhost", 9200),
    () -> SearchRequest.of(r -> r.index("users")),
    hit -> hit.source().toJson().toString()
);
```

A sink maps every pipeline item to a Java Client bulk operation:

```java
Sink<Map<String, Object>> sink = ElasticSinks.elastic(
    () -> ElasticClients.client("localhost", 9200),
    item -> BulkOperation.of(op -> op.index(index -> index
        .index("users")
        .document(item)))
);
```

The builders expose settings that do not belong in the compact factory API,
including slicing, co-located reads, request options, retry count, scroll
keep-alive and bulk-request settings:

```java
BatchSource<String> source = new ElasticSourceBuilder<String>()
    .clientFn(() -> ElasticClients.client("localhost", 9200))
    .searchRequestFn(() -> SearchRequest.of(r -> r.index("my-index-*")))
    .optionsFn(request -> DefaultTransportOptions.EMPTY)
    .mapToItemFn(hit -> hit.source().toJson().toString())
    .enableSlicing()
    .build();
```

The API accepts `SupplierEx<SearchRequest>` because Java Client requests are
immutable and each processor must receive its own request instance. The
connector uses `rebuild()` to add scroll, slicing and shard-preference fields
without changing the caller's template.

## Slicing

Slicing parallelizes reads. Without co-location, slice IDs use global
processor coordinates. With co-location, they use local processor
coordinates so each Elasticsearch node receives the expected local slices.
The number of slices should normally not exceed the number of shards because
excess slices add initial latency and server-side memory use. See Elastic's
[sliced scroll documentation](https://www.elastic.co/docs/reference/elasticsearch/rest-apis/paginate-search-results#sliced-scroll).

## Co-located reads

When Hazelcast and Elasticsearch run on the same hosts, the connector assigns
shards to matching Hazelcast members. Each processor restricts its REST 5
client to the assigned local Elasticsearch node and applies an
`_shards:...|_only_local` preference. Every primary/replica shard identity is
assigned once, preventing duplicate reads.

## Authentication and request options

`ElasticClients.client(username, password, hostname, port, scheme)` creates a
REST 5 builder with a UTF-8 Basic authorization header. Applications needing
TLS material, bearer tokens or other HTTP customization can configure the
returned `Rest5ClientBuilder`. Per-request headers and timeouts can be supplied
through `optionsFn`, which returns `TransportOptions`.

## Testing

Pure connector behavior is covered without Docker: builder validation, client
shutdown, request options, scrolling, scroll cleanup, slicing, co-location,
shard assignment and `_cat` response parsing. Container-backed integration
tests run Elasticsearch using Testcontainers and cover end-to-end source,
sink, authentication and retry behavior.

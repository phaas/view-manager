# Efficient maintenance of Query Model in a CQRS architecture

This project attempts to provide a very simple Object Persistence layer for session-level caching. This improves performance over a plain JDBC implementation, without the added overhead of a full JPA EntityManager.
It is intended to be used for entities that store a rich object graph in JSON byte stream. Each JSON object graph is associated with a priamry ID and additional query attributes for locating the document.

## Features
- Entity Caching -- Optimize repeated access to the same objects within a transaction
- Optimistic Locking -- Track the version of managed objects
- SQL Batching -- all deleted, updates and inserts are performed on transaction commit

## Requirements
 - Spring JDBC and Spring Transactions
 - Jackson ObjectMapper for Object<->Json mapping
 

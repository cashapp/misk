CREATE TABLE rate_limit_buckets(
    `id` varchar(255) NOT NULL PRIMARY KEY,
    `state` BLOB
);

CREATE TABLE `character_name_lookup` (
    `name` varchar(255) NOT NULL,
    `keyspace_id` varbinary(16) NOT NULL,
    PRIMARY KEY (`name`,`keyspace_id`)
) ENGINE=InnoDB;
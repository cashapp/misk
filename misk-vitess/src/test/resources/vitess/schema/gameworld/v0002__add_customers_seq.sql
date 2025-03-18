CREATE TABLE `customers_seq` (
    `id` tinyint unsigned NOT NULL DEFAULT '0',
    `next_id` bigint unsigned DEFAULT NULL,
    `cache` bigint unsigned DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB COMMENT='vitess_sequence';
CREATE TABLE `movies_seq` (
  `id` int(11) NOT NULL DEFAULT '0',
  `next_id` bigint DEFAULT NULL,
  `cache` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='vitess_sequence';

CREATE TABLE `movies_seq` (
  `id` int(11) NOT NULL DEFAULT '0',
  `next_id` bigint DEFAULT NULL,
  `cache` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='vitess_sequence';

INSERT INTO `movies_seq` (id, next_id, cache) VALUES (0, 1, 100);

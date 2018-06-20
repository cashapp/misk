CREATE TABLE `characters_seq` (
  `id` int(11) NOT NULL DEFAULT '0',
  `next_id` bigint(20) DEFAULT NULL,
  `cache` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='vitess_sequence';

INSERT INTO `characters_seq` (id, next_id, cache) VALUES (0, 1, 100);

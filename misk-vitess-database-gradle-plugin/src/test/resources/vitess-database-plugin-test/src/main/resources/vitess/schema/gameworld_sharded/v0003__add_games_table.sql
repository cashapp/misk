CREATE TABLE `games` (
    `id` bigint unsigned NOT NULL,
    `title` varchar(191) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_title` (`title`)
) ENGINE=InnoDB;

CREATE TABLE `customers` (
    `id` bigint unsigned NOT NULL,
    `email` varchar(191) NOT NULL,
    `token` varchar(191) NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_email_address` (`email`),
    KEY `idx_token` (`token`)
) ENGINE=InnoDB;

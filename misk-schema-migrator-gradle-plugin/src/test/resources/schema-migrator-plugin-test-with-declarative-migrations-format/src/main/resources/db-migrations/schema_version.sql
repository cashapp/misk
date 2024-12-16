CREATE TABLE `schema_version` (
    `version` varchar(50) COLLATE utf8mb4_bin NOT NULL,
    `installed_by` varchar(30) COLLATE utf8mb4_bin DEFAULT NULL,
    UNIQUE KEY `version` (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin
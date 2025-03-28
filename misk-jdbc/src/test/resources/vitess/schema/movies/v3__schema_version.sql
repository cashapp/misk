CREATE TABLE `schema_version` (
    `version` varchar(50) PRIMARY KEY,
    `installed_by` varchar(30) DEFAULT NULL
);
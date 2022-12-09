CREATE TABLE movies (
    id bigint NOT NULL PRIMARY KEY AUTO_INCREMENT,
    created_at timestamp(3) NOT NULL DEFAULT NOW(3),
    updated_at timestamp(3) NOT NULL DEFAULT NOW(3) ON UPDATE NOW(3),
    name varchar(191) NOT NULL,
    release_date date NULL,
    language varchar(5) NULL,
    UNIQUE KEY `unq_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;
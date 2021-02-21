CREATE TABLE `quoted_basic_table` (
    `id` BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
    `tbl1_string_nullable` VARCHAR(255) DEFAULT NULL,
    `tbl1_int_nullable` INT,
    `tbl1_bin_nullable` VARBINARY(255),
    `tbl1_string` VARCHAR(255) NOT NULL,
    `tbl1_int` INT NOT NULL DEFAULT 0,
    `tbl1_bin` VARBINARY(255) NOT NULL,
    `created_at` timestamp(3) NOT NULL DEFAULT NOW(3),
    `updated_at` timestamp(3) NOT NULL DEFAULT NOW(3) ON UPDATE NOW(3)
)
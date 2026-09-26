CREATE TABLE products (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(255) NOT NULL,
    `description` TEXT,
    `price_cents` INT NOT NULL,
    `currency` VARCHAR(3) NOT NULL DEFAULT 'USD',
    `stock_quantity` INT NOT NULL DEFAULT 0,
    `category` VARCHAR(100),
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
)

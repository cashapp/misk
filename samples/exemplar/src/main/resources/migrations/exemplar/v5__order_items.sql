CREATE TABLE order_items (
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `order_id` BIGINT NOT NULL,
    `product_id` BIGINT NOT NULL,
    `quantity` INT NOT NULL DEFAULT 1,
    `unit_price_cents` INT NOT NULL,
    FOREIGN KEY (`order_id`) REFERENCES orders(`id`),
    FOREIGN KEY (`product_id`) REFERENCES products(`id`)
)

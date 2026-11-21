-- =============================================
-- Divar Extraction - Useful SQL Scripts
-- =============================================

-- ========== Insert Base URLs ==========
INSERT INTO `divar_extraction`.`scrape_urls` (`id`, `created_at`, `enabled`, `url`) VALUES
(1, NULL, b'1', 'https://divar.ir/s/karaj/buy-apartment/kuy-zanbaq?bbox=50.8103752%2C35.7740784%2C50.9564743%2C35.8202362&business-type=personal%2C&districts=1039%2C1041%2C1042%2C1049%2C1054%2C1097%2C1111%2C1112%2C1113%2C1114%2C567%2C582');
INSERT INTO `divar_extraction`.`scrape_urls` (`id`, `created_at`, `enabled`, `url`) VALUES
(2, NULL, b'1', 'https://divar.ir/s/karaj/rent-apartment/kuy-zanbaq?bbox=50.8103752%2C35.7740784%2C50.9564743%2C35.8202362&business-type=personal%2C&districts=1039%2C1041%2C1042%2C1049%2C1054%2C1097%2C1111%2C1112%2C1113%2C1114%2C567%2C582');

-- ========== View All URLs ==========
SELECT * FROM `divar_extraction`.`scrape_urls`;

-- ========== Disable/Enable URL ==========
UPDATE `divar_extraction`.`scrape_urls` SET `enabled` = b'0' WHERE `id` = 1;
UPDATE `divar_extraction`.`scrape_urls` SET `enabled` = b'1' WHERE `id` = 1;

-- ========== Delete URL ==========
-- DELETE FROM `divar_extraction`.`scrape_urls` WHERE `id` = 1;

-- ========== Stats ==========
SELECT COUNT(*) AS total FROM `divar_extraction`.`property_listings`;
SELECT COUNT(*) AS personal FROM `divar_extraction`.`property_listings` WHERE `is_broker` = 0;
SELECT COUNT(*) AS brokers FROM `divar_extraction`.`property_listings` WHERE `is_broker` = 1;
SELECT COUNT(*) AS sent FROM `divar_extraction`.`property_listings` WHERE `is_sent` = 1;
SELECT COUNT(*) AS unsent FROM `divar_extraction`.`property_listings` WHERE `is_sent` = 0 AND `is_broker` = 0;

-- ========== By Listing Type ==========
SELECT `listing_type`, COUNT(*) AS count FROM `divar_extraction`.`property_listings` WHERE `is_broker` = 0 GROUP BY `listing_type`;

-- ========== By City ==========
SELECT `city`, COUNT(*) AS count FROM `divar_extraction`.`property_listings` WHERE `is_broker` = 0 GROUP BY `city` ORDER BY count DESC;

-- ========== By District ==========
SELECT `district`, COUNT(*) AS count FROM `divar_extraction`.`property_listings` WHERE `is_broker` = 0 GROUP BY `district` ORDER BY count DESC;

-- ========== Recent Listings ==========
SELECT `id`, `title`, `city`, `district`, `listing_type`, `is_sent`, `scraped_at`
FROM `divar_extraction`.`property_listings`
WHERE `is_broker` = 0
ORDER BY `scraped_at` DESC
LIMIT 20;

-- ========== Unsent Personal Listings ==========
SELECT `id`, `title`, `city`, `district`, `listing_type`, `scraped_at`
FROM `divar_extraction`.`property_listings`
WHERE `is_broker` = 0 AND `is_sent` = 0
ORDER BY `scraped_at` DESC;

-- ========== Search in Title ==========
SELECT `id`, `title`, `city`, `district`
FROM `divar_extraction`.`property_listings`
WHERE `is_broker` = 0 AND `title` LIKE '%کلید%';

-- ========== Search in Description ==========
SELECT `id`, `title`, `city`, `district`
FROM `divar_extraction`.`property_listings`
WHERE `is_broker` = 0 AND `description` LIKE '%آسانسور%';

-- ========== Mark All Unsent as Sent ==========
-- UPDATE `divar_extraction`.`property_listings` SET `is_sent` = 1 WHERE `is_sent` = 0;

-- ========== Reset All Sent Status ==========
-- UPDATE `divar_extraction`.`property_listings` SET `is_sent` = 0;

-- ========== Delete All Brokers ==========
-- DELETE FROM `divar_extraction`.`property_listings` WHERE `is_broker` = 1;

-- ========== Delete All Listings ==========
-- DELETE FROM `divar_extraction`.`property_listings`;

-- ========== Drop and Recreate Database ==========
-- DROP DATABASE IF EXISTS `divar_extraction`;
-- CREATE DATABASE `divar_extraction` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ========== View Filter Keywords ==========
SELECT * FROM `divar_extraction`.`filter_keywords`;

-- ========== Add Filter Keyword ==========
-- INSERT INTO `divar_extraction`.`filter_keywords` (`word`, `enabled`) VALUES ('کلمه جدید', 1);

-- ========== Toggle Filter Keyword ==========
-- UPDATE `divar_extraction`.`filter_keywords` SET `enabled` = 0 WHERE `word` = 'مشاور';

-- ========== Delete Filter Keyword ==========
-- DELETE FROM `divar_extraction`.`filter_keywords` WHERE `word` = 'مشاور';

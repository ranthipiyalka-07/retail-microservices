#!/bin/sh
set -eu

for svc in auth customer product inventory cart order payment review campaign support wishlist notification; do
  mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<SQL
CREATE DATABASE IF NOT EXISTS ${svc}_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${svc}_user'@'%' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON ${svc}_db.* TO '${svc}_user'@'%';
SQL
done
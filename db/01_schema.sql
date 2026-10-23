-- =====================================================================
-- ElectroShop · Esquema de base de datos (versión 1 · línea base)
-- MySQL 8.0
--
-- Uso:   mysql -u root -p < db/01_schema.sql
--
-- Nota para el informe: esta es la versión SIN optimizar. Solo existen
-- los índices que MySQL crea por defecto (claves primarias y las claves
-- foráneas, que InnoDB indexa automáticamente). Cualquier índice
-- adicional debe agregarse en la actividad 3 como respuesta a un
-- hallazgo de la línea base, en un script aparte (p. ej. 03_indices.sql).
-- =====================================================================

DROP DATABASE IF EXISTS electroshop;
CREATE DATABASE electroshop CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE electroshop;

CREATE TABLE cliente (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    documento       VARCHAR(20)  NOT NULL,
    nombre          VARCHAR(120) NOT NULL,
    email           VARCHAR(120) NOT NULL,
    ciudad          VARCHAR(60)  NOT NULL,
    fecha_registro  DATETIME     NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE producto (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    sku        VARCHAR(20)   NOT NULL,          -- código comercial con el que llegan los pedidos
    nombre     VARCHAR(150)  NOT NULL,
    categoria  VARCHAR(50)   NOT NULL,
    precio     DECIMAL(12,2) NOT NULL,          -- pesos colombianos
    stock      INT           NOT NULL,
    activo     BOOLEAN       NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE pedido (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    cliente_id       BIGINT        NOT NULL,
    fecha            DATETIME      NOT NULL,
    estado           VARCHAR(20)   NOT NULL,
    total            DECIMAL(14,2) NOT NULL,
    direccion_envio  VARCHAR(200)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_pedido_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id)
) ENGINE = InnoDB;

CREATE TABLE detalle_pedido (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    pedido_id        BIGINT        NOT NULL,
    producto_id      BIGINT        NOT NULL,
    cantidad         INT           NOT NULL,
    precio_unitario  DECIMAL(12,2) NOT NULL,
    subtotal         DECIMAL(14,2) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_detalle_pedido   FOREIGN KEY (pedido_id)   REFERENCES pedido (id),
    CONSTRAINT fk_detalle_producto FOREIGN KEY (producto_id) REFERENCES producto (id)
) ENGINE = InnoDB;

CREATE TABLE pago (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    pedido_id   BIGINT        NOT NULL,
    monto       DECIMAL(14,2) NOT NULL,
    metodo      VARCHAR(20)   NOT NULL,
    estado      VARCHAR(20)   NOT NULL,
    referencia  VARCHAR(40)   NOT NULL,
    fecha       DATETIME      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_pago_pedido FOREIGN KEY (pedido_id) REFERENCES pedido (id)
) ENGINE = InnoDB;

CREATE TABLE orden_preparacion (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    pedido_id       BIGINT      NOT NULL,
    bodega          VARCHAR(30) NOT NULL,
    estado          VARCHAR(20) NOT NULL,
    prioridad       VARCHAR(10) NOT NULL,
    fecha_creacion  DATETIME    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_orden_pedido FOREIGN KEY (pedido_id) REFERENCES pedido (id)
) ENGINE = InnoDB;

-- Usuario de la aplicación (cambiar la contraseña si se despliega fuera de la máquina local)
CREATE USER IF NOT EXISTS 'electroshop'@'localhost' IDENTIFIED BY 'electroshop';
GRANT ALL PRIVILEGES ON electroshop.* TO 'electroshop'@'localhost';
FLUSH PRIVILEGES;

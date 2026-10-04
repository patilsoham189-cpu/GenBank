-- ======================================================================
-- GenBank (Apna Bank) - MySQL Schema & Initial Seed Script
-- Compatible with MySQL 8.0+ / 9.0+
-- ======================================================================

CREATE DATABASE IF NOT EXISTS genbank_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE genbank_db;

-- 1. Admins Table
CREATE TABLE IF NOT EXISTS admins (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255) DEFAULT NULL,
    password VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_username (username),
    UNIQUE KEY uk_admin_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Retail Customer Accounts Table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    account_number VARCHAR(255) NOT NULL,
    ifsc_code VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    balance DECIMAL(38,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
    status ENUM('ACTIVE','BLOCKED','FROZEN') NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_account (account_number),
    UNIQUE KEY uk_user_email (email),
    UNIQUE KEY uk_user_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Core Clearing Ledger Transactions Table
CREATE TABLE IF NOT EXISTS transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT DEFAULT NULL,
    reference_id VARCHAR(255) NOT NULL,
    account_number VARCHAR(255) NOT NULL,
    counterparty_account VARCHAR(255) DEFAULT NULL,
    counterparty_name VARCHAR(255) DEFAULT NULL,
    type VARCHAR(20) NOT NULL,
    amount DECIMAL(38,2) NOT NULL,
    balance_after DECIMAL(38,2) NOT NULL,
    narration VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_tx_account (account_number),
    KEY idx_tx_created (created_at),
    KEY idx_tx_user (user_id),
    UNIQUE KEY uk_tx_ref_account (reference_id, account_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ======================================================================
-- Initial Seed Data
-- ======================================================================

-- Master Admin (Password: Admin@123)
INSERT INTO admins (id, username, email, password)
VALUES (1, 'admin', 'admin@genbank.com', '$2a$10$MxzuwkMFFigNqz1fSVCpFO5r7WONuM6Lq0L5um/pg5octGVU2eW0e')
ON DUPLICATE KEY UPDATE email = VALUES(email);

-- Demo Retail Customers
INSERT INTO users (id, account_number, ifsc_code, full_name, email, phone, password, balance, status, version)
VALUES 
(1, 'GB1893228017', 'GBIN0GENBNK', 'Soham Rajkumar Patil', 'patilsoham189@gmail.com', '7823060566', '$2a$10$tmOeyVpndTzfjemFF5cbhu45zK/wmOJANxuNY41e2rkmB.yOKRHSq', 2000.00, 'ACTIVE', 1),
(2, 'GB1269557905', 'GBIN0GENBNK', 'Chaudappa Sidaramappa Sindagi', 'sindagi99official@gmail.com', '9552748061', '$2a$10$y3oJlK2e6IdzTu/TJHDZH.VU6OdaYmPinBevkhSBWUpmiAqYRvgce', 500.00, 'ACTIVE', 2),
(3, 'GB1235036932', 'GBIN0GENBNK', 'Atharv Umesh Bhange', 'bhangeatharv@gmail.com', '8668750371', '$2a$10$9R49zAM4I3jBAdw79FZYteOynj1OYghM5l/Cyihfbel4d4VtCkre.', 1000.00, 'ACTIVE', 0)
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name);

-- Seed Double-Entry Ledger Transactions
INSERT INTO transactions (id, user_id, reference_id, account_number, counterparty_name, type, amount, balance_after, narration, created_at)
VALUES
(1, 1, 'REW202609231550141001', 'GB1893228017', 'GenBank Digital Onboarding', 'CREDIT', 1000.00, 1000.00, 'Initial Digital Account Activation Reward', '2026-09-23 15:50:14.000000'),
(2, 2, 'REW202609261108341002', 'GB1269557905', 'GenBank Digital Onboarding', 'CREDIT', 1000.00, 1000.00, 'Initial Digital Account Activation Reward', '2026-09-26 11:08:34.000000'),
(3, 2, 'TXN202609261115004321', 'GB1269557905', 'Soham Rajkumar Patil', 'DEBIT', 500.00, 500.00, 'Fund Transfer to Soham Rajkumar Patil (GB1893228017)', '2026-09-26 11:15:00.000000'),
(4, 1, 'TXN202609261115004321', 'GB1893228017', 'Chaudappa Sidaramappa Sindagi', 'CREDIT', 500.00, 1500.00, 'Fund Transfer from Chaudappa Sidaramappa Sindagi (GB1269557905)', '2026-09-26 11:15:00.000000'),
(5, 2, 'DEP202610021200057860', 'GB1269557905', 'Chaudappa Sidaramappa Sindagi', 'CREDIT', 500.00, 1000.00, 'Account Balance Adjustment', '2026-10-02 12:00:05.849706'),
(6, 2, 'TXN202610021209197615', 'GB1269557905', 'Soham Rajkumar Patil', 'DEBIT', 500.00, 500.00, 'Fund Transfer to Soham Rajkumar Patil (GB1893228017)', '2026-10-02 12:09:19.378664'),
(7, 1, 'TXN202610021209197615', 'GB1893228017', 'Chaudappa Sidaramappa Sindagi', 'CREDIT', 500.00, 2000.00, 'Fund Transfer from Chaudappa Sidaramappa Sindagi (GB1269557905)', '2026-10-02 12:09:19.383146'),
(8, 3, 'REW202610021218165415', 'GB1235036932', 'GenBank Digital Onboarding', 'CREDIT', 1000.00, 1000.00, 'Initial Digital Account Activation Reward', '2026-10-02 12:18:16.914917')
ON DUPLICATE KEY UPDATE reference_id = VALUES(reference_id);

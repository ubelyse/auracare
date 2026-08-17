-- Enable pgcrypto for encryption
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Facilities
CREATE TABLE facilities (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            name VARCHAR(100) NOT NULL UNIQUE,
                            code VARCHAR(20) NOT NULL UNIQUE,
                            address TEXT,
                            phone VARCHAR(20),
                            email VARCHAR(100),
                            is_active BOOLEAN DEFAULT true,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Departments
CREATE TABLE departments (
                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                             facility_id UUID NOT NULL REFERENCES facilities(id) ON DELETE CASCADE,
                             name VARCHAR(100) NOT NULL,
                             code VARCHAR(20) NOT NULL,
                             description TEXT,
                             is_active BOOLEAN DEFAULT true,
                             created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                             updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                             UNIQUE(facility_id, code)
);

-- Users
CREATE TABLE users (
                       id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       username VARCHAR(50) NOT NULL UNIQUE,
                       email VARCHAR(100) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       first_name VARCHAR(50) NOT NULL,
                       last_name VARCHAR(50) NOT NULL,
                       phone VARCHAR(20),
                       is_active BOOLEAN DEFAULT true,
                       email_verified BOOLEAN DEFAULT false,
                       mfa_enabled BOOLEAN DEFAULT false,
                       mfa_secret VARCHAR(255),
                       facility_id UUID REFERENCES facilities(id),
                       role VARCHAR(20) NOT NULL,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       last_login TIMESTAMP,
                       CONSTRAINT valid_role CHECK (role IN ('DISTRICT_ADMIN', 'FACILITY_ADMIN', 'DOCTOR', 'STAFF', 'PATIENT'))
);

-- User facility assignment (for multi-facility staff)
CREATE TABLE user_facilities (
                                 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                 facility_id UUID NOT NULL REFERENCES facilities(id) ON DELETE CASCADE,
                                 is_primary BOOLEAN DEFAULT false,
                                 assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                 PRIMARY KEY (user_id, facility_id)
);

-- Login attempts for rate limiting
CREATE TABLE login_attempts (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                username VARCHAR(50) NOT NULL,
                                ip_address VARCHAR(45) NOT NULL,
                                attempt_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                success BOOLEAN DEFAULT false,
                                user_agent TEXT
);

-- Refresh tokens
CREATE TABLE refresh_tokens (
                                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                token VARCHAR(255) NOT NULL UNIQUE,
                                expires_at TIMESTAMP NOT NULL,
                                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                revoked BOOLEAN DEFAULT false
);

-- Audit log (write-only)
CREATE TABLE audit_logs (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            user_id UUID REFERENCES users(id),
                            username VARCHAR(50),
                            action VARCHAR(50) NOT NULL,
                            resource_type VARCHAR(50) NOT NULL,
                            resource_id VARCHAR(50),
                            ip_address VARCHAR(45),
                            user_agent TEXT,
                            details JSONB,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Add indexes for performance
CREATE INDEX idx_users_facility_id ON users(facility_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_login_attempts_username ON login_attempts(username);
CREATE INDEX idx_login_attempts_ip_address ON login_attempts(ip_address);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);

-- Row Level Security
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE departments ENABLE ROW LEVEL SECURITY;

-- RLS Policies
CREATE POLICY users_facility_isolation ON users
    USING (facility_id = current_setting('app.current_facility_id')::UUID);

CREATE POLICY departments_facility_isolation ON departments
    USING (facility_id = current_setting('app.current_facility_id')::UUID);

-- Functions
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Triggers
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER update_facilities_updated_at BEFORE UPDATE ON facilities
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER update_departments_updated_at BEFORE UPDATE ON departments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- Insert initial data
INSERT INTO facilities (id, name, code, address, phone, email) VALUES
                                                                   ('11111111-1111-1111-1111-111111111111', 'Remera Health Center', 'RHC', 'Remera, Kigali', '+250788000001', 'remera@mvura.rw'),
                                                                   ('22222222-2222-2222-2222-222222222222', 'Kacyiru Health Center', 'KHC', 'Kacyiru, Kigali', '+250788000002', 'kacyiru@mvura.rw'),
                                                                   ('33333333-3333-3333-3333-333333333333', 'Kimironko Clinic', 'KMC', 'Kimironko, Kigali', '+250788000003', 'kimironko@mvura.rw');

-- Create initial admin user (password: Admin@2026)
INSERT INTO users (id, username, email, password_hash, first_name, last_name, role, facility_id, email_verified, mfa_enabled) VALUES
    ('44444444-4444-4444-4444-444444444444', 'district_admin', 'admin@mvura.rw',
     '$2a$10$YHk1YHk1YHk1YHk1YHk1YuYHk1YHk1YHk1YHk1YHk1YHk1YHk1',
     'District', 'Admin', 'DISTRICT_ADMIN', NULL, true, false);

-- Insert departments
INSERT INTO departments (id, facility_id, name, code, description) VALUES
                                                                       ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', 'General Medicine', 'GEN', 'General Medical Services'),
                                                                       ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '11111111-1111-1111-1111-111111111111', 'Pediatrics', 'PED', 'Children''s Health'),
                                                                       ('cccccccc-cccc-cccc-cccc-cccccccccccc', '11111111-1111-1111-1111-111111111111', 'Maternity', 'MAT', 'Maternity Services'),
                                                                       ('dddddddd-dddd-dddd-dddd-dddddddddddd', '11111111-1111-1111-1111-111111111111', 'Laboratory', 'LAB', 'Clinical Laboratory');

-- Insert staff users for testing
INSERT INTO users (id, username, email, password_hash, first_name, last_name, phone, role, facility_id, email_verified, mfa_enabled) VALUES
                                                                                                                                         ('55555555-5555-5555-5555-555555555555', 'dr.keza', 'keza@mvura.rw',
                                                                                                                                          '$2a$10$YHk1YHk1YHk1YHk1YHk1YuYHk1YHk1YHk1YHk1YHk1YHk1YHk1',
                                                                                                                                          'Keza', 'Uwimana', '+250788000004', 'DOCTOR', '11111111-1111-1111-1111-111111111111', true, false),

                                                                                                                                         ('66666666-6666-6666-6666-666666666666', 'claude.reception', 'claude@mvura.rw',
                                                                                                                                          '$2a$10$YHk1YHk1YHk1YHk1YHk1YuYHk1YHk1YHk1YHk1YHk1YHk1YHk1',
                                                                                                                                          'Claude', 'Mugabo', '+250788000005', 'STAFF', '11111111-1111-1111-1111-111111111111', true, false),

                                                                                                                                         ('77777777-7777-7777-7777-777777777777', 'dr.uwimana', 'uwimana@mvura.rw',
                                                                                                                                          '$2a$10$YHk1YHk1YHk1YHk1YHk1YuYHk1YHk1YHk1YHk1YHk1YHk1YHk1',
                                                                                                                                          'Uwimana', 'Habimana', '+250788000006', 'DOCTOR', '22222222-2222-2222-2222-222222222222', true, false);

-- Assign staff to facilities
INSERT INTO user_facilities (user_id, facility_id, is_primary) VALUES
                                                                   ('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111', true),
                                                                   ('66666666-6666-6666-6666-666666666666', '11111111-1111-1111-1111-111111111111', true),
                                                                   ('77777777-7777-7777-7777-777777777777', '22222222-2222-2222-2222-222222222222', true);
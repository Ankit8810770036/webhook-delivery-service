-- V1__init_schema.sql
-- Reliable Webhook Delivery Service: Core relational schema

CREATE TABLE IF NOT EXISTS tenants (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS endpoints (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    url VARCHAR(1024) NOT NULL,
    secret VARCHAR(255) NOT NULL,
    subscribed_event_types TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_endpoints_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS events (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    event_id_external VARCHAR(255) NOT NULL,
    type VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_tenant_event_external UNIQUE (tenant_id, event_id_external),
    CONSTRAINT fk_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS deliveries (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    endpoint_id UUID NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL, -- PENDING, PROCESSING, SUCCESS, DEAD_LETTERED, CANCELLED
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by VARCHAR(128),
    locked_until TIMESTAMP WITH TIME ZONE,
    last_response_code INT,
    last_response_snippet VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_deliveries_event FOREIGN KEY (event_id) REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT fk_deliveries_endpoint FOREIGN KEY (endpoint_id) REFERENCES endpoints(id) ON DELETE CASCADE,
    CONSTRAINT fk_deliveries_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS delivery_attempts (
    id UUID PRIMARY KEY,
    delivery_id UUID NOT NULL,
    attempt_number INT NOT NULL,
    response_code INT,
    latency_ms BIGINT,
    error_message VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attempts_delivery FOREIGN KEY (delivery_id) REFERENCES deliveries(id) ON DELETE CASCADE
);

-- Seed default demo tenants
INSERT INTO tenants (id, name, created_at)
VALUES 
    ('tenant-alpha', 'Alpha Corporation', CURRENT_TIMESTAMP),
    ('tenant-beta', 'Beta Logistics', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

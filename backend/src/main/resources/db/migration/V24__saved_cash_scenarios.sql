CREATE TABLE saved_scenarios (
 id UUID PRIMARY KEY, company_id UUID NOT NULL, created_by UUID NOT NULL, author VARCHAR(120) NOT NULL,
 name VARCHAR(120) NOT NULL, definition TEXT NOT NULL, creation_key UUID NOT NULL, creation_hash VARCHAR(64) NOT NULL,
 revision BIGINT NOT NULL DEFAULT 0, archived BOOLEAN NOT NULL DEFAULT FALSE, modified_at TIMESTAMP WITH TIME ZONE NOT NULL,
 created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
 CONSTRAINT uq_scenario_creation UNIQUE(company_id,creation_key)
);
CREATE INDEX idx_saved_scenario_company ON saved_scenarios(company_id,archived,updated_at);
CREATE TABLE scenario_runs (
 id UUID PRIMARY KEY, company_id UUID NOT NULL, scenario_id UUID NOT NULL REFERENCES saved_scenarios(id),
 request_id UUID NOT NULL, definition_revision BIGINT NOT NULL, author VARCHAR(120) NOT NULL,
 definition TEXT NOT NULL, result TEXT NOT NULL, fingerprint VARCHAR(64) NOT NULL, calculated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
 CONSTRAINT uq_scenario_run_request UNIQUE(company_id,scenario_id,request_id)
);
CREATE INDEX idx_scenario_runs_company ON scenario_runs(company_id,scenario_id,created_at);

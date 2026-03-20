-- V1: Crear tabla principal de performance_intervals
CREATE TABLE IF NOT EXISTS performance_intervals (
  dispatch_unit   VARCHAR(255)      NOT NULL,
  node_id         VARCHAR(255)      NOT NULL,
  dttm_utc        TIMESTAMPTZ       NOT NULL,
  metered_value   DOUBLE PRECISION,
  baseline_value  DOUBLE PRECISION,
  baseline_id     VARCHAR(255),
  updated_at      TIMESTAMPTZ       NOT NULL DEFAULT NOW(),
  PRIMARY KEY (dispatch_unit, dttm_utc)
);

CREATE INDEX IF NOT EXISTS idx_pi_node_id    ON performance_intervals(node_id);
CREATE INDEX IF NOT EXISTS idx_pi_updated_at ON performance_intervals(updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_pi_baseline_id ON performance_intervals(baseline_id) WHERE baseline_id IS NOT NULL;

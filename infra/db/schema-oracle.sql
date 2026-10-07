-- Run as each application schema (P360_CATALOG, P360_ORDERS, P360_AUDIT, P360_REPORT).
-- Never run application services with the RDS master account.
CREATE TABLE p360_documents (
  id VARCHAR2(160 CHAR) PRIMARY KEY,
  kind VARCHAR2(40 CHAR) NOT NULL,
  payload CLOB NOT NULL,
  revision NUMBER(19,0) NOT NULL
);
CREATE INDEX p360_kind_idx ON p360_documents(kind);

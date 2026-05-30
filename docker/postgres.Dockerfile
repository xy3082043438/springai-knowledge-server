FROM pgvector/pgvector:0.8.2-pg17
COPY postgres-init.sql /docker-entrypoint-initdb.d/01-init.sql

# PostgreSQL for Open Idea Studio.
# Schema is owned by Flyway in the API; this image only prepares the database.
FROM postgres:17.6-alpine

COPY init/ /docker-entrypoint-initdb.d/

HEALTHCHECK --interval=5s --timeout=3s --start-period=10s --retries=10 \
  CMD pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" || exit 1

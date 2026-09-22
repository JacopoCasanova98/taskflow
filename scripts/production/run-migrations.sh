#!/usr/bin/env bash
# REFERENCE ONLY: no AWS/RDS execution during TaskFlow validation.
set +x
set -euo pipefail
: "${TASKFLOW_DB_URL:?Non-secret verify-full JDBC URL is required}"
: "${TASKFLOW_MIGRATION_PASSWORD_FILE:?Protected migration password file is required}"
: "${TASKFLOW_BACKEND_JAR:?Reviewed backend release JAR is required}"
# Values remain in the protected file, never JVM arguments. No application startup follows.
exec java -Dloader.main=com.taskflow.operations.DatabaseMigration \
  -cp "$TASKFLOW_BACKEND_JAR" org.springframework.boot.loader.launch.PropertiesLauncher

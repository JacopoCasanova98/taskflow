-- REFERENCE ONLY: privileged operator, connected to the dedicated taskflow DB.
-- No passwords here. After this transaction, use psql \password interactively
-- over verify-full TLS, with SCRAM password encryption. Never put values in argv.
-- This is role administration, not an application Flyway migration.
BEGIN;
DO $bootstrap$
BEGIN
    IF current_database() <> 'taskflow' THEN
        RAISE EXCEPTION 'Bootstrap requires the dedicated taskflow database';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'taskflow_migrator') THEN
        CREATE ROLE taskflow_migrator LOGIN;
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'taskflow_app') THEN
        CREATE ROLE taskflow_app LOGIN;
    END IF;
    IF EXISTS (
        SELECT FROM pg_roles r WHERE r.rolname IN ('taskflow_app', 'taskflow_migrator')
        AND (r.rolsuper OR r.rolcreatedb OR r.rolcreaterole OR r.rolreplication OR r.rolbypassrls)
    ) OR EXISTS (
        SELECT FROM pg_auth_members m JOIN pg_roles r ON r.oid = m.member
        WHERE r.rolname IN ('taskflow_app', 'taskflow_migrator')
    ) THEN
        RAISE EXCEPTION 'Existing TaskFlow role privileges require operator review';
    END IF;
    IF EXISTS (
        SELECT FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r','p','S','v','m','f')
        AND c.relowner <> 'taskflow_migrator'::regrole
    ) THEN
        RAISE EXCEPTION 'Existing public objects require ownership review before bootstrap';
    END IF;
END
$bootstrap$;
ALTER ROLE taskflow_migrator LOGIN NOINHERIT;
ALTER ROLE taskflow_app LOGIN NOINHERIT;
ALTER ROLE taskflow_migrator IN DATABASE taskflow SET search_path = public;
ALTER ROLE taskflow_app IN DATABASE taskflow SET search_path = public;

-- Database/schema ownership stays with the administrative identity.
REVOKE ALL PRIVILEGES ON DATABASE taskflow FROM PUBLIC, taskflow_app, taskflow_migrator;
GRANT CONNECT ON DATABASE taskflow TO taskflow_migrator, taskflow_app;
REVOKE ALL PRIVILEGES ON SCHEMA public FROM PUBLIC, taskflow_app, taskflow_migrator;
GRANT USAGE, CREATE ON SCHEMA public TO taskflow_migrator;
GRANT USAGE ON SCHEMA public TO taskflow_app;

-- Allow the privileged operator to configure defaults without superuser reliance.
-- This temporary object access is never granted to the app or the EC2 IAM role.
GRANT taskflow_migrator TO CURRENT_USER WITH INHERIT TRUE, SET TRUE;
-- Reset both global and schema-specific defaults so reruns cannot retain broader grants.
ALTER DEFAULT PRIVILEGES FOR ROLE taskflow_migrator REVOKE ALL PRIVILEGES ON TABLES FROM PUBLIC, taskflow_app;
ALTER DEFAULT PRIVILEGES FOR ROLE taskflow_migrator IN SCHEMA public
    REVOKE ALL PRIVILEGES ON TABLES FROM PUBLIC, taskflow_app;
ALTER DEFAULT PRIVILEGES FOR ROLE taskflow_migrator REVOKE ALL PRIVILEGES ON SEQUENCES FROM PUBLIC, taskflow_app;
ALTER DEFAULT PRIVILEGES FOR ROLE taskflow_migrator IN SCHEMA public
    REVOKE ALL PRIVILEGES ON SEQUENCES FROM PUBLIC, taskflow_app;
ALTER DEFAULT PRIVILEGES FOR ROLE taskflow_migrator IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO taskflow_app;
-- Current entities use UUIDs; USAGE covers future nextval/currval, not setval.
ALTER DEFAULT PRIVILEGES FOR ROLE taskflow_migrator IN SCHEMA public
    GRANT USAGE ON SEQUENCES TO taskflow_app;
ALTER DEFAULT PRIVILEGES FOR ROLE taskflow_migrator REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
SET LOCAL ROLE taskflow_migrator;

REVOKE ALL PRIVILEGES ON ALL TABLES IN SCHEMA public FROM PUBLIC, taskflow_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO taskflow_app;
REVOKE ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC, taskflow_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO taskflow_app;
DO $history$
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        REVOKE INSERT, UPDATE, DELETE ON public.flyway_schema_history FROM taskflow_app;
    END IF;
END
$history$;
RESET ROLE;
-- Keep the creator's ADMIN OPTION for future role/password administration,
-- but remove inherited data access and SET ROLE capability between bootstrap runs.
GRANT taskflow_migrator TO CURRENT_USER WITH INHERIT FALSE, SET FALSE;
COMMIT;

# RDS TLS, roles and migration contract — MS9.4

This is a reference operator design, not authorization to execute against AWS.
The [zero-provisioning policy](AWS_ARCHITECTURE.md#hard-project-execution-policy)
remains binding: no RDS connection, AWS credentials/API, master-secret retrieval,
secret population, Terraform plan/apply/destroy or CloudFormation operation.
Validation uses disposable local PostgreSQL and static fixtures only.

## Three identities, one existing schema

| Identity | Purpose and boundary |
| --- | --- |
| RDS administrative/master identity (`taskflowadmin` in IaC) | Independent privileged operator bootstrap only; never backend or migration credentials |
| `taskflow_migrator` | Ordinary login; owns objects/Flyway history it creates, CONNECT and public schema USAGE/CREATE |
| `taskflow_app` | Backend runtime; CONNECT, public schema USAGE, table SELECT/INSERT/UPDATE/DELETE; no DDL ownership |

Neither application role has SUPERUSER, CREATEDB, CREATEROLE, REPLICATION,
BYPASSRLS, `rds_superuser` membership or extension-administration authority.
The app cannot SET ROLE to migrator. The database and `public` schema remain
administratively owned; migrator owns its objects, not the database/schema.

All six existing migrations use unqualified objects in `public`. Retain it and
its Flyway history; no applied migration changes or new schema are needed.
Entities generate UUIDs in Hibernate, so current tables require no sequences.
USAGE on future migrator-created sequences supports nextval/currval when a future
migration needs them, without UPDATE/setval privileges. Runtime gets no TRUNCATE,
REFERENCES, TRIGGER or grant option. Flyway history is readable but not writable
by the app after the controlled migration step.

## Privileged bootstrap and passwords

[bootstrap-database.sql](../scripts/production/bootstrap-database.sql) runs in a
transaction on the dedicated `taskflow` database. It creates the two roles without
passwords, restricts attributes, revokes PUBLIC database/schema privileges and
grants only the required rights. It sets their search path to `public` (with
PostgreSQL's implicit pg_catalog lookup). No application table is created here.
The script fails on unexpected privileged roles/memberships or existing public
objects owned by someone other than migrator. It is not an automatic ownership
conversion tool for an existing deployment. A privileged operator must review
any such adoption separately; never rewrite migration history or run broad
REASSIGN OWNED commands across databases to bypass that check.

Both existing-object grants and **ALTER DEFAULT PRIVILEGES FOR ROLE
`taskflow_migrator`** are required. Defaults apply to objects actually created
as that role, not simply to a role's members. Future tables get DML and sequences
get USAGE. PUBLIC execution of future migrator functions is revoked; any future
application function requires an explicit reviewed grant. Existing-object grants
are scoped to this dedicated public schema, with Flyway-history mutations revoked.
The bootstrap temporarily enables migrator inheritance/SET ROLE for the privileged
operator to configure grants as the object owner, then disables both capabilities.
The creator retains ADMIN OPTION for later role/password administration; app
never receives migrator membership. The operator must own the database/schema
and be authorized to administer both roles.
See [PostgreSQL default privileges](https://www.postgresql.org/docs/17/sql-alterdefaultprivileges.html).

A real authorized operator would obtain the master credential independently and
connect privately with verify-full TLS. The EC2 application IAM role still cannot
retrieve the RDS-managed master secret; no AWS retrieval helper is added. Use a
protected libpq service file for non-secret host/database/user/TLS settings and a
0600 password file via `PGPASSFILE` for the operator connection, or a terminal
password prompt. Never use a password-bearing connection string or `-v password=...`.

**REFERENCE ONLY — DO NOT RUN AGAINST RDS AS PART OF TASKFLOW VALIDATION**

```sh
# PGSERVICEFILE and PGPASSFILE refer to protected operator-managed files.
psql -X --set=ON_ERROR_STOP=1 --dbname='service=taskflow-bootstrap' \
  --file=scripts/production/bootstrap-database.sql
# Separate interactive session, only after successful bootstrap:
psql -X --dbname='service=taskflow-bootstrap'
```

Inside that privileged interactive session, set `password_encryption` to
`scram-sha-256`, then use `\password taskflow_migrator` and
`\password taskflow_app`. These prompts avoid plaintext values in command
history/server logs; see [psql password handling](https://www.postgresql.org/docs/17/app-psql.html).
New roles cannot authenticate by password until assigned one. Never write literal
password placeholders into bootstrap SQL. Disconnect the master when finished.
Credential custody and exact operator sequencing follow the
[MS9.7 deployment runbook](DEPLOYMENT_RUNBOOK.md#credential-and-ca-rotation).

The existing application SecretString must now contain username `taskflow_app`
and its password. MS9.3's materializer rejects other usernames and still retrieves
only app DB/JWT secrets. Migration credentials remain separate: the independent
operator supplies an ephemeral, access-restricted migration password file for
one controlled step. No third Secrets Manager resource is needed for this manual
model, and EC2 IAM is unchanged. Future automated custody belongs to MS10 and
must not make the migrator password available to the long-running backend.

## Verified TLS and public trust material

Use the actual RDS-generated endpoint hostname, not a custom DNS alias or IP.
The exact non-secret runtime URL contract is:

```text
jdbc:postgresql://<RDS_ENDPOINT>:5432/taskflow?sslmode=verify-full&sslrootcert=/opt/taskflow/trust/rds-ca-bundle.pem
```

Percent-encode query values when needed, never the whole URL. No username or
password is allowed in it. Production validation accepts only those two query
parameters, rejecting duplicates and SSL-factory/hostname-verifier overrides.
The endpoint is supplied by operator configuration (MS9.7); no AWS lookup occurs.
The validator checks URL structure, not AWS endpoint existence. pgJDBC performs
certificate-chain and hostname verification at connection time. `require` only
encrypts and is not the target; see [pgJDBC SSL modes](https://jdbc.postgresql.org/documentation/ssl/).

Use the regional **eu-west-1-bundle.pem** from the official AWS RDS HTTPS truststore:
`https://truststore.pki.rds.amazonaws.com/eu-west-1/eu-west-1-bundle.pem`.
This public CA material is deliberately not vendored in Git and is not a secret.
An independent operator downloads it during controlled preparation, validates
non-empty PEM certificates and reviews root identities/validity, then atomically
installs it at `/opt/taskflow/trust/rds-ca-bundle.pem`, root-owned 0644 in a 0755
root-owned directory. No download occurs during TaskFlow validation.

**REFERENCE ONLY — DO NOT RUN AS PART OF TASKFLOW VALIDATION**

```sh
set -euo pipefail
install -d -o root -g root -m 0755 /opt/taskflow/trust
bundle_tmp=$(mktemp /opt/taskflow/trust/.rds-ca.XXXXXXXX)
trap 'rm -f -- "$bundle_tmp"' EXIT
curl --fail --silent --show-error --proto '=https' --tlsv1.2 \
  --output "$bundle_tmp" \
  https://truststore.pki.rds.amazonaws.com/eu-west-1/eu-west-1-bundle.pem
test -s "$bundle_tmp"
openssl crl2pkcs7 -nocrl -certfile "$bundle_tmp" |
  openssl pkcs7 -print_certs -noout
# Operator reviews roots and validity before publication.
chown root:root "$bundle_tmp"
chmod 0644 "$bundle_tmp"
mv -f -- "$bundle_tmp" /opt/taskflow/trust/rds-ca-bundle.pem
```

The commands fail on download/parse errors; they are not a substitute for the
operator's review. Do not use `curl -k`, an arbitrary mirror or an individual
server certificate fingerprint. Trust AWS root CAs, not a captured intermediate
server chain, because intermediates can rotate. AWS documents the regional bundle
and root guidance in [RDS certificate trust](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html).

Production Compose mounts this file read-only into backend at the identical path.
`TASKFLOW_RDS_CA_FILE` optionally changes the host source only, useful for static
fixtures; the container path is fixed. `create_host_path: false` prevents silently
creating a directory for a missing file. Frontend gets no CA mount. The file is
outside `/run/taskflow/secrets`; all MS9.3 secret ownership and isolation remain.
Startup checks require a readable, non-empty file; only a real verifying JDBC
connection establishes certificate correctness. Local path fixtures do not prove
RDS TLS behavior.

Before AWS CA expiry/rotation, refresh the regional bundle from the same official
source, review roots/validity and validate connectivity in an independently
operated environment. Keep the new root available before server CA changes.
Recreate containers after atomic source-file replacement to remount the new inode
and establish new JDBC connections; do not assume existing pooled connections
or bind mounts immediately use updated trust. MS9.7 owns coordination and recovery.

## One-shot migrations reuse the release artifact

`com.taskflow.operations.DatabaseMigration` is an alternative entry point in the
**same reviewed backend JAR/image**, using the existing Flyway libraries and
packaged `db/migration` resources. It starts no Spring application, HTTP server,
JPA context or JWT processing. This avoids a second Flyway image/version and a
parallel migration-file packaging path. The ordinary Boot main class stays
`BackendApplication`; Dockerfiles need no change.

[run-migrations.sh](../scripts/production/run-migrations.sh) invokes Boot's
PropertiesLauncher with that alternative main. It requires a reviewed backend
JAR path, non-secret `TASKFLOW_DB_URL` and `TASKFLOW_MIGRATION_PASSWORD_FILE`.
Only the file path reaches the environment/arguments, never its contents.
The entry point always validates verify-full and the fixed CA path, reads a
non-blank password file and uses the fixed `taskflow_migrator` username. No local
TLS bypass is exposed in the helper or production entry point. Clean is disabled,
Flyway naming validation is enabled, and no automatic repair/baseline is performed.
After any migration attempt, if history exists, runtime mutation rights on it
are removed (default table grants initially include newly created history).
Failure exits nonzero with a fixed diagnostic; no exception stack or password is
printed by the entry point. Migration files themselves must never contain secrets.

For a real one-shot invocation of the backend image, override its entrypoint to
Java/PropertiesLauncher, mount only the public CA and migration password, and
provide the non-secret URL and password-file path. Preserve UID 10001, read-only
root, tmpfs and metadata isolation; grant the password file 10001:10001/0400 under
a protected ephemeral operator directory such as `/run/taskflow-migration`.
Do not mount app/JWT/master secrets or pass migration credentials to Compose.
An operator can also use the helper with Java 21 and the exact release JAR on a
controlled private-network runner. MS9.7 chooses and coordinates execution; MS9.4
does not add a deployment system or execute either real workflow.

## Ordering, failure and compatibility

The required sequence is: RDS exists → CA trust available → privileged role
bootstrap → app secret contains `taskflow_app` → migration credential available
only to the controlled step → Flyway succeeds → backend starts as `taskflow_app`
→ Hibernate validates the schema.

Production Compose fixes `SPRING_FLYWAY_ENABLED=false` and enables the production
DB contract check. Runtime rejects another DB role, enabled Flyway, a Hibernate
mode other than `validate`, a nonconforming URL or missing CA file. This is a
runtime override, not a new Spring profile. Local Compose is unchanged: its single
local user retains automatic Flyway and requires neither role bootstrap nor RDS CA.

Migration failure means **do not start or recreate backend**. The migration helper
never launches application containers; MS9.7 gates deployment on its exit
status. Keep an existing healthy version only while schema-compatible; otherwise
contain the incident. Schema changes already
committed by earlier migrations may remain after a later failure. Immutability
of image digests alone therefore does not make rollback safe. Future migrations
should expand first, deploy mutually compatible code, then contract/destructively
clean up later. Existing migrations remain immutable. Rollback target selection,
schema/application compatibility analysis and migration-failure decisions follow
the [MS9.7 rollback gates](DEPLOYMENT_RUNBOOK.md#rollback-and-failure-decisions).
DB PITR/snapshot recovery follows the [disaster-recovery runbook](DISASTER_RECOVERY.md).

## Local verification boundary

Disposable PostgreSQL tests execute the actual bootstrap SQL, migrate all six
unchanged migrations as migrator, check repeated bootstrap/migration, then start
real backend wiring as app with Flyway disabled and Hibernate validate. They
exercise application CRUD, ownership/history visibility, denied CREATE TABLE,
DROP/ALTER TABLE, CREATE ROLE/DATABASE, schema/temp creation, TRUNCATE and history
mutation; future table/identity-sequence grants are verified. A failed Flyway
validation prevents a simulated deployment action while the current app remains
usable. A local database-owning CREATEROLE operator without superuser simulates bootstrap;
it does not establish RDS-specific administrative acceptance.

Verification passed: all 485 backend tests and JAR packaging; the strengthened
non-superuser bootstrap scenario also passed separately. The packaged migration
main rejected invalid TLS before opening a connection. Offline materializer/helper
tests and static Compose checks passed without real AWS commands.

Focused tests separately validate URL parsing, weak-mode/override rejection,
CA path/readability requirements and production-role/Flyway guards. Local PG
uses its isolated test connection; these tests do not claim RDS certificate or
hostname behavior. No real endpoint, account identifier, master credential or
certificate is required. MS9.5–MS9.7 are documented in their respective
contracts/runbooks; MS9.8 remains deferred.

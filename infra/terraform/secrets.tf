resource "aws_secretsmanager_secret" "app_database" {
  name                    = "${local.name_prefix}/database/application"
  description             = "Dedicated application PostgreSQL role credentials; value populated outside Terraform."
  recovery_window_in_days = 7
  tags                    = { Component = "database" }
}

resource "aws_secretsmanager_secret" "jwt_signing" {
  name                    = "${local.name_prefix}/jwt/signing"
  description             = "TASKFLOW_JWT_SECRET_BASE64 runtime value; must decode to 32 bytes, populated outside Terraform."
  recovery_window_in_days = 7
  tags                    = { Component = "application" }
}

resource "aws_db_subnet_group" "app" {
  name       = "${local.name_prefix}-db"
  subnet_ids = [aws_subnet.database["a"].id, aws_subnet.database["b"].id]
  tags = {
    Name      = "${local.name_prefix}-db"
    Component = "database"
    Tier      = "database"
  }
}

resource "aws_db_instance" "postgres" {
  identifier                  = "${local.name_prefix}-db"
  engine                      = "postgres"
  engine_version              = var.db_engine_version
  instance_class              = var.db_instance_class
  db_name                     = "taskflow"
  username                    = "taskflowadmin"
  manage_master_user_password = true

  db_subnet_group_name   = aws_db_subnet_group.app.name
  vpc_security_group_ids = [aws_security_group.db.id]
  publicly_accessible    = false
  multi_az               = false
  availability_zone      = local.availability_zones["a"]
  port                   = 5432

  allocated_storage = 20
  storage_type      = "gp3"
  storage_encrypted = true

  backup_retention_period   = 7
  delete_automated_backups  = false
  deletion_protection       = true
  skip_final_snapshot       = false
  final_snapshot_identifier = "${local.name_prefix}-db-final"
  copy_tags_to_snapshot     = true

  auto_minor_version_upgrade  = true
  allow_major_version_upgrade = false
  apply_immediately           = false

  iam_database_authentication_enabled = false
  performance_insights_enabled        = false
  database_insights_mode              = "standard"
  monitoring_interval                 = 0
  enabled_cloudwatch_logs_exports     = ["postgresql"]

  # Create the destination first so exported logs have controlled retention.
  depends_on = [aws_cloudwatch_log_group.postgres]

  tags = {
    Name      = "${local.name_prefix}-db"
    Component = "database"
    Tier      = "database"
  }
}

locals {
  alarm_notification_actions = var.alarm_topic_arn == "" ? [] : [var.alarm_topic_arn]
}

resource "aws_cloudwatch_log_group" "postgres" {
  name              = "/aws/rds/instance/${local.name_prefix}-db/postgresql"
  retention_in_days = 14
  tags              = { Component = "database" }
}

resource "aws_cloudwatch_metric_alarm" "rds_cpu" {
  alarm_name                = "${local.name_prefix}-rds-cpu"
  alarm_description         = "RDS CPU above 80 percent for fifteen minutes; optional external notifications only."
  namespace                 = "AWS/RDS"
  metric_name               = "CPUUtilization"
  statistic                 = "Average"
  comparison_operator       = "GreaterThanThreshold"
  threshold                 = 80
  period                    = 300
  evaluation_periods        = 3
  treat_missing_data        = "missing"
  alarm_actions             = local.alarm_notification_actions
  ok_actions                = local.alarm_notification_actions
  insufficient_data_actions = []
  dimensions = {
    DBInstanceIdentifier = aws_db_instance.postgres.identifier
  }
  tags = { Component = "database" }
}

resource "aws_cloudwatch_metric_alarm" "rds_storage" {
  alarm_name                = "${local.name_prefix}-rds-storage"
  alarm_description         = "RDS free storage below 5 GiB, one quarter of the reference allocation, for fifteen minutes; optional external notifications only."
  namespace                 = "AWS/RDS"
  metric_name               = "FreeStorageSpace"
  statistic                 = "Minimum"
  comparison_operator       = "LessThanThreshold"
  threshold                 = 5 * 1024 * 1024 * 1024
  period                    = 300
  evaluation_periods        = 3
  treat_missing_data        = "missing"
  alarm_actions             = local.alarm_notification_actions
  ok_actions                = local.alarm_notification_actions
  insufficient_data_actions = []
  dimensions = {
    DBInstanceIdentifier = aws_db_instance.postgres.identifier
  }
  tags = { Component = "database" }
}

resource "aws_cloudwatch_log_group" "app" {
  for_each          = toset(["backend", "nginx", "host"])
  name              = "/${var.project_name}/${var.environment}/${each.key}"
  retention_in_days = 14
  tags              = { Component = each.key }
}

resource "aws_cloudwatch_metric_alarm" "ec2_status" {
  alarm_name                = "${local.name_prefix}-ec2-status"
  alarm_description         = "EC2 status check failure for two minutes; optional external notifications only."
  namespace                 = "AWS/EC2"
  metric_name               = "StatusCheckFailed"
  statistic                 = "Maximum"
  threshold                 = 0
  comparison_operator       = "GreaterThanThreshold"
  period                    = 60
  evaluation_periods        = 2
  treat_missing_data        = "missing"
  alarm_actions             = local.alarm_notification_actions
  ok_actions                = local.alarm_notification_actions
  insufficient_data_actions = []
  dimensions = {
    InstanceId = aws_instance.app.id
  }
  tags = { Component = "observability" }
}

resource "aws_cloudwatch_metric_alarm" "ec2_cpu" {
  alarm_name                = "${local.name_prefix}-ec2-cpu"
  alarm_description         = "Sustained EC2 CPU above 80 percent for fifteen minutes; optional external notifications only."
  namespace                 = "AWS/EC2"
  metric_name               = "CPUUtilization"
  statistic                 = "Average"
  threshold                 = 80
  comparison_operator       = "GreaterThanThreshold"
  period                    = 300
  evaluation_periods        = 3
  treat_missing_data        = "missing"
  alarm_actions             = local.alarm_notification_actions
  ok_actions                = local.alarm_notification_actions
  insufficient_data_actions = []
  dimensions = {
    InstanceId = aws_instance.app.id
  }
  tags = { Component = "observability" }
}

resource "aws_cloudwatch_metric_alarm" "alb_healthy" {
  alarm_name                = "${local.name_prefix}-alb-healthy"
  alarm_description         = "No healthy application target for two minutes; optional external notifications only."
  namespace                 = "AWS/ApplicationELB"
  metric_name               = "HealthyHostCount"
  statistic                 = "Minimum"
  threshold                 = 1
  comparison_operator       = "LessThanThreshold"
  period                    = 60
  evaluation_periods        = 2
  treat_missing_data        = "breaching"
  alarm_actions             = local.alarm_notification_actions
  ok_actions                = local.alarm_notification_actions
  insufficient_data_actions = []
  dimensions = {
    LoadBalancer = aws_lb.app.arn_suffix
    TargetGroup  = aws_lb_target_group.app.arn_suffix
  }
  tags = { Component = "observability" }
}

resource "aws_cloudwatch_metric_alarm" "alb_5xx" {
  alarm_name                = "${local.name_prefix}-alb-5xx"
  alarm_description         = "At least five ALB-generated 5xx responses per five minutes, twice; optional external notifications only."
  namespace                 = "AWS/ApplicationELB"
  metric_name               = "HTTPCode_ELB_5XX_Count"
  statistic                 = "Sum"
  threshold                 = 5
  comparison_operator       = "GreaterThanOrEqualToThreshold"
  period                    = 300
  evaluation_periods        = 2
  treat_missing_data        = "notBreaching"
  alarm_actions             = local.alarm_notification_actions
  ok_actions                = local.alarm_notification_actions
  insufficient_data_actions = []
  dimensions = {
    LoadBalancer = aws_lb.app.arn_suffix
  }
  tags = { Component = "observability" }
}

resource "aws_cloudwatch_metric_alarm" "host_memory" {
  alarm_name                = "${local.name_prefix}-host-memory"
  alarm_description         = "Host memory at least 85 percent for fifteen minutes; warning only, no remediation."
  namespace                 = "TaskFlow/${var.environment}"
  metric_name               = "mem_used_percent"
  statistic                 = "Average"
  comparison_operator       = "GreaterThanOrEqualToThreshold"
  threshold                 = 85
  period                    = 300
  evaluation_periods        = 3
  treat_missing_data        = "missing"
  alarm_actions             = local.alarm_notification_actions
  ok_actions                = local.alarm_notification_actions
  insufficient_data_actions = []
  # Agent publishes only the InstanceId rollup; root is its sole disk resource.
  dimensions = { InstanceId = aws_instance.app.id }
  tags       = { Component = "observability" }
}

resource "aws_cloudwatch_metric_alarm" "host_root_disk" {
  alarm_name                = "${local.name_prefix}-host-root-disk"
  alarm_description         = "Root filesystem usage at least 85 percent for fifteen minutes; warning only, no remediation."
  namespace                 = "TaskFlow/${var.environment}"
  metric_name               = "disk_used_percent"
  statistic                 = "Average"
  comparison_operator       = "GreaterThanOrEqualToThreshold"
  threshold                 = 85
  period                    = 300
  evaluation_periods        = 3
  treat_missing_data        = "missing"
  alarm_actions             = local.alarm_notification_actions
  ok_actions                = local.alarm_notification_actions
  insufficient_data_actions = []
  # Agent publishes only the InstanceId rollup; root is its sole disk resource.
  dimensions = { InstanceId = aws_instance.app.id }
  tags       = { Component = "observability" }
}

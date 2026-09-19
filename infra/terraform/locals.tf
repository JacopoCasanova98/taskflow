locals {
  name_prefix = "${var.project_name}-${var.environment}"

  # Logical account-relative AZ labels; no live availability discovery.
  availability_zones = {
    a = "${var.aws_region}a"
    b = "${var.aws_region}b"
  }

  common_tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "Terraform"
  }
}

resource "aws_ecr_repository" "app" {
  for_each = toset(["frontend", "backend"])

  name                 = "${local.name_prefix}-${each.key}"
  image_tag_mutability = "IMMUTABLE"

  encryption_configuration {
    encryption_type = "AES256"
  }
  tags = { Component = each.key }
}

# Registry-wide ownership is opt-in: filters do not isolate configuration ownership.
# Otherwise the registry operator must supply BASIC scan-on-push for this prefix.
resource "aws_ecr_registry_scanning_configuration" "taskflow" {
  count     = var.manage_ecr_registry_scanning ? 1 : 0
  scan_type = "BASIC"

  rule {
    scan_frequency = "SCAN_ON_PUSH"
    repository_filter {
      filter      = "${local.name_prefix}-*"
      filter_type = "WILDCARD"
    }
  }
}

resource "aws_ecr_lifecycle_policy" "untagged" {
  for_each   = aws_ecr_repository.app
  repository = each.value.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Expire untagged images after seven days"
      selection = {
        tagStatus   = "untagged"
        countType   = "sinceImagePushed"
        countUnit   = "days"
        countNumber = 7
      }
      action = { type = "expire" }
    }]
  })
}

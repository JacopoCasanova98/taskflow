output "reference_region" {
  description = "Region modelled by the reference architecture, not evidence of a deployment."
  value       = var.aws_region
}

output "environment" {
  description = "Environment modelled by the reference architecture."
  value       = var.environment
}

output "name_prefix" {
  description = "Reusable project/environment prefix for later resource names."
  value       = local.name_prefix
}

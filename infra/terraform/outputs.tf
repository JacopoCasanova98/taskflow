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

output "alb_dns_name" {
  description = "AWS-generated ALB hostname, not the final TaskFlow domain; no ALB exists because this project never applies Terraform."
  value       = aws_lb.app.dns_name
}

output "alb_zone_id" {
  description = "ALB canonical hosted zone ID for a hypothetical external Route 53 alias."
  value       = aws_lb.app.zone_id
}

output "app_instance_id" {
  description = "Application host identifier for reference SSM targeting and diagnostics."
  value       = aws_instance.app.id
}

output "vpc_id" {
  description = "Reference VPC identifier for external infrastructure integrations."
  value       = aws_vpc.this.id
}

output "public_subnet_ids" {
  description = "Public subnet identifiers keyed by logical AZ labels a and b."
  value       = { for key, subnet in aws_subnet.public : key => subnet.id }
}

output "database_subnet_ids" {
  description = "Isolated database subnet identifiers keyed by logical AZ labels a and b."
  value       = { for key, subnet in aws_subnet.database : key => subnet.id }
}

output "security_group_ids" {
  description = "Security boundary identifiers keyed by alb, app and db."
  value = {
    alb = aws_security_group.alb.id
    app = aws_security_group.app.id
    db  = aws_security_group.db.id
  }
}

output "ecr_repository_urls" {
  description = "Repository URLs keyed by frontend and backend for conceptual external release integration."
  value       = { for key, repository in aws_ecr_repository.app : key => repository.repository_url }
}

output "database_endpoint" {
  description = "Private RDS hostname only, reachable through the approved network/security boundary; not a credential, and no database exists in this unapplied project."
  value       = aws_db_instance.postgres.address
}

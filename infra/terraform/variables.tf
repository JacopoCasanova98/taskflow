variable "ec2_ami_id" {
  description = "Required regional Amazon Linux 2023 x86_64 AMI with SSM support; syntax only, never looked up."
  type        = string
  nullable    = false
  validation {
    condition     = can(regex("^ami-([0-9a-f]{8}|[0-9a-f]{17})$", var.ec2_ami_id))
    error_message = "Provide an AMI ID in ami- hexadecimal identifier format."
  }
}

variable "ec2_instance_type" {
  description = "Reference x86_64 instance type; availability and AMI compatibility are operator checks."
  type        = string
  default     = "t3.medium"
  nullable    = false
  validation {
    condition     = can(regex("^[a-z][a-z0-9]*[.][a-z0-9]+$", var.ec2_instance_type))
    error_message = "Provide an EC2 instance type such as t3.medium."
  }
}

variable "root_volume_size_gib" {
  description = "Encrypted gp3 root volume size in GiB; must also fit the selected AMI."
  type        = number
  default     = 30
  nullable    = false
  validation {
    condition     = var.root_volume_size_gib >= 30 && var.root_volume_size_gib <= 16384 && floor(var.root_volume_size_gib) == var.root_volume_size_gib
    error_message = "Root storage must be an integer from 30 to 16384 GiB."
  }
}

variable "acm_certificate_arn" {
  description = "Required externally managed regional ACM certificate ARN for HTTPS; no certificate or DNS lookup."
  type        = string
  nullable    = false
  validation {
    condition     = can(regex("^arn:aws:acm:[a-z0-9-]+:[0-9]{12}:certificate/[a-f0-9-]+$", var.acm_certificate_arn))
    error_message = "Provide a regional ACM certificate ARN."
  }
}

variable "vpc_cidr" {
  description = "IPv4 CIDR for the reference VPC."
  type        = string
  default     = "10.42.0.0/16"
  nullable    = false

  validation {
    condition     = can(cidrnetmask(var.vpc_cidr))
    error_message = "Provide a valid IPv4 CIDR for the VPC."
  }
}

variable "public_subnet_cidrs" {
  description = "Public IPv4 subnet CIDRs for exactly the two logical AZs a and b."
  type        = map(string)
  default = {
    a = "10.42.0.0/24"
    b = "10.42.1.0/24"
  }
  nullable = false

  validation {
    condition     = toset(keys(var.public_subnet_cidrs)) == toset(["a", "b"])
    error_message = "Public subnet CIDRs must have exactly the keys a and b."
  }

  validation {
    condition     = alltrue([for cidr in values(var.public_subnet_cidrs) : can(cidrnetmask(cidr))])
    error_message = "Each public subnet CIDR must be valid IPv4 CIDR notation."
  }
}

variable "database_subnet_cidrs" {
  description = "Isolated database IPv4 subnet CIDRs for exactly the two logical AZs a and b."
  type        = map(string)
  default = {
    a = "10.42.10.0/24"
    b = "10.42.11.0/24"
  }
  nullable = false

  validation {
    condition     = toset(keys(var.database_subnet_cidrs)) == toset(["a", "b"])
    error_message = "Database subnet CIDRs must have exactly the keys a and b."
  }

  validation {
    condition     = alltrue([for cidr in values(var.database_subnet_cidrs) : can(cidrnetmask(cidr))])
    error_message = "Each database subnet CIDR must be valid IPv4 CIDR notation."
  }
}

variable "project_name" {
  description = "Project identifier used in the naming prefix and Project tag."
  type        = string
  default     = "taskflow"
  nullable    = false

  validation {
    condition     = can(regex("^[a-z][a-z0-9]*(-[a-z0-9]+)*$", var.project_name))
    error_message = "Use lowercase letters, digits and single separating hyphens, starting with a letter."
  }
}

variable "environment" {
  description = "Reference environment being modelled; prod does not indicate a deployed stack."
  type        = string
  default     = "prod"
  nullable    = false

  validation {
    condition     = can(regex("^[a-z][a-z0-9]*(-[a-z0-9]+)*$", var.environment))
    error_message = "Use lowercase letters, digits and single separating hyphens, starting with a letter."
  }
}

variable "aws_region" {
  description = "AWS reference region; Ireland is the architecture default. No live region lookup is performed."
  type        = string
  default     = "eu-west-1"
  nullable    = false

  validation {
    condition     = can(regex("^[a-z]{2}(-[a-z]+)+-[0-9]+$", var.aws_region))
    error_message = "Use an AWS region identifier such as eu-west-1."
  }
}

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

variable "subscription_id" {
  description = "Azure for Students subscription ID. Supply through a local tfvars file or TF_VAR_subscription_id."
  type        = string
  sensitive   = true
}

variable "location" {
  description = "Single Azure region used by all workload resources."
  type        = string
  default     = "centralindia"
}

variable "name_prefix" {
  description = "Short lowercase prefix used in Azure resource names."
  type        = string
  default     = "docflow"

  validation {
    condition     = can(regex("^[a-z][a-z0-9]{2,10}$", var.name_prefix))
    error_message = "name_prefix must be 3-11 lowercase alphanumeric characters and start with a letter."
  }
}

variable "budget_alert_email" {
  description = "Private address that receives Azure cost alerts. Supply only through local configuration."
  type        = string
  sensitive   = true
}

variable "budget_amount_usd" {
  description = "Resource-group budget in USD for the short-lived evaluation environment."
  type        = number
  default     = 10
}

variable "budget_start_date" {
  description = "Budget period start in RFC3339 format; Azure requires the first day of a month."
  type        = string
  default     = "2026-10-01T00:00:00Z"
}

variable "budget_end_date" {
  description = "Budget period end in RFC3339 format."
  type        = string
  default     = "2026-12-01T00:00:00Z"
}

variable "deploy_apps" {
  description = "Create Container Apps after the three images have been pushed to the provisioned registry."
  type        = bool
  default     = false
}

variable "image_tag" {
  description = "Immutable image tag shared by the API and worker images."
  type        = string
  default     = "dev"
}

variable "postgresql_sku_name" {
  description = "Lowest practical PostgreSQL Flexible Server burstable SKU."
  type        = string
  default     = "B_Standard_B1ms"
}

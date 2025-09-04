
variable "mailsvc_db_user_pass" {
  type        = string
  description = "Mail-svc database user password"
}

variable "subscription_id" {
  type = string
  description = "Subscription ID"
}

variable "tenant_id" {
  type = string
  description = "Tenant ID"
}

variable "cae_id" {
  type        = string
  description = "ID на Container App Environment-a"
}

variable "rg_name" {
  type        = string
  default     = "mail-svc-rg"
  description = "Resource group, в която е app-a"
}

variable "deploy_version" {
  type        = string
  description = "Версия на deploy-а (ползва се за форсиране на нов revision)"
}
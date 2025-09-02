variable "mailsvc_db_root_pass" {
  type        = string
  description = "Mail-svc database root password"
}

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

variable "client_id" {
  type = string
  description = "CREDENTIALS CHARACTERISTIC"
}

variable "client_secret" {
  type = string
  description = "CREDENTIALS CHARACTERISTIC"
}
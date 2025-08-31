
variable "subscription_id" {
  type        = string
  description = "subscription ID"
}

variable "tenant_id" {
  type = string
  description = "tenant ID"
}

variable "mailsvc_db_root_pass" {
  type = string
  description = "Mail-svc database root password"
}

variable "mailsvc_db_user_pass" {
  type = string
  description = "Mail-svc database user password"
}

# variable "image_1_name" {
#   type = string
#   description = "Docker image name"
#
# }variable "image_2_name" {
#   type = string
#   description = "Docker image name"
# }

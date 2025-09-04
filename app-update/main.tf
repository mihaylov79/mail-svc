terraform {
  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "4.42.0"
    }
    azapi = {
      source  = "azure/azapi"
      version = "1.14.0"
    }
  }
}

provider "azurerm" {
  features {}
  subscription_id = var.subscription_id
  tenant_id       = var.tenant_id
}

resource "azurerm_container_app" "caapp" {
  container_app_environment_id = var.cae_id
  name                         = "mail-svc-app-container-app"
  resource_group_name          = "mail-svc-rg"
  revision_mode                = "Single"

  secret {
    name  = "mailsvc-db-user-pass"
    value = var.mailsvc_db_user_pass
  }

  lifecycle {
    ignore_changes = [
      secret,
    ]
  }

  template {
    container {
      cpu    = 0.75
      image  = "mihaylov79/mail-svc:latest"
      memory = "1.5Gi"
      name   = "mail-svc-app"

      env {
        name  = "DB_HOST"
        value = "mail-svc-db-container-app.mail-svc-env.internal"
      }
      env {
        name  = "DB_USER"
        value = "admin_user"
      }
      env {
        name        = "DB_PASS"
        secret_name = "mailsvc-db-user-pass"
      }
      env {
        name  = "DB_NAME"
        value = "mailsvc_db"
      }
      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "prod"
      }

      env {
        name = "DEPLOY_VERSION"
        value = var.deploy_version
      }
    }
  }

  ingress {
    external_enabled = true
    target_port      = 8080

    traffic_weight {
      latest_revision = true
      percentage       = 100
    }
  }

  }




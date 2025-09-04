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

  template {
    container {
      cpu    = 0.75
      image  = "mihaylov79/mail-svc:latest"
      memory = "1.5Gi"
      name   = "mail-svc-app"


      env {
        name = "DEPLOY_VERSION"
        value = var.deploy_version
      }
    }
  }

  }




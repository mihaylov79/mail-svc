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

provider "azapi" {
  client_id     = var.client_id
  client_secret = var.client_secret
  tenant_id     = var.tenant_id
  subscription_id = var.subscription_id
}

# provider "azapi" {
#   use_cli = true
# }

resource "azurerm_resource_group" "rg" {
  name     = "mail-svc-rg"
  location = "Switzerland North"
}

resource "azurerm_managed_disk" "disk" {
  create_option        = "Empty"
  location             = azurerm_resource_group.rg.location
  name                 = "mysql-disk"
  resource_group_name  = azurerm_resource_group.rg.name
  storage_account_type = "Standard_LRS"
  disk_size_gb = 2
}


resource "azurerm_container_app_environment" "cae" {
  location            = azurerm_resource_group.rg.location
  name                = "mail-svc-env"
  resource_group_name = azurerm_resource_group.rg.name
}



resource "azurerm_container_app" "cadb" {
  container_app_environment_id = azurerm_container_app_environment.cae.id
  name                         = "mail-svc-db-container-app"
  resource_group_name          = azurerm_resource_group.rg.name
  revision_mode                = "Single"

  secret {
    name  = "mailsvc-db-root-pass"
    value = var.mailsvc_db_root_pass
  }
  secret {
    name  = "mailsvc-db-user-pass"
    value = var.mailsvc_db_user_pass
  }

  template {
    container {
      cpu    = 0.5
      image  = "mysql:8.0"
      memory = "1Gi"
      name   = "mailsvc-db"

      env {
        name        = "MYSQL_ROOT_PASSWORD"
        secret_name = "mailsvc-db-root-pass"
      }
      env {
        name  = "MYSQL_DATABASE"
        value = "mailsvc_db"
      }
      env {
        name  = "MYSQL_USER"
        value = "admin_user"
      }
      env {
        name        = "MYSQL_PASSWORD"
        secret_name = "mailsvc-db-user-pass"
      }

      volume_mounts {
        name = "mail-svc-data"
        path = "/var/lib/mysql"
      }
    }

    # Свързване на volume чрез AzAPI resource
    volume {
      name         = "mail-svc-data"
      storage_type = "AzureDisk"
      storage_name = azurerm_managed_disk.disk.name
    }
  }

  ingress {
    external_enabled = false
    target_port      = 3306

    traffic_weight {
      latest_revision = true
      percentage       = 100
    }
  }
}

resource "azurerm_container_app" "caapp" {
  container_app_environment_id = azurerm_container_app_environment.cae.id
  name                         = "mail-svc-app-container-app"
  resource_group_name          = azurerm_resource_group.rg.name
  revision_mode                = "Single"

  secret {
    name  = "mailsvc-db-user-pass"
    value = var.mailsvc_db_user_pass
  }


  template {
    container {
      cpu    = 0.75
      image  = "mihaylov79/mail-svc:latest"
      memory = "1.5Gi"
      name   = "mail-svc-app"

      env {
        name  = "DB_HOST"
        value = "${azurerm_container_app.cadb.name}.${azurerm_container_app_environment.cae.name}.internal"
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

resource "azurerm_container_app" "api" {
  count                        = var.deploy_apps ? 1 : 0
  name                         = "ca-${var.name_prefix}-api-${local.suffix}"
  container_app_environment_id = azapi_resource.container_app_environment.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
  workload_profile_name        = "Consumption"
  tags                         = local.tags

  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.apps.id]
  }

  registry {
    server   = azurerm_container_registry.main.login_server
    identity = azurerm_user_assigned_identity.apps.id
  }

  secret {
    name  = "database-password"
    value = azurerm_key_vault_secret.database_password.value
  }
  secret {
    name  = "storage-connection"
    value = azurerm_key_vault_secret.storage_connection.value
  }
  secret {
    name  = "document-key"
    value = azurerm_key_vault_secret.document_key.value
  }

  ingress {
    external_enabled = true
    target_port      = 8080
    transport        = "auto"
    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  template {
    min_replicas = 1
    max_replicas = 2

    http_scale_rule {
      name                = "api-http"
      concurrent_requests = 20
    }

    container {
      name   = "api"
      image  = "${azurerm_container_registry.main.login_server}/docflow-api:${coalesce(var.api_image_tag, var.image_tag)}"
      cpu    = 0.5
      memory = "1Gi"

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "cloud"
      }
      env {
        name  = "API_BIND_ADDRESS"
        value = "0.0.0.0"
      }
      env {
        name  = "DB_URL"
        value = local.database_url
      }
      env {
        name  = "DB_USER"
        value = azurerm_postgresql_flexible_server.main.administrator_login
      }
      env {
        name        = "DB_PASSWORD"
        secret_name = "database-password"
      }
      env {
        name        = "STORAGE_CONNECTION"
        secret_name = "storage-connection"
      }
      env {
        name        = "APP_DOCUMENT_SECRET"
        secret_name = "document-key"
      }
      env {
        name  = "APP_COOKIE_SECURE"
        value = "true"
      }
      env {
        name  = "APP_COOKIE_SAME_SITE"
        value = "Strict"
      }
      env {
        name  = "JAVA_TOOL_OPTIONS"
        value = "-Xmx512m -Dio.netty.transport.noNative=true"
      }
    }
  }

  depends_on = [
    azurerm_role_assignment.acr_pull,
    azurerm_key_vault_access_policy.apps,
    azurerm_postgresql_flexible_server_firewall_rule.azure_services
  ]
}

resource "azurerm_container_app" "pdf_worker" {
  count                        = var.deploy_apps ? 1 : 0
  name                         = "ca-${var.name_prefix}-pdf-${local.suffix}"
  container_app_environment_id = azapi_resource.container_app_environment.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
  workload_profile_name        = "Consumption"
  tags                         = local.tags

  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.apps.id]
  }

  registry {
    server   = azurerm_container_registry.main.login_server
    identity = azurerm_user_assigned_identity.apps.id
  }

  secret {
    name  = "database-password"
    value = azurerm_key_vault_secret.database_password.value
  }
  secret {
    name  = "storage-connection"
    value = azurerm_key_vault_secret.storage_connection.value
  }
  secret {
    name  = "document-key"
    value = azurerm_key_vault_secret.document_key.value
  }

  template {
    min_replicas = 0
    max_replicas = 3

    azure_queue_scale_rule {
      name         = "pdf-queue"
      queue_name   = azurerm_storage_queue.pdf_jobs.name
      queue_length = 1
      authentication {
        secret_name       = "storage-connection"
        trigger_parameter = "connection"
      }
    }

    container {
      name   = "pdf-worker"
      image  = "${azurerm_container_registry.main.login_server}/docflow-pdf-worker:${var.image_tag}"
      cpu    = 0.5
      memory = "1Gi"

      env {
        name  = "DB_URL"
        value = local.database_url
      }
      env {
        name  = "DB_USER"
        value = azurerm_postgresql_flexible_server.main.administrator_login
      }
      env {
        name        = "DB_PASSWORD"
        secret_name = "database-password"
      }
      env {
        name        = "STORAGE_CONNECTION"
        secret_name = "storage-connection"
      }
      env {
        name        = "APP_DOCUMENT_SECRET"
        secret_name = "document-key"
      }
      env {
        name  = "JAVA_TOOL_OPTIONS"
        value = "-Xmx384m -Dio.netty.transport.noNative=true"
      }
    }
  }

  depends_on = [
    azurerm_role_assignment.acr_pull,
    azurerm_key_vault_access_policy.apps,
    azurerm_postgresql_flexible_server_firewall_rule.azure_services
  ]
}

resource "azurerm_container_app" "office_worker" {
  count                        = var.deploy_apps ? 1 : 0
  name                         = "ca-${var.name_prefix}-office-${local.suffix}"
  container_app_environment_id = azapi_resource.container_app_environment.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
  workload_profile_name        = "Consumption"
  tags                         = local.tags

  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.apps.id]
  }

  registry {
    server   = azurerm_container_registry.main.login_server
    identity = azurerm_user_assigned_identity.apps.id
  }

  secret {
    name  = "database-password"
    value = azurerm_key_vault_secret.database_password.value
  }
  secret {
    name  = "storage-connection"
    value = azurerm_key_vault_secret.storage_connection.value
  }

  template {
    min_replicas = 0
    max_replicas = 2

    azure_queue_scale_rule {
      name         = "office-queue"
      queue_name   = azurerm_storage_queue.office_jobs.name
      queue_length = 1
      authentication {
        secret_name       = "storage-connection"
        trigger_parameter = "connection"
      }
    }

    container {
      name   = "office-worker"
      image  = "${azurerm_container_registry.main.login_server}/docflow-office-worker:${var.image_tag}"
      cpu    = 1.0
      memory = "2Gi"

      env {
        name  = "DB_URL"
        value = local.database_url
      }
      env {
        name  = "DB_USER"
        value = azurerm_postgresql_flexible_server.main.administrator_login
      }
      env {
        name        = "DB_PASSWORD"
        secret_name = "database-password"
      }
      env {
        name        = "STORAGE_CONNECTION"
        secret_name = "storage-connection"
      }
      env {
        name  = "JAVA_TOOL_OPTIONS"
        value = "-Xmx384m -Dio.netty.transport.noNative=true"
      }
    }
  }

  depends_on = [
    azurerm_role_assignment.acr_pull,
    azurerm_key_vault_access_policy.apps,
    azurerm_postgresql_flexible_server_firewall_rule.azure_services
  ]
}

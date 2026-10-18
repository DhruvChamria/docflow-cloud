resource "azurerm_container_app" "api" {
  count                        = var.deploy_apps ? 1 : 0
  name                         = "ca-${var.name_prefix}-api-${local.suffix}"
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
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
    name                = "database-password"
    identity            = azurerm_user_assigned_identity.apps.id
    key_vault_secret_id = azurerm_key_vault_secret.database_password.versionless_id
  }
  secret {
    name                = "storage-connection"
    identity            = azurerm_user_assigned_identity.apps.id
    key_vault_secret_id = azurerm_key_vault_secret.storage_connection.versionless_id
  }
  secret {
    name                = "document-key"
    identity            = azurerm_user_assigned_identity.apps.id
    key_vault_secret_id = azurerm_key_vault_secret.document_key.versionless_id
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
      image  = "${azurerm_container_registry.main.login_server}/docflow-api:${var.image_tag}"
      cpu    = 0.5
      memory = "1Gi"

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "cloud"
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
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
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
    name                = "database-password"
    identity            = azurerm_user_assigned_identity.apps.id
    key_vault_secret_id = azurerm_key_vault_secret.database_password.versionless_id
  }
  secret {
    name                = "storage-connection"
    identity            = azurerm_user_assigned_identity.apps.id
    key_vault_secret_id = azurerm_key_vault_secret.storage_connection.versionless_id
  }
  secret {
    name                = "document-key"
    identity            = azurerm_user_assigned_identity.apps.id
    key_vault_secret_id = azurerm_key_vault_secret.document_key.versionless_id
  }

  template {
    min_replicas = 0
    max_replicas = 3

    custom_scale_rule {
      name             = "pdf-queue"
      custom_rule_type = "azure-queue"
      metadata = {
        queueName   = azurerm_storage_queue.pdf_jobs.name
        queueLength = "1"
      }
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
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
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
    name                = "database-password"
    identity            = azurerm_user_assigned_identity.apps.id
    key_vault_secret_id = azurerm_key_vault_secret.database_password.versionless_id
  }
  secret {
    name                = "storage-connection"
    identity            = azurerm_user_assigned_identity.apps.id
    key_vault_secret_id = azurerm_key_vault_secret.storage_connection.versionless_id
  }

  template {
    min_replicas = 0
    max_replicas = 2

    custom_scale_rule {
      name             = "office-queue"
      custom_rule_type = "azure-queue"
      metadata = {
        queueName   = azurerm_storage_queue.office_jobs.name
        queueLength = "1"
      }
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

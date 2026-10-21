output "resource_group_name" {
  value = azurerm_resource_group.main.name
}

output "container_registry_name" {
  value = azurerm_container_registry.main.name
}

output "container_registry_login_server" {
  value = azurerm_container_registry.main.login_server
}

output "api_url" {
  value = var.deploy_apps ? "https://${azurerm_container_app.api[0].ingress[0].fqdn}" : null
}

output "teardown_command" {
  value = "terraform destroy -var-file=dev.tfvars"
}

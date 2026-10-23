# Azure development environment

This Terraform root creates the short-lived DocFlow Cloud evaluation environment. It uses
one region and one resource group, an Azure Container Registry Basic instance, Storage
Blob/Queue, PostgreSQL Flexible Server `B1ms`, Key Vault, Log Analytics, a Workload Profiles
Container Apps environment using its built-in Consumption profile, and an INR 800
resource-group budget with four alerts.

Deployment is intentionally two-stage because Container Apps cannot pull images before the
registry exists:

1. Copy `terraform.tfvars.example` to the ignored `dev.tfvars`, then enter the active Azure
   for Students subscription ID and private alert address.
2. Run `terraform init`, `terraform fmt -check`, `terraform validate`, and review a saved plan
   with `deploy_apps = false`.
3. After explicit approval, apply infrastructure, build/tag the three images, sign in to the
   output registry, and push the shared immutable image tag.
4. Change `deploy_apps = true`, review a second saved plan, and apply the three Container Apps.
5. Run cloud health, guest merge, account isolation, PDF-worker, and office-worker smoke tests.
6. Capture evidence, then destroy the resource group after evaluation when explicitly approved.

Never commit `dev.tfvars`, plan files, `.terraform/`, state files, Azure IDs, alert addresses,
registry credentials, database credentials, storage keys, or document-encryption keys. State
is local for this single-user lab deployment and contains generated secrets; store it only on
the workstation and delete it after confirmed teardown/evidence retention.

The API remains at one minimum replica for dispatch and HTTP availability. Both workers use
Azure Queue KEDA rules with zero minimum replicas. PostgreSQL allows Azure-service access via
the provider's `0.0.0.0` service rule for this short-lived lab environment; record that as a
demo tradeoff, not a production security pattern.

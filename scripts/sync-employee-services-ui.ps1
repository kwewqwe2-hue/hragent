$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Copy-Item -LiteralPath (Join-Path $projectRoot 'hragent-chat/src/components/EmployeePulse.vue') -Destination (Join-Path $projectRoot 'hragentv1/frontend/src/components/EmployeePulse.vue') -Force
$source = Join-Path $projectRoot 'hragent-chat/src/components/EmployeeServices.vue'
$target = Join-Path $projectRoot 'hragentv1/frontend/src/components/EmployeeServices.vue'
# The two independently built Vue clients share this component. Keep the chat copy canonical.
Copy-Item -LiteralPath $source -Destination $target -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'hragent-chat/src/components/EmployeeRelations.vue') -Destination (Join-Path $projectRoot 'hragentv1/frontend/src/components/EmployeeRelations.vue') -Force
Copy-Item -LiteralPath (Join-Path $projectRoot 'hragent-chat/src/components/CareWorkspace.vue') -Destination (Join-Path $projectRoot 'hragentv1/frontend/src/components/CareWorkspace.vue') -Force

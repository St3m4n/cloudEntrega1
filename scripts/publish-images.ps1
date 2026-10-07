param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[0-9]{12}$')][string]$AccountId,
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-z0-9-]+$')][string]$Region,
    [ValidatePattern('^[a-zA-Z0-9._-]+$')][string]$Tag='1.0.0'
)
$ErrorActionPreference='Stop'
$workspaceRoot=Split-Path $PSScriptRoot -Parent
$registry="$AccountId.dkr.ecr.$Region.amazonaws.com"
$services=@('bff','catalog','orders','audit','report','notify','rabbit-admin','kafka-admin')
Push-Location $workspaceRoot
try {
    $loginPassword=& aws ecr get-login-password --region $Region
    if($LASTEXITCODE -ne 0) { throw 'No se pudo obtener acceso ECR.' }
    $loginPassword | & docker login --username AWS --password-stdin $registry
    Clear-Variable loginPassword
    if($LASTEXITCODE -ne 0) { throw 'Login Docker/ECR falló.' }
    foreach($service in $services) {
        $repository="pedidos360/$service"
        $result=& aws ecr describe-repositories --region $Region --repository-names $repository 2>&1
        if($LASTEXITCODE -ne 0) {
            if(($result | Out-String) -notmatch 'RepositoryNotFoundException') { throw "No se pudo consultar ECR: $result" }
            & aws ecr create-repository --region $Region --repository-name $repository --image-scanning-configuration scanOnPush=true --encryption-configuration encryptionType=AES256
            if($LASTEXITCODE -ne 0) { throw "No se pudo crear $repository" }
        }
        $image="${registry}/${repository}:$Tag"
        & docker build -f backend/Dockerfile --build-arg "SERVICE=ms-pedidos360-$service" -t $image .
        if($LASTEXITCODE -ne 0) { throw "Build falló: $service" }
        & docker push $image
        if($LASTEXITCODE -ne 0) { throw "Push falló: $service" }
    }
} finally { Pop-Location }

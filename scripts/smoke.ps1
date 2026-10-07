param([string]$BaseUrl='http://localhost:8080')
$ErrorActionPreference='Stop'
foreach($key in @('P360_ADMIN_TOKEN','P360_OPERATOR_TOKEN','P360_CUSTOMER_TOKEN')) {
    if(-not [Environment]::GetEnvironmentVariable($key)) { throw "Configura $key con un access token de prueba." }
}
function Invoke-P360([string]$method,[string]$path,[string]$token,$body=$null) {
    $parameters=@{Method=$method;Uri=($BaseUrl.TrimEnd('/')+$path);Headers=@{Authorization="Bearer $token"};TimeoutSec=30}
    if($null -ne $body) { $parameters['ContentType']='application/json';$parameters['Body']=($body | ConvertTo-Json -Depth 10) }
    Invoke-RestMethod @parameters
}
$product=Invoke-P360 POST /api/catalog/products $env:P360_ADMIN_TOKEN @{nombre='Producto smoke';categoria='Pruebas';descripcion='Creado por smoke.ps1';precio=1000;stock=5}
$order=Invoke-P360 POST /api/orders $env:P360_CUSTOMER_TOKEN @{email='smoke@example.com';items=@(@{productId=$product.id;quantity=2})}
# Uses an example mailbox. Configure SMTP/Mailpit before running; each transition sends a task.
foreach($status in @('ACEPTADO','ACEPTADO','EN_PREPARACION','DESPACHADO','ENTREGADO')) {
    $null=Invoke-P360 PUT "/api/orders/$($order.id)/status" $env:P360_OPERATOR_TOKEN @{status=$status}
}
$stock=Invoke-P360 GET "/api/catalog/products/$($product.id)" $env:P360_ADMIN_TOKEN
if($stock.stock -ne 3) { throw 'Stock incorrecto o aceptación duplicada.' }
$found=$false
for($attempt=0;$attempt -lt 30;$attempt++) {
    $events=Invoke-P360 GET "/api/audit/events?orderId=$($order.id)" $env:P360_ADMIN_TOKEN
    $top=Invoke-P360 GET '/api/report/top-products?range=last24h' $env:P360_ADMIN_TOKEN
    if($events.Count -ge 5 -and ($top | Where-Object { $_.productId -eq $product.id -and $_.quantity -eq 2 })) { $found=$true;break }
    Start-Sleep -Seconds 1
}
if(-not $found) { throw 'Audit/report no reflejaron Kafka en 30 segundos. Revisa outbox, brokers y DLT.' }
$cluster=Invoke-P360 GET /api/admin/kafka/cluster $env:P360_ADMIN_TOKEN
if($cluster.brokers.Count -ne 3) { throw 'No están activos los tres brokers Kafka.' }
Write-Output "Flujo OK. Pedido $($order.id), producto $($product.id), stock 3; eventos y ventas recibidos."
# Intentionally keeps delivered order/audit evidence for the defense. Delete only the test product if desired.

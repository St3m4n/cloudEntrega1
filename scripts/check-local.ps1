param([string]$EnvFile='infra/local/.env')
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Net.Http

# Lee solamente puertos; no muestra credenciales del archivo de entorno.
$ports=@{BFF_HOST_PORT=8080;CATALOG_HOST_PORT=8082}
foreach($line in Get-Content -LiteralPath $EnvFile) {
    if($line -match '^\s*(BFF_HOST_PORT|CATALOG_HOST_PORT)\s*=\s*(\d+)\s*$') {
        $ports[$matches[1]]=[int]$matches[2]
    }
}
$services=@(
    @{Name='bff';Port=$ports.BFF_HOST_PORT;Path='/api/me'},
    @{Name='catalog';Port=$ports.CATALOG_HOST_PORT;Path='/api/catalog/products'},
    @{Name='orders';Port=8083;Path='/api/orders'},
    @{Name='audit';Port=8084;Path='/api/audit/events'},
    @{Name='report';Port=8085;Path='/api/report/kpis'},
    @{Name='notify';Port=8086;Path='/v3/api-docs'},
    @{Name='rabbit-admin';Port=8087;Path='/api/admin/rabbit/queues'},
    @{Name='kafka-admin';Port=8088;Path='/api/admin/kafka/cluster'}
)
$results=[System.Collections.Generic.List[object]]::new()
$client=[System.Net.Http.HttpClient]::new()
$client.Timeout=[TimeSpan]::FromSeconds(30)
try {
    foreach($service in $services) {
        foreach($mode in @('salud','sin token','token invalido')) {
            $path=if($mode -eq 'salud') {'/actuator/health'} else {$service.Path}
            $expected=if($mode -eq 'salud') {200} else {401}
            $request=[System.Net.Http.HttpRequestMessage]::new(
                [System.Net.Http.HttpMethod]::Get,"http://localhost:$($service.Port)$path")
            if($mode -eq 'token invalido') {
                $request.Headers.Authorization=[System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer','invalid-test-token')
            }
            $response=$null
            try {
                $response=$client.SendAsync($request).GetAwaiter().GetResult()
                $status=[int]$response.StatusCode
                $passed=$status -eq $expected
                if($mode -eq 'salud' -and $passed) {
                    $body=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult() | ConvertFrom-Json
                    $passed=$body.status -eq 'UP'
                }
                $results.Add([pscustomobject]@{Servicio=$service.Name;Prueba=$mode;HTTP=$status;OK=$passed})
            } catch {
                $results.Add([pscustomobject]@{Servicio=$service.Name;Prueba=$mode;HTTP='error/timeout';OK=$false})
            } finally {
                if($null -ne $response) {$response.Dispose()}
                $request.Dispose()
            }
        }
    }
    foreach($auxiliary in @(
        @{Name='mailpit';Url='http://localhost:8025/api/v1/messages'},
        @{Name='kafka-ui';Url='http://localhost:8090'}
    )) {
        $response=$null
        try {
            $response=$client.GetAsync($auxiliary.Url).GetAwaiter().GetResult()
            $status=[int]$response.StatusCode
            $results.Add([pscustomobject]@{Servicio=$auxiliary.Name;Prueba='disponibilidad';HTTP=$status;OK=($status -eq 200)})
        } catch {
            $results.Add([pscustomobject]@{Servicio=$auxiliary.Name;Prueba='disponibilidad';HTTP='error/timeout';OK=$false})
        } finally {if($null -ne $response) {$response.Dispose()}}
    }
} finally {$client.Dispose()}
$results | Format-Table -AutoSize
if(@($results | Where-Object {-not $_.OK}).Count -gt 0) {exit 1}
Write-Output '26 comprobaciones HTTP aprobadas. El flujo con usuarios autenticados se prueba por separado con smoke.ps1.'

# ── Nimbus Agent Installer (Windows) ────────────────────────────
# Usage: irm https://raw.githubusercontent.com/jonax1337/Nimbus/main/install-agent.ps1 | iex
# ────────────────────────────────────────────────────────────────

$ErrorActionPreference = "Stop"

$RepoOwner = "jonax1337"
$RepoName = "Nimbus"
$InstallDir = "C:\NimbusAgent"
$JavaVersion = 21

function Write-Info($msg)    { Write-Host "[nimbus-agent] " -ForegroundColor Cyan -NoNewline; Write-Host $msg }
function Write-Success($msg) { Write-Host "[nimbus-agent] " -ForegroundColor Green -NoNewline; Write-Host $msg }
function Write-Warn($msg)    { Write-Host "[nimbus-agent] " -ForegroundColor Yellow -NoNewline; Write-Host $msg }
function Write-Err($msg)     { Write-Host "[nimbus-agent] " -ForegroundColor Red -NoNewline; Write-Host $msg }

function Show-Banner {
    Write-Host ""
    Write-Host "   _  __ __ _   __ ___  _ __  ___" -ForegroundColor Cyan
    Write-Host "  / |/ // // \,' // o.)/// /,' _/" -ForegroundColor Cyan
    Write-Host " / || // // \,' // o \/ U /_\ ``." -ForegroundColor Cyan
    Write-Host "/_/|_//_//_/ /_//___,'\_,'/___,'" -ForegroundColor Cyan
    Write-Host "            A G E N T" -ForegroundColor DarkGray
    Write-Host "         Installer" -ForegroundColor DarkGray
    Write-Host ""
}

# ── Check/install Java 21 ───────────────────────────────────────

function Test-Java {
    try {
        $output = & java -version 2>&1 | Select-Object -First 1
        if ($output -match '"(\d+)') {
            $ver = [int]$Matches[1]
            if ($ver -ge $JavaVersion) {
                Write-Success "Java $ver found"
                return $true
            }
            Write-Warn "Java $ver found, but Java $JavaVersion+ is required"
        }
    } catch {
        Write-Warn "Java not found"
    }
    return $false
}

function Install-Java {
    Write-Info "Installing Java $JavaVersion (Eclipse Temurin)..."

    $arch = if ([Environment]::Is64BitOperatingSystem) { "x64" } else { "x86" }
    $apiUrl = "https://api.adoptium.net/v3/assets/latest/$JavaVersion/hotspot?architecture=$arch&image_type=msi&os=windows&vendor=eclipse"

    try {
        $releases = Invoke-RestMethod -Uri $apiUrl -UseBasicParsing
        $msiUrl = $releases[0].binary.installer.link
        $msiName = $releases[0].binary.installer.name

        $tempMsi = Join-Path $env:TEMP $msiName
        Write-Info "Downloading Temurin JDK $JavaVersion..."
        Invoke-WebRequest -Uri $msiUrl -OutFile $tempMsi -UseBasicParsing

        Write-Info "Installing (this may take a moment)..."
        Start-Process msiexec.exe -ArgumentList "/i", "`"$tempMsi`"", "ADDLOCAL=FeatureMain,FeatureJavaHome,FeaturePath", "/quiet", "/norestart" -Wait -NoNewWindow

        Remove-Item $tempMsi -Force -ErrorAction SilentlyContinue

        $env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path", "User")

        if (Test-Java) {
            Write-Success "Java $JavaVersion installed successfully"
        } else {
            Write-Warn "Java installed but verification failed. You may need to restart your terminal."
        }
    } catch {
        Write-Err "Failed to install Java: $_"
        Write-Err "Please install Java $JavaVersion manually from: https://adoptium.net/temurin/releases/"
        exit 1
    }
}

# ── Download latest Nimbus Agent ────────────────────────────────

function Install-NimbusAgent {
    Write-Info "Fetching latest release from GitHub..."

    try {
        $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$RepoOwner/$RepoName/releases/latest" -UseBasicParsing
    } catch {
        Write-Err "Failed to fetch release info from GitHub: $_"
        exit 1
    }

    $tagName = $release.tag_name
    Write-Info "Latest version: $tagName"

    $jarAsset = $release.assets | Where-Object { $_.name -like "*agent*-all.jar" } | Select-Object -First 1
    if (-not $jarAsset) {
        $jarAsset = $release.assets | Where-Object { $_.name -like "*agent*.jar" } | Select-Object -First 1
    }
    if (-not $jarAsset) {
        Write-Err "No agent JAR asset found in release $tagName"
        exit 1
    }

    if (-not (Test-Path $InstallDir)) {
        New-Item -ItemType Directory -Path $InstallDir -Force | Out-Null
    }

    $jarPath = Join-Path $InstallDir "nimbus-agent.jar"
    Write-Info "Downloading Nimbus Agent..."
    Invoke-WebRequest -Uri $jarAsset.browser_download_url -OutFile $jarPath -UseBasicParsing
    Write-Success "Downloaded to $jarPath"
}

# ── Create default config ───────────────────────────────────────

function New-AgentConfig {
    $configPath = Join-Path $InstallDir "agent.toml"
    if (Test-Path $configPath) {
        Write-Info "Config already exists, skipping"
        return
    }

    Write-Info "Creating agent config..."
    Write-Host ""

    $controllerHost = Read-Host "[nimbus-agent] Controller host [127.0.0.1]"
    if ([string]::IsNullOrEmpty($controllerHost)) { $controllerHost = "127.0.0.1" }

    $controllerPort = Read-Host "[nimbus-agent] Controller port [8443]"
    if ([string]::IsNullOrEmpty($controllerPort)) { $controllerPort = "8443" }

    $nodeId = Read-Host "[nimbus-agent] Node ID [$env:COMPUTERNAME]"
    if ([string]::IsNullOrEmpty($nodeId)) { $nodeId = $env:COMPUTERNAME }

    $authToken = Read-Host "[nimbus-agent] Auth token"

    $config = @"
[agent]
node_id = "$nodeId"
controller_host = "$controllerHost"
controller_port = $controllerPort
auth_token = "$authToken"
"@
    Set-Content -Path $configPath -Value $config -Encoding UTF8
    Write-Success "Config saved to $configPath"
}

# ── Create start script ─────────────────────────────────────────

function New-StartScript {
    Write-Info "Creating start scripts..."

    $batContent = @"
@echo off
cd /d "%~dp0"

set JAVA_OPTS=-Xms256M -Xmx512M
set JAVA_OPTS=%JAVA_OPTS% -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200

java %JAVA_OPTS% -jar nimbus-agent.jar %*
"@
    Set-Content -Path (Join-Path $InstallDir "nimbus-agent.bat") -Value $batContent -Encoding ASCII

    $ps1Content = @'
Set-Location $PSScriptRoot
$javaOpts = @("-Xms256M", "-Xmx512M", "-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:MaxGCPauseMillis=200", "-jar", "nimbus-agent.jar")
& java @javaOpts @args
'@
    Set-Content -Path (Join-Path $InstallDir "nimbus-agent.ps1") -Value $ps1Content -Encoding UTF8

    # Add to PATH
    $currentPath = [System.Environment]::GetEnvironmentVariable("Path", "User")
    if ($currentPath -notlike "*$InstallDir*") {
        [System.Environment]::SetEnvironmentVariable("Path", "$currentPath;$InstallDir", "User")
        $env:Path = "$env:Path;$InstallDir"
        Write-Success "Added $InstallDir to PATH"
    }

    Write-Success "Created nimbus-agent.bat and nimbus-agent.ps1"
}

# ── Create Windows service ──────────────────────────────────────

function New-AgentService {
    $answer = Read-Host "[nimbus-agent] Create Windows service for auto-start? [Y/n]"
    if ($answer -in @("n", "no", "N", "No")) {
        return
    }

    $isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
    if (-not $isAdmin) {
        Write-Warn "Creating a Windows service requires administrator rights."
        Write-Warn "Please re-run as Administrator or create the service manually."
        return
    }

    $serviceName = "NimbusAgent"
    $javaPath = (Get-Command java).Source
    $jarPath = Join-Path $InstallDir "nimbus-agent.jar"

    & sc.exe create $serviceName binPath= "`"$javaPath`" -Xms256M -Xmx512M -jar `"$jarPath`"" start= auto DisplayName= "Nimbus Agent"
    & sc.exe description $serviceName "Nimbus Cloud Agent Node"

    Write-Success "Windows service '$serviceName' created"

    $startNow = Read-Host "[nimbus-agent] Start agent now? [Y/n]"
    if ($startNow -notin @("n", "no", "N", "No")) {
        & sc.exe start $serviceName
        Write-Success "Agent started"
    }

    Write-Info "  Status:  sc.exe query NimbusAgent"
    Write-Info "  Stop:    sc.exe stop NimbusAgent"
}

# ── Main ────────────────────────────────────────────────────────

function Main {
    Show-Banner

    if (-not (Test-Java)) {
        Install-Java
    }

    Install-NimbusAgent
    New-AgentConfig
    New-StartScript
    New-AgentService

    Write-Host ""
    Write-Host "Nimbus Agent installed successfully!" -ForegroundColor Green
    Write-Host ""
    Write-Host "  Installation:  " -ForegroundColor Cyan -NoNewline; Write-Host $InstallDir
    Write-Host "  Config:        " -ForegroundColor Cyan -NoNewline; Write-Host "$InstallDir\agent.toml"
    Write-Host "  Start:         " -ForegroundColor Cyan -NoNewline; Write-Host "nimbus-agent.bat"
    Write-Host ""
}

Main

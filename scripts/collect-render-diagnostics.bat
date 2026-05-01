@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem 为 Windows Server 用户保留 .bat 入口，但把重型诊断逻辑放到下方 PowerShell 载荷中执行。
rem 这样既能规避 cmd.exe 转义问题，也能保留双击运行脚本的体验。
chcp 65001 >nul
set "PYTHONIOENCODING=utf-8"

set "SCRIPT_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPT_DIR%.."

rem 当此脚本被复制到受影响的 Windows Server 桌面采集信息时，旧的 "..\logs" 目标很容易被漏看。
rem 在仓库内运行时仍写入项目 logs 目录；找不到 NeoLink 项目标记时，则写到被复制的 bat 旁边。
if exist "%PROJECT_ROOT%\gradlew.bat" (
    set "LOG_DIR=%PROJECT_ROOT%\logs"
) else (
    set "PROJECT_ROOT=%SCRIPT_DIR%"
    set "LOG_DIR=%SCRIPT_DIR%logs"
)

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>nul

for /f %%I in ('powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set "STAMP=%%I"
if not defined STAMP set "STAMP=unknown-time"

set "LOG_FILE=%LOG_DIR%\render-diagnostics-%COMPUTERNAME%-%STAMP%.log"

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; [Console]::OutputEncoding=[Text.Encoding]::UTF8; $OutputEncoding=[Text.Encoding]::UTF8; $source=Get-Content -LiteralPath '%~f0' -Raw -Encoding UTF8; $marker='### POWERSHELL_PAYLOAD ###'; $index=$source.LastIndexOf($marker); if ($index -lt 0) { throw 'PowerShell payload marker not found.' }; $payload=$source.Substring($index + $marker.Length); & ([scriptblock]::Create($payload))"
set "PS_EXIT=%ERRORLEVEL%"

if "%PS_EXIT%"=="0" (
    echo NeoLink render diagnostics written to:
    echo %LOG_FILE%
) else (
    echo NeoLink render diagnostics failed with exit code %PS_EXIT%.
    echo Partial log may exist at:
    echo %LOG_FILE%
)

echo.
echo Press any key to close this window.
pause >nul

exit /b %PS_EXIT%

### POWERSHELL_PAYLOAD ###
$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$LogPath = [System.IO.Path]::GetFullPath($env:LOG_FILE)
$ProjectRoot = [System.IO.Path]::GetFullPath($env:PROJECT_ROOT)
$DxDiagPath = Join-Path $env:TEMP ('neolink-dxdiag-' + [guid]::NewGuid().ToString('N') + '.txt')

function Write-Line {
    param([AllowNull()][object]$Value = '')

    $text = if ($null -eq $Value) { '' } else { [string]$Value }
    [System.IO.File]::AppendAllText($LogPath, $text + [Environment]::NewLine, [System.Text.Encoding]::UTF8)
}

function Write-Console {
    param([AllowNull()][object]$Value = '')

    $text = if ($null -eq $Value) { '' } else { [string]$Value }
    [Console]::WriteLine($text)
}

function Write-Section {
    param([string]$Title)

    Write-Line ''
    Write-Line '================================================================================'
    Write-Line ('# ' + $Title)
    Write-Line '================================================================================'
}

function Write-Command {
    param(
        [string]$Title,
        [scriptblock]$Block
    )

    Write-Section $Title
    Write-Console ('[START] ' + $Title)
    $startedAt = Get-Date
    try {
        $result = & $Block 2>&1
        if ($null -eq $result) {
            Write-Line '(no output)'
            Write-Console ('[ OK  ] ' + $Title + ' (no output, ' + [int]((Get-Date) - $startedAt).TotalSeconds + 's)')
            return
        }

        $result |
            Out-String -Width 4096 |
            ForEach-Object { $_.TrimEnd() } |
            ForEach-Object { Write-Line $_ }
        Write-Console ('[ OK  ] ' + $Title + ' (' + [int]((Get-Date) - $startedAt).TotalSeconds + 's)')
    } catch {
        Write-Line ('[ERROR] ' + $_.Exception.GetType().FullName + ': ' + $_.Exception.Message)
        Write-Console ('[WARN ] ' + $Title + ' failed: ' + $_.Exception.Message)
    }
}

function Write-RegistryTree {
    param(
        [string]$Title,
        [string]$Path
    )

    Write-Command $Title {
        if (Test-Path $Path) {
            Get-ItemProperty -Path $Path | Format-List *
        } else {
            'Registry path not found: ' + $Path
        }
    }
}

function Get-SafeCim {
    param(
        [string]$ClassName,
        [string]$Namespace = 'root/cimv2'
    )

    try {
        Get-CimInstance -Namespace $Namespace -ClassName $ClassName
    } catch {
        '[ERROR] ' + $ClassName + ': ' + $_.Exception.Message
    }
}

function Invoke-DwmProbe {
    Write-Section 'NeoLink DWM Probe - DwmSetWindowAttribute Attr 38'
    Write-Console '[START] NeoLink DWM Probe - DwmSetWindowAttribute Attr 38'
    $startedAt = Get-Date
    try {
        Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

public static class NeoLinkDwmProbe {
    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr CreateWindowExW(int exStyle, string className, string windowName, int style, int x, int y, int width, int height, IntPtr parent, IntPtr menu, IntPtr instance, IntPtr param);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool DestroyWindow(IntPtr hwnd);

    [DllImport("dwmapi.dll", PreserveSig = true)]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int attrValue, int attrSize);

    [DllImport("dwmapi.dll", PreserveSig = true)]
    private static extern int DwmIsCompositionEnabled(out bool enabled);

    public static string Run() {
        bool compositionEnabled = false;
        int compositionHr = DwmIsCompositionEnabled(out compositionEnabled);
        IntPtr hwnd = CreateWindowExW(0, "Static", "NeoLink_Dwm_Probe", 0, 0, 0, 0, 0, IntPtr.Zero, IntPtr.Zero, IntPtr.Zero, IntPtr.Zero);

        if (hwnd == IntPtr.Zero) {
            return "CreateWindowExW failed. LastWin32Error=" + Marshal.GetLastWin32Error()
                + "; DwmIsCompositionEnabled HRESULT=" + compositionHr
                + "; Enabled=" + compositionEnabled;
        }

        try {
            int darkMode = 1;
            int cornerRound = 2;
            int transientWindow = 3;
            int hrDark = DwmSetWindowAttribute(hwnd, 20, ref darkMode, sizeof(int));
            int hrCorner = DwmSetWindowAttribute(hwnd, 33, ref cornerRound, sizeof(int));
            int hrBackdrop = DwmSetWindowAttribute(hwnd, 38, ref transientWindow, sizeof(int));

            return "DwmIsCompositionEnabled HRESULT=" + compositionHr + "; Enabled=" + compositionEnabled
                + Environment.NewLine + "DWMWA_USE_IMMERSIVE_DARK_MODE(20) HRESULT=" + hrDark
                + Environment.NewLine + "DWMWA_WINDOW_CORNER_PREFERENCE(33) HRESULT=" + hrCorner
                + Environment.NewLine + "DWMWA_SYSTEMBACKDROP_TYPE(38, DWMSBT_TRANSIENTWINDOW=3) HRESULT=" + hrBackdrop
                + Environment.NewLine + "NeoLink current policy: only HRESULT 0 for Attr 38 should allow DirectX + transparent Acrylic.";
        } finally {
            DestroyWindow(hwnd);
        }
    }
}
'@

        Write-Line ([NeoLinkDwmProbe]::Run())
        Write-Console ('[ OK  ] NeoLink DWM Probe - DwmSetWindowAttribute Attr 38 (' + [int]((Get-Date) - $startedAt).TotalSeconds + 's)')
    } catch {
        Write-Line ('[ERROR] DWM probe failed: ' + $_.Exception.GetType().FullName + ': ' + $_.Exception.Message)
        Write-Console ('[WARN ] NeoLink DWM Probe failed: ' + $_.Exception.Message)
    }
}

try {
    if (Test-Path $LogPath) {
        Remove-Item -LiteralPath $LogPath -Force
    }

    Write-Line 'NeoLink Windows Render Diagnostics'
    Write-Line ('GeneratedAtLocal=' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz'))
    Write-Line ('GeneratedAtUtc=' + ((Get-Date).ToUniversalTime().ToString('yyyy-MM-dd HH:mm:ss') + 'Z'))
    Write-Line ('ComputerName=' + $env:COMPUTERNAME)
    Write-Line ('UserName=' + $env:USERNAME)
    Write-Line ('ProjectRoot=' + $ProjectRoot)
    Write-Line ('LogPath=' + $LogPath)
    Write-Console 'NeoLink Windows Render Diagnostics'
    Write-Console ('Log file: ' + $LogPath)
    Write-Console 'Collecting render-related hardware and software information...'
    Write-Console ''

    Write-Command 'Repository Snapshot' {
        Set-Location -LiteralPath $ProjectRoot
        'CurrentDirectory=' + (Get-Location).Path
        if (Get-Command git -ErrorAction SilentlyContinue) {
            git rev-parse --show-toplevel 2>&1
            git rev-parse HEAD 2>&1
            git status --short 2>&1
        } else {
            'git not found in PATH'
        }
    }

    Write-Command 'NeoLink Local Files Relevant To Render Debug' {
        Get-ChildItem -LiteralPath $ProjectRoot -Force |
            Select-Object Name,FullName,Length,LastWriteTime,Attributes |
            Format-Table -AutoSize

        $logs = Join-Path $ProjectRoot 'logs'
        if (Test-Path $logs) {
            Get-ChildItem -LiteralPath $logs -Force |
                Select-Object Name,Length,LastWriteTime |
                Sort-Object LastWriteTime -Descending |
                Select-Object -First 20 |
                Format-Table -AutoSize
        }
    }

    Write-Command 'Process And Session Context' {
        'SESSIONNAME=' + $env:SESSIONNAME
        'CLIENTNAME=' + $env:CLIENTNAME
        'LOGONSERVER=' + $env:LOGONSERVER
        query session 2>&1
        qwinsta 2>&1
        Get-Process -ErrorAction SilentlyContinue |
            Where-Object { $_.ProcessName -match 'java|neolink|dwm|explorer|rdpclip|mstsc' } |
            Select-Object Id,ProcessName,MainWindowTitle,StartTime,Path |
            Format-Table -AutoSize
    }

    Write-Command 'OS And Windows Server Identity' {
        Get-SafeCim Win32_OperatingSystem |
            Select-Object Caption,Version,BuildNumber,ProductType,OperatingSystemSKU,OSArchitecture,InstallDate,LastBootUpTime,LocalDateTime,FreePhysicalMemory,TotalVisibleMemorySize,WindowsDirectory,SystemDirectory,DataExecutionPrevention_Available,DataExecutionPrevention_SupportPolicy |
            Format-List
        Get-SafeCim Win32_ComputerSystem |
            Select-Object Manufacturer,Model,SystemType,SystemFamily,Domain,PartOfDomain,HypervisorPresent,NumberOfProcessors,NumberOfLogicalProcessors,TotalPhysicalMemory,UserName |
            Format-List
        Get-SafeCim Win32_BIOS |
            Select-Object Manufacturer,Name,Version,SMBIOSBIOSVersion,ReleaseDate,SerialNumber |
            Format-List
        systeminfo 2>&1
    }

    Write-Command 'Virtualization And Remote Desktop Indicators' {
        Get-SafeCim Win32_ComputerSystem |
            Select-Object Manufacturer,Model,HypervisorPresent |
            Format-List
        Get-SafeCim Win32_VideoController |
            Select-Object Name,PNPDeviceID,AdapterCompatibility,DriverProviderName,DriverVersion |
            Where-Object { $_.Name -match 'Remote|Virtual|Hyper-V|VMware|Microsoft Basic|RDP' -or $_.PNPDeviceID -match 'ROOT\\RDP|VEN_1414' } |
            Format-List
        Get-ItemProperty -Path 'HKLM:\SYSTEM\CurrentControlSet\Control\Terminal Server' -ErrorAction SilentlyContinue |
            Format-List *
        Get-ItemProperty -Path 'HKLM:\SOFTWARE\Policies\Microsoft\Windows NT\Terminal Services' -ErrorAction SilentlyContinue |
            Format-List *
    }

    Invoke-DwmProbe

    Write-Command 'DWM And Desktop Composition Services' {
        Get-Service -Name 'UxSms','Themes','TermService','SessionEnv','UmRdpService' -ErrorAction SilentlyContinue |
            Select-Object Name,DisplayName,Status,StartType,ServiceType |
            Format-Table -AutoSize
        Get-Process -Name dwm -ErrorAction SilentlyContinue |
            Select-Object Id,SessionId,StartTime,Path,MainWindowTitle |
            Format-Table -AutoSize
        tasklist /v /fi "imagename eq dwm.exe" 2>&1
    }

    Write-RegistryTree 'DWM Registry - Current User' 'HKCU:\Software\Microsoft\Windows\DWM'
    Write-RegistryTree 'DWM Registry - Machine' 'HKLM:\SOFTWARE\Microsoft\Windows\DWM'
    Write-RegistryTree 'Graphics Drivers Registry - Machine' 'HKLM:\SYSTEM\CurrentControlSet\Control\GraphicsDrivers'
    Write-RegistryTree 'Direct3D Driver Settings Registry - Current User' 'HKCU:\Software\Microsoft\Direct3D'

    Write-Command 'Display Adapters - Win32_VideoController' {
        Get-SafeCim Win32_VideoController |
            Select-Object Name,Description,VideoProcessor,AdapterCompatibility,AdapterDACType,PNPDeviceID,DeviceID,Status,Availability,ConfigManagerErrorCode,CurrentHorizontalResolution,CurrentVerticalResolution,CurrentBitsPerPixel,CurrentRefreshRate,MaxRefreshRate,MinRefreshRate,AdapterRAM,DriverProviderName,DriverVersion,DriverDate,InstalledDisplayDrivers,InfFilename,InfSection,VideoModeDescription |
            Format-List
    }

    Write-Command 'Display Adapters - PnP Signed Drivers' {
        Get-SafeCim Win32_PnPSignedDriver |
            Where-Object { $_.DeviceClass -eq 'DISPLAY' -or $_.DeviceName -match 'Display|Graphics|GPU|NVIDIA|AMD|Intel|Microsoft Basic|Remote' } |
            Select-Object DeviceName,DeviceClass,Manufacturer,DriverProviderName,DriverVersion,DriverDate,InfName,IsSigned,Signer,HardWareID,CompatID,DeviceID |
            Format-List
    }

    Write-Command 'Display Devices - PnP Present Entities' {
        Get-SafeCim Win32_PnPEntity |
            Where-Object { $_.PNPClass -in @('Display','Monitor') -or $_.Name -match 'Display|Monitor|Graphics|GPU|NVIDIA|AMD|Intel|Remote' } |
            Select-Object Name,PNPClass,Manufacturer,Service,Status,ConfigManagerErrorCode,DeviceID |
            Format-List
    }

    Write-Command 'Monitors And Desktop Settings' {
        Get-SafeCim Win32_DesktopMonitor |
            Select-Object Name,MonitorType,MonitorManufacturer,PNPDeviceID,ScreenHeight,ScreenWidth,Status |
            Format-List
        Get-SafeCim Win32_DisplayConfiguration | Format-List *
        Get-SafeCim Win32_DisplayControllerConfiguration | Format-List *
        Get-SafeCim Win32_Desktop |
            Select-Object Name,Wallpaper,ScreenSaverActive,ScreenSaverSecure,ScreenSaverTimeout |
            Format-List
    }

    Write-Command 'DirectX Diagnostic Tool - dxdiag' {
        if (Get-Command dxdiag.exe -ErrorAction SilentlyContinue) {
            Write-Console '        dxdiag is running. This can take 30-120 seconds on Windows Server...'
            Start-Process -FilePath dxdiag.exe -ArgumentList "/t `"$DxDiagPath`"" -Wait -WindowStyle Hidden
            if (Test-Path $DxDiagPath) {
                $bytes = [System.IO.File]::ReadAllBytes($DxDiagPath)
                if ($bytes.Length -ge 2 -and $bytes[0] -eq 255 -and $bytes[1] -eq 254) {
                    [System.Text.Encoding]::Unicode.GetString($bytes)
                } elseif ($bytes.Length -ge 3 -and $bytes[0] -eq 239 -and $bytes[1] -eq 187 -and $bytes[2] -eq 191) {
                    [System.Text.Encoding]::UTF8.GetString($bytes)
                } else {
                    [System.Text.Encoding]::Default.GetString($bytes)
                }
            } else {
                'dxdiag did not create output file: ' + $DxDiagPath
            }
        } else {
            'dxdiag.exe not found'
        }
    }

    Write-Command 'Java Runtime And Render Environment' {
        'JAVA_HOME=' + $env:JAVA_HOME
        'PATH=' + $env:PATH
        'SKIKO_RENDER_API=' + $env:SKIKO_RENDER_API
        'sun.java2d.d3d=' + $env:sun_java2d_d3d
        if (Get-Command java -ErrorAction SilentlyContinue) {
            java -XshowSettings:properties -version 2>&1
        } else {
            'java not found in PATH'
        }

        $gradlew = Join-Path $ProjectRoot 'gradlew.bat'
        if (Test-Path $gradlew) {
            & $gradlew --version 2>&1
        } else {
            'gradlew.bat not found'
        }
    }

    Write-Command 'Environment Variables - Render Relevant' {
        Get-ChildItem Env: |
            Where-Object { $_.Name -match 'JAVA|JDK|JRE|GRADLE|KOTLIN|COMPOSE|SKIKO|SKIA|D3D|DIRECTX|DX|OPENGL|ANGLE|GPU|NVIDIA|AMD|INTEL|DISPLAY|SESSION|CLIENT|RDP|WT_' } |
            Sort-Object Name |
            Format-Table -AutoSize
    }

    Write-Command 'Installed Graphics And Runtime Software' {
        $uninstallRoots = @(
            'HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*',
            'HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*'
        )

        Get-ItemProperty $uninstallRoots -ErrorAction SilentlyContinue |
            Where-Object { $_.DisplayName -match 'NVIDIA|AMD|Radeon|Intel.*Graphics|DirectX|Visual C\+\+|OpenGL|Vulkan|Mesa|Citrix|VMware Tools|Hyper-V|Remote Desktop' } |
            Select-Object DisplayName,DisplayVersion,Publisher,InstallDate,InstallLocation |
            Sort-Object DisplayName |
            Format-Table -AutoSize
    }

    Write-Command 'Windows Features And Optional Components' {
        Write-Console '        DISM feature enumeration is running. This can be slow on Server images...'
        dism /online /Get-Features /Format:Table 2>&1
    }

    Write-Command 'Recent Display DWM Driver Application Events' {
        $filters = @(
            @{LogName='System'; StartTime=(Get-Date).AddDays(-14)},
            @{LogName='Application'; StartTime=(Get-Date).AddDays(-14)}
        )

        foreach ($filter in $filters) {
            Get-WinEvent -FilterHashtable $filter -MaxEvents 500 -ErrorAction SilentlyContinue |
                Where-Object {
                    $_.ProviderName -match 'Display|Desktop Window Manager|DWM|Application Error|Windows Error Reporting|nvlddmkm|amdkmdag|igfx|DriverFrameworks' -or
                    $_.Message -match 'dwm.exe|DirectX|D3D|graphics|display driver|RenderException|skiko|java.exe|NeoLink'
                } |
                Select-Object TimeCreated,LogName,ProviderName,Id,LevelDisplayName,Message |
                Format-List
        }
    }

    Write-Command 'Power And Performance Policy' {
        powercfg /getactivescheme 2>&1
        powercfg /query 2>&1
    }

    Write-Command 'Driver Query' {
        Write-Console '        driverquery is enumerating installed drivers...'
        driverquery /v /fo list 2>&1
    }
} finally {
    if (Test-Path $DxDiagPath) {
        Remove-Item -LiteralPath $DxDiagPath -Force -ErrorAction SilentlyContinue
    }
    Write-Console ''
    Write-Console 'Diagnostics collection finished.'
    Write-Console ('Log file: ' + $LogPath)
}

# run-server.ps1 - compile and run the backend with required jars
# Usage: Open PowerShell at repo root and run: .\backend\run-server.ps1

$src = Join-Path $PSScriptRoot 'src'
$bin = Join-Path $PSScriptRoot 'bin'
$lib = Join-Path $PSScriptRoot 'lib'

# Clean classes
Get-ChildItem -Path $src -Recurse -Filter *.class | Remove-Item -Force -ErrorAction SilentlyContinue

# Ensure bin exists
New-Item -ItemType Directory -Force -Path $bin | Out-Null

# Collect java sources
Get-ChildItem -Path $src -Recurse -Filter *.java | ForEach-Object { $_.FullName } | Set-Content "$PSScriptRoot\\sources.txt"

# Compile
$files = Get-Content "$PSScriptRoot\\sources.txt"
javac -d $bin $files

# Build classpath including libs
$cp = "$bin"
if (Test-Path $lib) {
    $jars = Get-ChildItem -Path $lib -Filter *.jar | ForEach-Object { $_.FullName }
    if ($jars) { $cp += ";" + ($jars -join ";") }
}

# Run
java -cp $cp com.delivery.Main

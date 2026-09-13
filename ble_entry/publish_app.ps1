param(
    [Parameter(Mandatory=$true)][string]$NotesFile,
    [string]$GradlePath,
    [switch]$Publish
)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$android=Join-Path $PSScriptRoot 'android'
$notes=(Resolve-Path -LiteralPath $NotesFile).Path
if(!$env:ANDROID_HOME){$env:ANDROID_HOME=Join-Path $env:LOCALAPPDATA 'Android/Sdk'}
if(!$GradlePath){
    $available=Get-Command gradle -ErrorAction SilentlyContinue
    if($available){$GradlePath=$available.Source}else{
        $GradlePath=(Get-ChildItem -Path (Join-Path $env:USERPROFILE '.gradle/wrapper/dists/gradle-9.3.1-bin/*/gradle-9.3.1/bin/gradle.bat') | Select-Object -First 1).FullName
    }
}
if(!$GradlePath){throw 'Gradle 9.3.1 required; pass -GradlePath.'}
if($Publish){
    $dirty=& git -C $repo status --porcelain -- ble_entry/android ble_entry/publish_app.ps1 ble_entry/release-notes
    if($dirty){throw 'Commit the app, publisher and release notes before publishing.'}
}
Push-Location $android
try{& $GradlePath --no-daemon :app:assembleRelease :app:lintRelease;if($LASTEXITCODE -ne 0){throw 'Release build/check failed'}}finally{Pop-Location}
$output=Join-Path $android 'app/build/outputs/apk/release'
$metadata=Get-Content -LiteralPath (Join-Path $output 'output-metadata.json') -Raw | ConvertFrom-Json
$element=$metadata.elements[0]
if($metadata.applicationId -ne 'home.doorble' -or $element.versionName -notmatch '^[0-9]+\.[0-9]+(?:\.[0-9]+)?$'){throw 'Use a numeric family release version, not a test build.'}
$version=$element.versionName
$tag="door-android-$version"
$apk=Join-Path $output $element.outputFile
$signer=Join-Path $env:ANDROID_HOME 'build-tools/36.0.0/apksigner.bat'
$certificate=& $signer verify --print-certs $apk
if($LASTEXITCODE -ne 0 -or !($certificate -match 'certificate SHA-256 digest: 3fa6d69470a1db86e4bf140294ba57d6a3c04cf6b3921e7d6f82ef72a0429275')){throw 'APK signing identity changed; existing phones cannot update safely.'}
$folder=Join-Path $repo "Shared/AppReleases/$tag"
New-Item -ItemType Directory -Force -Path $folder | Out-Null
$filename="Door-$version.apk"
$copy=Join-Path $folder $filename
Copy-Item -LiteralPath $apk -Destination $copy
$manifest=[ordered]@{
    schema=1;packageName='home.doorble';versionCode=[int]$element.versionCode;versionName=$version;minSdk=26
    apkUrl="https://github.com/AryanRai/DoorUnlock/releases/download/$tag/$filename"
    sha256=(Get-FileHash -LiteralPath $copy -Algorithm SHA256).Hash.ToLowerInvariant();bytes=(Get-Item -LiteralPath $copy).Length
    notes=[System.IO.File]::ReadAllText($notes)
}
$manifestFile=Join-Path $folder 'door-update.json'
if($manifest.notes.Length -gt 6000 -or $manifest.bytes -gt 50000000){throw 'Release exceeds the updater metadata/download limits.'}
[System.IO.File]::WriteAllText($manifestFile,($manifest | ConvertTo-Json),[System.Text.UTF8Encoding]::new($false))
if($Publish){
    $previous=$null
    try{$previous=Invoke-RestMethod -Uri 'https://github.com/AryanRai/DoorUnlock/releases/latest/download/door-update.json' -TimeoutSec 30}catch{if([int]$_.Exception.Response.StatusCode -ne 404){throw}}
    if($previous -and [int]$element.versionCode -le [int]$previous.versionCode){throw 'Increment versionCode before publishing another update.'}
    $head=& git -C $repo rev-parse HEAD
    & gh release create $tag $copy $manifestFile --repo AryanRai/DoorUnlock --target $head --draft --title "Door $version - family dry run" --notes-file $notes
    if($LASTEXITCODE -ne 0){throw 'Draft release creation failed. Existing releases are not overwritten.'}
    & gh release edit $tag --repo AryanRai/DoorUnlock --draft=false --latest
    if($LASTEXITCODE -ne 0){throw 'Release remains a draft; inspect it before retrying.'}
}
Write-Output "Prepared signed Door $version (code $($element.versionCode)) in $folder"

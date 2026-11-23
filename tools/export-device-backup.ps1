param([Parameter(Mandatory=$true)][ValidatePattern('^[A-Za-z0-9._-]+$')][string]$Name)
$ErrorActionPreference='Stop'
$adb='D:\Codex\platform-tools\adb.exe'
function Snapshot {
    & $adb shell uiautomator dump /sdcard/shiling-window.xml | Out-Null
    [xml]((& $adb shell cat /sdcard/shiling-window.xml) -join "`n")
}
function Tap-Node($node) {
    if(-not $node){throw 'Expected UI control is not visible'}
    $coords=[regex]::Matches($node.bounds,'\d+')|ForEach-Object{[int]$_.Value}
    & $adb shell input tap ([int](($coords[0]+$coords[2])/2)) ([int](($coords[1]+$coords[3])/2))
}
function Tap-Text($label) {
    $xml=Snapshot
    Tap-Node ($xml.SelectNodes('//node')|Where-Object{$_.text -eq $label -and $_.package -eq 'com.fuguang.localdiary.nativeapp'}|Select-Object -First 1)
}
& $adb shell am start --user 0 -n com.fuguang.localdiary.nativeapp/com.fuguang.localdiary.MainActivity | Out-Null
Tap-Text '我的'
Tap-Text '导出备份'
Tap-Text '继续导出'
$xml=Snapshot
Tap-Node ($xml.SelectNodes('//node')|Where-Object{$_.class -eq 'android.widget.EditText'}|Select-Object -First 1)
$keys=@('shell','input','keyevent','123')+(1..80|ForEach-Object{'67'})
& $adb @keys
& $adb shell input text $Name
& $adb shell input keyevent 4
$xml=Snapshot
Tap-Node ($xml.SelectNodes('//node')|Where-Object{$_.text -eq '保存'}|Select-Object -First 1)
Write-Output "Requested local backup: /sdcard/Download/$Name"

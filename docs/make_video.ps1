# BODA 글라스 PoC 소개 영상 합성
# 1) manifest.json 의 슬라이드별 내레이션을 뉴럴 TTS(edge-tts) 로 합성 (실패 시 SAPI 폴백)
# 2) 각 슬라이드 = 이미지(루프) + 내레이션, 페이드 인/아웃 세그먼트
# 3) 세그먼트 연결 → 최종 MP4
# 사용: powershell -File make_video.ps1 [-Voice ko-KR-InJoonNeural] [-Rate "+3%"] [-Pitch "+10Hz"] [-Out path.mp4]
param(
    [string]$Voice = "ko-KR-SunHiNeural",
    [string]$Rate  = "-5%",
    [string]$Pitch = "+0Hz",
    [double]$Pad   = 1.0,      # 슬라이드 끝 여백(초)
    [string]$Out   = "D:\BODA_글라스_PoC_소개영상.mp4"
)
$ErrorActionPreference = "Stop"
$dir   = "D:\Repo\Android\poc-video"
$work  = Join-Path $dir "work"
$final = $Out
$inv   = [Globalization.CultureInfo]::InvariantCulture
New-Item -ItemType Directory -Force -Path $work | Out-Null

# --- ffmpeg/ffprobe 위치 (zip 압축 해제 보장) ---
if (-not (Get-ChildItem -Path (Join-Path $dir "ffmpeg") -Recurse -Filter ffmpeg.exe -ErrorAction SilentlyContinue)) {
    Expand-Archive -Path (Join-Path $dir "ffmpeg.zip") -DestinationPath (Join-Path $dir "ffmpeg") -Force
}
$ffmpeg  = (Get-ChildItem -Path (Join-Path $dir "ffmpeg") -Recurse -Filter ffmpeg.exe  | Select-Object -First 1).FullName
$ffprobe = (Get-ChildItem -Path (Join-Path $dir "ffmpeg") -Recurse -Filter ffprobe.exe | Select-Object -First 1).FullName
Write-Host "ffmpeg : $ffmpeg"

# --- TTS 준비 (Heami, ko-KR) ---
Add-Type -AssemblyName System.Speech
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
try { $synth.SelectVoice("Microsoft Heami Desktop") } catch { Write-Host "Heami 음성 없음 — 기본 음성 사용" }
$synth.Rate = -2   # 차분한 속도(부드러운 어조)

# 문장 끝/쉼표에 자연스러운 쉼(break)을 넣어 딱딱함을 줄인 SSML 로 변환.
function To-Ssml($text) {
    $e = $text -replace '&', '&amp;' -replace '<', '&lt;' -replace '>', '&gt;'
    $e = [regex]::Replace($e, '([.!?])\s+', '$1<break time="380ms"/> ')
    $e = [regex]::Replace($e, '([,])\s+',   '$1<break time="170ms"/> ')
    return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='ko-KR'>$e</speak>"
}

$manifest = Get-Content (Join-Path $dir "manifest.json") -Raw -Encoding UTF8 | ConvertFrom-Json
$listFile = Join-Path $work "concat.txt"
if (Test-Path $listFile) { Remove-Item $listFile }

# 네이티브(ffmpeg) stderr 를 PowerShell 이 종료 오류로 오인하지 않도록 Continue 로 전환.
# 성공/실패는 $LASTEXITCODE 로만 판정한다.
$ErrorActionPreference = "Continue"

# 뉴럴 음성(edge-tts) 내레이션 생성 — 실패 시 슬라이드별 SAPI(Heami) 폴백.
Write-Host "Generating neural narration (edge-tts): $Voice rate=$Rate pitch=$Pitch"
$env:TTS_VOICE = $Voice; $env:TTS_RATE = $Rate; $env:TTS_PITCH = $Pitch
python "D:\Repo\Android\docs\tts_edge.py"

foreach ($s in $manifest) {
    $i   = "{0:D2}" -f $s.index
    $png = $s.file
    $seg = Join-Path $work "seg-$i.mp4"

    # 1) 내레이션 오디오 — 뉴럴(mp3) 우선, 없으면 SAPI(wav) 폴백
    $audio = Join-Path $work "narr-$i.mp3"
    if (-not (Test-Path $audio)) {
        $audio = Join-Path $work "narr-$i.wav"
        $synth.SetOutputToWaveFile($audio)
        try { $synth.SpeakSsml((To-Ssml $s.narration)) } catch { $synth.Speak($s.narration) }
        $synth.SetOutputToNull()
    }

    # 2) 길이 = 내레이션 + 여백(Pad)초 (최소 4.0s)
    $raw = (& $ffprobe -v error -show_entries format=duration -of csv=p=0 $audio).Trim()
    $nar = [double]::Parse($raw, $inv)
    $dur = [math]::Round([math]::Max(4.0, $nar + $Pad), 2)
    $durS = $dur.ToString($inv)
    $outS = ($dur - 0.4).ToString($inv)
    Write-Host ("slide {0}: narration {1}s -> segment {2}s" -f $i, [math]::Round($nar,1), $dur)

    # 3) 세그먼트 합성 (페이드 인/아웃 + 오디오 무음 패딩)
    $fc = "[0:v]scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2,setsar=1,fade=t=in:st=0:d=0.4,fade=t=out:st=${outS}:d=0.4,format=yuv420p[v];[1:a]apad,atrim=0:${durS},asetpts=N/SR/TB[a]"
    & $ffmpeg -y -hide_banner -loglevel error -nostats -loop 1 -i $png -i $audio -t $durS -filter_complex $fc `
        -map "[v]" -map "[a]" -c:v libx264 -preset medium -crf 20 -r 30 -pix_fmt yuv420p `
        -c:a aac -b:a 160k $seg
    if ($LASTEXITCODE -ne 0) { throw "ffmpeg segment failed on slide $i (exit $LASTEXITCODE)" }
    Add-Content -Path $listFile -Value ("file '{0}'" -f ($seg -replace '\\','/')) -Encoding ascii
}

# 4) 연결
& $ffmpeg -y -hide_banner -loglevel error -nostats -f concat -safe 0 -i $listFile -c copy $final
if ($LASTEXITCODE -ne 0) { throw "ffmpeg concat failed (exit $LASTEXITCODE)" }
$mb = [math]::Round((Get-Item $final).Length/1MB,1)
$tot = (& $ffprobe -v error -show_entries format=duration -of csv=p=0 $final).Trim()
Write-Host ""
Write-Host ("DONE: {0} ({1} MB, {2}s)" -f $final, $mb, [math]::Round([double]::Parse($tot,$inv),1))

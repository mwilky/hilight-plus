Add-Type -AssemblyName System.Drawing

$outputDir = "C:\Android\Development\hilight-plus\play_store_assets"
if (-not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir | Out-Null
}

# =========================================================================
# 1. GENERATE APP ICON (512x512 PNG, 32-bit ARGB)
# =========================================================================
$iconBmp = New-Object System.Drawing.Bitmap(512, 512, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($iconBmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality

# Background: Deep Sleek OLED Obsidian Gradient
$bgRect = New-Object System.Drawing.Rectangle(0, 0, 512, 512)
$bgBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.PointF(0, 0)),
    (New-Object System.Drawing.PointF(512, 512)),
    [System.Drawing.Color]::FromArgb(255, 18, 20, 26),
    [System.Drawing.Color]::FromArgb(255, 8, 9, 12)
)
$g.FillRectangle($bgBrush, $bgRect)

# Center point for 8-LED Ring
$cx = 256.0
$cy = 256.0
$ringRadius = 145.0
$ledRadius = 24.0

# 8 Pixel LED Colors (Google / Vibrant Palette)
$colors = @(
    [System.Drawing.Color]::FromArgb(255, 66, 133, 244),   # Blue
    [System.Drawing.Color]::FromArgb(255, 0, 229, 255),    # Cyan
    [System.Drawing.Color]::FromArgb(255, 52, 168, 83),    # Green
    [System.Drawing.Color]::FromArgb(255, 251, 188, 5),    # Yellow
    [System.Drawing.Color]::FromArgb(255, 234, 67, 53),    # Red
    [System.Drawing.Color]::FromArgb(255, 255, 0, 127),    # Neon Pink
    [System.Drawing.Color]::FromArgb(255, 138, 43, 226),   # Purple
    [System.Drawing.Color]::FromArgb(255, 100, 181, 246)   # Light Blue
)

# Draw Ambient LED Outer Glows
for ($i = 0; $i -lt 8; $i++) {
    $angle = ($i * 45.0 - 90.0) * [Math]::PI / 180.0
    $lx = $cx + $ringRadius * [Math]::Cos($angle)
    $ly = $cy + $ringRadius * [Math]::Sin($angle)
    $c = $colors[$i]

    # Diffused Bloom Glow (3 concentric expanding circles)
    $glowPen1 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(35, $c.R, $c.G, $c.B))
    $g.FillEllipse($glowPen1, [float]($lx - 48), [float]($ly - 48), 96.0, 96.0)

    $glowPen2 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(70, $c.R, $c.G, $c.B))
    $g.FillEllipse($glowPen2, [float]($lx - 34), [float]($ly - 34), 68.0, 68.0)

    # Core Solid LED
    $coreBrush = New-Object System.Drawing.SolidBrush($c)
    $g.FillEllipse($coreBrush, [float]($lx - $ledRadius), [float]($ly - $ledRadius), [float]($ledRadius * 2), [float]($ledRadius * 2))

    # Specular Glass Center Highlight
    $whiteCenter = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(220, 255, 255, 255))
    $g.FillEllipse($whiteCenter, [float]($lx - 7), [float]($ly - 7), 14.0, 14.0)
}

# Center Symbol: Vibrant Glowing "Plus" Emblem
$centerBg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(30, 255, 255, 255))
$g.FillEllipse($centerBg, [float]($cx - 55), [float]($cy - 55), 110.0, 110.0)

# Center Icon: Stylized White Plus
$plusPen = New-Object System.Drawing.Pen([System.Drawing.Color]::White, 16.0)
$plusPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$plusPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round

$g.DrawLine($plusPen, [float]($cx - 26), [float]$cy, [float]($cx + 26), [float]$cy)
$g.DrawLine($plusPen, [float]$cx, [float]($cy - 26), [float]$cx, [float]($cy + 26))

$iconPath = Join-Path $outputDir "icon_512x512.png"
$iconBmp.Save($iconPath, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose()
$iconBmp.Dispose()


# =========================================================================
# 2. GENERATE FEATURE GRAPHIC (1024x500 PNG, 32-bit ARGB)
# =========================================================================
$featBmp = New-Object System.Drawing.Bitmap(1024, 500, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$fg = [System.Drawing.Graphics]::FromImage($featBmp)
$fg.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$fg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$fg.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$fg.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

# Background: Premium Dark Linear Gradient
$featRect = New-Object System.Drawing.Rectangle(0, 0, 1024, 500)
$featBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.PointF(0, 0)),
    (New-Object System.Drawing.PointF(1024, 500)),
    [System.Drawing.Color]::FromArgb(255, 14, 16, 22),
    [System.Drawing.Color]::FromArgb(255, 6, 7, 9)
)
$fg.FillRectangle($featBrush, $featRect)

# Subtle Background Ambient Bloom on the right behind the LED ring
$bloomBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(30, 66, 133, 244))
$fg.FillEllipse($bloomBrush, 620.0, 70.0, 360.0, 360.0)

# Draw 8-LED Ring on the Right Side (Hero Visual)
$rcx = 790.0
$rcy = 250.0
$rRingRadius = 120.0
$rLedRadius = 19.0

for ($i = 0; $i -lt 8; $i++) {
    $angle = ($i * 45.0 - 90.0) * [Math]::PI / 180.0
    $lx = $rcx + $rRingRadius * [Math]::Cos($angle)
    $ly = $rcy + $rRingRadius * [Math]::Sin($angle)
    $c = $colors[$i]

    # Diffused Bloom Glow
    $glowPen1 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(35, $c.R, $c.G, $c.B))
    $fg.FillEllipse($glowPen1, [float]($lx - 40), [float]($ly - 40), 80.0, 80.0)

    $glowPen2 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(70, $c.R, $c.G, $c.B))
    $fg.FillEllipse($glowPen2, [float]($lx - 28), [float]($ly - 28), 56.0, 56.0)

    # Core Solid LED
    $coreBrush = New-Object System.Drawing.SolidBrush($c)
    $fg.FillEllipse($coreBrush, [float]($lx - $rLedRadius), [float]($ly - $rLedRadius), [float]($rLedRadius * 2), [float]($rLedRadius * 2))

    # Specular Glass Center
    $whiteCenter = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(230, 255, 255, 255))
    $fg.FillEllipse($whiteCenter, [float]($lx - 5), [float]($ly - 5), 10.0, 10.0)
}

# Center Emblem inside Ring
$rCenterBg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(30, 255, 255, 255))
$fg.FillEllipse($rCenterBg, [float]($rcx - 45), [float]($rcy - 45), 90.0, 90.0)

$rPlusPen = New-Object System.Drawing.Pen([System.Drawing.Color]::White, 13.0)
$rPlusPen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$rPlusPen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$fg.DrawLine($rPlusPen, [float]($rcx - 22), [float]$rcy, [float]($rcx + 22), [float]$rcy)
$fg.DrawLine($rPlusPen, [float]$rcx, [float]($rcy - 22), [float]$rcx, [float]($rcy + 22))

# Left Side Typography & Badges
$titleFont = New-Object System.Drawing.Font("Segoe UI", 48, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$subtitleFont = New-Object System.Drawing.Font("Segoe UI", 22, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$badgeFont = New-Object System.Drawing.Font("Segoe UI", 13, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)

# Category Badge Pill: "PIXEL HARDWARE LIGHTING"
$badgeRect = New-Object System.Drawing.Rectangle(70, 95, 230, 32)
$badgeBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(45, 66, 133, 244))
$fg.FillRectangle($badgeBrush, $badgeRect)
$badgeTextBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 100, 181, 246))
$fg.DrawString("PIXEL HARDWARE LIGHTING", $badgeFont, $badgeTextBrush, 80.0, 103.0)

# Main Title: "HiLight Plus"
$titleBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
$fg.DrawString("HiLight Plus", $titleFont, $titleBrush, 68.0, 145.0)

# Subtitle
$subBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 180, 188, 200))
$fg.DrawString("Custom ring illumination for calls,`ncontacts & app notifications.", $subtitleFont, $subBrush, 70.0, 218.0)

# Feature Badges
$pills = @("Caller Lighting", "Contact Messages", "6 Dynamic Patterns")
$px = 70.0
for ($j = 0; $j -lt 3; $j++) {
    $pText = $pills[$j]
    $pRect = New-Object System.Drawing.Rectangle([int]$px, 335, 160, 36)
    $pBg = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(35, 255, 255, 255))
    $fg.FillRectangle($pBg, $pRect)
    $pTextBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(240, 240, 245))
    $fg.DrawString($pText, $badgeFont, $pTextBrush, [float]($px + 12), 345.0)
    $px += 175.0
}

$featPath = Join-Path $outputDir "feature_graphic_1024x500.png"
$featBmp.Save($featPath, [System.Drawing.Imaging.ImageFormat]::Png)
$fg.Dispose()
$featBmp.Dispose()

Write-Output "SUCCESS: Graphics generated in $outputDir"

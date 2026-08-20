param(
    [Parameter(Mandatory = $true)]
    [string] $SourceLogo,

    [string] $ResourceRoot = "app/src/main/res"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing.Common
$drawingAssembly = [System.Drawing.Bitmap].Assembly.Location
$drawingAssemblyDirectory = Split-Path -Parent $drawingAssembly
$gdiAssembly = Join-Path $drawingAssemblyDirectory "System.Private.Windows.GdiPlus.dll"
$windowsCoreAssembly = Join-Path $drawingAssemblyDirectory "System.Private.Windows.Core.dll"
$drawingPrimitivesAssembly = Join-Path $drawingAssemblyDirectory "System.Drawing.Primitives.dll"

Add-Type -ReferencedAssemblies @(
    $drawingAssembly,
    $gdiAssembly,
    $windowsCoreAssembly,
    $drawingPrimitivesAssembly
) -TypeDefinition @"
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;

public static class StealthMeshAssetRenderer
{
    public static Bitmap ExtractWhiteMark(string sourcePath)
    {
        using (var source = new Bitmap(sourcePath))
        {
            var mask = new Bitmap(source.Width, source.Height, PixelFormat.Format32bppArgb);
            int minX = source.Width;
            int minY = source.Height;
            int maxX = -1;
            int maxY = -1;

            for (int y = 0; y < source.Height; y++)
            {
                for (int x = 0; x < source.Width; x++)
                {
                    Color pixel = source.GetPixel(x, y);
                    int luminance = Math.Max(pixel.R, Math.Max(pixel.G, pixel.B));
                    int alpha = luminance <= 40
                        ? 0
                        : luminance >= 232
                            ? 255
                            : (luminance - 40) * 255 / 192;

                    if (alpha > 4)
                    {
                        minX = Math.Min(minX, x);
                        minY = Math.Min(minY, y);
                        maxX = Math.Max(maxX, x);
                        maxY = Math.Max(maxY, y);
                    }

                    mask.SetPixel(x, y, Color.FromArgb(alpha, 255, 255, 255));
                }
            }

            if (maxX < minX || maxY < minY)
                throw new InvalidOperationException("The supplied image did not contain a visible white logo mark.");

            int padding = 3;
            minX = Math.Max(0, minX - padding);
            minY = Math.Max(0, minY - padding);
            maxX = Math.Min(source.Width - 1, maxX + padding);
            maxY = Math.Min(source.Height - 1, maxY + padding);

            var crop = new Bitmap(maxX - minX + 1, maxY - minY + 1, PixelFormat.Format32bppArgb);
            using (Graphics graphics = Graphics.FromImage(crop))
            {
                graphics.CompositingMode = CompositingMode.SourceCopy;
                graphics.DrawImage(mask,
                    new Rectangle(0, 0, crop.Width, crop.Height),
                    new Rectangle(minX, minY, crop.Width, crop.Height),
                    GraphicsUnit.Pixel);
            }
            mask.Dispose();
            return crop;
        }
    }

    public static void SaveTransparentMark(Bitmap mark, string outputPath, int size)
    {
        using (var canvas = new Bitmap(size, size, PixelFormat.Format32bppArgb))
        using (Graphics graphics = Graphics.FromImage(canvas))
        {
            Configure(graphics);
            graphics.Clear(Color.Transparent);
            DrawCentered(graphics, mark, size, 0.64f);
            canvas.Save(outputPath, ImageFormat.Png);
        }
    }

    public static void SaveLauncher(Bitmap mark, string outputPath, int size)
    {
        using (var canvas = new Bitmap(size, size, PixelFormat.Format32bppArgb))
        using (Graphics graphics = Graphics.FromImage(canvas))
        using (var background = new SolidBrush(Color.Black))
        using (GraphicsPath shape = RoundedRectangle(size, size * 0.18f))
        {
            Configure(graphics);
            graphics.Clear(Color.Transparent);
            graphics.FillPath(background, shape);
            DrawCentered(graphics, mark, size, 0.58f);
            canvas.Save(outputPath, ImageFormat.Png);
        }
    }

    private static void Configure(Graphics graphics)
    {
        graphics.CompositingMode = CompositingMode.SourceOver;
        graphics.CompositingQuality = CompositingQuality.HighQuality;
        graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
        graphics.PixelOffsetMode = PixelOffsetMode.HighQuality;
        graphics.SmoothingMode = SmoothingMode.HighQuality;
    }

    private static void DrawCentered(Graphics graphics, Bitmap mark, int canvasSize, float maxFraction)
    {
        float maximum = canvasSize * maxFraction;
        float scale = Math.Min(maximum / mark.Width, maximum / mark.Height);
        int width = Math.Max(1, (int)Math.Round(mark.Width * scale));
        int height = Math.Max(1, (int)Math.Round(mark.Height * scale));
        int x = (canvasSize - width) / 2;
        int y = (canvasSize - height) / 2;
        graphics.DrawImage(mark, new Rectangle(x, y, width, height));
    }

    private static GraphicsPath RoundedRectangle(int size, float radius)
    {
        float diameter = radius * 2f;
        float max = size - 1f;
        var path = new GraphicsPath();
        path.AddArc(0, 0, diameter, diameter, 180, 90);
        path.AddArc(max - diameter, 0, diameter, diameter, 270, 90);
        path.AddArc(max - diameter, max - diameter, diameter, diameter, 0, 90);
        path.AddArc(0, max - diameter, diameter, diameter, 90, 90);
        path.CloseFigure();
        return path;
    }
}
"@

$sourcePath = (Resolve-Path -LiteralPath $SourceLogo).Path
$resourcePath = Join-Path (Resolve-Path -LiteralPath $ResourceRoot).Path ""
$densities = [ordered]@{
    "mdpi" = 1.0
    "hdpi" = 1.5
    "xhdpi" = 2.0
    "xxhdpi" = 3.0
    "xxxhdpi" = 4.0
}

$mark = [StealthMeshAssetRenderer]::ExtractWhiteMark($sourcePath)
try {
    foreach ($entry in $densities.GetEnumerator()) {
        $density = $entry.Key
        $scale = [double] $entry.Value
        $drawableDirectory = Join-Path $resourcePath "drawable-$density"
        $mipmapDirectory = Join-Path $resourcePath "mipmap-$density"
        [void] (New-Item -ItemType Directory -Force -Path $drawableDirectory)

        $markSize = [int] [Math]::Round(108 * $scale)
        [StealthMeshAssetRenderer]::SaveTransparentMark(
            $mark,
            (Join-Path $drawableDirectory "stealthmesh_mark.png"),
            $markSize
        )

        [StealthMeshAssetRenderer]::SaveTransparentMark(
            $mark,
            (Join-Path $mipmapDirectory "ic_launcher_adaptive_fore.png"),
            $markSize
        )

        $launcherSize = [int] [Math]::Round(48 * $scale)
        [StealthMeshAssetRenderer]::SaveLauncher(
            $mark,
            (Join-Path $mipmapDirectory "ic_launcher.png"),
            $launcherSize
        )
    }
}
finally {
    $mark.Dispose()
}

Write-Output "Generated deterministic StealthMesh launcher, adaptive, monochrome, and splash mark assets."

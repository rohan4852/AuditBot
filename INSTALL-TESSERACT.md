# Installing Tesseract on Windows

This file provides quick commands to install and verify Tesseract on Windows, useful for running OCR via Tess4J in this project.

## Option A — UB Mannheim installer (recommended)
1. Download the installer from the UB Mannheim builds (stable builds for Windows):
   https://github.com/UB-Mannheim/tesseract/wiki
2. Run the installer (default path is usually `C:\Program Files\Tesseract-OCR`).

3. Add Tesseract to your user PATH and set `TESSDATA_PREFIX` (PowerShell example):

```powershell
setx PATH "$env:PATH;C:\Program Files\Tesseract-OCR"
setx TESSDATA_PREFIX "C:\Program Files\Tesseract-OCR\tessdata"
```

4. Close and reopen your PowerShell window to pick up the new environment variables.

5. Verify installation:

```powershell
tesseract --version
```

You should see version information and a path to the `tesseract` binary.

## Option B — Chocolatey
If you use Chocolatey:

```powershell
choco install tesseract -y
```

Then verify:

```powershell
tesseract --version
```

## Option C — winget

```powershell
winget install --id UB-Mannheim.Tesseract
```

## Verify Tess4J integration
Tess4J needs access to Tesseract's `tessdata` files. Either ensure `TESSDATA_PREFIX` points to the `tessdata` folder, or configure the datapath in Java code:

```java
Tesseract t = new Tesseract();
// If Tess4J cannot find tessdata, set datapath to the parent folder that contains "tessdata"
t.setDatapath("C:\\Program Files\\Tesseract-OCR");
```

## Adding language data
To OCR languages other than English, download the corresponding `.traineddata` files and place them in the `tessdata` folder (e.g., `tessdata/spa.traineddata`), then set:

```java
t.setLanguage("spa");
```

## Troubleshooting
- If you get "Tesseract not found on PATH" from the Java program, ensure `tesseract --version` works in a new shell and that `TESSDATA_PREFIX` points to the `tessdata` folder.
- Ensure Java bitness matches Tesseract native libraries (both 64-bit on modern systems).


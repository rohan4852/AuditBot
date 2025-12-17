# AuditBot

AuditBot is a lightweight Java CLI that reads a local PDF, extracts its text (or runs OCR for scanned PDFs), and sends the content plus a user question to the Google Gemini (Generative Language) API to produce a strict compliance answer.

## Prerequisites

- Java 17+ and Maven installed.
- Tesseract OCR installed on your machine for OCR support:
  - Windows: install via the official installer (e.g., https://github.com/tesseract-ocr/tesseract)
  - Ensure the `tesseract` executable is on your `PATH`, or set `TESSDATA_PREFIX` to your tessdata folder.
- A working Google Generative Language API key with model access.

## Files

- `src/main/java/SimpleAuditBot.java` - main program.
- `src/main/resources/policy.pdf` - place your PDF here.
- `pom.xml` - Maven build file (includes PDFBox, OkHttp, JSON, and Tess4J dependencies).

## Setup

1. Place your PDF at:

   src/main/resources/policy.pdf

2. Set your Gemini API key. You can either set the environment variable `GEMINI_API_KEY` or create a `.env` file in the project root.

   - PowerShell (env var):

```powershell
$env:GEMINI_API_KEY = 'YOUR_REAL_KEY'
$env:TESSDATA_PREFIX = 'C:\Program Files\Tesseract-OCR\tessdata'  # optional if needed
```

   - Or create a `.env` file at the project root with the following contents (copy from `.env.example`):

```text
GEMINI_API_KEY=YOUR_REAL_KEY
TESSDATA_PREFIX=C:\Program Files\Tesseract-OCR\tessdata
```

3. Build the project:

```bash
mvn package
```

4. Run the program:

```bash
mvn -q exec:java
```

The program will:
- Discover a model that supports `generateContent` and use it.
- Extract text from the PDF. If the PDF has little or no selectable text, it will perform OCR on each page.
- Send the document text + the hardcoded question `What are the password complexity requirements?` to the Gemini API and print the audit result.

## Notes & Troubleshooting

- If PDFBox extracts no or very little text and OCR returns nothing, your PDF may be encrypted or corrupted.
- Tess4J relies on native Tesseract libraries; ensure Tesseract is installed and `TESSDATA_PREFIX` points to the `tessdata` folder if Tess4J cannot find language data.
- If you need a different OCR language, modify `tesseract.setLanguage("eng")` in `SimpleAuditBot.java`.

## Next steps
- Make the question a CLI argument.
- Add interactive model selection instead of auto-fallback.
- Add retries, exponential backoff, and response validation for the API call.


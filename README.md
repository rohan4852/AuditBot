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
- `src/main/resources/PUB100420.pdf` - sample PDF (replace with your own).
- `pom.xml` - Maven build file (includes PDFBox, OkHttp, JSON, and Tess4J dependencies).
- `.env.example` - example environment file for API key.

## Setup

1. Place your PDF at:

   `src/main/resources/PUB100420.pdf` (or update the `PDF_PATH` constant in the code for a different name).

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
mvn -q exec:java [optional question]
```

   - If no arguments are provided, it uses the default question: "What are the benefits for the Development Bank of Japan?"
   - You can pass a custom question as command-line arguments, e.g., `mvn -q exec:java What are the key policies?`

The program will:
- Dynamically discover and use a Gemini model that supports `generateContent`.
- Extract text from the PDF. If the PDF has little or no selectable text, it will perform OCR on each page.
- Send the document text + your question to the Gemini API and print the audit result.

## Notes & Troubleshooting

- If PDFBox extracts no or very little text and OCR returns nothing, your PDF may be encrypted or corrupted.
- Tess4J relies on native Tesseract libraries; ensure Tesseract is installed and `TESSDATA_PREFIX` points to the `tessdata` folder if Tess4J cannot find language data.
- If you need a different OCR language, modify `tesseract.setLanguage("eng")` in `SimpleAuditBot.java`.
- The program caches PDF text and chosen model for performance.


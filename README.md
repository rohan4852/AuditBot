# AuditBot

AuditBot is a lightweight Java CLI that reads a local PDF, extracts its text (or runs OCR for scanned PDFs), and sends the content plus a user question to the Google Gemini (Generative Language) API to produce a strict compliance answer.

## Quick Start for Pilot (No Code Required)

1. Ensure you have Java 17+ installed.
2. Download the `auditbot-1.0.0.jar` file.
3. Place the PDF you want to audit in the same folder and rename it to `policy.pdf`.
4. Open your terminal/command prompt in that folder and run:
   java -jar auditbot-1.0.0.jar "What is the password policy?"

## Prerequisites

- Java 17+ and Maven installed.
- Tesseract OCR installed on your machine for OCR support:
  - Windows: install via the official installer (e.g., https://github.com/tesseract-ocr/tesseract)
  - Ensure the `tesseract` executable is on your `PATH`, or set `TESSDATA_PREFIX` to your tessdata folder.
- A working Google Generative Language API key with model access.

## Files

- `src/main/java/SimpleAuditBot.java` - main program.
- `auditbot-1.0.0.jar` - executable JAR file (built with `mvn package`).
- `pom.xml` - Maven build file (includes PDFBox, OkHttp, JSON, and Tess4J dependencies).
- `.env.example` - example environment file for API key.

## Setup

1. Place your PDF in the same folder as the JAR file and rename it to `policy.pdf`.

2. A `.env` file is included with a temporary Gemini API key for testing purposes. If you need to use your own key, replace it in the `.env` file or set the environment variable `GEMINI_API_KEY`.

   - To use your own key, edit the `.env` file:

```text
GEMINI_API_KEY=YOUR_REAL_KEY
TESSDATA_PREFIX=C:\Program Files\Tesseract-OCR\tessdata
```

   - Or set the environment variable in PowerShell:

```powershell
$env:GEMINI_API_KEY = 'YOUR_REAL_KEY'
$env:TESSDATA_PREFIX = 'C:\Program Files\Tesseract-OCR\tessdata'  # optional if needed
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
# AuditBot (Streamlit)

AuditBot is a **Python Streamlit** web app that audits one or more PDFs against a user question using **Gemini**, while enforcing strict evidence requirements.

Core logic preserved from the original Java CLI:

1. **PDF extraction**: Try selectable text first (pypdf). If extracted text is **< 50 characters**, fall back to OCR.
2. **OCR fallback**: `pdf2image` + `pytesseract`.
3. **AI model**: Gemini via `google-generativeai` (default: `gemini-1.5-flash`, optional `gemini-pro`).
4. **System prompt** (verbatim):

> "You are a strict Compliance Auditor. Answer the question ONLY using the facts from the document provided below. If the answer is not in the text, say 'EVIDENCE NOT FOUND'. Quote the specific sentence from the text as proof."

## Setup

### 1) Create a virtualenv (recommended)

```bash
python -m venv .venv
# Windows PowerShell:
.\.venv\Scripts\Activate.ps1
```

### 2) Install dependencies

```bash
pip install -r requirements.txt
```

### 3) Provide your API key

Create a `.env` file in the project root:

```text
GEMINI_API_KEY=YOUR_REAL_KEY
# Optional
TESSDATA_PREFIX=C:\\Program Files\\Tesseract-OCR\\tessdata
TESSERACT_CMD=C:\\Program Files\\Tesseract-OCR\\tesseract.exe
POPPLER_PATH=C:\\path\\to\\poppler\\Library\\bin
```

Or paste the API key directly into the app sidebar.

## Running the app

```bash
streamlit run app.py
```

## Notes / Troubleshooting

- **OCR prerequisites**:
  - `pytesseract` requires **Tesseract OCR** installed on your machine.
  - `pdf2image` requires **Poppler** installed (and available on PATH), or set `POPPLER_PATH` to the Poppler `bin` directory.
- The app caches expensive PDF extraction / OCR work using `@st.cache_data` by caching on the **PDF bytes + OCR settings** (not on the Streamlit UploadedFile object).

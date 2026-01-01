import io
import json
import os
import re
from dataclasses import asdict, dataclass
from typing import Any, Dict, List, Optional, Tuple

import pandas as pd
import streamlit as st
from dotenv import load_dotenv
from pdf2image import convert_from_bytes
from pypdf import PdfReader
import pytesseract

import google.generativeai as genai


SYSTEM_PROMPT = (
    "You are a strict Compliance Auditor. Answer the question ONLY using the facts from the document provided below. "
    "If the answer is not in the text, say 'EVIDENCE NOT FOUND'. Quote the specific sentence from the text as proof."
)


@dataclass
class AuditRow:
    filename: str
    audit_finding: str
    evidence: str


def _safe_str(exc: BaseException) -> str:
    msg = f"{type(exc).__name__}: {exc}"
    # Keep table readable; streamlit will still show full exception in logs if needed.
    return msg[:4000]


def _clean_text(text: str) -> str:
    # Light normalization to reduce prompt bloat; do not over-process.
    text = text.replace("\x00", " ")
    text = re.sub(r"[\t\r ]+", " ", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


@st.cache_data(show_spinner=False)
def extract_pdf_text(
    pdf_bytes: bytes,
    *,
    filename: str,
    ocr_threshold_chars: int,
    ocr_dpi: int,
    ocr_lang: str,
    poppler_path: Optional[str],
) -> Tuple[str, bool]:
    """Extracts text from a PDF.

    1) Try selectable text extraction via pypdf.
    2) If extracted text is < ocr_threshold_chars, fall back to OCR.

    Caching note:
    - Streamlit can cache this as long as we pass *hashable* inputs.
    - We cache on the PDF bytes + OCR settings (not on UploadedFile objects).
    """
    extracted = ""
    try:
        reader = PdfReader(io.BytesIO(pdf_bytes))
        pages: List[str] = []
        for page in reader.pages:
            t = page.extract_text() or ""
            t = t.strip()
            if t:
                pages.append(t)
        extracted = "\n\n".join(pages).strip()
    except Exception:
        # If PDF parsing fails, we'll let OCR attempt to salvage.
        extracted = ""

    if len(extracted) >= ocr_threshold_chars:
        return _clean_text(extracted), False

    # OCR fallback
    ocr_text = ocr_pdf_text(
        pdf_bytes,
        dpi=ocr_dpi,
        lang=ocr_lang,
        poppler_path=poppler_path,
    )
    ocr_text = (ocr_text or "").strip()
    if ocr_text:
        return _clean_text(ocr_text), True

    # No OCR text found either; return whatever we had.
    return _clean_text(extracted), True


@st.cache_data(show_spinner=False)
def ocr_pdf_text(
    pdf_bytes: bytes,
    *,
    dpi: int,
    lang: str,
    poppler_path: Optional[str],
) -> str:
    images = convert_from_bytes(
        pdf_bytes,
        dpi=dpi,
        fmt="png",
        poppler_path=poppler_path or None,
    )
    parts: List[str] = []
    for img in images:
        parts.append(pytesseract.image_to_string(img, lang=lang) or "")
    return "\n".join(parts)


def _extract_json_object(text: str) -> Optional[Dict[str, Any]]:
    # Gemini sometimes wraps JSON in prose/markdown. Extract first {...} block.
    m = re.search(r"\{[\s\S]*\}", text)
    if not m:
        return None
    try:
        return json.loads(m.group(0))
    except Exception:
        return None


def ask_gemini(*, api_key: str, model_name: str, document_text: str, question: str) -> AuditRow:
    genai.configure(api_key=api_key)

    if model_name.startswith("models/"):
        model_name = model_name[len("models/") :]

    model = genai.GenerativeModel(
        model_name=model_name,
        system_instruction=SYSTEM_PROMPT,
    )

    # Keep system prompt exactly as specified (above). Use the user prompt only to request structure.
    user_prompt = (
        "DOCUMENT:\n"
        f"{document_text}\n\n"
        "QUESTION:\n"
        f"{question}\n\n"
        "Return ONLY a JSON object with keys: \"audit_finding\" and \"evidence\". "
        "The value of \"evidence\" must be an exact sentence copied from DOCUMENT (verbatim quote). "
        "If the answer cannot be supported from DOCUMENT, set BOTH values to 'EVIDENCE NOT FOUND'."
    )

    resp = model.generate_content(user_prompt)
    raw = (getattr(resp, "text", None) or "").strip()

    parsed = _extract_json_object(raw)
    if isinstance(parsed, dict):
        audit_finding = str(parsed.get("audit_finding", "")).strip()
        evidence = str(parsed.get("evidence", "")).strip()
        if not audit_finding:
            audit_finding = raw
        if not evidence:
            evidence = ""
        return AuditRow(filename="", audit_finding=audit_finding, evidence=evidence)

    # Fallback: treat the entire response as the finding.
    return AuditRow(filename="", audit_finding=raw or "", evidence="")


def choose_model(api_key: str, preferred: str) -> str:
    """Mirror the Java behavior lightly: allow an 'Auto' mode that selects a model supporting generateContent."""
    if preferred != "Auto (list models)":
        return preferred

    genai.configure(api_key=api_key)
    try:
        for m in genai.list_models():
            methods = getattr(m, "supported_generation_methods", None) or []
            if "generateContent" in methods:
                # Names are typically like 'models/gemini-1.5-flash'
                return str(getattr(m, "name", "gemini-1.5-flash"))
    except Exception:
        pass

    return "gemini-1.5-flash"


def main() -> None:
    load_dotenv()

    st.set_page_config(page_title="AuditBot", layout="wide")
    st.title("AuditBot — Compliance Auditor")

    with st.sidebar:
        st.header("Configuration")

        api_key = st.text_input(
            "GEMINI_API_KEY",
            value=os.getenv("GEMINI_API_KEY", ""),
            type="password",
            help="Stored only in this browser session unless you also put it in your .env.",
        )

        model_choice = st.selectbox(
            "Model",
            options=[
                "gemini-1.5-flash",
                "gemini-pro",
                "Auto (list models)",
            ],
            index=0,
        )

        st.subheader("OCR (fallback)")
        ocr_threshold_chars = st.number_input(
            "OCR fallback threshold (chars)",
            min_value=0,
            max_value=10000,
            value=50,
            step=10,
            help="If extracted selectable text is shorter than this, OCR will run.",
        )
        ocr_dpi = st.number_input("OCR DPI", min_value=100, max_value=600, value=300, step=50)
        ocr_lang = st.text_input("Tesseract language", value="eng")

        tessdata_prefix = st.text_input(
            "TESSDATA_PREFIX (optional)",
            value=os.getenv("TESSDATA_PREFIX", ""),
            help="Optional path to tessdata folder. On Windows, often ends with \\tessdata.",
        )
        tesseract_cmd = st.text_input(
            "Tesseract executable path (optional)",
            value=os.getenv("TESSERACT_CMD", ""),
            help="If tesseract isn't on PATH, set full path to tesseract.exe.",
        )

        poppler_path = st.text_input(
            "Poppler bin path (optional)",
            value=os.getenv("POPPLER_PATH", ""),
            help="pdf2image needs Poppler. If it's not on PATH, set the folder containing pdftoppm.exe.",
        )

    if tessdata_prefix:
        os.environ["TESSDATA_PREFIX"] = tessdata_prefix
    if tesseract_cmd:
        pytesseract.pytesseract.tesseract_cmd = tesseract_cmd

    question = st.text_input(
        "Audit question",
        value="What are the benefits for the Development Bank of Japan?",
    )

    files = st.file_uploader(
        "Upload PDF files",
        type=["pdf"],
        accept_multiple_files=True,
    )

    run = st.button("Run Audit", type="primary", disabled=not (files and question.strip()))

    if not run:
        st.caption(
            "Tip: For scanned PDFs, ensure Tesseract + Poppler are installed. OCR runs only when selectable text is < 50 chars."
        )
        return

    if not api_key.strip():
        st.error("Please enter your GEMINI_API_KEY in the sidebar.")
        return

    chosen_model = choose_model(api_key, model_choice)

    rows: List[AuditRow] = []
    progress = st.progress(0)

    for i, f in enumerate(files):
        progress.progress((i) / max(len(files), 1))
        try:
            pdf_bytes = f.getvalue()

            doc_text, used_ocr = extract_pdf_text(
                pdf_bytes,
                filename=f.name,
                ocr_threshold_chars=int(ocr_threshold_chars),
                ocr_dpi=int(ocr_dpi),
                ocr_lang=ocr_lang.strip() or "eng",
                poppler_path=poppler_path.strip() or None,
            )

            if not doc_text:
                raise RuntimeError(
                    "No text could be extracted from the PDF (selectable text + OCR both returned empty)."
                )

            result = ask_gemini(
                api_key=api_key,
                model_name=chosen_model,
                document_text=doc_text,
                question=question.strip(),
            )
            result.filename = f.name

            # Intentionally keep Evidence as a pure quote from the document.
            # (We don’t prepend notes like "[OCR used]" to avoid corrupting the quoted sentence.)

            rows.append(result)

        except Exception as e:
            rows.append(
                AuditRow(
                    filename=f.name,
                    audit_finding="ERROR",
                    evidence=_safe_str(e),
                )
            )

    progress.progress(1.0)

    df = pd.DataFrame([asdict(r) for r in rows])
    # Match requested column naming
    df = df.rename(
        columns={
            "filename": "Filename",
            "audit_finding": "Audit Finding",
            "evidence": "Evidence",
        }
    )

    st.subheader("Results")
    st.dataframe(df, use_container_width=True)

    csv_bytes = df.to_csv(index=False).encode("utf-8")
    st.download_button(
        "Download Report (CSV)",
        data=csv_bytes,
        file_name="audit_report.csv",
        mime="text/csv",
    )


if __name__ == "__main__":
    main()

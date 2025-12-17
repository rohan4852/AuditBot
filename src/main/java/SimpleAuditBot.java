import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import java.awt.image.BufferedImage;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.json.JSONArray;
import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SimpleAuditBot {
    private static final String PDF_PATH = "src/main/resources/PUB100420.pdf";
    private static final String API_KEY;
    private static final OkHttpClient httpClient = new OkHttpClient();
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    static {
        // Load .env file (if present) so users may supply values in a project-local
        // file.
        java.util.Map<String, String> dot = loadDotEnv();
        String k = System.getenv("GEMINI_API_KEY");
        if ((k == null || k.isBlank()) && dot.containsKey("GEMINI_API_KEY")) {
            k = dot.get("GEMINI_API_KEY");
        }
        if (k == null || k.isBlank()) {
            k = "YOUR_GEMINI_API_KEY";
        }
        API_KEY = k;
    }

    // Simple .env loader: reads lines like KEY=VALUE from project root .env
    private static java.util.Map<String, String> loadDotEnv() {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        java.io.File f = new java.io.File(".env");
        if (!f.exists())
            return map;
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#"))
                    continue;
                int eq = line.indexOf('=');
                if (eq <= 0)
                    continue;
                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();
                // Remove surrounding quotes if present
                if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                    val = val.substring(1, val.length() - 1);
                }
                map.put(key, val);
            }
        } catch (Exception e) {
            System.err.println("Failed to read .env: " + e.getMessage());
        }
        return map;
    }

    public static void main(String[] args) {
        if (System.getenv("GEMINI_API_KEY") == null || System.getenv("GEMINI_API_KEY").isBlank()) {
            System.err.println("Warning: GEMINI_API_KEY not set. Using placeholder -- API calls will fail.");
        }
        // Log Tesseract diagnostic info to help debug OCR issues
        String tessDataEnv = System.getenv("TESSDATA_PREFIX");
        System.out.println("TESSDATA_PREFIX=" + (tessDataEnv == null ? "(null)" : tessDataEnv));
        try {
            ProcessBuilder pb = new ProcessBuilder("tesseract", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            if (line != null) {
                System.out.println("Tesseract found: " + line);
            } else {
                System.err.println("Tesseract executable did not return version info.");
            }
            p.destroy();
        } catch (IOException e) {
            System.err.println("Tesseract not found on PATH or failed to run: " + e.getMessage());
        }
        // Determine a model that supports generateContent
        String chosenModel = null;
        try {
            chosenModel = chooseModelForGenerateContent();
            if (chosenModel != null) {
                System.out.println("Using model: " + chosenModel);
            } else {
                System.err
                        .println("No model advertising generateContent found. Falling back to models/gemini-2.5-flash");
                chosenModel = "models/gemini-2.5-flash";
            }
        } catch (Exception e) {
            System.err.println("Failed to discover models: " + e.getMessage());
            chosenModel = "models/gemini-2.5-flash";
        }

        File pdf = new File(PDF_PATH);
        if (!pdf.exists()) {
            System.err.println("PDF not found at: " + PDF_PATH);
            System.err.println("Place your PDF at src/main/resources/policy.pdf");
            System.exit(1);
        }

        String pdfText;
        try {
            pdfText = extractTextFromPDF(pdf);
        } catch (IOException e) {
            System.err.println("Failed to read PDF: " + e.getMessage());
            return;
        }

        String question = "What are the password complexity requirements?";

        try {
            String responseBody = askGemini(pdfText, question, chosenModel);
            String extracted = tryExtractTextField(responseBody);
            System.out.println("\n=== Audit Report ===\n");
            if (extracted != null && !extracted.isEmpty()) {
                System.out.println(extracted);
            } else {
                // Fallback: print raw body
                System.out.println(responseBody);
            }
        } catch (Exception e) {
            System.err.println("Error calling Gemini API: " + e.getMessage());
        }
    }

    // Lists available models for the configured API key and prints them to stdout.
    public static void listModels() throws IOException {
        String url = "https://generativelanguage.googleapis.com/v1/models?key=" + API_KEY;
        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "AuditBot/1.0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String resp = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                System.err.println("ListModels returned: " + response.code() + " - " + resp);
                return;
            }
            System.out.println("\nAvailable models (ListModels response):\n" + resp + "\n");
        }
    }

    // Returns the first model name that supports the "generateContent" method, or
    // null if none found.
    public static String chooseModelForGenerateContent() throws IOException {
        String url = "https://generativelanguage.googleapis.com/v1/models?key=" + API_KEY;
        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("User-Agent", "AuditBot/1.0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String resp = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("ListModels failed: " + response.code() + " - " + resp);
            }
            JSONObject root = new JSONObject(resp);
            if (!root.has("models"))
                return null;
            JSONArray models = root.getJSONArray("models");
            for (int i = 0; i < models.length(); i++) {
                JSONObject m = models.getJSONObject(i);
                if (!m.has("supportedGenerationMethods"))
                    continue;
                JSONArray methods = m.getJSONArray("supportedGenerationMethods");
                for (int j = 0; j < methods.length(); j++) {
                    String method = methods.getString(j);
                    if ("generateContent".equals(method)) {
                        return m.optString("name", null);
                    }
                }
            }
            return null;
        }
    }

    public static String extractTextFromPDF(File file) throws IOException {
        // First try selectable text extraction
        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            text = text == null ? "" : text.trim();
            // If extracted text is very short, attempt OCR on rendered pages
            if (text.length() < 50) {
                System.out.println("Extracted text is short (" + text.length() + " chars). Running OCR...");
                String ocr = performOCR(file);
                return (ocr == null || ocr.isBlank()) ? text : ocr.trim();
            }
            return text;
        }
    }

    // Uses PDFBox to render pages and Tesseract (Tess4J) to OCR each page.
    private static String performOCR(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        PDDocument document = null;
        try {
            document = PDDocument.load(file);
            PDFRenderer renderer = new PDFRenderer(document);
            Tesseract tesseract = new Tesseract();
            // If user set TESSDATA_PREFIX environment variable, configure tessdata path
            String tessData = System.getenv("TESSDATA_PREFIX");
            if (tessData != null && !tessData.isBlank()) {
                tesseract.setDatapath(tessData);
            }
            // Default to English; user can modify source to change language
            tesseract.setLanguage("eng");

            int pages = document.getNumberOfPages();
            for (int i = 0; i < pages; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, 300);
                try {
                    String result = tesseract.doOCR(image);
                    if (result != null)
                        sb.append(result).append(System.lineSeparator());
                } catch (TesseractException e) {
                    System.err.println("Tesseract OCR failed on page " + i + ": " + e.getMessage());
                }
            }
        } finally {
            if (document != null)
                document.close();
        }
        return sb.toString();
    }

    public static String askGemini(String pdfContext, String question, String modelName) throws IOException {
        String systemPrompt = "You are a strict Compliance Auditor. Answer the question ONLY using the facts from the document provided below. If the answer is not in the text, say 'EVIDENCE NOT FOUND'. Quote the specific sentence from the text as proof.";
        String combined = systemPrompt + "\n\n" + pdfContext + "\n\nQuestion: " + question;

        JSONObject part = new JSONObject();
        part.put("text", combined);
        JSONArray parts = new JSONArray();
        parts.put(part);

        JSONObject content = new JSONObject();
        content.put("parts", parts);

        JSONArray contentsArr = new JSONArray();
        JSONObject contentsObj = new JSONObject();
        contentsObj.put("parts", parts);
        contentsArr.put(contentsObj);

        JSONObject body = new JSONObject();
        body.put("contents", contentsArr);

        String modelId = (modelName == null) ? "gemini-2.5-flash" : modelName.replaceFirst("^models/", "");
        String url = "https://generativelanguage.googleapis.com/v1/models/" + modelId + ":generateContent?key="
                + API_KEY;

        RequestBody reqBody = RequestBody.create(body.toString(), JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(reqBody)
                .header("User-Agent", "AuditBot/1.0")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response.code() + " - " + response.message() + "\n"
                        + (response.body() != null ? response.body().string() : ""));
            }
            String resp = response.body() != null ? new String(response.body().bytes(), StandardCharsets.UTF_8) : "";
            return resp;
        }
    }

    private static String tryExtractTextField(String responseBody) {
        try {
            Object json = new org.json.JSONTokener(responseBody).nextValue();
            Object found = findTextInJson(json);
            return found == null ? null : found.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static Object findTextInJson(Object node) {
        if (node == null)
            return null;
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            for (String key : obj.keySet()) {
                Object val = obj.get(key);
                if ("text".equalsIgnoreCase(key) && val instanceof String) {
                    return val;
                }
                Object found = findTextInJson(val);
                if (found != null)
                    return found;
            }
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); i++) {
                Object val = arr.get(i);
                Object found = findTextInJson(val);
                if (found != null)
                    return found;
            }
        }
        return null;
    }
}

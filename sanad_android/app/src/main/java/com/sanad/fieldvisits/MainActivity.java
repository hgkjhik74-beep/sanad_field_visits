package com.sanad.fieldvisits;

import android.app.Activity;
import androidx.core.content.FileProvider;
import java.io.File;
import android.os.Bundle;
import android.webkit.*;
import android.content.Intent;
import android.net.Uri;
import android.content.ContentValues;
import android.provider.MediaStore;
import android.os.Environment;
import android.widget.Toast;
import android.util.Base64;
import android.print.PrintManager;
import android.print.PrintAttributes;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private static final int FILE_REQUEST = 1001;
    private Uri cameraUri;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        webView.addJavascriptInterface(new FileBridge(), "AndroidFiles");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                boolean wantsImage = false;
                for (String type : params.getAcceptTypes()) {
                    if (type != null && type.toLowerCase().contains("image")) wantsImage = true;
                }
                Intent picker = new Intent(Intent.ACTION_GET_CONTENT);
                picker.addCategory(Intent.CATEGORY_OPENABLE);
                picker.setType(wantsImage ? "image/*" : "application/json");
                Intent chooser = Intent.createChooser(picker, wantsImage ? "التقاط أو اختيار صورة" : "اختر النسخة الاحتياطية");
                if (wantsImage) {
                    try {
                        File photo = new File(getCacheDir(), "sanad_camera_" + System.currentTimeMillis() + ".jpg");
                        cameraUri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", photo);
                        Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                        camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                        camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
                    } catch (Exception ignored) { cameraUri = null; }
                }
                startActivityForResult(chooser, FILE_REQUEST);
                return true;
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    private class FileBridge {

        @JavascriptInterface public void openWhatsApp(String url) {
            runOnUiThread(() -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "تعذر فتح واتساب", Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public void printPdf() {
            runOnUiThread(() -> {
                try {
                    PrintManager manager = (PrintManager) getSystemService(PRINT_SERVICE);
                    if (manager == null) throw new Exception("خدمة الطباعة غير متاحة");
                    manager.print("سند - الزيارات الميدانية",
                        webView.createPrintDocumentAdapter("Sanad-Field-Visits"),
                        new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "تعذر تشغيل تصدير PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }


        @JavascriptInterface public void saveImage(String name, String mime, String base64) {
            runOnUiThread(() -> {
                try {
                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                    values.put(MediaStore.Images.Media.MIME_TYPE, mime);
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Sanad");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                    Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new Exception("تعذر إنشاء الصورة");
                    OutputStream out = getContentResolver().openOutputStream(uri);
                    if (out == null) throw new Exception("تعذر حفظ الصورة");
                    out.write(bytes); out.close();
                    ContentValues done = new ContentValues();
                    done.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(uri, done, null, null);
                    Toast.makeText(MainActivity.this, "تم حفظ الصورة في الاستديو / Pictures/Sanad", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "تعذر حفظ الصورة: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }

        @JavascriptInterface public void saveFile(String name, String mime, String base64) {
            runOnUiThread(() -> {
                try {
                    byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    values.put(MediaStore.Downloads.MIME_TYPE, mime);
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Sanad");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new Exception("تعذر إنشاء الملف");
                    OutputStream out = getContentResolver().openOutputStream(uri);
                    if (out == null) throw new Exception("تعذر حفظ الملف");
                    out.write(bytes); out.close();
                    Toast.makeText(MainActivity.this, "تم حفظ الملف في مجلد Downloads/Sanad", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "تعذر حفظ الملف: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_REQUEST && fileCallback != null) {
            Uri selected = null;
            if (resultCode == Activity.RESULT_OK) {
                if (data != null && data.getData() != null) selected = data.getData();
                else if (cameraUri != null) selected = cameraUri;
            }
            Uri[] result = selected != null ? new Uri[]{selected} : null;
            fileCallback.onReceiveValue(result); fileCallback = null; cameraUri = null;
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}

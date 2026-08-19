package com.atrcapture.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private TextRecognizer textRecognizer;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        webView.setBackgroundColor(0xff050505);
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new NativeOcrBridge(), "NativeOcr");
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                startActivityForResult(i, 42);
                return true;
            }
        });
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 42 && fileCallback != null) {
            Uri[] result = (resultCode == RESULT_OK && data != null && data.getData() != null)
                    ? new Uri[]{data.getData()} : null;
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (textRecognizer != null) textRecognizer.close();
        super.onDestroy();
    }

    private class NativeOcrBridge {
        @JavascriptInterface public void recognize(String requestId, String dataUrl) {
            try {
                int comma = dataUrl.indexOf(',');
                String data = comma >= 0 ? dataUrl.substring(comma + 1) : dataUrl;
                byte[] bytes = Base64.decode(data, Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap == null) throw new IllegalArgumentException("Invalid image crop");
                InputImage image = InputImage.fromBitmap(bitmap, 0);
                textRecognizer.process(image)
                    .addOnSuccessListener(result -> sendOcrResult(requestId, result.getText(), null))
                    .addOnFailureListener(error -> sendOcrResult(requestId, "", error.getMessage()));
            } catch (Exception error) {
                sendOcrResult(requestId, "", error.getMessage());
            }
        }
    }

    private void sendOcrResult(String requestId, String text, String error) {
        runOnUiThread(() -> webView.evaluateJavascript(
            "window.__nativeOcrResult(" + JSONObject.quote(requestId) + ","
                + JSONObject.quote(text == null ? "" : text) + ","
                + (error == null ? "null" : JSONObject.quote(error)) + ");", null));
    }
}

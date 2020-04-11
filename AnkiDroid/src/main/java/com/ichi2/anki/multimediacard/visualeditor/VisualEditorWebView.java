/*
MIT License

Copyright (c) 2017 Jhon Kenneth Cariño

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

//Based off https://github.com/jkennethcarino/rtexteditorview/blob/7c0a50240de51ee576793afdfcf4f173e4c609fd/library/src/main/java/com/jkcarino/rtexteditorview/RTextEditorView.java

package com.ichi2.anki.multimediacard.visualeditor;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Base64;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.ichi2.utils.FunctionalInterfaces.Consumer;
import com.jkcarino.rtexteditorview.RTextEditorView;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import timber.log.Timber;

public class VisualEditorWebView extends WebView {

    private static final String DEFAULT_EDITOR_URL = "file:///android_asset/visualeditor/visual_editor.html";
    private String content;

    private RTextEditorView.OnTextChangeListener onTextChangeListener;
    private SelectionChangeListener mSelectionChangedListener;
    private boolean isReady;

    public VisualEditorWebView(Context context) {
        super(context);
    }


    public VisualEditorWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }


    public VisualEditorWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public VisualEditorWebView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public void init(InputStream content, String baseUrl) {
        WebSettings settings = getSettings();
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);

        setWebChromeClient(getDefaultWebChromeClient());
        setWebViewClient(getDefaultWebViewClient());
        addJavascriptInterface(this, "RTextEditorView");
        //TODO: Janky, don't do this here.
        String asString;
        try {
            byte[] fs;
            fs = readFile(content);
            asString = new String(fs, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        loadDataWithBaseURL(baseUrl + "__visual_editor__.html\"", asString, "text/html; charset=utf-8", "base64", null);
    }

    private byte[] readFile(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = null;
        try {
            buffer = new ByteArrayOutputStream();

            int nRead;
            byte[] data = new byte[16384];

            while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }

            return buffer.toByteArray();
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
            if (buffer != null) {
                buffer.close();
            }
        }
    }

    @JavascriptInterface
    public void onEditorContentChanged(String content) {
        if (onTextChangeListener != null) {
            onTextChangeListener.onTextChanged(content);
        }
        this.content = content;
    }

    /** SELECTION */

    @JavascriptInterface
    public void onImageSelection(String guid, String src) {
        Timber.d("onImageSelection %s", src);
        onSelectionChanged(SelectionType.imageFromSrc(guid, src));
    }

    @JavascriptInterface
    public void onRegularSelection() {
        onSelectionChanged(SelectionType.REGULAR);
    }

    protected void onSelectionChanged(SelectionType selection) {
        SelectionChangeListener listener = getSelectionChangedListener();
        if (listener == null) {
            return;
        }
        listener.onSelectionChanged(selection);
    }

    public void setSelectionChangedListener(SelectionChangeListener listener) {
        this.mSelectionChangedListener = listener;
    }

    public SelectionChangeListener getSelectionChangedListener() {
        return this.mSelectionChangedListener;
    }

    /** END SELECTION */

    public boolean isReady() {
        return isReady;
    }


    public RTextEditorView.OnTextChangeListener getOnTextChangeListener() {
        return onTextChangeListener;
    }


    public void setOnTextChangeListener(RTextEditorView.OnTextChangeListener onTextChangeListener) {
        this.onTextChangeListener = onTextChangeListener;
    }


    protected WebViewClient getDefaultWebViewClient() {
        return new VisualEditorWebViewClient();
    }

    protected WebChromeClient getDefaultWebChromeClient() {
        return new WebChromeClient() {

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                String message = String.format(Locale.US, "%s (%s:%d)",
                        consoleMessage.message(),
                        consoleMessage.sourceId(),
                        consoleMessage.lineNumber());


                getLevel(consoleMessage).consume(message);
                return super.onConsoleMessage(consoleMessage);
            }

            @CheckResult
            private Consumer<String> getLevel(ConsoleMessage consoleMessage) {
                switch (consoleMessage.messageLevel()) {
                    case WARNING: return Timber::w;
                    case ERROR: return Timber::e;
                    case LOG: return Timber::i;

                    case DEBUG:
                    case TIP:
                    default: return Timber::d;
                }
            }
        };
    }

    public void setHtml(@NonNull String html) {
        ExecEscaped s = ExecEscaped.fromString(html);
        execUnsafe("setHtml('" + s.getEscapedValue() + "');");
    }

    /** Executes a niladic JavaScript function */
    public void execFunction(String functionName) {
        execUnsafe(functionName + "();");
    }

    public void exec(@NonNull final ExecEscaped safeString) {
        String unsafeString = safeString.getEscapedValue();
        if (unsafeString == null) {
            return;
        }
        execUnsafe(unsafeString);
    }

    protected void execUnsafe(@NonNull final String unsafeString) {
        execInternal("javascript:" + unsafeString);
    }


    private void execInternal(@NonNull final String valueToExec) {
        //Note: don't mutate valueToExec due to the postDelayed
        if (isReady) {
            load(valueToExec);
        } else {
            postDelayed(() -> execInternal(valueToExec), 100);
        }
    }


    private void load(@NonNull String trigger) {
        Timber.v("Executing JS: '%s'", trigger);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            evaluateJavascript(trigger, null);
        } else {
            loadUrl(trigger);
        }
    }
    
    @JavascriptInterface
    public void updateCurrentStyle(String style) {
        //Required by existing JS, but currently unused.
    }

    public String getContent() {
        return content;
    }

    public void injectCss(String css) {
        Timber.v("Applying CSS: %s", css);
        //Snippet from https://stackoverflow.com/a/30018910
        try {
            byte[] data = css.getBytes("UTF-8");
            String encoded = Base64.encodeToString(data, Base64.NO_WRAP);
            // Base-64 decode in the browser.
            this.execInternal("javascript:(function() {" +
                    "var parent = document.getElementsByTagName('head').item(0);" +
                    "var style = document.createElement('style');" +
                    "style.type = 'text/css';" +
                    "style.innerHTML = window.atob('" + encoded + "');" +
                    "parent.appendChild(style)" +
                    "})()");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void deleteImage(@NonNull String guid) {
        //noinspection ConstantConditions
        if (guid == null) {
            Timber.w("Failed to delete image - no guid");
            return;
        }
        ExecEscaped safeString =  ExecEscaped.fromString(guid);
        String safeCommand = String.format("deleteImage('%s')", safeString.getEscapedValue());
        execUnsafe(safeCommand);
    }


    public static class ExecEscaped {
        private final String escapedValue;

        protected ExecEscaped(@Nullable String value) {
            this.escapedValue = value;
        }

        @Nullable
        public String getEscapedValue() {
            return escapedValue;
        }

        public static ExecEscaped fromString(String s) {
            if (s == null) {
                return new ExecEscaped(s);
            }
            return new ExecEscaped(escapeString(s));
        }


        public static String escapeString(String s) {
            return s.replace("'", "\\'");
        }
    }

    /** TODO: Crash Protection */
    protected class VisualEditorWebViewClient extends WebViewClient {

        @Override
        public void onPageFinished(WebView view, String url) {
            isReady = true; //ideally we should confirm the url, but as we load
            super.onPageFinished(view, url);
        }

        @Override
        @SuppressWarnings("deprecation") //TODO
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Timber.i("TODO: Intercepted %s", url);
            view.loadUrl(url);
            return true;
        }

        @TargetApi(Build.VERSION_CODES.N)
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            view.loadUrl(request.getUrl().toString());
            return true;
        }
    }

    @FunctionalInterface
    public interface SelectionChangeListener {
        void onSelectionChanged(SelectionType selection);
    }

    public enum SelectionType {
        REGULAR,
        IMAGE;

        private String mData;
        private String mGuid;

        public static SelectionType imageFromSrc(String guid, String src) {
            SelectionType type = SelectionType.IMAGE;
            type.mData = src;
            type.mGuid = guid;
            return type;
        }


        public String getImageSrc() {
            return mData;
        }


        public String getGuid() {
            return mGuid;
        }
    }
}

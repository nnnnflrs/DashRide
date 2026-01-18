package com.motorcycle.dashride.ph;

import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.Locale;

@CapacitorPlugin(name = "TextToSpeech")
public class TextToSpeechPlugin extends Plugin {
    private static final String TAG = "TextToSpeechPlugin";
    private TextToSpeech tts;
    private boolean isInitialized = false;

    @Override
    public void load() {
        super.load();

        // Initialize TextToSpeech
        tts = new TextToSpeech(getContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language not supported");
                    isInitialized = false;
                } else {
                    tts.setSpeechRate(0.9f);
                    isInitialized = true;
                    Log.d(TAG, "TextToSpeech initialized successfully");
                }
            } else {
                Log.e(TAG, "TextToSpeech initialization failed");
                isInitialized = false;
            }
        });

        // Set up utterance progress listener for callbacks
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.d(TAG, "TTS started: " + utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                Log.d(TAG, "TTS completed: " + utteranceId);
            }

            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "TTS error: " + utteranceId);
            }
        });
    }

    @PluginMethod
    public void speak(PluginCall call) {
        String text = call.getString("text");
        if (text == null || text.isEmpty()) {
            call.reject("Text is required");
            return;
        }

        if (!isInitialized) {
            call.reject("TextToSpeech not initialized");
            return;
        }

        Float rate = call.getFloat("rate", 0.9f);
        tts.setSpeechRate(rate);

        int result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "navigation_instruction");

        if (result == TextToSpeech.SUCCESS) {
            call.resolve();
        } else {
            call.reject("Failed to speak text");
        }
    }

    @PluginMethod
    public void stop(PluginCall call) {
        if (tts != null && tts.isSpeaking()) {
            tts.stop();
        }
        call.resolve();
    }

    @PluginMethod
    public void isSpeaking(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("speaking", tts != null && tts.isSpeaking());
        call.resolve(ret);
    }

    @PluginMethod
    public void setLanguage(PluginCall call) {
        String language = call.getString("language", "en-US");

        if (!isInitialized) {
            call.reject("TextToSpeech not initialized");
            return;
        }

        Locale locale;
        switch (language) {
            case "en-US":
                locale = Locale.US;
                break;
            case "en-GB":
                locale = Locale.UK;
                break;
            default:
                locale = Locale.US;
                break;
        }

        int result = tts.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            call.reject("Language not supported");
        } else {
            call.resolve();
        }
    }

    @Override
    protected void handleOnDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.handleOnDestroy();
    }
}

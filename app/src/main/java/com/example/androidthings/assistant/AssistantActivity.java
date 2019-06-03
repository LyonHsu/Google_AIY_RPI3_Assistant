/*
 * Copyright 2017, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.androidthings.assistant;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.androidthings.assistant.EmbeddedAssistant.ConversationCallback;
import com.example.androidthings.assistant.EmbeddedAssistant.RequestCallback;
import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.voicehat.Max98357A;
import com.google.android.things.contrib.driver.voicehat.VoiceHat;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;
import com.google.assistant.embedded.v1alpha2.SpeechRecognitionResult;
import com.google.auth.oauth2.UserCredentials;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.json.JSONException;
import org.json.JSONObject;

public class AssistantActivity extends Activity implements Button.OnButtonEventListener ,VolumeDialog.VolumeAdjustListener {
    private static final String TAG = AssistantActivity.class.getSimpleName();

    // Peripheral and drivers constants.
    private static final int BUTTON_DEBOUNCE_DELAY_MS = 20;
    // Default on using the Voice Hat on Raspberry Pi 3.
    private static final boolean USE_VOICEHAT_I2S_DAC = Build.DEVICE.equals(BoardDefaults.DEVICE_RPI3);

    // Audio constants.
    private static final String PREF_CURRENT_VOLUME = "current_volume";
    private static final int SAMPLE_RATE = 16000;
    private static final int DEFAULT_VOLUME = 100;

    // Assistant SDK constants.
    private static final String DEVICE_MODEL_ID = "PLACEHOLDER";
    private static final String DEVICE_INSTANCE_ID = "PLACEHOLDER";
    private static final String LANGUAGE_CODE = "en-US";

    // Hardware peripherals.
    private Button mButton;
    private android.widget.Button mButtonWidget;
    private Gpio mLed;
    private Max98357A mDac;

    private Handler mMainHandler;

    // List & adapter to store and display the history of Assistant Requests.
    private EmbeddedAssistant mEmbeddedAssistant;
    private ArrayList<String> mAssistantRequests = new ArrayList<>();
    private ArrayAdapter<String> mAssistantRequestsAdapter;
    private CheckBox mHtmlOutputCheckbox;
    private WebView mWebView;

    private TextToSpeech textToSpeech;
    /* Keyword we are looking for to activate menu */
    private static final String KEYPHRASE = "ok google";

    //volume
    private VolumeDialog dialog;
    private AudioManager mAudioMgr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "starting assistant demo");

        setContentView(R.layout.activity_main);

        final ListView assistantRequestsListView = findViewById(R.id.assistantRequestsListView);
        mAssistantRequestsAdapter =
            new ArrayAdapter<>(this, android.R.layout.simple_list_item_1,
                mAssistantRequests);
        assistantRequestsListView.setAdapter(mAssistantRequestsAdapter);
        mHtmlOutputCheckbox = findViewById(R.id.htmlOutput);
        mHtmlOutputCheckbox.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean useHtml) {
                mWebView.setVisibility(useHtml ? View.VISIBLE : View.GONE);
                assistantRequestsListView.setVisibility(useHtml ? View.GONE : View.VISIBLE);
                mEmbeddedAssistant.setResponseFormat(useHtml
                        ? EmbeddedAssistant.HTML : EmbeddedAssistant.TEXT);
            }
        });
        mWebView = findViewById(R.id.webview);
        mWebView.getSettings().setJavaScriptEnabled(true);

        mMainHandler = new Handler(getMainLooper());
        mButtonWidget = findViewById(R.id.assistantQueryButton);
        mButtonWidget.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                mEmbeddedAssistant.startConversation();
            }
        });


        // Audio routing configuration: use default routing.
        AudioDeviceInfo audioInputDevice = null;
        AudioDeviceInfo audioOutputDevice = null;
        if (USE_VOICEHAT_I2S_DAC) {
            audioInputDevice = findAudioDevice(AudioManager.GET_DEVICES_INPUTS, AudioDeviceInfo.TYPE_BUS);
            if (audioInputDevice == null) {
                Log.e(TAG, "failed to find I2S audio input device, using default");
            }
            audioOutputDevice = findAudioDevice(AudioManager.GET_DEVICES_OUTPUTS, AudioDeviceInfo.TYPE_BUS);
            if (audioOutputDevice == null) {
                Log.e(TAG, "failed to found I2S audio output device, using default");
            }
        }

        try {
            if (USE_VOICEHAT_I2S_DAC) {
                Log.i(TAG, "initializing DAC trigger");
                mDac = VoiceHat.openDac();
                mDac.setSdMode(Max98357A.SD_MODE_SHUTDOWN);

                mButton = VoiceHat.openButton();
                mLed = VoiceHat.openLed();
            } else {
                PeripheralManager pioManager = PeripheralManager.getInstance();
                mButton = new Button(BoardDefaults.getGPIOForButton(),
                    Button.LogicState.PRESSED_WHEN_LOW);
                mLed = pioManager.openGpio(BoardDefaults.getGPIOForLED());
            }

            mButton.setDebounceDelay(BUTTON_DEBOUNCE_DELAY_MS);
            mButton.setOnButtonEventListener(this);

            mLed.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            mLed.setActiveType(Gpio.ACTIVE_HIGH);
        } catch (IOException e) {
            Log.e(TAG, "error configuring peripherals:", e);
            return;
        }

        if(mLed!=null){
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for(int i=0;i<3;i++){
                        try {
                            mLed.setValue(true);
                            Thread.sleep(500);
                            mLed.setValue(false);
                            Thread.sleep(500);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }  catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();
        }

        //set text to speech
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                Log.d(TAG, "TTS init status:" + status);
                if (status != TextToSpeech.ERROR) {
                    int result = textToSpeech.setLanguage(Locale.getDefault());//Locale.);
                    result = textToSpeech.speak("please say " + KEYPHRASE + " to open KeyWord!", TextToSpeech.QUEUE_FLUSH, null);

                    Log.d(TAG, "speak result:" + result);
                }
            }
        });

        // Set volume from preferences
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        int initVolume = preferences.getInt(PREF_CURRENT_VOLUME, DEFAULT_VOLUME);
        Log.i(TAG, "setting audio track volume to: " + initVolume);

        UserCredentials userCredentials = null;
        try {
            userCredentials =
                    EmbeddedAssistant.generateCredentials(this, R.raw.credentials);
        } catch (IOException | JSONException e) {
            Log.e(TAG, "error getting user credentials", e);
        }

        if(userCredentials==null){
            Log.e(TAG,"credentials userCredentials==null EmbeddedAssistant build fail!");
            Toast.makeText(this,"this Google Action Credentials is null",Toast.LENGTH_SHORT);
        }else {
            Log.e(TAG,"credentials ="+userCredentials);
            mEmbeddedAssistant = new EmbeddedAssistant.Builder()
                    .setCredentials(userCredentials)
                    .setDeviceInstanceId(DEVICE_INSTANCE_ID)
                    .setDeviceModelId(DEVICE_MODEL_ID)
                    .setLanguageCode(LANGUAGE_CODE)
                    .setAudioInputDevice(audioInputDevice)
                    .setAudioOutputDevice(audioOutputDevice)
                    .setAudioSampleRate(SAMPLE_RATE)
                    .setAudioVolume(initVolume)
                    .setRequestCallback(new RequestCallback() {
                        @Override
                        public void onRequestStart() {
                            Log.i(TAG, "starting assistant request, enable microphones");
                            mButtonWidget.setText(R.string.button_listening);
                            mButtonWidget.setEnabled(false);
                        }

                        @Override
                        public void onSpeechRecognition(List<SpeechRecognitionResult> results) {
                            for (final SpeechRecognitionResult result : results) {
                                Log.i(TAG, "assistant request text: " + result.getTranscript() +
                                        " stability: " + Float.toString(result.getStability()));
                                mAssistantRequestsAdapter.add(result.getTranscript());
                            }
                        }
                    })
                    .setConversationCallback(new ConversationCallback() {
                        @Override
                        public void onResponseStarted() {
                            super.onResponseStarted();
                            // When bus type is switched, the AudioManager needs to reset the stream volume
                            if (mDac != null) {
                                try {
                                    mDac.setSdMode(Max98357A.SD_MODE_LEFT);
                                } catch (IOException e) {
                                    Log.e(TAG, "error enabling DAC", e);
                                }
                            }
                        }

                        @Override
                        public void onResponseFinished() {
                            super.onResponseFinished();
                            if (mDac != null) {
                                try {
                                    mDac.setSdMode(Max98357A.SD_MODE_SHUTDOWN);
                                } catch (IOException e) {
                                    Log.e(TAG, "error disabling DAC", e);
                                }
                            }
                            if (mLed != null) {
                                try {
                                    mLed.setValue(false);
                                } catch (IOException e) {
                                    Log.e(TAG, "cannot turn off LED", e);
                                }
                            }
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            Log.e(TAG, "assist error: " + throwable.getMessage(), throwable);
                        }

                        @Override
                        public void onVolumeChanged(int percentage) {
                            Log.i(TAG, "assistant volume changed: " + percentage);

                            int result = textToSpeech.speak("assistant volume changed:  " + percentage, TextToSpeech.QUEUE_FLUSH, null);
                            Log.d(TAG, "speak result:" + result);
                            // Update our shared preferences
                            Editor editor = PreferenceManager
                                    .getDefaultSharedPreferences(AssistantActivity.this)
                                    .edit();
                            editor.putInt(PREF_CURRENT_VOLUME, percentage);
                            editor.apply();
                        }

                        @Override
                        public void onConversationFinished() {
                            Log.i(TAG, "assistant conversation finished");
                            mButtonWidget.setText(R.string.button_new_request);
                            mButtonWidget.setEnabled(true);
                        }

                        @Override
                        public void onAssistantResponse(final String response) {
                            if (!response.isEmpty()) {
                                mMainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        mAssistantRequestsAdapter.add("Google Assistant: " + response);
                                    }
                                });
                            }
                        }

                        @Override
                        public void onAssistantDisplayOut(final String html) {
                            mMainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    // Need to convert to base64
                                    try {
                                        final byte[] data = html.getBytes("UTF-8");
                                        final String base64String =
                                                Base64.encodeToString(data, Base64.DEFAULT);
                                        mWebView.loadData(base64String, "text/html; charset=utf-8",
                                                "base64");
                                    } catch (UnsupportedEncodingException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        }

                        public void onDeviceAction(String intentName, JSONObject parameters) {
                            if (parameters != null) {
                                Log.d(TAG, "Get device action " + intentName + " with parameters: " +
                                        parameters.toString());
                            } else {
                                Log.d(TAG, "Get device action " + intentName + " with no paramete"
                                        + "rs");
                            }
                            if (intentName.equals("action.devices.commands.OnOff")) {
                                try {
                                    boolean turnOn = parameters.getBoolean("on");
                                    mLed.setValue(turnOn);
                                } catch (JSONException e) {
                                    Log.e(TAG, "Cannot get value of command", e);
                                } catch (IOException e) {
                                    Log.e(TAG, "Cannot set value of LED", e);
                                }
                            }
                        }
                    })
                    .build();
            mEmbeddedAssistant.connect();


        }
    }

    private AudioDeviceInfo findAudioDevice(int deviceFlag, int deviceType) {
        AudioManager manager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo[] adis = manager.getDevices(deviceFlag);
        for (AudioDeviceInfo adi : adis) {
            if (adi.getType() == deviceType) {
                return adi;
            }
        }
        return null;
    }

    @Override
    public void onButtonEvent(Button button, boolean pressed) {
        try {
            if (mLed != null) {
                String onOff="off";
                if(pressed)
                    onOff="on";
                Log.v(TAG,"Led is "+onOff);
                mLed.setValue(pressed);
            }
        } catch (IOException e) {
            Log.d(TAG, "error toggling LED:", e);
        }
        if (pressed) {
            int result = textToSpeech.speak("Ouch!", TextToSpeech.QUEUE_FLUSH, null);
            Log.d(TAG, "speak result:" + result);
            mEmbeddedAssistant.startConversation();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "destroying assistant demo");
        if (mLed != null) {
            try {
                mLed.close();
            } catch (IOException e) {
                Log.w(TAG, "error closing LED", e);
            }
            mLed = null;
        }
        if (mButton != null) {
            try {
                mButton.close();
            } catch (IOException e) {
                Log.w(TAG, "error closing button", e);
            }
            mButton = null;
        }
        if (mDac != null) {
            try {
                mDac.close();
            } catch (IOException e) {
                Log.w(TAG, "error closing voice hat trigger", e);
            }
            mDac = null;
        }
        mEmbeddedAssistant.destroy();
    }

    @Override
    public void onVolumeAdjust(int volume) {
        if(volume>100)
            volume=100;
        if(volume<0)
            volume=0;
        Log.d(TAG,"调节后的音乐音量大小为：" + volume);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && event.getAction() == KeyEvent.ACTION_DOWN) {
            showVolumeDialog(AudioManager.ADJUST_RAISE);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && event.getAction() == KeyEvent.ACTION_DOWN) {
            showVolumeDialog(AudioManager.ADJUST_LOWER);
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return false;
        } else {
            return false;
        }
    }

    private void showVolumeDialog(int direction) {
        if (dialog == null || dialog.isShowing() != true) {
            dialog = new VolumeDialog(this);
            dialog.setVolumeAdjustListener(this);
            dialog.show();
        }
        dialog.adjustVolume(direction, true);
        if(mAudioMgr==null)
            mAudioMgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        onVolumeAdjust(mAudioMgr.getStreamVolume(AudioManager.STREAM_MUSIC));
    }
}

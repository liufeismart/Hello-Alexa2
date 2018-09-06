package com.liufeismart.test;

import android.app.Activity;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import com.willblaschko.android.alexa.AlexaManager;
import com.willblaschko.android.alexa.audioplayer.AlexaAudioPlayer;
import com.willblaschko.android.alexa.callbacks.AsyncCallback;
import com.willblaschko.android.alexa.interfaces.AvsItem;
import com.willblaschko.android.alexa.interfaces.AvsResponse;
import com.willblaschko.android.alexa.interfaces.audioplayer.AvsPlayAudioItem;
import com.willblaschko.android.alexa.interfaces.audioplayer.AvsPlayContentItem;
import com.willblaschko.android.alexa.interfaces.audioplayer.AvsPlayRemoteItem;
import com.willblaschko.android.alexa.interfaces.errors.AvsResponseException;
import com.willblaschko.android.alexa.interfaces.playbackcontrol.AvsMediaNextCommandItem;
import com.willblaschko.android.alexa.interfaces.playbackcontrol.AvsMediaPauseCommandItem;
import com.willblaschko.android.alexa.interfaces.playbackcontrol.AvsMediaPlayCommandItem;
import com.willblaschko.android.alexa.interfaces.playbackcontrol.AvsMediaPreviousCommandItem;
import com.willblaschko.android.alexa.interfaces.playbackcontrol.AvsReplaceAllItem;
import com.willblaschko.android.alexa.interfaces.playbackcontrol.AvsReplaceEnqueuedItem;
import com.willblaschko.android.alexa.interfaces.playbackcontrol.AvsStopItem;
import com.willblaschko.android.alexa.interfaces.speaker.AvsAdjustVolumeItem;
import com.willblaschko.android.alexa.interfaces.speaker.AvsSetMuteItem;
import com.willblaschko.android.alexa.interfaces.speaker.AvsSetVolumeItem;
import com.willblaschko.android.alexa.interfaces.speechrecognizer.AvsExpectSpeechItem;
import com.willblaschko.android.alexa.interfaces.speechsynthesizer.AvsSpeakItem;
import com.willblaschko.android.alexa.requestbody.DataRequestBody;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ee.ioc.phon.android.speechutils.AudioRecorder;
import ee.ioc.phon.android.speechutils.RawAudioRecorder;
import okio.BufferedSink;

/**
 * Created by humax on 18/9/6
 */
public class AlexaUtil {

    private final String TAG = "AlexaUtil";

    private final int STATE_LISTENING = 1;
    private final int STATE_PROCESSING = 2;
    private final int STATE_SPEAKING = 3;
    private final int STATE_PROMPTING = 4;
    private final int STATE_FINISHED = 0;

    private final String PRODUCT_ID = "Sample3";


    private static final int AUDIO_RATE = 16000;
    private RawAudioRecorder recorder;
    protected AlexaManager alexaManager;
    //about Alexa Answer
    private AlexaAudioPlayer audioPlayer;
    private List<AvsItem> avsQueue = new ArrayList<>();
    private Activity activity;
    public AlexaUtil(Activity context) {
        this.activity = context;
        alexaManager = AlexaManager.getInstance(activity, PRODUCT_ID);

        //instantiate our audio player
        audioPlayer = AlexaAudioPlayer.getInstance(activity);

        //Remove the current item and check for more items once we've finished playing
        audioPlayer.addCallback(alexaAudioPlayerCallback);
    }

    public void startRecog() {
        if(recorder == null){
            recorder = new RawAudioRecorder(AUDIO_RATE);
        }
        recorder.start();
        alexaManager.sendAudioRequest(requestBody, requestCallback);
    }


    public void stopRecog(){
        if(recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
        }
    }

    public void interruptSpeak() {
        if(audioPlayer != null){
            audioPlayer.stop();
            setState(STATE_FINISHED);
        }
    }

    public void stop() {
        interruptSpeak();
        stopRecog();
    }

    public void destroy() {
        if(recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
        }
        if(audioPlayer != null){
            audioPlayer.stop();
            audioPlayer.removeCallback(alexaAudioPlayerCallback);
            audioPlayer.release();
            audioPlayer = null;
        }
    }





    private AlexaAudioPlayer.Callback alexaAudioPlayerCallback = new AlexaAudioPlayer.Callback() {

        private boolean almostDoneFired = false;
        private boolean playbackStartedFired = false;

        @Override
        public void playerPrepared(AvsItem pendingItem) {

        }

        @Override
        public void playerProgress(AvsItem item, long offsetInMilliseconds, float percent) {
//            if(BuildConfig.DEBUG) {
//                //Log.i(TAG, "Player percent: " + percent);
//            }
            if(item instanceof AvsPlayContentItem || item == null){
                return;
            }
            if(!playbackStartedFired){
//                if(BuildConfig.DEBUG) {
//                    Log.i(TAG, "PlaybackStarted " + item.getToken() + " fired: " + percent);
//                }
                playbackStartedFired = true;
                sendPlaybackStartedEvent(item, offsetInMilliseconds);
            }
            if(!almostDoneFired && percent > .8f){
//                if(BuildConfig.DEBUG) {
//                    Log.i(TAG, "AlmostDone " + item.getToken() + " fired: " + percent);
//                }
                almostDoneFired = true;
                if(item instanceof AvsPlayAudioItem) {
                    sendPlaybackNearlyFinishedEvent((AvsPlayAudioItem) item, offsetInMilliseconds);
                }
            }
        }

        @Override
        public void itemComplete(AvsItem completedItem) {
            almostDoneFired = false;
            playbackStartedFired = false;
            avsQueue.remove(completedItem);
            checkQueue();
            if(completedItem instanceof AvsPlayContentItem || completedItem == null){
                //Alexa answer has finished, need call startRecog()
                setState(STATE_FINISHED);
                return;
            }
//            if(BuildConfig.DEBUG) {
//                Log.i(TAG, "Complete " + completedItem.getToken() + " fired");
//            }
            sendPlaybackFinishedEvent(completedItem);
        }

        @Override
        public boolean playerError(AvsItem item, int what, int extra) {
            return false;
        }

        @Override
        public void dataError(AvsItem item, Exception e) {
            e.printStackTrace();
        }


    };

    /**
     * Send an event back to Alexa that we're nearly done with our current playback event, this should supply us with the next item
     * https://developer.amazon.com/public/solutions/alexa/alexa-voice-service/reference/audioplayer#PlaybackNearlyFinished Event
     */
    private void sendPlaybackNearlyFinishedEvent(AvsPlayAudioItem item, long offsetInMilliseconds){
        if (item != null) {
            alexaManager.sendPlaybackNearlyFinishedEvent(item, offsetInMilliseconds, requestCallback);
//            Log.i(TAG, "Sending PlaybackNearlyFinishedEvent");
        }
    }

    /**
     * Send an event back to Alexa that we're starting a speech event
     * https://developer.amazon.com/public/solutions/alexa/alexa-voice-service/reference/audioplayer#PlaybackNearlyFinished Event
     */
    private void sendPlaybackStartedEvent(AvsItem item, long milliseconds){
        alexaManager.sendPlaybackStartedEvent(item, milliseconds, null);
//        Log.i(TAG, "Sending SpeechStartedEvent");
    }

    /**
     * Send an event back to Alexa that we're done with our current speech event, this should supply us with the next item
     * https://developer.amazon.com/public/solutions/alexa/alexa-voice-service/reference/audioplayer#PlaybackNearlyFinished Event
     */
    private void sendPlaybackFinishedEvent(AvsItem item){
        if (item != null) {
            alexaManager.sendPlaybackFinishedEvent(item, null);
//            Log.i(TAG, "Sending PlaybackFinishedEvent");
        }
    }


    private void checkQueue() {

        //if we're out of things, hang up the phone and move on
        if (avsQueue.size() == 0) {
            setState(STATE_FINISHED);
//            new android.os.Handler(Looper.getMainLooper()).post(new Runnable() {
//                @Override
//                public void run() {
//                    long totalTime = System.currentTimeMillis() - startTime;
//                    Toast.makeText(BaseActivity.this, "Total interaction time: "+totalTime+" miliseconds", Toast.LENGTH_LONG).show();
//                    Log.i(TAG, "Total interaction time: "+totalTime+" miliseconds");
//                }
//            });
            return;
        }


        final AvsItem current = avsQueue.get(0);

        Log.i(TAG, "Response Item type " + current.getClass().getName());

        if (current instanceof AvsPlayRemoteItem) {
            //play a URL
            if (!audioPlayer.isPlaying()) {
                Log.i(TAG, "Response AvsPlayRemoteItem");
                audioPlayer.playItem((AvsPlayRemoteItem) current);
            }
        } else if (current instanceof AvsPlayContentItem) {
            //play a URL
            if (!audioPlayer.isPlaying()) {
                Log.i(TAG, "Response AvsPlayContentItem");
                audioPlayer.playItem((AvsPlayContentItem) current);
            }
        } else if (current instanceof AvsSpeakItem) {
            //play a sound file
            if (!audioPlayer.isPlaying()) {
                Log.i(TAG, "Response AvsSpeakItem");
                audioPlayer.playItem((AvsSpeakItem) current);
            }
//            setState(STATE_SPEAKING);
        } else if (current instanceof AvsStopItem) {
            //stop our play
            Log.i(TAG, "Response AvsStopItem");
            audioPlayer.stop();
            avsQueue.remove(current);
        } else if (current instanceof AvsReplaceAllItem) {
            Log.i(TAG, "Response AvsReplaceAllItem");
            //clear all items
            //mAvsItemQueue.clear();
            audioPlayer.stop();
            avsQueue.remove(current);
        } else if (current instanceof AvsReplaceEnqueuedItem) {
            Log.i(TAG, "Response AvsReplaceEnqueuedItem");
            //clear all items
            //mAvsItemQueue.clear();
            avsQueue.remove(current);
        } else if (current instanceof AvsExpectSpeechItem) {
            Log.i(TAG, "Response AvsExpectSpeechItem");
            //listen for user input
            audioPlayer.stop();
            avsQueue.clear();
            setState(STATE_FINISHED);
        } else if (current instanceof AvsSetVolumeItem) {
            Log.i(TAG, "Response AvsSetVolumeItem");
            //set our volume
//            setVolume(((AvsSetVolumeItem) current).getVolume());
            avsQueue.remove(current);
        } else if(current instanceof AvsAdjustVolumeItem){
            Log.i(TAG, "Response AvsAdjustVolumeItem");
            //adjust the volume
//            adjustVolume(((AvsAdjustVolumeItem) current).getAdjustment());
            avsQueue.remove(current);
        } else if(current instanceof AvsSetMuteItem){
            Log.i(TAG, "Response AvsSetMuteItem");
            //mute/unmute the device
//            setMute(((AvsSetMuteItem) current).isMute());
            avsQueue.remove(current);
        }else if(current instanceof AvsMediaPlayCommandItem){
            Log.i(TAG, "Response AvsMediaPlayCommandItem");
            //fake a hardware "play" press
//            sendMediaButton(this, KeyEvent.KEYCODE_MEDIA_PLAY);
//            Log.i(TAG, "Media play command issued");
            avsQueue.remove(current);
        }else if(current instanceof AvsMediaPauseCommandItem){
            Log.i(TAG, "Response AvsMediaPauseCommandItem");
            //fake a hardware "pause" press
//            sendMediaButton(this, KeyEvent.KEYCODE_MEDIA_PAUSE);
//            Log.i(TAG, "Media pause command issued");
            avsQueue.remove(current);
        }else if(current instanceof AvsMediaNextCommandItem){
            Log.i(TAG, "Response AvsMediaNextCommandItem");
            //fake a hardware "next" press
//            sendMediaButton(this, KeyEvent.KEYCODE_MEDIA_NEXT);
//            Log.i(TAG, "Media next command issued");
            avsQueue.remove(current);
        }else if(current instanceof AvsMediaPreviousCommandItem){
            Log.i(TAG, "Response AvsMediaPreviousCommandItem");
            //fake a hardware "previous" press
//            sendMediaButton(this, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
//            Log.i(TAG, "Media previous command issued");
            avsQueue.remove(current);
        }else if(current instanceof AvsResponseException){
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    new AlertDialog.Builder(activity)
                            .setTitle("Error")
                            .setMessage(((AvsResponseException) current).getDirective().getPayload().getCode() + ": " + ((AvsResponseException) current).getDirective().getPayload().getDescription())
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
            });

            avsQueue.remove(current);
            checkQueue();
        }
    }


    private void setState(final int state){
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch (state){
                    case(STATE_LISTENING):
//                        stateListening();
                        break;
                    case(STATE_PROCESSING):
//                        stateProcessing();
                        break;
                    case(STATE_SPEAKING):
//                        stateSpeaking();
                        break;
                    case(STATE_FINISHED):
//                        stateFinished();
                        startRecog();
                        break;
                    case(STATE_PROMPTING):
//                        statePrompting();
                        break;
                    default:
//                        stateNone();
                        break;
                }
            }
        });
    }

    private AsyncCallback<AvsResponse, Exception> requestCallback = new AsyncCallback<AvsResponse, Exception>() {
        @Override
        public void start() {
//            startTime = System.currentTimeMillis();
            Log.i(TAG, "Event Start");
            setState(STATE_PROCESSING);
        }

        @Override
        public void success(AvsResponse result) {
            Log.i(TAG, "Event Success");
            handleResponse(result);
        }

        @Override
        public void failure(Exception error) {
            error.printStackTrace();
            Log.i(TAG, "Event Error");
//            setState(STATE_FINISHED);
        }

        @Override
        public void complete() {
            Log.i(TAG, "Event Complete");
//            new android.os.Handler(Looper.getMainLooper()).post(new Runnable() {
//                @Override
//                public void run() {
//                    long totalTime = System.currentTimeMillis() - startTime;
//                    Toast.makeText(BaseActivity.this, "Total request time: "+totalTime+" miliseconds", Toast.LENGTH_LONG).show();
//                    //Log.i(TAG, "Total request time: "+totalTime+" miliseconds");
//                }
//            });
        }
    };


    private void handleResponse(AvsResponse response){
        Log.i(TAG, "Resposne: handleResponse");
        boolean checkAfter = (avsQueue.size() == 0);
        if(response != null){
            //if we have a clear queue item in the list, we need to clear the current queue before proceeding
            //iterate backwards to avoid changing our array positions and getting all the nasty errors that come
            //from doing that

            Log.i(TAG, "Resposne: response.size() = " + response.size());
            for(int i = response.size() - 1; i >= 0; i--){
                if(response.get(i) instanceof AvsReplaceAllItem || response.get(i) instanceof AvsReplaceEnqueuedItem){
                    //clear our queue
                    avsQueue.clear();
                    //remove item
                    response.remove(i);
                }
            }
            Log.i(TAG, "Adding "+response.size()+" items to our queue");
            for (int i = 0; i < response.size(); i++){
                Log.i(TAG, "\tAdding: "+response.get(i).getToken());
            }
            avsQueue.addAll(response);
        }
        Log.i(TAG, "Resposne: checkAfter = " + checkAfter);
        if(checkAfter) {
            checkQueue();
        }
    }

    private DataRequestBody requestBody = new DataRequestBody() {
        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            Log.i(TAG, "writeTo ");
            Log.i(TAG, "recorder != null =  "+(recorder != null));
            if(recorder!=null) {
                Log.i(TAG, "recorder.getState() = " + recorder.getState());
                Log.i(TAG, "!recorder.isPausing() = " + !recorder.isPausing());
            }
            while (recorder != null && recorder.getState() != AudioRecorder.State.ERROR && !recorder.isPausing()) {
                if(recorder != null) {
                    final float rmsdb = recorder.getRmsdb();
                    if(sink != null && recorder != null) {
                        sink.write(recorder.consumeRecording());
                    }
                    Log.i(TAG, "Received audio");
                    Log.i(TAG, "RMSDB: " + rmsdb);
                }

                try {
                    Thread.sleep(25);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            stopRecog();
        }

    };

}

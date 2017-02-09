package com.cl.slack.playaudio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created by slack
 * on 17/2/9 上午11:01.
 * 播放背景音乐 mp3/mp4
 *
 */

class PlayBackMusic {

    private static final String TAG = "PlayBackMusic";
    private PCMData mPCMData;
    private Queue<byte[]> backGroundBytes = new LinkedBlockingDeque<>();//new ArrayDeque<>();// ArrayDeque不是线程安全的

    private static final int mFrequence = 44100;
    private static final int mPlayChannelConfig = AudioFormat.CHANNEL_OUT_STEREO;
    private static final int mAudioEncoding = AudioFormat.ENCODING_PCM_16BIT;//一个采样点16比特-2个字节

    private boolean mIsPlaying = false;
    private boolean mIsRecording = false;

    PlayBackMusic(String path) {
        mPCMData = new PCMData(path);
    }

    byte[] getBackGroundBytes() {
        byte[] temp = null;
        if (backGroundBytes.isEmpty()) {
            return temp;
        }
        // poll 如果队列为空，则返回null
        temp = backGroundBytes.poll();
//        Log.i(TAG,"getBackGroundBytes... "+ (temp == null ? "is" : "not") + " null");
        return temp;
    }

    PlayBackMusic startPlayBackMusic() {
        initPCMData();
        mIsPlaying = true;
        new PlayNeedMixAudioTask(new BackGroundFrameListener() {

            @Override
            public void onFrameArrive(byte[] bytes) {
                addBackGroundBytes(bytes);
            }
        }).start();
        return this;
    }

    boolean hasFrameBytes() {
        return !backGroundBytes.isEmpty();
    }

    public boolean isPlayingMusic() {
        return mIsPlaying;
    }

    boolean isPCMDataEos(){
        return mPCMData.isPCMExtractorEOS();
    }

    int getBufferSize() {
        return mPCMData.getBufferSize();
    }

    PlayBackMusic setNeedRecodeDataEnable(boolean enable) {
        mIsRecording = enable;
        return this;
    }

    PlayBackMusic release() {
        mIsPlaying = false;
        mIsRecording = false;
        mPCMData.release();
        backGroundBytes.clear();
        return this;
    }


    /**
     * 这样的方式控制同步 需要添加到队列时判断同时在播放和录制
     */
    private void addBackGroundBytes(byte[] bytes) {
        if (mIsPlaying && mIsRecording) {
            backGroundBytes.add(bytes); // what if out of memory?
        }
    }

    /**
     * 解析 mp3 --> pcm
     */
    private void initPCMData() {
        mPCMData.startPcmExtractor();
    }

    /**
     * 虽然可以新建多个 AsyncTask的子类的实例，但是AsyncTask的内部Handler和ThreadPoolExecutor都是static的，
     * 这么定义的变 量属于类的，是进程范围内共享的，所以AsyncTask控制着进程范围内所有的子类实例，
     * 而且该类的所有实例都共用一个线程池和Handler
     * 这里新开一个线程
     * 自己解析出来 pcm data
     */
    private class PlayNeedMixAudioTask extends Thread {

        private BackGroundFrameListener listener;

        PlayNeedMixAudioTask(BackGroundFrameListener l) {
            listener = l;
        }

        @Override
        public void run() {
            Log.i("thread", "PlayNeedMixAudioTask: " + Thread.currentThread().getId());
            try {
                int bufferSize = AudioTrack.getMinBufferSize(mFrequence,
                        mPlayChannelConfig, mAudioEncoding);
                // 实例AudioTrack
                AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC,
                        mFrequence,
                        mPlayChannelConfig, mAudioEncoding, bufferSize,
                        AudioTrack.MODE_STREAM);
                // 开始播放
                track.play();

                while (mIsPlaying) {
//                    Log.i("slack", "PlayNeedMixAudioTask..." + mIsPlaying);
                    byte[] temp = mPCMData.getPCMData();
                    if (temp == null) {
                        continue;
                    }
                    track.write(temp, 0, temp.length);
                    if (listener != null) {
                        listener.onFrameArrive(temp);
                    }
                }
//                Log.i("slack", "finish PlayNeedMixAudioTask..." + mIsPlaying);
                track.stop();
                track.release();
            } catch (Exception e) {
                // TODO: handle exception
                Log.e("slack", "error:" + e.getMessage());
            } finally {
                mIsPlaying = false;
            }
        }
    }

    interface BackGroundFrameListener {
        void onFrameArrive(byte[] bytes);
    }
}

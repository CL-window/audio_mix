package com.cl.slack.playaudio.media;

import android.util.Log;

import com.cl.slack.playaudio.audio.AudioDecoder;
import com.cl.slack.playaudio.util.BytesTransUtil;

import java.io.IOException;

/**
 * Created by slack
 * on 17/2/10 下午6:16.
 * 视频文件 只合成音频部分 fail
 */

public class MediaMixAudio {

    private static final String TAG = "MediaMixAudio";
    private MediaDecoder mediaDecoder;
    private MediaMuxerMixAudioAndVideo mediaEncoder;
    private AudioDecoder mAudioDecoder;
    private MediaInfo mediaInfo;

    private boolean mixEOS = false;

    /**
     * @param mp4 需要处理的视频文件
     * @param mp3 需要合并的音频文件
     * @param des 保存路径
     */
    public MediaMixAudio(String mp4,String mp3,String des) {
        mediaDecoder = new MediaDecoder();
        mediaEncoder = new MediaMuxerMixAudioAndVideo(des);
        mAudioDecoder = new AudioDecoder(mp3);
        mediaInfo = mediaDecoder.prepare(mp4);
        try {
            mediaEncoder.prepare(mediaInfo);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG,"mediaEncoder.prepare error: " + e.toString());
        }

    }

    public MediaMixAudio start(){
        mixEOS = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                // 代价有点高 会出现 OOM
                mAudioDecoder.startPcmExtractor();
                mediaDecoder.startExtractor();
                mixAudioData();
            }
        }).start();
        return this;
    }

    public MediaMixAudio stop(){
        mixEOS = true;
        return this;
    }

    private void mixAudioData() {
        while (!mixEOS){
            MediaFrame frame = mediaDecoder.getMediaFrameData();
            if(frame == null && mediaDecoder.isExtractorEOS()){
                break;
            }
            if(frame == null || !frame.hasData()){
                Log.i("slack","continue ...");
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }
            mediaEncoder.offerMediaEncoder(mixAudio(frame));
        }
        Log.i("slack","finish while ...");
        release();
    }

    private void release(){
        mAudioDecoder.release();
        mediaDecoder.release();
        mediaEncoder.release();
    }

    private MediaFrame mixAudio(MediaFrame frame){
        if(frame.isAudioTrack(mediaInfo.mAudioTrackIndex)) {
            byte[] back = mAudioDecoder.getPCMData();
            if (back != null) {
                frame.setByteData(BytesTransUtil.INSTANCE.averageMix(frame.getByteData(),back));
            }
            return frame;
        }else {
            return frame;
        }
    }

}

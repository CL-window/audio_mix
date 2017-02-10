package com.cl.slack.playaudio;

import android.util.Log;

/**
 * Created by slack
 * on 17/2/9 下午5:55.
 * 读入一个视频，在硬件解码后将其硬件编码回去
 * only video 
 */

public class VideoEncodeDecode {

    private static final String TAG = "VideoEncodeDecode";

    private VideoEncoder myencoder;
    private VideoDecoder mydecoder;

    private boolean mixStop = false;


    void videoCodecPrepare(String videoInputFilePath) {
        mydecoder = new VideoDecoder();
        myencoder = new VideoEncoder();
        mydecoder.videoDecodePrepare(videoInputFilePath);
        mydecoder.startFramesExtractor();
//        myencoder.VideoEncodePrepare();
        myencoder.VideoEncodePrepare(mydecoder.mediaFormat);

    }

    void videoEncodeDecodeLoop() {
        mixStop = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                writeVideoData();
            }
        }).start();
    }

    private void writeVideoData() {

        byte[] des = null;

        while (!mixStop ) {

            des = mydecoder.getFramesQueueBytes();
            if(des == null && mydecoder.isExtractorEOS()){
                // finish
                break;
            }
            if(des == null){
                Log.i("slack","continue writeBackDataOnly...");
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                continue;
            }

            // test 写入 mp4
            myencoder.offerAudioEncoder(des);

        }
        Log.i("slack","finish while...");
        myencoder.stop();
        mydecoder.stop();
    }


    public void close() {
        mixStop = true;
    }

}

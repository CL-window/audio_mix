package com.cl.slack.playaudio;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by slack
 * on 17/2/8 下午4:36.
 * 合成 视频中的音频 需要先从mp4 分离音频
 */

public class MixAudioInVideo {

    private String srcPath;// mp4 file path
    private static final File SDCARD_PATH = new File(Environment.getExternalStorageDirectory(), "slack");
    private static final String OUT_PUT_VIDEO_TRACK_NAME = "output_video.mp4";// test
    private static final String OUT_PUT_AUDIO_TRACK_NAME = "output_audio";

    private PCMData mPCMData;
    private PCMData mBackPCMData;
    private boolean mBackLoop = false;

    private MixListener mMixListener;

    public MixAudioInVideo(String filePath) {
        srcPath = filePath;
//        extractorMedia();
        extractorAudio();
    }

    public void setMixListener(MixListener mMixListener) {
        this.mMixListener = mMixListener;
    }

    public void startMixAudioInVideoWithPlay(){

    }

    /**
     *
     * @param mp3FilePath 需要写入的背景音乐
     * @param loop 背景音乐是否循环（背景音乐短而mp4音频长时需要用到）
     */
    public void startMixAudioInVideoWithoutPlay(String mp3FilePath,boolean loop){
        mBackLoop = loop;
        mBackPCMData = new PCMData(mp3FilePath);
        new Thread(new Runnable() {
            @Override
            public void run() {
                mBackPCMData.startPcmExtractor();
                mixData();
            }
        }).start();
    }

    private AudioEncoder mAudioEncoder; // test
    private void mixData() {
        if (!SDCARD_PATH.exists()) {
            SDCARD_PATH.mkdirs();
        }
        File test = new File(SDCARD_PATH,"test.mp3");
        mAudioEncoder = new AudioEncoder(test.getAbsolutePath());
        mAudioEncoder.prepareEncoder();

        byte[] src = mPCMData.getPCMData();
        byte[] back = mBackPCMData.getPCMData();
        byte[] des;
        /**
         * 原视频 还有数据没有读完,或者背景音乐还有数据没有读完
         * 或者没有数据，但是没有读取数据完成，继续循环
         */
        while (src != null || back != null ||
                !mPCMData.isPCMExtractorEOS() || !mBackPCMData.isPCMExtractorEOS()) {

            if (src == null && mPCMData.isPCMExtractorEOS()) {
                Log.i("slack","end mix write data...");
                // end of the mix
                mBackPCMData.release();
                mAudioEncoder.stop();
                if(mMixListener != null){
                    mMixListener.onFinished();
                }
                break;
            }
            // 防止两个文件都在读取的过程中
            if(src == null && back == null){
                Log.i("slack","continue mix write data...");
                continue;
            }
            // 判断是否需要合成
//            if (back == null) {
//                if (mBackPCMData.isPCMExtractorEOS() && mBackLoop) {
//                    mBackPCMData.startPcmExtractor();
//                }
//                des = src;
//            } else {
//                des = BytesTransUtil.INSTANCE.averageMix(back,src);
//            }
            des = back;
            // 写入 mp4
            mAudioEncoder.offerAudioEncoderSyn(des);
            Log.i("slack","mix write data...");
            src = mPCMData.getPCMData();
            back = mBackPCMData.getPCMData();
        }

    }

    /**
     * 只分离视频中的音频
     */
    private void extractorAudio() {
        mPCMData = new PCMData(srcPath);
        mPCMData.startPcmExtractor();
    }

    /**
     * 分离视频中的音视频,直接输出到文件
     */
    private void extractorMedia() {
        MediaExtractor mMediaExtractor = null;
        if (!SDCARD_PATH.exists()) {
            SDCARD_PATH.mkdirs();
        }
        FileOutputStream videoOutputStream = null;
        FileOutputStream audioOutputStream = null;
        try {
            //分离的视频文件
            File videoFile = new File(SDCARD_PATH, OUT_PUT_VIDEO_TRACK_NAME);
            //分离的音频文件
            File audioFile = new File(SDCARD_PATH, OUT_PUT_AUDIO_TRACK_NAME);
            videoOutputStream = new FileOutputStream(videoFile);
            audioOutputStream = new FileOutputStream(audioFile);
            //源文件
            mMediaExtractor = new MediaExtractor();
            mMediaExtractor.setDataSource(srcPath);
            //信道总数
            int trackCount = mMediaExtractor.getTrackCount();
            int audioTrackIndex = -1;
            int videoTrackIndex = -1;
            for (int i = 0; i < trackCount; i++) {
                MediaFormat trackFormat = mMediaExtractor.getTrackFormat(i);
                String mineType = trackFormat.getString(MediaFormat.KEY_MIME);
                //视频信道
                if (mineType.startsWith("video/")) {
                    videoTrackIndex = i;
                }
                //音频信道
                if (mineType.startsWith("audio/")) {
                    audioTrackIndex = i;
                }
            }

            ByteBuffer byteBuffer = ByteBuffer.allocate(500 * 1024);
            //切换到视频信道
            mMediaExtractor.selectTrack(videoTrackIndex);
            while (true) {
                int readSampleCount = mMediaExtractor.readSampleData(byteBuffer, 0);
                if (readSampleCount < 0) {
                    break;
                }
                //保存视频信道信息
                byte[] buffer = new byte[readSampleCount];
                byteBuffer.get(buffer);
                videoOutputStream.write(buffer);
                byteBuffer.clear();
                mMediaExtractor.advance();
            }
            //切换到音频信道
            mMediaExtractor.selectTrack(audioTrackIndex);
            while (true) {
                int readSampleCount = mMediaExtractor.readSampleData(byteBuffer, 0);
                if (readSampleCount < 0) {
                    break;
                }
                //保存音频信息
                byte[] buffer = new byte[readSampleCount];
                byteBuffer.get(buffer);
                audioOutputStream.write(buffer);
                byteBuffer.clear();
                mMediaExtractor.advance();
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(mMediaExtractor != null) {
                mMediaExtractor.release();
            }
            try {
                if (videoOutputStream != null) {
                    videoOutputStream.close();
                }
                if (audioOutputStream != null) {
                    audioOutputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public interface MixListener{
        void onFinished();
    }
}

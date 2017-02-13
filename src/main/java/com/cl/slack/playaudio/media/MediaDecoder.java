package com.cl.slack.playaudio.media;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.MessageQueue;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created by slack
 * on 17/2/10 下午3:30.
 * 解码 视频文件 同时 视频和音频
 */

public class MediaDecoder implements Handler.Callback {

    private static final int BUFFER_SIZE = 2048;
    private final static int WHAT_START_DECODER = 0x11;
    private final static int WHAT_RELEASE = 0x12;

    private Queue<MediaFrame> mMediaQueue = new LinkedBlockingDeque<>();
    private MediaExtractor mediaExtractor;
    private MediaCodec mediaDecode;
    private ByteBuffer[] decodeInputBuffers;
    private ByteBuffer[] decodeOutputBuffers;

    private MediaInfo mediaInfo;
    private MediaFrame mediaFrame;

    private MediaCodec.BufferInfo decodeBufferInfo;

    private Handler mHandler;
    private HandlerThread mThread;

    boolean isExtractorEOS() {
        return sawOutputEOS;
    }

    private boolean sawOutputEOS = false;

    public MediaInfo prepare(String path) {
        return initMediaDecode(path);
    }

    public MediaDecoder startExtractor() {
        try {
            mediaDecode = MediaCodec.createDecoderByType(mediaInfo.mVideoMime);
            mediaDecode.configure(mediaInfo.mVideoFormat, null, null, 0);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mediaDecode == null) {
            Log.e("slack", "create mediaDecode failed");
            return this;
        }
        mediaDecode.start();//启动MediaCodec ，等待传入数据
        decodeInputBuffers = mediaDecode.getInputBuffers();//MediaCodec在此ByteBuffer[]中获取输入数据
        decodeOutputBuffers = mediaDecode.getOutputBuffers();//MediaCodec将解码后的数据放到此ByteBuffer[]中

        mThread = new HandlerThread("MediaDecoder_" + System.currentTimeMillis());
        mThread.start();
        mHandler = new Handler(mThread.getLooper(), this);

        mHandler.sendEmptyMessage(WHAT_START_DECODER);
        return this;
    }

    public MediaDecoder release() {
        sawOutputEOS = true;
        mMediaQueue.clear();
        if (mHandler != null) {
            mHandler.sendEmptyMessage(WHAT_RELEASE);
        } else if (mThread != null) {
            this.releaseInternal();
        }
        System.gc();
        return this;
    }

    public MediaFrame getMediaFrameData() {
        return mMediaQueue.poll();
    }

    /**
     * 测试时发现 播放音频的 MediaCodec.BufferInfo.size 是变换的
     * 需要下一帧的音频长度 todo Queue －－ > list 方便寻找音频帧
     */
    public int getBufferSize() {
//        MediaFrame frame = mMediaQueue.peek();
//        if(frame.isAudioTrack(mediaInfo.mAudioTrackIndex)){
        return mediaInfo.mAudioBufferSize;
//        }else {
//            return BUFFER_SIZE;// here
//        }
    }

    private MediaInfo initMediaDecode(String path) {
        try {
            mediaInfo = new MediaInfo();
            decodeBufferInfo = new MediaCodec.BufferInfo();
            mediaExtractor = new MediaExtractor();//此类可分离视频文件的音轨和视频轨道
            mediaExtractor.setDataSource(path);//媒体文件的位置
            int count = mediaExtractor.getTrackCount();
            for (int i = 0; i < count; i++) {
                MediaFormat format = mediaExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/") && mediaInfo.mVideoTrackIndex < 0) {//获取视频轨道
                    mediaExtractor.selectTrack(i);//选择此视频轨道
                    mediaInfo.setVideoFormat(i, format);
                } else if (mime.startsWith("audio/") && mediaInfo.mAudioTrackIndex < 0) {//获取音频轨道
                    mediaExtractor.selectTrack(i);//选择此音频轨道
                    mediaInfo.setAudioFormat(i, format);
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("slack", "error :: " + e.getMessage());
            return null;
        }
        return mediaInfo;
    }

    private void addMediaFrameData(MediaFrame frame) {
        mMediaQueue.add(frame);
        if (mMediaQueue.size() > 50) { // test only 100 frames
            sawOutputEOS = true;
        }
    }

    /**
     * 解码文件
     */
    private void decodeToMediaFrame() {

        sawOutputEOS = false;
        boolean sawInputEOS = false;
        try {
            while (!sawOutputEOS) {
                int trackIndex = mediaExtractor.getSampleTrackIndex(); // -1 no more data
                if (!sawInputEOS) {
                    int inputIndex = mediaDecode.dequeueInputBuffer(-1);//获取可用的inputBuffer -1代表一直等待，0表示不等待 建议-1,避免丢帧
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = decodeInputBuffers[inputIndex];//拿到inputBuffer
                        inputBuffer.clear();//清空之前传入inputBuffer内的数据
                        int sampleSize = mediaExtractor.readSampleData(inputBuffer, 0);//MediaExtractor读取数据到inputBuffer中
                        if (sampleSize < 0) {//小于0 代表所有数据已读取完成
                            sawInputEOS = true;
                            mediaDecode.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        } else {
                            long presentationTimeUs = mediaExtractor.getSampleTime();
                            mediaDecode.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0);//通知MediaDecode解码刚刚传入的数据
                            mediaExtractor.advance();//MediaExtractor移动到下一取样处
                        }
                    }
                }

                //
                if (trackIndex >= 0) {

                    int outputIndex = mediaDecode.dequeueOutputBuffer(decodeBufferInfo, 10000);
                    if (outputIndex >= 0) {
                        // Simply ignore codec config buffers.
                        if ((decodeBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            mediaDecode.releaseOutputBuffer(outputIndex, false);
                            continue;
                        }

                        if (decodeBufferInfo.size != 0) {

                            ByteBuffer outBuf = decodeOutputBuffers[outputIndex];//拿到用于存放PCM数据的Buffer

                            outBuf.position(decodeBufferInfo.offset);
                            outBuf.limit(decodeBufferInfo.offset + decodeBufferInfo.size);
                            byte[] data = new byte[decodeBufferInfo.size];//BufferInfo内定义了此数据块的大小
                            outBuf.get(data);//将Buffer内的数据取出到字节数组中
                            addMediaFrameData(new MediaFrame(trackIndex, data, decodeBufferInfo));
                        }

                        mediaDecode.releaseOutputBuffer(outputIndex, true);//此操作一定要做，不然MediaCodec用完所有的Buffer后 将不能向外输出数据

                        if ((decodeBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEOS = true;
                            Log.i("slack", "media decode finished...");
                        }

                    } else if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        decodeOutputBuffers = mediaDecode.getOutputBuffers();
                    }
                } else {
                    sawOutputEOS = true;
                }
            }
        } finally {
            Log.i("slack", "media decode finally...");
            if (mediaDecode != null) {
                mediaDecode.release();
            }
            if (mediaExtractor != null) {
                mediaExtractor.release();
            }
        }
    }


    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case WHAT_START_DECODER:
                this.decodeToMediaFrame();
                break;

            case WHAT_RELEASE:
                this.releaseInternal();
                break;
        }
        return true;
    }

    private void releaseInternal() {
        if (mediaExtractor != null) {
            mediaExtractor.release();
            mediaExtractor = null;
        }

        if (mediaDecode != null) {
            mediaDecode.release();
            mediaDecode = null;
        }

        if (mThread != null) {
            mThread.quit();
            mThread = null;
        }

        mediaFrame = null;
        decodeInputBuffers = null;
        decodeOutputBuffers = null;
    }

}

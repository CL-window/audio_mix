package com.cl.slack.playaudio.media;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

/**
 * Created by slack
 * on 17/2/10 下午6:03.
 * 混合 视频
 * MediaMuxer的使用 如下顺序：
 * addTrack-->start-->writeSampleData-->stop-->release
 */

public abstract class MediaEncoder {

    private static final String TAG = "MediaEncoder";

    MediaCodec mMediaCodec;

    private WeakReference<MediaMuxerMixAudioAndVideo> mWeakMuxer;

    private boolean eosReceived = false;  //终止录音的标志
    private long mLastMediaPresentationTimeUs = 0;

    private int mTrackIndex = -1; // video or audio

    private MediaCodec.BufferInfo bufferInfo;

    private boolean mMuxerStarted = false;

    public MediaEncoder(MediaMuxerMixAudioAndVideo mediaMuxer) {
        mWeakMuxer = new WeakReference<>(mediaMuxer);
        bufferInfo = new MediaCodec.BufferInfo();
    }

    abstract void prepare(MediaInfo info) throws IOException;

    private void closeMediaCodec() {

        mMediaCodec.stop();
        mMediaCodec.release();
        mMediaCodec = null;
        mMuxerStarted = false;

    }

    void offerMediaEncoder(MediaFrame frame) {
        _offerMediaEncoder(frame);
    }

    protected void stop() {
        _stop();
    }

    private void _offerMediaEncoder(MediaFrame frame) {

        drainEncoder(mMediaCodec, false);
        try {
            ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
            int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
//            Log.d(TAG, "inputBufferIndex--" + inputBufferIndex);
            if (inputBufferIndex >= 0) {
                if (frame != null && frame.hasData()) {
                    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                    inputBuffer.clear();
                    inputBuffer.put(frame.getByteData());
                }

                if (eosReceived) {
                    mMediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, getPTSUs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    finishMediaCodec();
                } else if (frame != null && frame.hasData()) {
                    mMediaCodec.queueInputBuffer(inputBufferIndex, 0, frame.dataLength(), getPTSUs(), 0);
                }
            }

        } catch (Throwable t) {
            Log.e(TAG, "_offerMediaEncoder exception " + t.toString());
        }

    }

    private void drainEncoder(MediaCodec encoder, boolean endOfStream) {
        final int TIMEOUT_USEC = 100;
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        MediaMuxerMixAudioAndVideo mediaMuxer = mWeakMuxer != null ? mWeakMuxer.get() : null;
        if (mediaMuxer != null) {
            LOOP:
            while (true) {
                int encoderIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
//                  Log.d(TAG, "encoderIndex---" + encoderIndex);
                if (encoderIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    //没有可进行混合的输出流数据 但还没有结束录音 此时退出循环
//                    Log.d(TAG, "info_try_again_later");
                    if (!endOfStream)
                        break;
                    else
                        Log.d(TAG, "no output available, spinning to await EOS");
                } else if (encoderIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    //只会在第一次接收数据前 调用一次
                    if (mMuxerStarted)
                        throw new RuntimeException("format 在muxer启动后发生了改变");
                    MediaFormat format = mMediaCodec.getOutputFormat();
                    mTrackIndex = mediaMuxer.addTrack(format);
                    mMuxerStarted = true;
                    mediaMuxer.start();
                    Log.i("slack", "add TrackIndex :" + mTrackIndex);
                } else if (encoderIndex < 0) {
                    Log.w(TAG, "encoderIndex 非法" + encoderIndex);
                } else {
                    if (!mediaMuxer.isStarted()) {
                        Log.d(TAG, "not start......");
                        break;
                    }
                    //退出循环
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }

                    ByteBuffer encodeData = encoderOutputBuffers[encoderIndex];
                    if (encodeData == null) {
                        throw new RuntimeException("编码数据为空");
//                    } else if (bufferInfo.size != 0 && mLastMediaPresentationTimeUs < bufferInfo.presentationTimeUs) {
                    } else if (bufferInfo.size != 0) {
                        if (!mediaMuxer.isStarted()) {
                            throw new RuntimeException("混合器未开启");
                        }
                        Log.d(TAG, "write_info_data......" + mTrackIndex);
                        encodeData.position(bufferInfo.offset);
                        encodeData.limit(bufferInfo.offset + bufferInfo.size);
                        bufferInfo.presentationTimeUs = getPTSUs(); 
                        mediaMuxer.writeSampleData(mTrackIndex, encodeData, bufferInfo);

                        mLastMediaPresentationTimeUs = bufferInfo.presentationTimeUs;
                        encoder.releaseOutputBuffer(encoderIndex, false);
                    }

                }
            }
        }

    }

    protected long getPTSUs() {
        long result = System.nanoTime() / 1000L;
        // presentationTimeUs should be monotonic
        // otherwise muxer fail to write
        if (result < mLastMediaPresentationTimeUs)
            result = (mLastMediaPresentationTimeUs - result) + result;
        return result;
    }

    //终止编码
    private void _stop() {
        eosReceived = true;
        _offerMediaEncoder(null);
        Log.d(TAG, "停止编码");
    }

    private void finishMediaCodec() {
        drainEncoder(mMediaCodec, true);
        closeMediaCodec();
        MediaMuxerMixAudioAndVideo muxer = mWeakMuxer != null ? mWeakMuxer.get() : null;
        if (muxer != null) {
            muxer.stop();
        }
    }

}

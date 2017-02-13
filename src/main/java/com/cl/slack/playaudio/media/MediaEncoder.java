package com.cl.slack.playaudio.media;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by slack
 * on 17/2/10 下午6:03.
 * 混合 视频
 * MediaMuxer的使用 如下顺序：
 * addTrack-->start-->writeSampleData-->stop-->release
 */

public class MediaEncoder {

    private static final String TAG = "MediaEncoder";

    private MediaCodec mMediaCodec;

    private MediaMuxer mMediaMuxer;
    private boolean mMuxerStarted;

    private boolean eosReceived = false;  //终止录音的标志
    private long videoStartTime;
    private static long mediaBytesReceived = 0;        //接收到的音频数据 用来设置录音起始时间的
    private long mLastMediaPresentationTimeUs = 0;

    private MediaInfo mediaInfo;
    private int mVideoTrackIndex = -1;
    private int mAudioTrackIndex = -1;

    //这里需要传进来一个编码时的 MediaInfo
    public void prepare(MediaInfo info, String file) {

        mediaInfo = info;
        eosReceived = false;
        mediaBytesReceived = 0;

        MediaFormat mformat = info.createVideoFormatCopy();
//        MediaFormat mformat = info.createAudioFormat(); // only audio is ok
        mMediaCodec = null;
        try {
            mMediaCodec = MediaCodec.createEncoderByType(mediaInfo.mVideoMime);
//            mMediaCodec = MediaCodec.createEncoderByType(mediaInfo.mAudioMime);// only audio is ok
            mMediaCodec.configure(mformat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mMediaCodec.start();

            mMediaMuxer = new MediaMuxer(file, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        } catch (IOException ioe) {
            throw new RuntimeException("failed init encoder", ioe);
        }

        mMuxerStarted = false;
    }

    private void close() {

        mMediaCodec.stop();
        mMediaCodec.release();
        mMediaCodec = null;
        if (mMediaMuxer != null) {
            mMediaMuxer.stop();
            mMediaMuxer.release();
            mMediaMuxer = null;
        }


    }

    public void offerMediaEncoder(MediaFrame frame) {
        _offerMediaEncoder(frame);
    }

    public void stop() {
        _stop();
    }

    private boolean isAudioTrack = false; // 上一帧是否是音频帧
    private void _offerMediaEncoder(MediaFrame frame) {
        if (mediaBytesReceived == 0) {
            videoStartTime = System.nanoTime();
        }
        if (frame != null && frame.hasData()) {
            mediaBytesReceived += frame.getByteData().length;
        }
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
                    isAudioTrack = frame.isAudioTrack(mediaInfo.mAudioTrackIndex);
                }

                long presentationTimeUs = (System.nanoTime() - videoStartTime) / 1000L;
//                Log.d(TAG, "presentationTimeUs--" + presentationTimeUs);
                if (eosReceived) {
                    mMediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    finishMuxer();
                } else if (frame != null && frame.hasData()) {
                    mMediaCodec.queueInputBuffer(inputBufferIndex, 0, frame.dataLength(), presentationTimeUs, 0);
                }
            }

        } catch (Throwable t) {
            Log.e(TAG, "_offerMediaEncoder exception " + t.toString());
        }

    }

    private MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

    private void drainEncoder(MediaCodec encoder, boolean endOfStream) {
        final int TIMEOUT_USEC = 100;
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();

        try {
            while (true) {
                int encoderIndex = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
//                  Log.d(TAG, "encoderIndex---" + encoderIndex);
                if (encoderIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    //没有可进行混合的输出流数据 但还没有结束录音 此时退出循环
                    Log.d(TAG, "info_try_again_later");
                    if (!endOfStream)
                        break;
                    else
                        Log.d(TAG, "no output available, spinning to await EOS");
                } else if (encoderIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    //只会在第一次接收数据前 调用一次
                    if (mMuxerStarted)
                        throw new RuntimeException("format 在muxer启动后发生了改变");

                    if(mediaInfo.mVideoFormat != null) {
                        mVideoTrackIndex = mMediaMuxer.addTrack(mediaInfo.mVideoFormat);
                    }
                    if(mediaInfo.mAudioFormat != null) {
                        mAudioTrackIndex = mMediaMuxer.addTrack(mediaInfo.mAudioFormat);
                    }
                    Log.d(TAG, "info_output_format_change..." + mAudioTrackIndex + mVideoTrackIndex);
                    if (!mMuxerStarted) {
                        mMediaMuxer.start();
                    }
                    mMuxerStarted = true;
                } else if (encoderIndex < 0) {
                    Log.w(TAG, "encoderIndex 非法" + encoderIndex);
                } else {
                    //退出循环
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }

                    ByteBuffer encodeData = encoderOutputBuffers[encoderIndex];
                    if (encodeData == null) {
                        throw new RuntimeException("编码数据为空");
//                    } else if (bufferInfo.size != 0 && mLastMediaPresentationTimeUs < bufferInfo.presentationTimeUs) {
                    } else if (info.size != 0) {
                        if (!mMuxerStarted) {
                            throw new RuntimeException("混合器未开启");
                        }
                        Log.d(TAG, "write_info_data......");
                        encodeData.position(info.offset);
                        encodeData.limit(info.offset + info.size);
//                          Log.d(TAG, "presentationTimeUs--bufferInfo : " + bufferInfo.presentationTimeUs);
                        mMediaMuxer.writeSampleData(isAudioTrack?mAudioTrackIndex:mVideoTrackIndex, encodeData, info);

                        mLastMediaPresentationTimeUs = info.presentationTimeUs;
                        encoder.releaseOutputBuffer(encoderIndex, false);
                    }

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "error :: " + e.toString());
        }

    }

    //终止编码
    private void _stop() {
        eosReceived = true;
        _offerMediaEncoder(null);
        Log.d(TAG, "停止编码");
    }

    private void finishMuxer() {
        drainEncoder(mMediaCodec, true);
        close();
    }

}

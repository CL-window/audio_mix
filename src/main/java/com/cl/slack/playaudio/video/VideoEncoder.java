package com.cl.slack.playaudio.video;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;

import junit.framework.Assert;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Created by slack
 * on 17/2/9 下午5:54.
 * 视频 编码  大致过程与 Audio 一样
 * TODO ： 颜色 编码还有些问题
 *          视频没有压缩，写入的视频太大
 *          应该是传入的 data[] 数据的问题
 */

public class VideoEncoder {
    private static final int TEST_Y = 120;                  // YUV values for colored rect
    private static final int TEST_U = 160;
    private static final int TEST_V = 200;

    public int mWidth = 1920;
    public int mHeight = 1080;

    private static final int BIT_RATE = 1920 * 1080 * 30;            // 2Mbps
    private static final int FRAME_RATE = 30;               // 30fps
    private static final int IFRAME_INTERVAL = 10;          // 10 seconds between I-frames
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    public static final int NUMFRAMES = 300;

    private static final String TAG = "VideoEncoder";
    private static final boolean VERBOSE = true;           // lots of logging
    private static final String MIME_TYPE = "video/avc";    // H.264 Advanced Video Coding




    public MediaCodec mVideoCodec;

    public MediaMuxer mMuxer;
    public int mTrackIndex;
    public boolean mMuxerStarted;

    private static final int encoderColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible; // MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;


    private boolean eosReceived = false;  //终止录音的标志
    private long videoStartTime;
    private static long videoBytesReceived = 0;        //接收到的音频数据 用来设置录音起始时间的
    private MediaCodec.BufferInfo mVideoBufferInfo;
    private TrackIndex mVideoTrackIndex = new TrackIndex();
    private long mLastVideoPresentationTimeUs = 0;

    private class TrackIndex {
        int index = 0;
    }

    //这里需要传进来一个编码时的mediaformat
    public void VideoEncodePrepare(MediaFormat format) {
       if(format == null){
           VideoEncodePrepare();
           return;
       }
        eosReceived = false;
        videoBytesReceived = 0;
        String outputPath = new File(Environment.getExternalStorageDirectory(),
                "test." + WIDTH + "x" + HEIGHT + ".mp4").toString();

        mVideoBufferInfo = new MediaCodec.BufferInfo();
        if(format.containsKey(MediaFormat.KEY_WIDTH) && format.containsKey(MediaFormat.KEY_HEIGHT)) {
            mWidth = format.getInteger(MediaFormat.KEY_WIDTH);
            mHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
        }
        Log.i("slack","video " + mWidth + " * " + mHeight);
        MediaFormat mformat = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight); //
        copyFormatInteger(format,mformat,MediaFormat.KEY_COLOR_FORMAT,encoderColorFormat);
        copyFormatInteger(format,mformat,MediaFormat.KEY_FRAME_RATE,FRAME_RATE);
        copyFormatInteger(format,mformat,MediaFormat.KEY_BIT_RATE,BIT_RATE);
        copyFormatInteger(format,mformat,MediaFormat.KEY_I_FRAME_INTERVAL,IFRAME_INTERVAL);

        mVideoCodec = null;

        try {
            mVideoCodec = MediaCodec.createEncoderByType(MIME_TYPE);
            mVideoCodec.configure(mformat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mVideoCodec.start();

            mMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        } catch (IOException ioe) {
            throw new RuntimeException("failed init encoder", ioe);
        }

        mTrackIndex = -1;
        mMuxerStarted = false;
    }


    public void VideoEncodePrepare() {
        eosReceived = false;
        videoBytesReceived = 0;
        String outputPath = new File(Environment.getExternalStorageDirectory(),
                "test." + WIDTH + "x" + HEIGHT + ".mp4").toString();


        mVideoBufferInfo = new MediaCodec.BufferInfo();

        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, encoderColorFormat);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);


        mVideoCodec = null;

        try {
            mVideoCodec = MediaCodec.createEncoderByType(MIME_TYPE);
            mVideoCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            mVideoCodec.start();

            mMuxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        } catch (IOException ioe) {
            throw new RuntimeException("failed init encoder", ioe);
        }

        mTrackIndex = -1;
        mMuxerStarted = false;

    }

    public void close() {

        mVideoCodec.stop();
        mVideoCodec.release();

        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }


    }

    public void offerEncoder(byte[] input, int frameIdx) {
        try {
            ByteBuffer[] inputBuffers = mVideoCodec.getInputBuffers();
            //outputBuffers = mediaCodec.getOutputBuffers();

            int inputBufferIndex = mVideoCodec.dequeueInputBuffer(-1);


            if (inputBufferIndex >= 0) {

                if (frameIdx == NUMFRAMES) {
                    mVideoCodec.queueInputBuffer(inputBufferIndex, 0, 0, System.nanoTime() / 1000,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    if (VERBOSE) Log.d(TAG, "sent input EOS (with zero-length frame)");
                } else {
                    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                    Assert.assertTrue(inputBuffer.capacity() >= input.length); //断言，帧数据容器超过缓冲区会抛出 AssertionFailedError
                    inputBuffer.clear();
                    inputBuffer.put(input);
                    mVideoCodec.queueInputBuffer(inputBufferIndex, 0, input.length, 0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
                }
            } else {
                Log.i("AvcEncoder", "input buffer " + frameIdx + " not read");
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }

    }


    private void generateFrame(int frameIndex, int colorFormat, byte[] frameData) {
        final int HALF_WIDTH = mWidth / 2;
        boolean semiPlanar = isSemiPlanarYUV(colorFormat);
        // Set to zero.  In YUV this is a dull green.
        Arrays.fill(frameData, (byte) 0);
        int startX, startY, countX, countY;
        frameIndex %= 8;
        //frameIndex = (frameIndex / 8) % 8;    // use this instead for debug -- easier to see
        if (frameIndex < 4) {
            startX = frameIndex * (mWidth / 4);
            startY = 0;
        } else {
            startX = (7 - frameIndex) * (mWidth / 4);
            startY = mHeight / 2;
        }
        for (int y = startY + (mHeight / 2) - 1; y >= startY; --y) {
            for (int x = startX + (mWidth / 4) - 1; x >= startX; --x) {
                if (semiPlanar) {
                    // full-size Y, followed by UV pairs at half resolution
                    // e.g. Nexus 4 OMX.qcom.video.encoder.avc COLOR_FormatYUV420SemiPlanar
                    // e.g. Galaxy Nexus OMX.TI.DUCATI1.VIDEO.H264E
                    //        OMX_TI_COLOR_FormatYUV420PackedSemiPlanar
                    frameData[y * mWidth + x] = (byte) TEST_Y;
                    if ((x & 0x01) == 0 && (y & 0x01) == 0) {
                        frameData[mWidth * mHeight + y * HALF_WIDTH + x] = (byte) TEST_U;
                        frameData[mWidth * mHeight + y * HALF_WIDTH + x + 1] = (byte) TEST_V;
                    }
                } else {
                    // full-size Y, followed by quarter-size U and quarter-size V
                    // e.g. Nexus 10 OMX.Exynos.AVC.Encoder COLOR_FormatYUV420Planar
                    // e.g. Nexus 7 OMX.Nvidia.h264.encoder COLOR_FormatYUV420Planar
                    frameData[y * mWidth + x] = (byte) TEST_Y;
                    if ((x & 0x01) == 0 && (y & 0x01) == 0) {
                        frameData[mWidth * mHeight + (y / 2) * HALF_WIDTH + (x / 2)] = (byte) TEST_U;
                        frameData[mWidth * mHeight + HALF_WIDTH * (mHeight / 2) +
                                (y / 2) * HALF_WIDTH + (x / 2)] = (byte) TEST_V;
                    }
                }
            }
        }
    }


    private static boolean isSemiPlanarYUV(int colorFormat) {
        switch (colorFormat) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                return false;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
                return true;
            default:
                throw new RuntimeException("unknown format " + colorFormat);
        }
    }


    public void excuate() {
        try {
            VideoEncodePrepare();
            doEncodeVideoFromBuffer(mVideoCodec, encoderColorFormat);

        } finally {
            // release encoder, muxer, and input Surface
            close();
        }

    }


    public byte[] swapYV12toI420(byte[] yv12bytes, int width, int height) {
        byte[] i420bytes = new byte[yv12bytes.length];
        for (int i = 0; i < width * height; i++)
            i420bytes[i] = yv12bytes[i];
        for (int i = width * height; i < width * height + (width / 2 * height / 2); i++)
            i420bytes[i] = yv12bytes[i + (width / 2 * height / 2)];
        for (int i = width * height + (width / 2 * height / 2); i < width * height + 2 * (width / 2 * height / 2); i++)
            i420bytes[i] = yv12bytes[i - (width / 2 * height / 2)];
        return i420bytes;
    }

    void swapYV12toNV21(byte[] yv12bytes, byte[] nv12bytes, int width, int height) {

        int nLenY = width * height;

        int nLenU = nLenY / 4;

        System.arraycopy(yv12bytes, 0, nv12bytes, 0, width * height);

        for (int i = 0; i < nLenU; i++) {

            nv12bytes[nLenY + 2 * i + 1] = yv12bytes[nLenY + i];

            nv12bytes[nLenY + 2 * i] = yv12bytes[nLenY + nLenU + i];

        }

    }

    private void doEncodeVideoFromBuffer(MediaCodec encoder, int encoderColorFormat) {
        final int TIMEOUT_USEC = 10000;
        ByteBuffer[] encoderInputBuffers = encoder.getInputBuffers();
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        int generateIndex = 0;
        int checkIndex = 0;
        int badFrames = 0;


        // The size of a frame of video data, in the formats we handle, is stride*sliceHeight
        // for Y, and (stride/2)*(sliceHeight/2) for each of the Cb and Cr channels.  Application
        // of algebra and assuming that stride==width and sliceHeight==height yields:
        byte[] frameData = new byte[mWidth * mHeight * 3 / 2];
        byte[] frameDataYV12 = new byte[mWidth * mHeight * 3 / 2];
        // Just out of curiosity.
        long rawSize = 0;
        long encodedSize = 0;
        // Save a copy to disk.  Useful for debugging the test.  Note this is a raw elementary
        // stream, not a .mp4 file, so not all players will know what to do with it.


        // Loop until the output side is done.
        boolean inputDone = false;
        boolean encoderDone = false;
        boolean outputDone = false;

        while (!outputDone) {

            // If we're not done submitting frames, generate a new one and submit it.  By
            // doing this on every loop we're working to ensure that the encoder always has
            // work to do.
            //
            // We don't really want a timeout here, but sometimes there's a delay opening
            // the encoder device, so a short timeout can keep us from spinning hard.
            if (!inputDone) {
                int inputBufIndex = encoder.dequeueInputBuffer(TIMEOUT_USEC);

                if (inputBufIndex >= 0) {
                    long ptsUsec = computePresentationTime(generateIndex);
                    if (generateIndex == NUMFRAMES) {
                        // Send an empty frame with the end-of-stream flag set.  If we set EOS
                        // on a frame with data, that frame data will be ignored, and the
                        // output will be short one frame.
                        encoder.queueInputBuffer(inputBufIndex, 0, 0, ptsUsec,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                        if (VERBOSE) Log.d(TAG, "sent input EOS (with zero-length frame)");


                    } else {
                        generateFrame(generateIndex, encoderColorFormat, frameData);

                        ByteBuffer inputBuf = encoderInputBuffers[inputBufIndex];
                        // the buffer should be sized to hold one full frame

                        inputBuf.clear();
                        inputBuf.put(frameData);
                        encoder.queueInputBuffer(inputBufIndex, 0, frameData.length, ptsUsec, 0);
                        if (VERBOSE) Log.d(TAG, "submitted frame " + generateIndex + " to enc");
                    }
                    generateIndex++;
                } else {
                    // either all in use, or we timed out during initial setup
                    if (VERBOSE) Log.d(TAG, "input buffer not available");
                }
            }
            // Check for output from the encoder.  If there's no output yet, we either need to
            // provide more input, or we need to wait for the encoder to work its magic.  We
            // can't actually tell which is the case, so if we can't get an output buffer right
            // away we loop around and see if it wants more input.
            //
            // Once we get EOS from the encoder, we don't need to do this anymore.
            if (!encoderDone) {
                int encoderStatus = encoder.dequeueOutputBuffer(info, TIMEOUT_USEC);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (VERBOSE) Log.d(TAG, "no output from encoder available");
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    // not expected for an encoder
                    encoderOutputBuffers = encoder.getOutputBuffers();
                    if (VERBOSE) Log.d(TAG, "encoder output buffers changed");
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // not expected for an encoder
                    MediaFormat newFormat = encoder.getOutputFormat();
                    if (VERBOSE) Log.d(TAG, "encoder output format changed: " + newFormat);

                    if (mMuxerStarted) {
                        throw new RuntimeException("format changed twice");
                    }

                    Log.d(TAG, "encoder output format changed: " + newFormat);

                    // now that we have the Magic Goodies, start the muxer
                    mTrackIndex = mMuxer.addTrack(newFormat);
                    mMuxer.start();
                    mMuxerStarted = true;

                } else if (encoderStatus < 0) {
                    Log.d(TAG, "unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                } else { // encoderStatus >= 0
                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    if (encodedData == null) {
                        Log.d(TAG, "encoderOutputBuffer " + encoderStatus + " was null");
                    }

                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        // Codec config info.  Only expected on first packet.  One way to
                        // handle this is to manually stuff the data into the MediaFormat
                        // and pass that to configure().  We do that here to exercise the API.

                        MediaFormat format =
                                MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);
                        format.setByteBuffer("csd-0", encodedData);

                        info.size = 0;

                    } else {
                        // Get a decoder input buffer, blocking until it's available.

                        encoderDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
                            outputDone = true;
                        if (VERBOSE) Log.d(TAG, "passed " + info.size + " bytes to decoder"
                                + (encoderDone ? " (EOS)" : ""));
                    }


                    // It's usually necessary to adjust the ByteBuffer values to match BufferInfo.

                    if (info.size != 0) {
                        encodedData.position(info.offset);
                        encodedData.limit(info.offset + info.size);
                        mMuxer.writeSampleData(mTrackIndex, encodedData, info);

                        encodedSize += info.size;

                    }

                    encoder.releaseOutputBuffer(encoderStatus, false);
                }
            }


        }
        if (VERBOSE) Log.d(TAG, "decoded " + checkIndex + " frames at "
                + mWidth + "x" + mHeight + ": raw=" + rawSize + ", enc=" + encodedSize);


        if (checkIndex != NUMFRAMES) {
            Log.d(TAG, "expected " + 120 + " frames, only decoded " + checkIndex);
        }
        if (badFrames != 0) {
            Log.d(TAG, "Found " + badFrames + " bad frames");
        }
    }

    public static long computePresentationTime(int frameIndex) {
        return 132 + frameIndex * 1000000 / FRAME_RATE;
    }

    void offerAudioEncoder(byte[] data){
        _offerAudioEncoder(data);
    }

    void stop(){
        _stop();
    }

    //发送音频数据和时间进行编码
    private void _offerAudioEncoder(byte[] input) {
        if (videoBytesReceived == 0) {
            videoStartTime = System.nanoTime();
        }
        if(input != null) {
            videoBytesReceived += input.length;
        }
        drainEncoder(mVideoCodec, mVideoBufferInfo, mVideoTrackIndex, false);
        try {
            ByteBuffer[] inputBuffers = mVideoCodec.getInputBuffers();
            int inputBufferIndex = mVideoCodec.dequeueInputBuffer(-1);
//            Log.d(TAG, "inputBufferIndex--" + inputBufferIndex);
            if (inputBufferIndex >= 0) {
                if(input != null) {
                    ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                    inputBuffer.clear();
                    inputBuffer.put(input);
                }

                long presentationTimeUs = (System.nanoTime() - videoStartTime) / 1000L;
//                Log.d(TAG, "presentationTimeUs--" + presentationTimeUs);
                if (eosReceived) {
                    mVideoCodec.queueInputBuffer(inputBufferIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    finishMuxer();
                } else if(input != null){
                    mVideoCodec.queueInputBuffer(inputBufferIndex, 0, input.length, presentationTimeUs, 0);
                }
            }

        } catch (Throwable t) {
            Log.e(TAG, "_offerAudioEncoder exception " + t.getMessage());
        }

    }

    private void drainEncoder(MediaCodec encoder, MediaCodec.BufferInfo bufferInfo, TrackIndex trackIndex, boolean endOfStream) {
        final int TIMEOUT_USEC = 100;
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        try {
            while (true) {
                int encoderIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
//                Log.d(TAG, "encoderIndex---" + encoderIndex);
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
                    MediaFormat newFormat = encoder.getOutputFormat();
                    trackIndex.index = mMuxer.addTrack(newFormat);
                    if (!mMuxerStarted) {
                        mMuxer.start();
                    }
                    mMuxerStarted = true;
                } else if (encoderIndex < 0) {
                    Log.w(TAG, "encoderIndex 非法" + encoderIndex);
                } else {
                    //退出循环
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }

                    ByteBuffer encodeData = encoderOutputBuffers[encoderIndex];
                    if (encodeData == null) {
                        throw new RuntimeException("编码数据为空");
                    }else
                    if (bufferInfo.size != 0 && mLastVideoPresentationTimeUs < bufferInfo.presentationTimeUs) {
                        if (!mMuxerStarted) {
                            throw new RuntimeException("混合器未开启");
                        }
                        Log.d(TAG, "write_info_data......");
                        encodeData.position(bufferInfo.offset);
                        encodeData.limit(bufferInfo.offset + bufferInfo.size);
//                        Log.d(TAG, "presentationTimeUs--bufferInfo : " + bufferInfo.presentationTimeUs);
                        mMuxer.writeSampleData(trackIndex.index, encodeData, bufferInfo);

                        mLastVideoPresentationTimeUs = bufferInfo.presentationTimeUs;
                        encoder.releaseOutputBuffer(encoderIndex, true);
                    }


                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "error :: " + e.getMessage());
        }
    }

    //终止编码
    private void _stop() {
        eosReceived = true;
        _offerAudioEncoder(null);
        Log.d(TAG, "停止编码");
    }

    private void finishMuxer() {
        drainEncoder(mVideoCodec, mVideoBufferInfo, mVideoTrackIndex, true);
        close();
    }

    private void copyFormatInteger(MediaFormat from, MediaFormat to, String key, int defValue) {
        if (from.containsKey(key)) {
            Log.e("slack","Copy Src Video Format: [" + key + "] : " + from.getInteger(key));
            to.setInteger(key, from.getInteger(key));
        } else {
            Log.e("slack","Set Video Format: [" + key + "] : " + defValue);
            to.setInteger(key, defValue);
        }
    }

}

package com.cl.slack.playaudio.media;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author slack
 * @time 17/2/13 下午4:14
 */
class MediaMuxerMixAudioAndVideo {

    private static final String TAG = "MediaMuxer";

    private MediaMuxer mMediaMuxer;
    private boolean mMuxerStarted = false;

    private MediaEncoder mAudioEncoder, mVideoEncoder;
    private MediaInfo mMediaInfo;

    private  int mEncoderCount = 0,mStatredCount = 0;

    MediaMuxerMixAudioAndVideo(String file) {

        try {
            mMediaMuxer = new MediaMuxer(file, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException ioe) {
            throw new RuntimeException("failed init encoder", ioe);
        }
        mStatredCount = 0;
        mAudioEncoder = new MediaAudioEncoder(this);
        mEncoderCount ++;
        mVideoEncoder = new MediaVideoEncoder(this);
        mEncoderCount ++;
    }

    void prepare(MediaInfo mediaInfo) throws IOException {
        mMediaInfo = mediaInfo;
        if (mVideoEncoder != null) {
            mVideoEncoder.prepare(mediaInfo);
        }
        if (mAudioEncoder != null) {
            mAudioEncoder.prepare(mediaInfo);
        }
    }

    synchronized boolean isStarted() {
        return mMuxerStarted;
    }

    /**
     * 音频和视频都需要准备好 才可以 start
     */
    synchronized boolean start() {
        if(!mMuxerStarted) {
            mStatredCount++;
            if ((mEncoderCount > 0) && (mStatredCount == mEncoderCount)) {
                mMediaMuxer.start();
                mMuxerStarted = true;
                notifyAll();
                Log.i("slack","MuxerStart...");
            }
        }
        return mMuxerStarted;
    }

    synchronized void release() {

        if (mVideoEncoder != null) {
            mVideoEncoder.stop();
        }
        if (mAudioEncoder != null) {
            mAudioEncoder.stop();
        }

    }

    synchronized void stop() {
        mStatredCount--;
        if ((mEncoderCount > 0) && (mStatredCount <= 0)) {
            mMediaMuxer.stop();
            mMediaMuxer.release();
            mMuxerStarted = false;
        }
    }

    synchronized int addTrack(final MediaFormat format) {
        if (mMuxerStarted)
            throw new IllegalStateException("muxer already started");
        final int trackIx = mMediaMuxer.addTrack(format);
        return trackIx;
    }

    synchronized void writeSampleData(final int trackIndex, final ByteBuffer byteBuf, final MediaCodec.BufferInfo bufferInfo) {
        mMediaMuxer.writeSampleData(trackIndex, byteBuf, bufferInfo);
    }

    void offerMediaEncoder(MediaFrame frame) {
        if(frame.isAudioTrack(mMediaInfo.mAudioTrackIndex)){
            if (mAudioEncoder != null) {
                mAudioEncoder.offerMediaEncoder(frame);
            }
        }else {
            if (mVideoEncoder != null) {
                mVideoEncoder.offerMediaEncoder(frame);
            }
        }
    }
}

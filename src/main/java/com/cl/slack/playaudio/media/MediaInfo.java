package com.cl.slack.playaudio.media;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;

/**
 * Created by slack
 * on 17/2/10 下午3:59.
 * 记录解析的视频的 信息
 */

public class MediaInfo {

    private static final int BIT_RATE = 1920 * 1080 * 30;            // 2Mbps
    private static final int FRAME_RATE = 30;               // 30fps
    private static final int IFRAME_INTERVAL = 10;          // 10 seconds between I-frames
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    /**
     * audio
     */
    public String mAudioMime;
    public int mAudioTrackIndex = -1;
    public int mAudioBufferSize = 0;
    public MediaFormat mAudioFormat;

    /**
     * video
     */
    public String mVideoMime;
    public int mVideoTrackIndex = -1;
    public int mVideoBufferSize = 0;
    public MediaFormat mVideoFormat;

    /**
     * video duration
     */
    public long mVideoDuration = 10000;

    /**
     * video size 原本视频的 width * height
     */
    public int mVideoWidth = 1;
    public int mVideoHeight = 1;

    public void setVideoFormat(int trackIndex, MediaFormat format) throws Exception {
        mVideoTrackIndex = trackIndex;
        mVideoFormat = format;
        if(mVideoFormat != null) {
            mVideoMime = mVideoFormat.getString(MediaFormat.KEY_MIME);

            mVideoWidth = mVideoFormat.getInteger(MediaFormat.KEY_WIDTH);
            mVideoHeight = mVideoFormat.getInteger(MediaFormat.KEY_HEIGHT);
            if (mVideoHeight <= 0 || mVideoWidth <= 0) {
                throw new IllegalStateException("Video Size is zero!");
            }

            mVideoDuration = mVideoFormat.getLong(MediaFormat.KEY_DURATION);
            if (mVideoDuration <= 0) {
                throw new IllegalStateException("Video duration is <= 0");
            }

            mVideoBufferSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
            if (mVideoBufferSize <= 0) {
                throw new IllegalStateException("Video max input size <= 0");
            }
        }
    }

    public void setAudioFormat(int trackIndex, MediaFormat format) {
        mAudioTrackIndex = trackIndex;
        mAudioFormat = format;
        if (mAudioFormat != null) {
            mAudioMime = mAudioFormat.getString(MediaFormat.KEY_MIME);
            mAudioBufferSize = mAudioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
            if (mAudioBufferSize <= 0) {
                throw new IllegalStateException("Audio max input size <= 0");
            }
        }
        else {
            mAudioMime = "";
        }
    }

    MediaFormat createVideoFormatCopy() {
        MediaFormat format = MediaFormat.createVideoFormat(mVideoMime, mVideoWidth, mVideoHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);// MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);    // API >= 18
        copyFormatInteger(mVideoFormat, format, MediaFormat.KEY_FRAME_RATE, FRAME_RATE);

        copyFormatInteger(mVideoFormat, format, MediaFormat.KEY_BIT_RATE, BIT_RATE);

        copyFormatInteger(mVideoFormat, format, MediaFormat.KEY_I_FRAME_INTERVAL,IFRAME_INTERVAL);

        return format;
    }

    MediaFormat createAudioFormat() {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 10 * 1024);
        return format;
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

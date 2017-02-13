package com.cl.slack.playaudio.media;

import android.media.MediaCodec;
import android.media.MediaFormat;

import java.nio.ByteBuffer;

/**
 * Created by slack
 * on 17/2/10 下午3:20.
 * 用来表示从 MediaExtractor 出来的一帧 audio 或者 video 的数据
 */

public class MediaFrame {

    private int trackIndex = -1; // 该帧轨道 标示 audio 或者 video
    private boolean hasData = false;

    private byte [] data;

    private MediaCodec.BufferInfo info;// useless

    public MediaFrame(int track,byte[] data ,MediaCodec.BufferInfo info) {
        this.trackIndex = track;
        this.data = data;
        this.info = info;
    }

    /**
     * 是否是音频帧
     */
    public boolean isAudioTrack(int index){
        return trackIndex == index;
    }

    public int size() {
        return info.size;
    }

    public long time() {
        return info.presentationTimeUs;
    }

    public int flags() {
        return info.flags;
    }

    public boolean hasData(){
        return data != null;
    }

    public byte[] getByteData() {
        return data;
    }

    public void setByteData(byte[] bytes) {
        data = bytes;
    }

    public int offset(){
        return info.offset;
    }

    public int infoLength(){
        return info.offset + info.size;
    }

    public int dataLength(){
        return data.length;
    }

    public int track(){
        return trackIndex;
    }

    public int setTrack(){
        return trackIndex;
    }

    public MediaCodec.BufferInfo getBufferInfo() {
        return info;
    }

    public void setBufferInfo(MediaCodec.BufferInfo info) {
        this.info = info;
    }
}

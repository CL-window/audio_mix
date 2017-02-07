package com.cl.slack.playaudio;

import java.nio.ByteOrder;

/**
 * Created by slack
 * on 17/2/7 下午4:54.
 */

public enum  BytesTransUtil {

    INSTANCE;

    public byte[] shorts2Bytes(short[] s) {
        byte bLength = 2;
        byte[] buf = new byte[s.length * bLength];
        for (int iLoop = 0; iLoop < s.length; iLoop++) {
            byte[] temp = getBytes(s[iLoop]);
            for (int jLoop = 0; jLoop < bLength; jLoop++) {
                buf[iLoop * bLength + jLoop] = temp[jLoop];
            }
        }
        return buf;
    }

    public short[] bytes2Shorts(byte[] buf) {
        byte bLength = 2;
        short[] s = new short[buf.length / bLength];
        for (int iLoop = 0; iLoop < s.length; iLoop++) {
            byte[] temp = new byte[bLength];
            for (int jLoop = 0; jLoop < bLength; jLoop++) {
                temp[jLoop] = buf[iLoop * bLength + jLoop];
            }
            s[iLoop] = getShort(temp);
        }
        return s;
    }

    /**
     * 噪音消除算法
     */
    public void noiseClear(short[] lin,int off,int len) {
        int i,j;
        for (i = 0; i < len; i++) {
            j = lin[i+off];
            lin[i+off] = (short)(j>>2);
        }
    }

    public void noiseClear(byte[] bytes,int off,int len) {
        short[] data = bytes2Shorts(bytes);
        noiseClear(data,off,len);
        bytes = shorts2Bytes(data);
    }

    /**
     * 调节 音量
     * @param level 音量
     */
    public void adjustVoice(byte[] buffer,int level){
        for(int i=0;i<buffer.length;i++)
        {
            buffer[i]= (byte) (buffer[i]*level);
        }
    }

    ///////////////// private /////////////////////////////////////////////

    /**
     * 大端小端 问题
     */
    private boolean thisCPU() {
        if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
            // System.out.println(is big ending);
            return true;
        } else {
            // System.out.println(is little ending);
            return false;
        }
    }

    private byte[] getBytes(long s, boolean bBigEnding) {
        byte[] buf = new byte[8];
        if (bBigEnding)
            for (int i = buf.length - 1; i >= 0; i--) {
                buf[i] = (byte) (s & 0x00000000000000ff);
                s >>= 8;
            }
        else
            for (int i = 0; i < buf.length; i++) {
                buf[i] = (byte) (s & 0x00000000000000ff);
                s >>= 8;
            }
        return buf;
    }

    private byte[] getBytes(short s) {
        return getBytes(s, this.thisCPU());
    }

    private short getShort(byte[] buf) {
        return getShort(buf, this.thisCPU());
    }

    private short getShort(byte[] buf, boolean bBigEnding) {
        if (buf == null) {
            throw new IllegalArgumentException("byte array is null!");
        }
        if (buf.length > 2) {
            throw new IllegalArgumentException("byte array size > 2 !");
        }
        short r = 0;
        if (bBigEnding) {
            for (int i = 0; i < buf.length; i++) {
                r <<= 8;
                r |= (buf[i] & 0x00ff);
            }
        } else {
            for (int i = buf.length - 1; i >= 0; i--) {
                r <<= 8;
                r |= (buf[i] & 0x00ff);
            }
        }

        return r;
    }
}

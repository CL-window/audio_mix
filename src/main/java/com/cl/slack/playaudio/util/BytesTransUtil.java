package com.cl.slack.playaudio.util;

import android.util.Log;

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
            System.arraycopy(temp, 0, buf, iLoop * bLength, bLength);
        }
        return buf;
    }

    public short[] bytes2Shorts(byte[] buf) {
        byte bLength = 2;
        short[] s = new short[buf.length / bLength];
        for (int iLoop = 0; iLoop < s.length; iLoop++) {
            byte[] temp = new byte[bLength];
            System.arraycopy(buf, iLoop * bLength, temp, 0, bLength);
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

    public byte[] noiseClear(byte[] bytes,int off,int len) {
        short[] data = bytes2Shorts(bytes);
        noiseClear(data,off,len);
        return shorts2Bytes(data);
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

    public short[] adjustVoice(short[] buffer,int level){
        byte[] temp = shorts2Bytes(buffer);
        adjustVoice(temp,level);
        return bytes2Shorts(temp);
    }

    public byte[] averageMix(byte[] src1,byte[] src2){
//        return averageMix(new byte[][]{src1,src2});
        return averageMixSelectShort(new byte[][]{src1,src2});
    }

    /**
     * 采用简单的平均算法 average audio mixing algorithm
     * code from :    http://www.codexiu.cn/android/blog/3618/
     * 测试发现这种算法会降低 录制的音量
     * 需要两个音频帧的数据长度相同才可以混合
     * bMulRoadAudioes[0] 原始默认的音频
     */
    public byte[] averageMix(byte[][] bMulRoadAudioes) {

        if (bMulRoadAudioes == null || bMulRoadAudioes.length == 0)
            return null;
        byte[] realMixAudio = bMulRoadAudioes[0];

        if (bMulRoadAudioes.length == 1)
            return realMixAudio;

        /**
         * 保证每一帧数据的长度都是一样的
         */
        for (int rw = 0; rw < bMulRoadAudioes.length; ++rw) {
            if (bMulRoadAudioes[rw].length != realMixAudio.length) {
                Log.e("app", "column of the road of audio + " + rw + " is diffrent.");
                return realMixAudio;
            }
        }

        int row = bMulRoadAudioes.length;
        int column = realMixAudio.length / 2;
        short[][] sMulRoadAudioes = new short[row][column];
        /** byte --> short
         * 对多维数组进行遍历，类似一个表格，row 行 column列
         * 对每一列 把相邻的两个数据，比如 （0，1） （2，3）（4，5）...  2 byte 合并为一个short（高位,地位）
         * byte只有8位,其范围是-128~127，第一位为符号位
         * 0xff二进制就是  (000...24个...0 )1111 1111。
         * & 运算是，如果对应的两个bit都是1，则那个bit结果为1，否则为0,比如 1010 & 1101 = 1000 （二进制）
         * java中的数值 为 int  所以 0xff 是int 型，
         * number & 0xff 意思是只取低八位，其他高位都是0
         * | 是逻辑“或”运算 如果对应的两个bit只要有一个是1，则那个bit结果为1，否则为0
         * << 8  左移8位  每次左移一位相当于乘2
         * short 2个字节 16位
         */
        for (int r = 0; r < row; ++r) {
            for (int c = 0; c < column; ++c) {
                sMulRoadAudioes[r][c] = (short) ((bMulRoadAudioes[r][c * 2] & 0xff) | (bMulRoadAudioes[r][c * 2 + 1] & 0xff) << 8);
            }
        }
        short[] sMixAudio = new short[column];
        int mixVal;
        int sr;
        /**
         * 对于 column列 上的每一项，合并 row 值  （累加取平均值）
         *       column1 column2 column3 column4 ...
         *  row1   .11.    .12.    .13.    .14.
         *  row2   .21.    .22.    .23.    .24.
         *  row3   .31.    .32.    .33.    .34.
         *   .
         *   .
         */
        for (int sc = 0; sc < column; ++sc) {
            mixVal = 0;
            sr = 0;
            for (; sr < row; ++sr) {
                mixVal += sMulRoadAudioes[sr][sc];
            }
            sMixAudio[sc] = (short) (mixVal / row);
        }
        /**
         * short --> byte
         */
        for (sr = 0; sr < column; ++sr) {
            realMixAudio[sr * 2] = (byte) (sMixAudio[sr] & 0x00FF);
            realMixAudio[sr * 2 + 1] = (byte) ((sMixAudio[sr] & 0xFF00) >> 8);
        }
        return realMixAudio;
    }

    /**
     * 以最短的帧 为主 ，会丢失一部分数据
     * 哎 会出现杂音
     */
    public byte[] averageMixSelectShort(byte[][] bMulRoadAudioes) {

        if (bMulRoadAudioes == null || bMulRoadAudioes.length == 0)
            return null;
        byte[] realMixAudio = bMulRoadAudioes[0];

        if (bMulRoadAudioes.length == 1)
            return realMixAudio;

        /**
         * 以最短的帧 为主
         */
        for (int rw = 0; rw < bMulRoadAudioes.length; ++rw) {
            if (bMulRoadAudioes[rw].length < realMixAudio.length) {
                realMixAudio = bMulRoadAudioes[rw];
            }
        }

        int row = bMulRoadAudioes.length;
        int column = realMixAudio.length / 2;
        short[][] sMulRoadAudioes = new short[row][column];

        for (int r = 0; r < row; ++r) {
            for (int c = 0; c < column; ++c) {
                sMulRoadAudioes[r][c] = (short) ((bMulRoadAudioes[r][c * 2] & 0xff) | (bMulRoadAudioes[r][c * 2 + 1] & 0xff) << 8);
            }
        }
        short[] sMixAudio = new short[column];
        int mixVal;
        int sr;

        for (int sc = 0; sc < column; ++sc) {
            mixVal = 0;
            sr = 0;
            for (; sr < row; ++sr) {
                mixVal += sMulRoadAudioes[sr][sc];
            }
            sMixAudio[sc] = (short) (mixVal / row);
        }

        for (sr = 0; sr < column; ++sr) {
            realMixAudio[sr * 2] = (byte) (sMixAudio[sr] & 0x00FF);
            realMixAudio[sr * 2 + 1] = (byte) ((sMixAudio[sr] & 0xFF00) >> 8);
        }
        return realMixAudio;
    }

    /**
     *  视频帧数据 YV12 --> I420
     */
    public byte[] swapYV12toI420(byte[] yv12bytes, int width, int height) {
        byte[] i420bytes = new byte[yv12bytes.length];
        System.arraycopy(yv12bytes, 0, i420bytes, 0, width * height);
        System.arraycopy(yv12bytes, width * height + (width / 2 * height / 2), i420bytes, width * height, width * height + (width / 2 * height / 2) - width * height);
        System.arraycopy(yv12bytes, width * height + (width / 2 * height / 2) - (width / 2 * height / 2), i420bytes, width * height + (width / 2 * height / 2), width * height + 2 * (width / 2 * height / 2) - (width * height + (width / 2 * height / 2)));
        return i420bytes;
    }

    ///////////////// private /////////////////////////////////////////////

    /**
     * 大端小端 问题
     */
    private boolean thisCPU() {
        return ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
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
            for (byte aBuf : buf) {
                r <<= 8;
                r |= (aBuf & 0x00ff);
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

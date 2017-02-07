package com.cl.slack.playaudio;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Objects;

/**
 * Created by slack
 * on 17/2/7 上午11:11.
 * mp3 --> pcm data
 */

public class PCMData {

    /**
     * 初始化解码器
     */
    private static final Object lockPCM = new Object();
    private static final int BUFFER_SIZE = 2048;

    private ArrayList<PCM> chunkPCMDataContainer = new ArrayList<>();//PCM数据块容器
    private MediaExtractor mediaExtractor;
    private MediaCodec mediaDecode;
    private ByteBuffer[] decodeInputBuffers;
    private ByteBuffer[] decodeOutputBuffers;
    private MediaCodec.BufferInfo decodeBufferInfo;
    boolean sawInputEOS = false;
    boolean sawOutputEOS = false;

    private String mp3FilePath;

    private MediaFormat mMediaFormat;

    public PCMData(String path) {
        mp3FilePath = path;
    }

    public PCMData startPcmExtractor(){
        initMediaDecode();
        new Thread(new Runnable() {
            @Override
            public void run() {
                srcAudioFormatToPCM();
            }
        }).start();
        return this;
    }

    public PCMData release(){
        chunkPCMDataContainer.clear();
        return this;
    }

    public byte[] getPCMData() {
        synchronized (lockPCM) {//记得加锁
            if (chunkPCMDataContainer.isEmpty()) {
                return null;
            }

            byte[] pcmChunk = chunkPCMDataContainer.get(0).bufferBytes;//每次取出index 0 的数据
            chunkPCMDataContainer.remove(0);//取出后将此数据remove掉 既能保证PCM数据块的取出顺序 又能及时释放内存
            return pcmChunk;
        }
    }

    /**
     * 测试时发现 播放音频的 MediaCodec.BufferInfo.size 是变换的
     */
    public int getBufferSize() {
        synchronized (lockPCM) {//记得加锁
            if (chunkPCMDataContainer.isEmpty()) {
                return BUFFER_SIZE;
            }
            return chunkPCMDataContainer.get(0).bufferSize;
        }
    }

    public MediaFormat getMediaFormat() {
        return mMediaFormat;
    }

    private void initMediaDecode() {
        try {
            mediaExtractor = new MediaExtractor();//此类可分离视频文件的音轨和视频轨道
            mediaExtractor.setDataSource(mp3FilePath);//媒体文件的位置
            for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {//遍历媒体轨道 此处我们传入的是音频文件，所以也就只有一条轨道
                mMediaFormat = mediaExtractor.getTrackFormat(i);
                String mime = mMediaFormat.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {//获取音频轨道
//                    format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 200 * 1024);
                    mediaExtractor.selectTrack(i);//选择此音频轨道
                    mediaDecode = MediaCodec.createDecoderByType(mime);//创建Decode解码器
                    mediaDecode.configure(mMediaFormat, null, null, 0);
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("slack","error :: " + e.getMessage());
        }

        if (mediaDecode == null) {
            Log.e("slack", "create mediaDecode failed");
            return;
        }
        mediaDecode.start();//启动MediaCodec ，等待传入数据
        decodeInputBuffers = mediaDecode.getInputBuffers();//MediaCodec在此ByteBuffer[]中获取输入数据
        decodeOutputBuffers = mediaDecode.getOutputBuffers();//MediaCodec将解码后的数据放到此ByteBuffer[]中 我们可以直接在这里面得到PCM数据
        decodeBufferInfo = new MediaCodec.BufferInfo();//用于描述解码得到的byte[]数据的相关信息
    }

    private void putPCMData(byte[] pcmChunk,int bufferSize) {
        synchronized (lockPCM) {//记得加锁
            chunkPCMDataContainer.add(new PCM(pcmChunk,bufferSize));
        }
    }


    /**
     * 解码音频文件 得到PCM数据块
     *
     * @return 是否解码完所有数据
     */
    private void srcAudioFormatToPCM() {

        sawOutputEOS = false;
        sawInputEOS = false;
        try {
            while (!sawOutputEOS) {
                if (!sawInputEOS) {
                    int inputIndex = mediaDecode.dequeueInputBuffer(-1);//获取可用的inputBuffer -1代表一直等待，0表示不等待 建议-1,避免丢帧
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = decodeInputBuffers[inputIndex];//拿到inputBuffer
                        inputBuffer.clear();//清空之前传入inputBuffer内的数据
                        int sampleSize = mediaExtractor.readSampleData(inputBuffer, 0);//MediaExtractor读取数据到inputBuffer中
                        if (sampleSize < 0) {//小于0 代表所有数据已读取完成
                            sawInputEOS = true;
                            mediaDecode.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        } else {
                            long presentationTimeUs = mediaExtractor.getSampleTime();
                            mediaDecode.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0);//通知MediaDecode解码刚刚传入的数据
                            mediaExtractor.advance();//MediaExtractor移动到下一取样处
                        }
                    }
                }

                //获取解码得到的byte[]数据 参数BufferInfo上面已介绍 10000同样为等待时间 同上-1代表一直等待，0代表不等待。此处单位为微秒
                //此处建议不要填-1 有些时候并没有数据输出，那么他就会一直卡在这 等待
                int outputIndex = mediaDecode.dequeueOutputBuffer(decodeBufferInfo, 10000);
                if (outputIndex >= 0) {
                    int outputBufIndex = outputIndex;
                    // Simply ignore codec config buffers.
                    if ((decodeBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        mediaDecode.releaseOutputBuffer(outputBufIndex, false);
                        continue;
                    }

                    if (decodeBufferInfo.size != 0) {

                        ByteBuffer outBuf = decodeOutputBuffers[outputBufIndex];//拿到用于存放PCM数据的Buffer

                        outBuf.position(decodeBufferInfo.offset);
                        outBuf.limit(decodeBufferInfo.offset + decodeBufferInfo.size);
                        byte[] data = new byte[decodeBufferInfo.size];//BufferInfo内定义了此数据块的大小
                        outBuf.get(data);//将Buffer内的数据取出到字节数组中
                        putPCMData(data,decodeBufferInfo.size);//自己定义的方法，供编码器所在的线程获取数据,下面会贴出代码
                    }

                    mediaDecode.releaseOutputBuffer(outputBufIndex, false);//此操作一定要做，不然MediaCodec用完所有的Buffer后 将不能向外输出数据

                    if ((decodeBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        sawOutputEOS = true;
                    }

                } else if (outputIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    decodeOutputBuffers = mediaDecode.getOutputBuffers();
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                }
            }
        } finally {
            if(mediaDecode != null) {
                mediaDecode.release();
            }
            if(mediaExtractor != null){
                mediaExtractor.release();
            }
        }
    }


    class PCM{
        public PCM(byte[] bufferBytes, int bufferSize) {
            this.bufferBytes = bufferBytes;
            this.bufferSize = bufferSize;
        }

        byte[] bufferBytes;
        int bufferSize;
    }
}

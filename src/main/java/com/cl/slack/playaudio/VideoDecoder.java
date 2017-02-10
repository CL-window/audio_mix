package com.cl.slack.playaudio;

import android.annotation.TargetApi;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Created by slack
 * on 17/2/9 下午5:51.
 * 解码视频 大致过程与 Audio 一样
 */

public class VideoDecoder {

    private static final String TAG = "VideoToFrames";
    private static final boolean VERBOSE = true;
    private static final long DEFAULT_TIMEOUT_US = 10000;
    private static final int IMAGES_COUNT = 50;

    private static final int COLOR_FormatI420 = 1;
    private static final int COLOR_FormatNV21 = 2;

    public static final int FILE_TypeI420 = 1;
    public static final int FILE_TypeNV21 = 2;
    public static final int FILE_TypeJPEG = 3;

    private final int decodeColorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;

    private int outputImageFileType = -1;
    private String OUTPUT_DIR;


    public int ImageWidth = 0;
    public int ImageHeight = 0;

    MediaExtractor extractor = null;
    MediaCodec decoder = null;
    MediaFormat mediaFormat = null ;

    private Queue<byte[]> FramesQueue = new LinkedBlockingDeque<>();

    private boolean sawOutputEOS = false;

    public void setSaveFrames(String dir, int fileType) throws IOException {
        if (fileType != FILE_TypeI420 && fileType != FILE_TypeNV21 && fileType != FILE_TypeJPEG) {
            throw new IllegalArgumentException("only support FILE_TypeI420 " + "and FILE_TypeNV21 " + "and FILE_TypeJPEG");
        }
        outputImageFileType = fileType;
        File theDir = new File(dir);
        if (!theDir.exists()) {
            theDir.mkdirs();
        } else if (!theDir.isDirectory()) {
            throw new IOException("Not a directory");
        }
        OUTPUT_DIR = theDir.getAbsolutePath() + "/";
    }

    void videoDecodePrepare(String videoFilePath) {
        extractor = null;
        decoder = null;
        try {
            File videoFile = new File(videoFilePath);
            extractor = new MediaExtractor();
            extractor.setDataSource(videoFile.toString());
            int trackIndex = selectVideoTrack(extractor);
            if (trackIndex < 0) {
                throw new RuntimeException("No video track found in " + videoFilePath);
            }
            extractor.selectTrack(trackIndex);
            mediaFormat = extractor.getTrackFormat(trackIndex);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);
            showSupportedColorFormat(decoder.getCodecInfo().getCapabilitiesForType(mime));
            if (isColorFormatSupported(decodeColorFormat, decoder.getCodecInfo().getCapabilitiesForType(mime))) {
                mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, decodeColorFormat);
                Log.i(TAG, "set decode color format to type " + decodeColorFormat);
            } else {
                Log.i(TAG, "unable to set decode color format, color format type " + decodeColorFormat + " not supported");
            }

            decoder.configure(mediaFormat, null, null, 0);
            decoder.start();

        } catch (IOException ioe) {
            throw new RuntimeException("failed init encoder", ioe);
        }
    }

    void close() {

        decoder.stop();
        decoder.release();

        if (extractor != null) {

            extractor.release();
            extractor = null;
        }
    }

    void stop(){
        sawOutputEOS = true;
        FramesQueue.clear();
    }
    /**
     * 解码视频 --> 图片
     */
    void startDecodeFramesToImage(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                decodeFramesToImage();
            }
        }).start();
    }

    public void decodeFramesToImage() {
        try {

            decodeFramesToImage(decoder, extractor, mediaFormat);

        } finally {
            // release encoder, muxer, and input Surface
            close();
            Log.i("slack","finish decodeFramesToImage");
        }

    }

    /**
     * 可以写数据，写出来的数据是绿屏幕 应该是 YUV 数据 格式的问题
     */
    void startFramesExtractor(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                framesExtractor();
            }
        }).start();
    }

    byte[] getFramesQueueBytes() {
        byte[] temp = null;
        if (FramesQueue.isEmpty()) {
            return null;
        }
        // poll 如果队列为空，则返回null
        temp = FramesQueue.poll();
//        Log.i(TAG,"getBackGroundBytes... "+ (temp == null ? "is" : "not") + " null");
        return temp;
    }

    public boolean isExtractorEOS() {
        return sawOutputEOS;
    }

    /**
     * decode video --> byte[]
     */
    private void framesExtractor(){
        try {

            decodeFrames(decoder, extractor);

        } finally {
            // release encoder, muxer, and input Surface
            close();
        }
    }

    private void addFramesQueueBytes(byte[] data) {
        FramesQueue.add(data);
        // test data --> jpg  error byte[] 数据 生成图片不多，显示不出来
//        dumpFile(FileUtil.INSTANCE.getSdcardFileDir() + "/" + FramesQueue.size() + ".yuv",data);
        // todo test only 50 frames
        if(FramesQueue.size() > 50){
            sawOutputEOS = true;
        }
    }

    private void decodeFrames(MediaCodec decoder, MediaExtractor extractor) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        sawOutputEOS = false;
        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                int inputBufferId = decoder.dequeueInputBuffer(DEFAULT_TIMEOUT_US);
                if (inputBufferId >= 0) {
                    ByteBuffer inputBuffer;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        inputBuffer = decoder.getInputBuffer(inputBufferId);
                    } else {
                        inputBuffer = decoder.getInputBuffers()[inputBufferId];
                    }
                    assert inputBuffer != null;
                    inputBuffer.clear();
                    int sampleSize = extractor.readSampleData(inputBuffer, 0); //将一部分视频数据读取到inputbuffer中，大小为sampleSize
                    if (sampleSize < 0) {
                        sawInputEOS = true;
                        decoder.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    } else {
                        long presentationTimeUs = extractor.getSampleTime();
                        decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0);
                        extractor.advance();  //移动到视频文件的下一个地址
                    }
                }
            }
            int outputBufferId = decoder.dequeueOutputBuffer(info, DEFAULT_TIMEOUT_US);
            if (outputBufferId >= 0) {

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    decoder.releaseOutputBuffer(outputBufferId, true);
                    continue;
                }

                if (info.size != 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        Image image = decoder.getOutputImage(outputBufferId);
//                        System.out.println("image format: " + image.getFormat());
                        addFramesQueueBytes(getDataFromImage(image, COLOR_FormatI420));//自己定义的方法，供编码器所在的线程获取数据
                        image.close();
                    } else {

                        ByteBuffer outBuf = decoder.getOutputBuffers()[outputBufferId];
                        outBuf.position(info.offset);
                        outBuf.limit(info.offset + info.size);
                        byte[] data = new byte[info.size];//BufferInfo内定义了此数据块的大小
                        outBuf.get(data);//将Buffer内的数据取出到字节数组中
//                        Log.i("slack","try put pcm data ...");
                        addFramesQueueBytes(data);//自己定义的方法，供编码器所在的线程获取数据
                    }

                }

                decoder.releaseOutputBuffer(outputBufferId, true);

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }
            }
        }
    }

    private void showSupportedColorFormat(MediaCodecInfo.CodecCapabilities caps) {
        System.out.print("supported color format: ");
        for (int c : caps.colorFormats) {
            System.err.print(c + "\t");
        }
        System.out.println();
    }

    private boolean isColorFormatSupported(int colorFormat, MediaCodecInfo.CodecCapabilities caps) {
        for (int c : caps.colorFormats) {
            if (c == colorFormat) {
                return true;
            }
        }
        return false;
    }

    private void decodeFramesToImage(MediaCodec decoder, MediaExtractor extractor, MediaFormat mediaFormat) {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;

        final int width = mediaFormat.getInteger(MediaFormat.KEY_WIDTH);
        final int height = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT);

        ImageWidth = width;
        ImageHeight = height;

        int outputFrameCount = 0;
        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                int inputBufferId = decoder.dequeueInputBuffer(DEFAULT_TIMEOUT_US);
                if (inputBufferId >= 0) {
                    ByteBuffer inputBuffer;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        inputBuffer = decoder.getInputBuffer(inputBufferId);
                    } else {
                        inputBuffer = decoder.getInputBuffers()[inputBufferId];
                    }
                    int sampleSize = extractor.readSampleData(inputBuffer, 0); //将一部分视频数据读取到inputbuffer中，大小为sampleSize
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        sawInputEOS = true;
                    } else {
                        long presentationTimeUs = extractor.getSampleTime();
                        decoder.queueInputBuffer(inputBufferId, 0, sampleSize, presentationTimeUs, 0);
                        extractor.advance();  //移动到视频文件的下一个地址
                    }
                }
            }
            int outputBufferId = decoder.dequeueOutputBuffer(info, DEFAULT_TIMEOUT_US);
            if (outputBufferId >= 0) {
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true;
                }

                boolean doRender = (info.size != 0);
                if (doRender) {
                    outputFrameCount++;
                    if(outputFrameCount >= IMAGES_COUNT){
                        sawOutputEOS = true;
                        continue;
                    }
                    // here out put TODO
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        Image image = decoder.getOutputImage(outputBufferId);
//                        System.out.println("image format: " + image.getFormat());
                        if (outputImageFileType != -1) {
                            String fileName;
                            switch (outputImageFileType) {
                                case FILE_TypeI420:
                                    fileName = OUTPUT_DIR + String.format("frame_%05d_I420_%dx%d.yuv", outputFrameCount, width, height);
                                    dumpFile(fileName, getDataFromImage(image, COLOR_FormatI420));
                                    break;
                                case FILE_TypeNV21:
                                    fileName = OUTPUT_DIR + String.format("frame_%05d_NV21_%dx%d.yuv", outputFrameCount, width, height);
                                    dumpFile(fileName, getDataFromImage(image, COLOR_FormatNV21));
                                    break;
                                case FILE_TypeJPEG:
                                    fileName = OUTPUT_DIR + String.format("frame_%05d.jpg", outputFrameCount);
                                    compressToJpeg(fileName, image);
                                    break;
                            }
                        }
                        image.close();
                    } else {
                        ByteBuffer outBuffer = decoder.getOutputBuffers()[outputBufferId];
                        //
                    }

                    decoder.releaseOutputBuffer(outputBufferId, true);
                }
            }
        }
    }

    private int selectVideoTrack(MediaExtractor extractor) {
        int numTracks = extractor.getTrackCount();
        for (int i = 0; i < numTracks; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                if (VERBOSE) {
                    Log.d(TAG, "Extractor selected track " + i + " (" + mime + "): " + format);
                }
                return i;
            }
        }
        return -1;
    }

    private static boolean isImageFormatSupported(Image image) {
        int format = image.getFormat();
        switch (format) {
            case ImageFormat.YUV_420_888:
            case ImageFormat.NV21:
            case ImageFormat.YV12:
                return true;
        }
        return false;
    }

    public byte[] getGrayFromData(Image image, int colorFormat) {
        if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
            throw new IllegalArgumentException("only support COLOR_FormatI420 " + "and COLOR_FormatNV21");
        }
        if (!isImageFormatSupported(image)) {
            throw new RuntimeException("can't convert Image to byte array, format " + image.getFormat());
        }

        Image.Plane[] planes = image.getPlanes();

        int i = 0;

        ByteBuffer buffer = planes[i].getBuffer();

        byte[] data = new byte[buffer.remaining()];
        buffer.get(data, 0, data.length);


        if (VERBOSE) Log.v(TAG, "Finished reading data from plane " + i);

        return data;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private byte[] getDataFromImage(Image image, int colorFormat) {
        if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
            throw new IllegalArgumentException("only support COLOR_FormatI420 " + "and COLOR_FormatNV21");
        }
        if (!isImageFormatSupported(image)) {
            throw new RuntimeException("can't convert Image to byte array, format " + image.getFormat());
        }
        Rect crop = image.getCropRect();
        int format = image.getFormat();
        int width = crop.width();
        int height = crop.height();
        Image.Plane[] planes = image.getPlanes();
        byte[] data = new byte[width * height * ImageFormat.getBitsPerPixel(format) / 8];
        byte[] rowData = new byte[planes[0].getRowStride()];

        int channelOffset = 0;
        int outputStride = 1;
        for (int i = 0; i < planes.length; i++) {
            switch (i) {
                case 0:
                    channelOffset = 0;
                    outputStride = 1;
                    break;
                case 1:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = width * height;
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height;
                        outputStride = 2;
                    }
                    break;
                case 2:
                    if (colorFormat == COLOR_FormatI420) {
                        channelOffset = (int) (width * height * 1.25);
                        outputStride = 1;
                    } else if (colorFormat == COLOR_FormatNV21) {
                        channelOffset = width * height + 1;
                        outputStride = 2;
                    }
                    break;
            }
            ByteBuffer buffer = planes[i].getBuffer();
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();
//            if (VERBOSE) {
//                Log.v(TAG, "pixelStride " + pixelStride);
//                Log.v(TAG, "rowStride " + rowStride);
//                Log.v(TAG, "width " + width);
//                Log.v(TAG, "height " + height);
//                Log.v(TAG, "buffer size " + buffer.remaining());
//            }
            int shift = (i == 0) ? 0 : 1;
            int w = width >> shift;
            int h = height >> shift;
            buffer.position(rowStride * (crop.top >> shift) + pixelStride * (crop.left >> shift));
            for (int row = 0; row < h; row++) {
                int length;
                if (pixelStride == 1 && outputStride == 1) {
                    length = w;
                    buffer.get(data, channelOffset, length);
                    channelOffset += length;
                } else {
                    length = (w - 1) * pixelStride + 1;
                    buffer.get(rowData, 0, length);
                    for (int col = 0; col < w; col++) {
                        data[channelOffset] = rowData[col * pixelStride];
                        channelOffset += outputStride;
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length);
                }
            }
//            if (VERBOSE) Log.v(TAG, "Finished reading data from plane " + i);
        }
        return data;
    }


    private void dumpFile(String fileName, byte[] data) {
        FileOutputStream outStream;
        try {
            outStream = new FileOutputStream(fileName);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to create output file " + fileName, ioe);
        }
        try {
            outStream.write(data);
            outStream.close();
        } catch (IOException ioe) {
            throw new RuntimeException("failed writing data to file " + fileName, ioe);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void compressToJpeg(String fileName, Image image) {
        FileOutputStream outStream;
        try {
            outStream = new FileOutputStream(fileName);
        } catch (IOException ioe) {
            throw new RuntimeException("Unable to create output file " + fileName, ioe);
        }

        Rect rect = image.getCropRect();
        YuvImage yuvImage = new YuvImage(getDataFromImage(image, COLOR_FormatNV21), ImageFormat.NV21, rect.width(), rect.height(), null);
        yuvImage.compressToJpeg(rect, 100, outStream);
    }
}


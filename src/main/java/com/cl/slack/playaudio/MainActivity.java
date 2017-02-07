package com.cl.slack.playaudio;

import android.content.res.AssetFileDescriptor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioRouting;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Environment;
import android.sax.StartElementListener;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.SynchronousQueue;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

/**
 * TODO 没有处理权限问题
 *
 * @author slack
 * @time 17/2/6 下午1:47
 */
public class MainActivity extends AppCompatActivity {

    private static final Object lockBGMusic = new Object();
    private String mp3FilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.mp3";
    private File medicCodecFile = null;
    private MediaPlayer mMediaPlayer;
    private Button mediaPlayerBtn, audioTrackBtn, recodeAudioBtn, playRecodeAudioBtn,
            mediaCodecBtn, playMediaCodecBtn, recodeMixBtn, playNeedMixedBtn, playMixBtn;

    private PlayTask mPlayer;
    private RecordTask mRecorder;
    private PlayPCMTask mPlayPCMTask;
    private RecordMediaCodecTask mRecordMediaCodecTask;
    private RecordMediaCodecByteBufferTask mRecordMediaCodecByteBufferTask;
    private RecordMixTask mRecordMixTask;
    private PlayNeedMixAudioTask mPlayNeedMixAudioTask;
    private File mAudioFile = null;
    private boolean mIsRecording = false, mIsPlaying = false;
    private int mFrequence = 44100;
    private int mChannelConfig = AudioFormat.CHANNEL_IN_MONO;//单音轨 保证能在所有设备上工作
    private int mChannelStereo = AudioFormat.CHANNEL_IN_STEREO;
    private int mPlayChannelConfig = AudioFormat.CHANNEL_OUT_STEREO;
    private int mAudioEncoding = AudioFormat.ENCODING_PCM_16BIT;//一个采样点16比特-2个字节

    private AudioEncoder mAudioEncoder;

    private Queue<byte[]> backGroundBytes = new ArrayDeque<>();
    private boolean mHasFrameBytes;

    private PCMData mPCMData = new PCMData(mp3FilePath);;

    public byte[] getBackGroundBytes() {
        synchronized (lockBGMusic){
            if (backGroundBytes.isEmpty()) {
                return null;
            }
            // poll 如果队列为空，则返回null
            byte[] temp = backGroundBytes.poll();
            if(temp == null){
                mHasFrameBytes = false;
            }
            return temp;
        }
    }

    /**
     * 这样的方式控制同步 需要添加到队列时判断同时在播放和录制
     */
    public void addBackGroundBytes(byte[] bytes) {
        synchronized (lockBGMusic){
            if(mIsPlaying && mIsRecording){
                backGroundBytes.add(bytes);
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mediaPlayerBtn = (Button) findViewById(R.id.media_player);
        audioTrackBtn = (Button) findViewById(R.id.audio_track);
        recodeAudioBtn = (Button) findViewById(R.id.recode_audio);
        playRecodeAudioBtn = (Button) findViewById(R.id.play_recode_audio);
        mediaCodecBtn = (Button) findViewById(R.id.recode_audio_mediacodec);
        playMediaCodecBtn = (Button) findViewById(R.id.play_audio_mediacodec);
        recodeMixBtn = (Button) findViewById(R.id.recode_mix_audio);
        playNeedMixedBtn = (Button) findViewById(R.id.play_bg_audio);
        playMixBtn = (Button) findViewById(R.id.play_mix_audio);

        medicCodecFile = new File(Environment.getExternalStorageDirectory(), "test_media_audio.mp3"); // m4a");
    }

    private void initMediaPlayer(String filePath) {
        releaseMediaPlayer();
        mMediaPlayer = new MediaPlayer();
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                Log.i("slack", "onPrepared...");
                mMediaPlayer.start();
            }
        });
        try {
            mMediaPlayer.setDataSource(filePath);
            mMediaPlayer.prepareAsync();
        } catch (IOException e) {
            e.printStackTrace();
            Log.i("slack", e.getMessage());
        }
    }

    /**
     * MediaPlayer 播放音频
     */
    public void mediaPlayer(View view) {
        if (mediaPlayerBtn.getTag() == null) {
            mediaPlayerBtn.setTag(this);
            mediaPlayerBtn.setText("stop");
            initMediaPlayer(mp3FilePath);
        } else {
            mediaPlayerBtn.setTag(null);
            mediaPlayerBtn.setText("play");
            mMediaPlayer.pause();
        }
    }

    /**
     * AudioTrack 播放音频 mp3 --> pcm data  ( libs/jl1.0.1.jar )
     */
    public void audioTrack(View view) {
        if (audioTrackBtn.getTag() == null) {
            audioTrackBtn.setText("stop");
            audioTrackBtn.setTag(this);
            mPlayer = new PlayTask();
            mPlayer.execute();
        } else {
            audioTrackBtn.setText("play");
            mIsPlaying = false;
            audioTrackBtn.setTag(null);
        }
    }

    /**
     * AudioRecord 录制音频 pcm file
     */
    public void recodeAudio(View view) {
        if (recodeAudioBtn.getTag() == null) {
            recodeAudioBtn.setText("stop");
            recodeAudioBtn.setTag(this);
            try {
                // 创建临时文件,注意这里的格式为.pcm
                mAudioFile = File.createTempFile("test_recording", ".pcm", Environment.getExternalStorageDirectory());
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            mRecorder = new RecordTask();
            mRecorder.execute();
        } else {
            recodeAudioBtn.setText("recode");
            recodeAudioBtn.setTag(null);
            mIsRecording = false;
        }
    }

    /**
     * AudioTrack 播放音频 pcm data
     */
    public void playRecodeAudio(View view) {
        if (mAudioFile == null) {
            return;
        }
        if (playRecodeAudioBtn.getTag() == null) {
            playRecodeAudioBtn.setText("stop");
            playRecodeAudioBtn.setTag(this);
            mPlayPCMTask = new PlayPCMTask();
            mPlayPCMTask.execute();
        } else {
            playRecodeAudioBtn.setText("play");
            mIsPlaying = false;
            playRecodeAudioBtn.setTag(null);
        }
    }

    /**
     * AudioRecord 录制音频 use MediaCodec & MediaMuxer write data
     */
    public void mediaCodec(View view) {
        if (mediaCodecBtn.getTag() == null) {
            mediaCodecBtn.setText("stop");
            mediaCodecBtn.setTag(this);
            mAudioEncoder = new AudioEncoder(medicCodecFile.getAbsolutePath());
            mAudioEncoder.prepareEncoder();
//            mRecordMediaCodecTask = new RecordMediaCodecTask();
//            mRecordMediaCodecTask.execute();
            mRecordMediaCodecByteBufferTask = new RecordMediaCodecByteBufferTask();
            mRecordMediaCodecByteBufferTask.execute();
        } else {
            mediaCodecBtn.setText("recode");
            mediaCodecBtn.setTag(null);
            mIsRecording = false;
            mAudioEncoder.stop();
        }
    }

    public void playMediaCodec(View view) {
        if (playMediaCodecBtn.getTag() == null) {
            playMediaCodecBtn.setTag(this);
            playMediaCodecBtn.setText("stop");
            initMediaPlayer(medicCodecFile.getAbsolutePath());
        } else {
            playMediaCodecBtn.setTag(null);
            playMediaCodecBtn.setText("play");
            mMediaPlayer.pause();
        }
    }

    public void playNeedMixedAudio(View view) {
        if (playNeedMixedBtn.getTag() == null) {
            playNeedMixedBtn.setText("stop");
            playNeedMixedBtn.setTag(this);
            mHasFrameBytes = false;
            initPCMData();
            initMixAudioPlayer();
        } else {
            playNeedMixedBtn.setText("play");
            mIsPlaying = false;
            playNeedMixedBtn.setTag(null);
            mPCMData.release();
        }
    }

    private void initMixAudioPlayer() {
        mPlayNeedMixAudioTask = new PlayNeedMixAudioTask(new BackGroundFrameListener() {

            @Override
            public void onFrameArrive(byte[] bytes) {
                mHasFrameBytes = true;
                addBackGroundBytes(bytes);
            }
        });
        mPlayNeedMixAudioTask.start();
    }

    /**
     * 解析 mp3 --> pcm
     */
    private void initPCMData() {
        mPCMData.startPcmExtractor();
    }

    /**
     * 混合音频
     */
    public void recodeMixAudio(View view) {
        if (recodeMixBtn.getTag() == null) {
            recodeMixBtn.setText("stop");
            recodeMixBtn.setTag(this);
            mAudioEncoder = new AudioEncoder(medicCodecFile.getAbsolutePath());
            mAudioEncoder.prepareEncoder();
//            // 测试 发现直接写入 播放的数据不清晰 猜测是 MediaFormat
//            if(mPCMData.getMediaFormat() == null){
//                // 先进行录制 再背景音乐播放
//                mAudioEncoder.prepareEncoder();
//            }else {
//                // just test mPCMData.getMediaFormat() may null  背景音乐先播放 再进行录制
//                // 录制的速率 太快
//                mAudioEncoder.prepareEncoder(mPCMData.getMediaFormat());
//            }
            mRecordMixTask = new RecordMixTask();
            mRecordMixTask.execute();
        } else {
            recodeMixBtn.setText("recode");
            recodeMixBtn.setTag(null);
            mIsRecording = false;
            mAudioEncoder.stop();
            mRecordMixTask.cancel(true);
        }
    }

    public void playMixAudio(View view) {
        if (playMixBtn.getTag() == null) {
            playMixBtn.setTag(this);
            playMixBtn.setText("stop");
            initMediaPlayer(medicCodecFile.getAbsolutePath());
        } else {
            playMixBtn.setTag(null);
            playMixBtn.setText("play");
            mMediaPlayer.pause();
        }
    }

    private static int[] mSampleRates = new int[]{8000, 11025, 22050, 44100};

    public AudioRecord findAudioRecord() {
        for (int rate : mSampleRates) {
            for (short audioFormat : new short[]{AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_16BIT}) {
                for (short channelConfig : new short[]{AudioFormat.CHANNEL_IN_MONO, AudioFormat.CHANNEL_IN_STEREO}) {
                    try {
                        Log.i("slack", "Attempting rate " + rate + "Hz, bits: " + audioFormat + ", channel: "
                                + channelConfig);
                        int bufferSize = AudioRecord.getMinBufferSize(rate, channelConfig, audioFormat);

                        if (bufferSize != AudioRecord.ERROR_BAD_VALUE) {
                            // check if we can instantiate and have a success
                            AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, rate, channelConfig, audioFormat, bufferSize);

                            if (recorder.getState() == AudioRecord.STATE_INITIALIZED)
                                return recorder;
                        }
                    } catch (Exception e) {
                        Log.e("slack", rate + "Exception, keep trying.", e);
                    }
                }
            }
        }
        return null;
    }

    class RecordTask extends AsyncTask<Void, Integer, Void> {
        @Override
        protected Void doInBackground(Void... arg0) {
            mIsRecording = true;
            try {
                // 开通输出流到指定的文件
                DataOutputStream dos = new DataOutputStream(
                        new BufferedOutputStream(
                                new FileOutputStream(mAudioFile)));
                // 根据定义好的几个配置，来获取合适的缓冲大小
                int bufferSize = AudioRecord.getMinBufferSize(mFrequence,
                        mChannelStereo, mAudioEncoding);
                // 实例化AudioRecord
//                AudioRecord record = findAudioRecord();
                AudioRecord record = new AudioRecord(
                        MediaRecorder.AudioSource.MIC, mFrequence,
                        mChannelConfig, mAudioEncoding, bufferSize);
                // 定义缓冲
                short[] buffer = new short[bufferSize];


                // 开始录制
                record.startRecording();


                int r = 0; // 存储录制进度
                // 定义循环，根据isRecording的值来判断是否继续录制
                while (mIsRecording) {
                    // 从bufferSize中读取字节，返回读取的short个数
                    int bufferReadResult = record
                            .read(buffer, 0, buffer.length);
                    // 循环将buffer中的音频数据写入到OutputStream中
                    for (int i = 0; i < bufferReadResult; i++) {
                        dos.writeShort(buffer[i]);
                    }
                    publishProgress(new Integer(r)); // 向UI线程报告当前进度
                    r++; // 自增进度值
                }
                // 录制结束
                record.stop();
                Log.i("slack", "::" + mAudioFile.length());
                dos.close();
            } catch (Exception e) {
                // TODO: handle exception
                Log.e("slack", "::" + e.getMessage());
            }
            return null;
        }


        // 当在上面方法中调用publishProgress时，该方法触发,该方法在UI线程中被执行
        protected void onProgressUpdate(Integer... progress) {
            //
        }


        protected void onPostExecute(Void result) {

        }

    }

    class PlayTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... arg0) {
            mIsPlaying = true;
            Decoder mDecoder = new Decoder();
            try {
                int bufferSize = AudioTrack.getMinBufferSize(mFrequence,
                        mPlayChannelConfig, mAudioEncoding);
                short[] buffer = new short[bufferSize];
                // 定义输入流，将音频写入到AudioTrack类中，实现播放
                FileInputStream fin = new FileInputStream(mp3FilePath);
                Bitstream bitstream = new Bitstream(fin);
                // 实例AudioTrack
                AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC,
                        mFrequence,
                        mPlayChannelConfig, mAudioEncoding, bufferSize,
                        AudioTrack.MODE_STREAM);
                // 开始播放
                track.play();
                // 由于AudioTrack播放的是流，所以，我们需要一边播放一边读取
                Header header;
                while (mIsPlaying && (header = bitstream.readFrame()) != null) {
                    SampleBuffer sampleBuffer = (SampleBuffer) mDecoder.decodeFrame(header, bitstream);
                    buffer = sampleBuffer.getBuffer();
                    track.write(buffer, 0, buffer.length);
                    bitstream.closeFrame();
                }

                // 播放结束
                track.stop();
                track.release();
                fin.close();
            } catch (Exception e) {
                // TODO: handle exception
                Log.e("slack", "error:" + e.getMessage());
            }
            return null;
        }


        protected void onPostExecute(Void result) {

        }


        protected void onPreExecute() {

        }
    }

    class PlayPCMTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... arg0) {
            mIsPlaying = true;
            int bufferSize = AudioTrack.getMinBufferSize(mFrequence,
                    mPlayChannelConfig, mAudioEncoding);
            short[] buffer = new short[bufferSize];
            try {
                // 定义输入流，将音频写入到AudioTrack类中，实现播放
                DataInputStream dis = new DataInputStream(
                        new BufferedInputStream(new FileInputStream(mAudioFile)));
                // 实例AudioTrack
                // AudioTrack AudioFormat.CHANNEL_IN_STEREO here may some problem
                AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC,
                        mFrequence,
                        AudioFormat.CHANNEL_IN_STEREO, mAudioEncoding, bufferSize,
                        AudioTrack.MODE_STREAM);
                // 开始播放
                track.play();
                // 由于AudioTrack播放的是流，所以，我们需要一边播放一边读取
                while (mIsPlaying && dis.available() > 0) {
                    int i = 0;
                    while (dis.available() > 0 && i < buffer.length) {
                        buffer[i] = dis.readShort();
                        i++;
                    }
                    // 然后将数据写入到AudioTrack中
                    track.write(buffer, 0, buffer.length);
                }


                // 播放结束
                track.stop();
                dis.close();
            } catch (Exception e) {
                // TODO: handle exception
                Log.e("slack", "error:" + e.getMessage());
            }
            return null;
        }


        protected void onPostExecute(Void result) {

        }


        protected void onPreExecute() {

        }
    }

    /**
     * use byte[]
     */
    class RecordMediaCodecTask extends AsyncTask<Void, Integer, Void> {
        @Override
        protected Void doInBackground(Void... arg0) {
            mIsRecording = true;
            int samples_per_frame = 2048;
            int bufferReadResult = 0;
            long audioPresentationTimeNs; //音频时间戳 pts
            try {
                // 根据定义好的几个配置，来获取合适的缓冲大小
                int bufferSize = AudioRecord.getMinBufferSize(mFrequence,
                        mChannelConfig, mAudioEncoding);
                // 实例化AudioRecord
                AudioRecord record = new AudioRecord(
                        MediaRecorder.AudioSource.MIC, mFrequence,
                        mChannelConfig, mAudioEncoding, bufferSize);
//                record.setRecordPositionUpdateListener(new AudioRecord.OnRecordPositionUpdateListener() {
//                    @Override
//                    public void onMarkerReached(AudioRecord recorder) {
//
//                    }
//
//                    @Override
//                    public void onPeriodicNotification(AudioRecord recorder) {
//
//                    }
//                });
                // 定义缓冲
                byte[] buffer = new byte[samples_per_frame];// byte size need less than MediaFormat.KEY_MAX_INPUT_SIZE

                // 开始录制
                record.startRecording();

                while (mIsRecording) {
                    // 从bufferSize中读取字节，返回读取的short个数
                    audioPresentationTimeNs = System.nanoTime();
                    //从缓冲区中读取数据，存入到buffer字节数组数组中
                    bufferReadResult = record.read(buffer, 0, samples_per_frame);
                    //判断是否读取成功
                    if (bufferReadResult == AudioRecord.ERROR_BAD_VALUE || bufferReadResult == AudioRecord.ERROR_INVALID_OPERATION)
                        Log.e("slack", "Read error");
                    if (mAudioEncoder != null) {
                        //将音频数据发送给AudioEncoder类进行编码
                        mAudioEncoder.offerAudioEncoder(buffer, audioPresentationTimeNs);
                    }

                }
                // 录制结束
                if (record != null) {
                    record.setRecordPositionUpdateListener(null);
                    record.stop();
                    record.release();
                    record = null;
                }

            } catch (Exception e) {
                // TODO: handle exception
                Log.e("slack", "::" + e.getMessage());
            }
            return null;
        }


        // 当在上面方法中调用publishProgress时，该方法触发,该方法在UI线程中被执行
        protected void onProgressUpdate(Integer... progress) {
            //
        }


        protected void onPostExecute(Void result) {

        }

    }

    /**
     * use ByteBuffer
     */
    class RecordMediaCodecByteBufferTask extends AsyncTask<Void, Integer, Void> {
        @Override
        protected Void doInBackground(Void... arg0) {
            mIsRecording = true;
            int samples_per_frame = 2048;// SAMPLES_PER_FRAME
            int bufferReadResult = 0;
            long audioPresentationTimeNs; //音频时间戳 pts
            try {
                // 根据定义好的几个配置，来获取合适的缓冲大小
                int bufferSize = AudioRecord.getMinBufferSize(mFrequence,
                        mChannelConfig, mAudioEncoding);
                // 实例化AudioRecord
                AudioRecord record = new AudioRecord(
                        MediaRecorder.AudioSource.MIC, mFrequence,
                        mChannelConfig, mAudioEncoding, bufferSize);
//                record.setRecordPositionUpdateListener(new AudioRecord.OnRecordPositionUpdateListener() {
//                    @Override
//                    public void onMarkerReached(AudioRecord recorder) {
//
//                    }
//
//                    @Override
//                    public void onPeriodicNotification(AudioRecord recorder) {
//
//                    }
//                });
                // 定义缓冲
                int readBytes;
                ByteBuffer buf = ByteBuffer.allocateDirect(samples_per_frame);

                // 开始录制
                record.startRecording();

                while (mIsRecording) {
                    // 从bufferSize中读取字节，返回读取的short个数
                    audioPresentationTimeNs = System.nanoTime();
                    //从缓冲区中读取数据，存入到buffer字节数组数组中
                    // read audio data from internal mic
                    buf.clear();
                    bufferReadResult = record.read(buf, samples_per_frame);
                    //判断是否读取成功
                    if (bufferReadResult == AudioRecord.ERROR || bufferReadResult == AudioRecord.ERROR_BAD_VALUE ||
                            bufferReadResult == AudioRecord.ERROR_INVALID_OPERATION)
                        Log.e("slack", "Read error");
                    if (mAudioEncoder != null) {
                        //将音频数据发送给AudioEncoder类进行编码
                        buf.position(bufferReadResult).flip();
                        mAudioEncoder.offerAudioEncoder(buf, audioPresentationTimeNs, bufferReadResult);
                    }

                }
                // 录制结束
                if (record != null) {
                    record.setRecordPositionUpdateListener(null);
                    record.stop();
                    record.release();
                    record = null;
                }

            } catch (Exception e) {
                // TODO: handle exception
                Log.e("slack", "::" + e.getMessage());
            }
            return null;
        }


        // 当在上面方法中调用publishProgress时，该方法触发,该方法在UI线程中被执行
        protected void onProgressUpdate(Integer... progress) {
            //
        }


        protected void onPostExecute(Void result) {

        }

    }

    class RecordMixTask extends AsyncTask<Void, Integer, Void> {
        @Override
        protected Void doInBackground(Void... arg0) {
            Log.i("thread", "RecordMixTask: " + Thread.currentThread().getId());
            mIsRecording = true;
            int bufferReadResult = 0;
            long audioPresentationTimeNs; //音频时间戳 pts
            try {
                // 根据定义好的几个配置，来获取合适的缓冲大小
                int bufferSize = AudioRecord.getMinBufferSize(mFrequence,
                        mChannelConfig, mAudioEncoding);
                // 实例化AudioRecord
                AudioRecord record = new AudioRecord(
                        MediaRecorder.AudioSource.MIC, mFrequence,
                        mChannelConfig, mAudioEncoding, bufferSize * 4);

                // 开始录制
                record.startRecording();

                while (mIsRecording) {

                    audioPresentationTimeNs = System.nanoTime();

                    int samples_per_frame = mPCMData.getBufferSize(); // 这里需要与 背景音乐读取出来的数据长度 一样
                    byte[] buffer = new byte[samples_per_frame];
                    //从缓冲区中读取数据，存入到buffer字节数组数组中
                    bufferReadResult = record.read(buffer, 0, buffer.length);
                    //判断是否读取成功
                    if (bufferReadResult == AudioRecord.ERROR_BAD_VALUE || bufferReadResult == AudioRecord.ERROR_INVALID_OPERATION)
                        Log.e("slack", "Read error");
                    if (mAudioEncoder != null) {
//                        Log.i("slack","buffer length: " + buffer.length + " " + bufferReadResult + " " + bufferSize);
                        buffer = mixBuffer(buffer);
                        //将音频数据发送给AudioEncoder类进行编码
                        mAudioEncoder.offerAudioEncoder(buffer, audioPresentationTimeNs);
                    }

                }
                // 录制结束
                if (record != null) {
                    record.setRecordPositionUpdateListener(null);
                    record.stop();
                    record.release();
                    record = null;
                }

            } catch (Exception e) {
                // TODO: handle exception
                Log.e("slack", "::" + e.getMessage());
            }
            return null;
        }


        // 当在上面方法中调用publishProgress时，该方法触发,该方法在UI线程中被执行
        protected void onProgressUpdate(Integer... progress) {
            //
        }


        protected void onPostExecute(Void result) {

        }

    }

    /**
     * 虽然可以新建多个 AsyncTask的子类的实例，但是AsyncTask的内部Handler和ThreadPoolExecutor都是static的，
     * 这么定义的变 量属于类的，是进程范围内共享的，所以AsyncTask控制着进程范围内所有的子类实例，
     * 而且该类的所有实例都共用一个线程池和Handler
     * 这里新开一个线程
     * 自己解析出来 pcm data
     */
    class PlayNeedMixAudioTask extends Thread {

        private BackGroundFrameListener listener;
        private long audioPresentationTimeNs; //音频时间戳 pts

        public PlayNeedMixAudioTask(BackGroundFrameListener l) {
            listener = l;
        }

        @Override
        public void run() {
            Log.i("thread", "PlayNeedMixAudioTask: " + Thread.currentThread().getId());
            mIsPlaying = true;
            try {
                int bufferSize = AudioTrack.getMinBufferSize(mFrequence,
                        mPlayChannelConfig, mAudioEncoding);
                // 实例AudioTrack
                AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC,
                        mFrequence,
                        mPlayChannelConfig, mAudioEncoding, bufferSize,
                        AudioTrack.MODE_STREAM);
                // 开始播放
                track.play();

                while (mIsPlaying) {
                    audioPresentationTimeNs = System.nanoTime();
                    byte[] temp = mPCMData.getPCMData();
                    if (temp == null) {
                        continue;
                    }
                    track.write(temp, 0, temp.length);
                    if (listener != null) {
                        listener.onFrameArrive(temp);
                    }
                }

                mHasFrameBytes = false;
                track.stop();
                track.release();
            } catch (Exception e) {
                // TODO: handle exception
                Log.e("slack", "error:" + e.getMessage());
            }
        }
    }

    /**
     * 混合 音频
     */
    private byte[] mixBuffer(byte[] buffer) {
        if(mIsPlaying && mHasFrameBytes){
//            return getBackGroundBytes(); // 直接写入背景音乐数据
            return averageMix(new byte[][]{buffer,getBackGroundBytes()});
        }
        return buffer;
    }

    /**
     * 采用简单的平均算法 average audio mixing algorithm
     * 测试发现这种算法会降低 录制的音量
     */
    private byte[] averageMix(byte[][] bMulRoadAudioes) {

        if (bMulRoadAudioes == null || bMulRoadAudioes.length == 0)
            return null;
        byte[] realMixAudio = bMulRoadAudioes[0];

        if (bMulRoadAudioes.length == 1)
            return realMixAudio;

        for (int rw = 0; rw < bMulRoadAudioes.length; ++rw) {
            if (bMulRoadAudioes[rw].length != realMixAudio.length) {
                Log.e("app", "column of the road of audio + " + rw + " is diffrent.");
                return null;
            }
        }

        int row = bMulRoadAudioes.length;
        int coloum = realMixAudio.length / 2;
        short[][] sMulRoadAudioes = new short[row][coloum];
        for (int r = 0; r < row; ++r) {
            for (int c = 0; c < coloum; ++c) {
                sMulRoadAudioes[r][c] = (short) ((bMulRoadAudioes[r][c * 2] & 0xff) | (bMulRoadAudioes[r][c * 2 + 1] & 0xff) << 8);
            }
        }
        short[] sMixAudio = new short[coloum];
        int mixVal;
        int sr = 0;
        for (int sc = 0; sc < coloum; ++sc) {
            mixVal = 0;
            sr = 0;
            for (; sr < row; ++sr) {
                mixVal += sMulRoadAudioes[sr][sc];
            }
            sMixAudio[sc] = (short) (mixVal / row);
        }
        for (sr = 0; sr < coloum; ++sr) {
            realMixAudio[sr * 2] = (byte) (sMixAudio[sr] & 0x00FF);
            realMixAudio[sr * 2 + 1] = (byte) ((sMixAudio[sr] & 0xFF00) >> 8);
        }
        return realMixAudio;
    }


    public interface BackGroundFrameListener {
        void onFrameArrive(byte[] bytes);
    }

    private void releaseMediaPlayer() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        releaseMediaPlayer();
        mIsPlaying = false;
        mIsRecording = false;
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}

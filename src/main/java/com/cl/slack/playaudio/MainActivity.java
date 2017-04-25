package com.cl.slack.playaudio;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.cl.slack.playaudio.audio.AudioEncoder;
import com.cl.slack.playaudio.audio.MixAudioInVideo;
import com.cl.slack.playaudio.audio.PlayBackMusic;
import com.cl.slack.playaudio.media.MediaMixAudio;
import com.cl.slack.playaudio.permission.PermissionsManager;
import com.cl.slack.playaudio.permission.PermissionsResultAction;
import com.cl.slack.playaudio.util.BytesTransUtil;
import com.cl.slack.playaudio.util.FileUtil;
import com.cl.slack.playaudio.video.VideoDecoder;
import com.cl.slack.playaudio.video.VideoEncodeDecode;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

/**
 * @author slack
 * @time 17/2/6 下午1:47
 */
public class MainActivity extends AppCompatActivity {

    private String mp3FilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.mp3";
    private String mp4FilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.mp4";
    private File medicCodecFile = null;
    private MediaPlayer mMediaPlayer;
    private Button mediaPlayerBtn, audioTrackBtn, recodeAudioBtn, playRecodeAudioBtn,
            mediaCodecBtn, playMediaCodecBtn, recodeMixBtn, playNeedMixedBtn, playMixBtn,
            videoAudioWithPlayBtn, videoAudioWithoutPlayBtn,videoMixAudio;

    private RecordMediaCodecTask mRecordMediaCodecTask;
    private RecordMixTask mRecordMixTask;
    private File mAudioFile = null;
    private boolean mIsRecording = false, mIsPlaying = false;
    private int mFrequence = 44100;
    private int mChannelConfig = AudioFormat.CHANNEL_IN_MONO;//单音轨 保证能在所有设备上工作
    private int mChannelStereo = AudioFormat.CHANNEL_IN_STEREO;
    private int mPlayChannelConfig = AudioFormat.CHANNEL_OUT_STEREO;
    private int mAudioEncoding = AudioFormat.ENCODING_PCM_16BIT;//一个采样点16比特-2个字节

    private AudioEncoder mAudioEncoder;


    private PlayBackMusic mPlayBackMusic = new PlayBackMusic(mp3FilePath);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initPermission();

        mediaPlayerBtn = (Button) findViewById(R.id.media_player);
        audioTrackBtn = (Button) findViewById(R.id.audio_track);
        recodeAudioBtn = (Button) findViewById(R.id.recode_audio);
        playRecodeAudioBtn = (Button) findViewById(R.id.play_recode_audio);
        mediaCodecBtn = (Button) findViewById(R.id.recode_audio_mediacodec);
        playMediaCodecBtn = (Button) findViewById(R.id.play_audio_mediacodec);
        recodeMixBtn = (Button) findViewById(R.id.recode_mix_audio);
        playNeedMixedBtn = (Button) findViewById(R.id.play_bg_audio);
        playMixBtn = (Button) findViewById(R.id.play_mix_audio);
        videoAudioWithPlayBtn = (Button) findViewById(R.id.mix_audio_in_video_with_play);
        videoAudioWithoutPlayBtn = (Button) findViewById(R.id.mix_audio_in_video_without_play);
        videoMixAudio = (Button) findViewById(R.id.video_mix_audio);

        medicCodecFile = new File(Environment.getExternalStorageDirectory(), "test_media_audio.mp3"); // m4a");
    }

    private void initPermission() {
        PermissionsManager.getInstance().requestAllManifestPermissionsIfNecessary(this, new PermissionsResultAction() {
            @Override
            public void onGranted() {
                Toast.makeText(MainActivity.this, "All permissions have been granted.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDenied(String permission) {
                String message = "Permission " + permission + " has been denied.";
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
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
            PlayTask mPlayer = new PlayTask();
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
            RecordTask mRecorder = new RecordTask();
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
            PlayPCMTask mPlayPCMTask = new PlayPCMTask();
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
            mRecordMediaCodecTask = new RecordMediaCodecTask();
            mRecordMediaCodecTask.execute();
//            RecordMediaCodecByteBufferTask mRecordMediaCodecByteBufferTask = new RecordMediaCodecByteBufferTask();
//            mRecordMediaCodecByteBufferTask.execute();
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
            mPlayBackMusic.release();
            mPlayBackMusic.startPlayBackMusic();
        } else {
            playNeedMixedBtn.setText("play");
            playNeedMixedBtn.setTag(null);
            mPlayBackMusic.stop();
        }
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
            if (mPlayBackMusic != null) {
                mPlayBackMusic.setNeedRecodeDataEnable(true);
            }
        } else {
            recodeMixBtn.setText("recode");
            recodeMixBtn.setTag(null);
            if (mPlayBackMusic != null) {
                mPlayBackMusic.setNeedRecodeDataEnable(false);
            }
            // feed all data done code in RecordMixTask
            mIsRecording = false;
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

    /**
     * 混合视频中的 音频 ，混合播放中的背景音乐
     * 播出来多少，写入多少 需要 initMixAudioPlayer() 配合
     */
    MixAudioInVideo mMixAudioInVideo = new MixAudioInVideo(mp4FilePath);

    VideoEncodeDecode mVideoEncodeDecode = new VideoEncodeDecode();

    public void mixAudioInVideoWithPlay(View view) {

        switch (view.getId()) {
            // 先播放
            case R.id.mix_audio_in_video_with_play_btn:
                // 见鬼了，解析 mp4音乐作为播放音乐，录制时没有问题，使用 mp3 就有些问题
                mMixAudioInVideo.playBackMusic(mp3FilePath) // mp4FilePath
                        .setMixListener(new MixAudioInVideo.MixListener() {
                            @Override
                            public void onFinished() {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this, "mix success ", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }
                        });

                break;
            case R.id.mix_audio_in_video_with_play:
                if (videoAudioWithPlayBtn.getTag() == null) {
                    videoAudioWithPlayBtn.setTag(this);
                    videoAudioWithPlayBtn.setText("stop");
//                    mMixAudioInVideo.startMixAudioInVideoWithPlay();// 视频帧不处理

                    mVideoEncodeDecode.videoCodecPrepare(mp4FilePath);
                    mVideoEncodeDecode.videoEncodeDecodeLoop();

                } else {
                    videoAudioWithPlayBtn.setTag(null);
                    videoAudioWithPlayBtn.setText("start");
//                    mMixAudioInVideo.stop();
                    mVideoEncodeDecode.close();
                }
                break;
        }

    }

    /**
     * 混合视频中的 音频 ，混合选中的背景音乐
     * 这里需要做个判断，这两个文件的长度肯定是不一样的
     * 如果视频中的长度长，写入的 背景音乐 有循环播放 和 播放一次的设置
     */
    public void mixAudioInVideoWithoutPlay(View view) {
        if (videoAudioWithoutPlayBtn.getTag() == null) {
            videoAudioWithoutPlayBtn.setTag(this);
            videoAudioWithoutPlayBtn.setText("stop");
            mMixAudioInVideo = new MixAudioInVideo(mp4FilePath);
            mMixAudioInVideo.setMixListener(new MixAudioInVideo.MixListener() {
                @Override
                public void onFinished() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "mix success ", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
            mMixAudioInVideo.startMixAudioInVideoWithoutPlay(mp3FilePath, true);// 视频帧不处理
        } else {
            videoAudioWithoutPlayBtn.setTag(null);
            videoAudioWithoutPlayBtn.setText("start");
            mMixAudioInVideo.stop();
        }

    }

    /**
     * video --> images
     * jpg is ok ,however yuv I can not open
     */
    public void videoGetImages(View view) {
        VideoDecoder videodecoder=new VideoDecoder();
        try {
            videodecoder.videoDecodePrepare(mp4FilePath);
            videodecoder.setSaveFrames(FileUtil.INSTANCE.getSdcardFileDir().getAbsolutePath(),3);
            videodecoder.startDecodeFramesToImage();
        } catch (IOException e) {
            e.printStackTrace();
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

    MediaMixAudio mMediaMixAudio;
    public void videoMixAudio(View view) {
        if (videoMixAudio.getTag() == null) {
            videoMixAudio.setText("stop");
            videoMixAudio.setTag(this);
            mMediaMixAudio = new MediaMixAudio(mp4FilePath,mp3FilePath,
                    new File(Environment.getExternalStorageDirectory(),"test_out.mp4").toString());
            mMediaMixAudio.start();
        } else {
            videoMixAudio.setText("start");
            videoMixAudio.setTag(null);
            mMediaMixAudio.stop();
        }
    }


    /**
     * 录制 声音小 杂音大
     */
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
                    // try 提高音量 但是会加入噪音
//                    buffer = BytesTransUtil.INSTANCE.adjustVoice(buffer,5);
                    // try 消除噪音 ，貌似是有用的，但是音量更低了
//                    BytesTransUtil.INSTANCE.noiseClear(buffer,0,bufferReadResult);
                    // 循环将buffer中的音频数据写入到OutputStream中
                    for (int i = 0; i < bufferReadResult; i++) {
                        dos.writeShort(buffer[i]);
                    }
                    publishProgress(r); // 向UI线程报告当前进度
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
            Log.i("slack", "onProgressUpdate:" + progress[0]);
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
                track.setStereoVolume(1.0f, 1.0f);//设置当前音量大小
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

        AudioRecord audioRecord;
        int bufferReadResult = 0;
        long audioPresentationTimeNs; //音频时间戳 pts

        public RecordMixTask() {
            // 根据定义好的几个配置，来获取合适的缓冲大小
            int bufferSize = AudioRecord.getMinBufferSize(mFrequence,
                    mChannelConfig, mAudioEncoding);
            // 实例化AudioRecord
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC, mFrequence,
                    mChannelConfig, mAudioEncoding, bufferSize * 4);
        }

        @Override
        protected Void doInBackground(Void... arg0) {
            Log.i("thread", "RecordMixTask: " + Thread.currentThread().getId());
            mIsRecording = true;

            try {

                // 开始录制
                audioRecord.startRecording();

                while (mIsRecording) {
                    writeMixData();
                }
                // 录制结束 停止接收麦克风数据
                audioRecord.stop();
                audioRecord.release();

            } catch (Exception e) {
                // TODO: handle exception
                Log.e("slack", "::" + e.getMessage());
            }finally {
                feedAllData();
                mAudioEncoder.stop();
            }
            return null;
        }

        private void writeMixData(){
            audioPresentationTimeNs = System.nanoTime();

            int samples_per_frame = mPlayBackMusic.getBufferSize(); // 这里需要与 背景音乐读取出来的数据长度 一样
            byte[] buffer = new byte[samples_per_frame];
            //从缓冲区中读取数据，存入到buffer字节数组数组中
            bufferReadResult = audioRecord.read(buffer, 0, buffer.length);
            //判断是否读取成功
            if (bufferReadResult < 0)
                Log.e("slack", "Read error");
            if (mAudioEncoder != null) {
                buffer = mixBuffer(buffer);
                //将音频数据发送给AudioEncoder类进行编码
                mAudioEncoder.offerAudioEncoder(buffer, audioPresentationTimeNs);
            }
        }

        /**
         *  这部分写入的 有问题，播放时 太快
         *  解决 ： 我靠 sleep 一下 就好了  20ms is OK
         *  录制出来的 时间 短 ，用户 录制 10 s，实际上写入的数据不足10秒
         * mAudioEncoder.stop(); 之前  应该先把需要写入的数据消费完
         */
        private void feedAllData() {
            while (mAudioEncoder != null && mPlayBackMusic.hasFrameBytes()){
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                audioPresentationTimeNs = System.nanoTime();
                Log.e("slack", "feedAllData ... " + audioPresentationTimeNs);
                mAudioEncoder.offerAudioEncoder(mPlayBackMusic.getBackGroundBytes(), audioPresentationTimeNs);
            }
        }


        /**
         * 混合 音频
         */
        private byte[] mixBuffer(byte[] buffer) {
//            return mPlayBackMusic.getBackGroundBytes(); // 直接写入背景音乐数据
            if (mPlayBackMusic.hasFrameBytes()) {
                return BytesTransUtil.INSTANCE.averageMix(buffer, mPlayBackMusic.getBackGroundBytes());
            }
            return buffer;
        }

        // 当在上面方法中调用publishProgress时，该方法触发,该方法在UI线程中被执行
        protected void onProgressUpdate(Integer... progress) {
            //
        }


        protected void onPostExecute(Void result) {

        }

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
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}

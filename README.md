try 录制audio时录制麦克风数据 和 写入背景音乐

``` 实现
＊MediaPlayer 播放音频
＊AudioTrack 播放音频 mp3 --> pcm data  ( libs/jl1.0.1.jar )
＊AudioRecord 录制音频 pcm file
＊AudioTrack 播放音频 pcm data
＊AudioRecord 录制音频 use MediaCodec & MediaMuxer write data
＊混合音频

```error
E/MPEG4Writer: timestampUs 6220411 < lastTimestampUs 6220442 for Audio track
这个问题与 record.read(buffer,0,samples_per_frame); samples_per_frame 参数的设置有关
int samples_per_frame = 2048; 但是开一个背景音乐，好像就嫌大了 so    int samples_per_frame = 1024;
与mAudioCodec.queueInputBuffer(inputBufferIndex, 0, input.length, presentationTimeUs, 0);中presentationTimeUs貌似也有关系
so    long presentationTimeUs = (System.nanoTime() - audioStartTime) / 1000L;
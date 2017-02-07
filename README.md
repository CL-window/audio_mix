try 录制audio时录制麦克风数据 和 写入背景音乐
没有处理权限的问题，需要先去设置里获取权限

``` 实现
＊MediaPlayer 播放音频
＊AudioTrack 播放音频 mp3 --> pcm data  ( libs/jl1.0.1.jar )
＊AudioRecord 录制音频 pcm file
＊AudioTrack 播放音频 pcm data
＊AudioRecord 录制音频 use MediaCodec & MediaMuxer write data
＊混合音频

混合音频：
 即背景播放的音乐和麦克风获取到的数据混合，混合时要保证这两个的一帧的数据长度一样
 获取麦克风数据时 长度 2048 没有问题，再大一点 就会出 timestampUs 问题
 背景音乐的播放 帧长度 2492 4608 4608 4608...(仅我的测试用例) MediaCodec.BufferInfo.size
 但是一帧数据长度小于 这个值，播放时 杂音很大，而且播放速度不对 ，看来播放这块是动不了了

```error
E/MPEG4Writer: timestampUs 6220411 < lastTimestampUs 6220442 for Audio track
这个问题与 record.read(buffer,0,samples_per_frame); samples_per_frame 参数的设置有关
int samples_per_frame = 2048; 但是开一个背景音乐，好像就嫌大了 so    int samples_per_frame = 1024;
与mAudioCodec.queueInputBuffer(inputBufferIn
dex, 0, input.length, presentationTimeUs, 0);中presentationTimeUs貌似也有关系
so    long presentationTimeUs = (System.nanoTime() - audioStartTime) / 1000L;
但是只要提高 samples_per_frame  必然出错
只能记录上一次的时间戳 然后加入判断 if(mLastAudioPresentationTimeUs < bufferInfo.presentationTimeUs)
这样  samples_per_frame 就可以随便设置了
package com.EasyMovieTexture;


import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by lihongsheng on 10/17/17.
 */

public class MyMediaCodec {
    private static final String SAMPLE = Environment.getExternalStorageDirectory() + "/viking.mp4";
    public MediaExtractor extractor;
    private MediaCodec decoder = null;
    public Surface renderSurface;
    private ByteBuffer[] inputBuffers = null;
    private ByteBuffer[] outputBuffers = null;
    private BufferInfo info;
    private ReadThread mReadThread = null;
    private DisplayThread mRDisplayThread = null;
    private boolean isPlayerStop = false;

    private static final String TAG = "MyMediaCodec";

    MyMediaCodec(){
        extractor = new MediaExtractor();
    }

    public void play(){
        //For init
        Init();
        //readThread
        mReadThread = new ReadThread();
        mReadThread.start();
        //DisplayThread
        mRDisplayThread = new DisplayThread();
        mRDisplayThread.start();
    }

    private void setDataSource(String str)
    {
//        extractor.setDataSource(str);
    }

    public void stop(){
        isPlayerStop = true;
    }

    public void resume(){
        isPlayerStop = false;
    }

    public void setSurface(Surface surface){
        renderSurface = surface;
        if(renderSurface != null)
        {
            Log.d(TAG,"renderSurface != null  1");
        }
    }

    private void Init(){
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                try {
                    extractor.selectTrack(i);
                    decoder = MediaCodec.createDecoderByType(mime);
                    if(renderSurface != null)
                    {
                        Log.d(TAG,"renderSurface != null  2");
                    }
                    Log.d(TAG,"s=======s=======s=======");
                    decoder.configure(format, renderSurface, null, 0);
                    Log.d(TAG,"s=======s=======s=======");
                }catch (IOException e)
                {
                    e.printStackTrace();
                }
                break;
            }
        }
        if (decoder == null) {
            Log.e("DecodeActivity", "Can't find video info!");
            return;
        }
        decoder.start();
        inputBuffers = decoder.getInputBuffers();
        outputBuffers = decoder.getOutputBuffers();
        info = new BufferInfo();
    }

    //ReadThread as ijk
    private class ReadThread extends Thread{
        @Override
        public void run() {
            while (!Thread.interrupted()){
                if(isPlayerStop)
                {
                    continue;
                }
                boolean isEOS = false;
                if (!isEOS) {
                    int inIndex = decoder.dequeueInputBuffer(10000);
                    if (inIndex >= 0) {
                        ByteBuffer buffer = inputBuffers[inIndex];
                        int sampleSize = extractor.readSampleData(buffer, 0);
                        if (sampleSize < 0) {
                            // We shouldn't stop the playback at this point, just pass the EOS
                            // flag to decoder, we will get it again from the
                            // dequeueOutputBuffer
                            Log.d("queueInputBuffer_if", String.valueOf(inIndex));
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            isEOS = true;
                        } else {
                            Log.d("queueInputBuffer_else", String.valueOf(inIndex));
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
            }
        }
    }

    //DisplayThread as ijk
    private class DisplayThread extends Thread {
        @Override
        public void run() {
            long startMs = System.currentTimeMillis();

            while (!Thread.interrupted()) {
                if(isPlayerStop)
                {
                    continue;
                }
                int outIndex = decoder.dequeueOutputBuffer(info, 10000);
                Log.d("dequeueOutputBuffer", String.valueOf(outIndex));
                switch (outIndex) {
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                        Log.d("DecodeActivity", "INFO_OUTPUT_BUFFERS_CHANGED");
                        outputBuffers = decoder.getOutputBuffers();
                        break;
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                        Log.d("DecodeActivity", "New format " + decoder.getOutputFormat());
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER:
                        Log.d("DecodeActivity", "dequeueOutputBuffer timed out!");
                        break;
                    default:
                        ByteBuffer buffer = outputBuffers[outIndex];
                        Log.v("DecodeActivity", "We can't use this buffer but render it due to the API limit, " + buffer);

                        // We use a very simple clock to keep the video FPS, or the video
                        // playback will be too fast
                        while (info.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                            try {
                                sleep(10);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                break;
                            }
                        }
                        Log.d("releaseOutputBuffer", String.valueOf(outIndex));
                        decoder.releaseOutputBuffer(outIndex, true);
                        //playerStop();
                        break;
                }


                // All decoded frames have been rendered, we can stop playing now
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d("DecodeActivity", "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                    break;
                }
            }

            decoder.stop();
            decoder.release();
            extractor.release();
        }
    }

}

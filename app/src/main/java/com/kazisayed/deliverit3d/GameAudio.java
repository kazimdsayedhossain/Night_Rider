package com.kazisayed.deliverit3d;

import android.content.Context;
import android.media.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.ArrayList;

/** Native PCM mixer: no media element, Javascript audio graph, or network dependency. */
public final class GameAudio implements AutoCloseable {
    private static final int RATE=48000,FRAMES=512;private final AudioManager manager;private final AudioTrack track;private final Thread thread;
    private final ConcurrentLinkedQueue<Voice> queue=new ConcurrentLinkedQueue<>();private final ArrayList<Voice> voices=new ArrayList<>();
    private volatile boolean alive=true,active;private volatile float speed,rain,traffic,master=.75f,effects=.8f,ambience=.5f;private long sample;private int noise=83719;private double lowNoise;
    private static class Voice {double frequency,remaining,duration,gain,phase;int kind;Voice(double f,double seconds,double gain,int kind){frequency=f;remaining=duration=seconds;this.gain=gain;this.kind=kind;}}
    private final AudioManager.OnAudioFocusChangeListener focus=change->{if(change<=0)active=false;};
    public GameAudio(Context context){manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);int size=Math.max(AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT),FRAMES*4*3);track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(size).build();thread=new Thread(this::mix,"DispatchAudio");thread.setDaemon(true);thread.start();}
    public void volumes(float m,float e,float a){master=m;effects=e;ambience=a;}
    public void state(float speed,float rain,float traffic){this.speed=Math.abs(speed);this.rain=rain;this.traffic=traffic;}
    public void resume(){if(manager.requestAudioFocus(focus,AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED)active=true;}
    public void pause(){active=false;queue.clear();manager.abandonAudioFocus(focus);}
    public void cue(String name){switch(name){case "horn"->{queue.add(new Voice(370,.28,.18,0));queue.add(new Voice(440,.28,.14,0));}case "delivery"->{queue.add(new Voice(660,.16,.16,0));queue.add(new Voice(990,.45,.13,1));}case "coin"->queue.add(new Voice(1400,.12,.08,1));case "finish"->{queue.add(new Voice(523,.8,.12,1));queue.add(new Voice(659,.9,.12,1));queue.add(new Voice(784,1.1,.12,1));}case "crash"->queue.add(new Voice(70,.55,.2,2));case "thunder"->queue.add(new Voice(34,2,.21,2));default->queue.add(new Voice(440,.08,.08,0));}}
    private void mix(){short[] pcm=new short[FRAMES*2];track.play();while(alive){Voice incoming;while((incoming=queue.poll())!=null){if(voices.size()<24)voices.add(incoming);}for(int i=0;i<FRAMES;i++){
            noise^=noise<<13;noise^=noise>>>17;noise^=noise<<5;double n=noise/(double)Integer.MAX_VALUE;lowNoise+=(n-lowNoise)*.025;
            double rolling=(n*.024+lowNoise*.12)*Math.min(1,speed/16),rainSound=(n*.032+lowNoise*.08)*rain,engine=Math.sin(sample*.0055)*.015*traffic;
            double sound=(rolling+rainSound+engine)*ambience;
            for(int v=voices.size()-1;v>=0;v--){Voice voice=voices.get(v);double env=Math.min(1,(voice.duration-voice.remaining)*300)*Math.pow(Math.max(0,voice.remaining/voice.duration),voice.kind==1?2:1);double tone=voice.kind==2?lowNoise:Math.sin(voice.phase)+(voice.kind==1?.25*Math.sin(voice.phase*2.75):0);sound+=tone*env*voice.gain*effects;voice.phase+=Math.PI*2*voice.frequency/RATE;voice.remaining-=1.0/RATE;if(voice.remaining<=0)voices.remove(v);}
            sound=active?Math.tanh(sound*master):0;short value=(short)(sound*28000);pcm[i*2]=value;pcm[i*2+1]=value;sample++;
        }int written=0;while(alive&&written<pcm.length){int n=track.write(pcm,written,pcm.length-written,AudioTrack.WRITE_BLOCKING);if(n<0){alive=false;break;}written+=n;}}track.stop();track.release();}
    @Override public void close(){alive=false;manager.abandonAudioFocus(focus);try{thread.join(1000);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}

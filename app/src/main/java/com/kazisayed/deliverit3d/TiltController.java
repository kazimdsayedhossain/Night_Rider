package com.kazisayed.deliverit3d;

import android.content.Context;
import android.hardware.*;
import android.os.*;
import android.view.Surface;
import android.view.WindowManager;

/** Native fused motion, display-axis remapping, and the phone-confirmed steering curve. */
public final class TiltController implements SensorEventListener,AutoCloseable {
    private static final int[] TYPES={Sensor.TYPE_GAME_ROTATION_VECTOR,Sensor.TYPE_GRAVITY,Sensor.TYPE_ROTATION_VECTOR,Sensor.TYPE_ACCELEROMETER};
    private final SensorManager manager;private final WindowManager windows;private final HandlerThread thread;private final Handler worker;
    private final float[] matrix=new float[9],remapped=new float[9];private final double[] acceleration=new double[3];
    private Sensor sensor;private boolean registered,wanted,initialized,centerPending,inverted;private int candidate,rotation=-1;
    private long eventNs,startedNs,samples;private double rawAngle,centerAngle,filteredAngle,sensitivity=1;
    public volatile Sample latest=new Sample(0,0,"off",0,0,"none");
    public record Sample(float steer,float degrees,String status,long timestamp,long samples,String source){}
    public TiltController(Context context){manager=(SensorManager)context.getSystemService(Context.SENSOR_SERVICE);windows=(WindowManager)context.getSystemService(Context.WINDOW_SERVICE);thread=new HandlerThread("DispatchSensors");thread.start();worker=new Handler(thread.getLooper());}
    public void configure(float value,boolean invert){worker.post(()->{sensitivity=Rules.clamp(value,.3f,2.5f);inverted=invert;});}
    public void start(){worker.post(()->{wanted=true;if(!registered){candidate=0;select();}});}
    public void stop(){worker.post(()->{wanted=false;unregister();publish("paused",0);});}
    public void center(){worker.post(()->{centerPending=true;if(latest.status.equals("ready")&&age()<600)captureCenter();});}
    public float steering(){Sample v=latest;return v.status.equals("ready")&&(SystemClock.elapsedRealtimeNanos()-v.timestamp)<600_000_000L?v.steer:0;}
    private double age(){return eventNs==0?Double.POSITIVE_INFINITY:(SystemClock.elapsedRealtimeNanos()-eventNs)/1e6;}
    private void select(){unregister();initialized=false;eventNs=0;startedNs=SystemClock.elapsedRealtimeNanos();publish("connecting",0);while(manager!=null&&candidate<TYPES.length){sensor=manager.getDefaultSensor(TYPES[candidate++]);if(sensor==null)continue;try{registered=manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_GAME,0,worker);}catch(RuntimeException e){registered=false;}if(registered){worker.postDelayed(watchdog,1500);return;}}sensor=null;publish("unavailable",0);}
    private final Runnable watchdog=new Runnable(){public void run(){if(!wanted||!registered)return;long last=eventNs==0?startedNs:eventNs;if(SystemClock.elapsedRealtimeNanos()-last>1_500_000_000L)select();else worker.postDelayed(this,1500);}};
    private void unregister(){if(manager!=null)manager.unregisterListener(this);registered=false;worker.removeCallbacks(watchdog);}
    private void captureCenter(){centerAngle=rawAngle;filteredAngle=0;centerPending=false;publish("ready",0);}
    private void publish(String status,float steer){latest=new Sample(steer,(float)filteredAngle,status,eventNs,samples,sensor==null?"none":sensor.getStringType());}
    @Override public void onSensorChanged(SensorEvent e){
        if(!registered||e.sensor!=sensor||e.timestamp<=eventNs)return;
        double dt=eventNs==0?0:(e.timestamp-eventNs)/1e9;eventNs=e.timestamp;samples++;
        int display=windows.getDefaultDisplay().getRotation();if(display!=rotation){rotation=display;centerAngle=0;initialized=false;}
        double x,y,z;int type=sensor.getType();
        if(type==Sensor.TYPE_GAME_ROTATION_VECTOR||type==Sensor.TYPE_ROTATION_VECTOR){
            SensorManager.getRotationMatrixFromVector(matrix,e.values);int ax=SensorManager.AXIS_X,ay=SensorManager.AXIS_Y;
            if(rotation==Surface.ROTATION_90){ax=SensorManager.AXIS_Y;ay=SensorManager.AXIS_MINUS_X;}
            else if(rotation==Surface.ROTATION_180){ax=SensorManager.AXIS_MINUS_X;ay=SensorManager.AXIS_MINUS_Y;}
            else if(rotation==Surface.ROTATION_270){ax=SensorManager.AXIS_MINUS_Y;ay=SensorManager.AXIS_X;}
            SensorManager.remapCoordinateSystem(matrix,ax,ay,remapped);x=remapped[6];y=remapped[7];z=remapped[8];
        }else{
            double length=Math.sqrt(e.values[0]*e.values[0]+e.values[1]*e.values[1]+e.values[2]*e.values[2]);if(length<6||length>14){publish("moving",0);return;}
            double blend=dt<=0||dt>.3?1:1-Math.exp(-dt/.10);for(int i=0;i<3;i++)acceleration[i]=type==Sensor.TYPE_ACCELEROMETER?acceleration[i]+blend*(e.values[i]-acceleration[i]):e.values[i];
            x=acceleration[0];y=acceleration[1];z=acceleration[2];double old=x;
            if(rotation==Surface.ROTATION_90){x=-y;y=old;}else if(rotation==Surface.ROTATION_180){x=-x;y=-y;}else if(rotation==Surface.ROTATION_270){x=y;y=-old;}
        }
        double length=Math.sqrt(x*x+y*y+z*z);if(!Double.isFinite(length)||length<.01){publish("connecting",0);return;}
        if(Math.hypot(x,y)/length<.22){initialized=false;filteredAngle=0;publish("flat",0);return;}
        rawAngle=Math.toDegrees(Math.atan2(-x,y));if(centerPending)captureCenter();
        double target=((rawAngle-centerAngle+180)%360+360)%360-180;target=Math.max(-80,Math.min(80,target));
        double blend=!initialized||dt>.3?1:1-Math.exp(-dt/.065);filteredAngle+=blend*(target-filteredAngle);initialized=true;
        double n=Math.max(0,Math.min(1,(Math.abs(filteredAngle)-2.5)/(45/sensitivity-2.5)));
        publish("ready",(float)(Math.signum(filteredAngle)*Math.pow(n,1.35)*(inverted?-1:1)));
    }
    @Override public void onAccuracyChanged(Sensor s,int accuracy){}
    @Override public void close(){worker.post(()->{wanted=false;unregister();thread.quitSafely();});}
}

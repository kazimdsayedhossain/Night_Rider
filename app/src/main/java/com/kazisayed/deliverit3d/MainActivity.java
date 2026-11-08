package com.kazisayed.deliverit3d;

import android.os.*;
import android.view.*;
import android.widget.FrameLayout;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.*;

/** Android owns the window, lifecycle, touch input, sensors, audio and accessibility. */
public class MainActivity extends AndroidApplication implements DispatchGame.Host {
    protected SaveStore saves;protected TiltController tilt;protected GameAudio audio;protected DispatchGame game;protected NativeUi ui;protected View surface;private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){if(ui!=null)ui.refresh(game.snapshot);main.postDelayed(this,50);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        android.util.Log.i("Dispatch", "MainActivity onCreate started");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        saves=new SaveStore(this);tilt=new TiltController(this);audio=new GameAudio(this);PlayerInput input=new PlayerInput();game=new DispatchGame(saves,input,tilt,audio,this);
        AndroidApplicationConfiguration config=new AndroidApplicationConfiguration();config.useGL30=true;config.useAccelerometer=false;config.useCompass=false;config.useGyroscope=false;config.useRotationVectorSensor=false;config.useImmersiveMode=true;config.numSamples=0;config.depth=24;config.stencil=0;config.r=config.g=config.b=8;config.a=0;
        surface=initializeForView(game,config);FrameLayout root=new FrameLayout(this);root.addView(surface,new FrameLayout.LayoutParams(-1,-1));ui=new NativeUi(this,game,input,saves,tilt);root.addView(ui,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{ui.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});surface.addOnLayoutChangeListener((v,a,b,c,d,e,f,g,h)->applyResolution());post(game::settings);
        android.util.Log.i("Dispatch", "MainActivity onCreate completed");
    }
    public void post(Runnable work){if(Gdx.app!=null)Gdx.app.postRunnable(work);}
    public void command(String value){post(()->game.command(value));}
    public void applySettings(){post(game::settings);applyResolution();}
    private void applyResolution(){
        if(surface instanceof SurfaceView s&&surface.getWidth()>0){
            float scale=saves.decimal("native-resolution",1f);
            if(Math.abs(scale-1.0f)>0.02f){
                s.getHolder().setFixedSize(Math.max(1,(int)(surface.getWidth()*scale)),Math.max(1,(int)(surface.getHeight()*scale)));
            }
        }
    }
    @Override protected void onResume(){super.onResume();if(tilt!=null)tilt.start();main.removeCallbacks(refresh);main.post(refresh);}
    @Override protected void onPause(){main.removeCallbacks(refresh);if(game!=null)game.input.release();if(tilt!=null)tilt.stop();if(audio!=null)audio.pause();super.onPause();}
    @Override protected void onDestroy(){main.removeCallbacks(refresh);if(tilt!=null)tilt.close();if(audio!=null)audio.close();super.onDestroy();}
    @Override public void onBackPressed(){if(ui!=null&&ui.closePanel())return;if(game!=null&&game.snapshot.state()==DispatchGame.State.PLAYING)command("pause");else if(game!=null&&game.snapshot.state()==DispatchGame.State.PAUSED)command("resume");else super.onBackPressed();}
    @Override public void feedback(int ms){if(!saves.flag("native-haptics",true))return;Vibrator vibrator=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(vibrator==null||!vibrator.hasVibrator())return;if(Build.VERSION.SDK_INT>=26)vibrator.vibrate(VibrationEffect.createOneShot(ms,VibrationEffect.DEFAULT_AMPLITUDE));else vibrator.vibrate(ms);}
    @Override public void playCue(String name){if(audio!=null)audio.cue(name);}
}

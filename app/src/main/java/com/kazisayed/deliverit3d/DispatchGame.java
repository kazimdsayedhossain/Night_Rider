package com.kazisayed.deliverit3d;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.Array;
import net.mgsx.gltf.scene3d.scene.SceneManager;
import net.mgsx.gltf.scene3d.shaders.*;
import net.mgsx.gltf.scene3d.attributes.*;
import net.mgsx.gltf.scene3d.utils.IBLBuilder;
import java.util.*;

/** The native application owns simulation, lifecycle, rendering, progression and persistence. */
public final class DispatchGame extends ApplicationAdapter {
    public enum State {LOADING,MENU,PLAYING,PAUSED,RESULT,ERROR}
    public interface Host {void feedback(int milliseconds); default void playCue(String name){}}
    public record Marker(float x,float z,int type){}
    public record Snapshot(State state,String status,float loading,int level,String district,float time,int delivered,int quota,int inventory,int cash,int tickets,int score,int combo,float comboFraction,boolean fever,float progress,float speed,float x,float z,float yaw,float steering,String target,float distance,float bearing,String weather,float hour,int fps,int width,int height,boolean won,int stars,String notice,List<Marker> markers){}
    public volatile Snapshot snapshot;public final PlayerInput input;public final SaveStore saves;private final TiltController tilt;private final GameAudio audio;private final Host host;
    private State state=State.LOADING;private String status="Preparing native renderer",notice="";private float noticeTime,loading;
    private MeshKit kit;private AssetLibrary assets;private SceneManager scene;private PerspectiveCamera camera;private WorldScene world;private RiderRig rider;private WeatherSystem weather;private TrafficSystem traffic;
    private Cubemap diffuse,specular;private Texture brdf;private ModelInstance coin,customer,depot,finish;private final Array<RenderableProvider> markers=new Array<>();
    public static final class TossedParcel {
        public final float sx, sy, sz, tx, ty, tz, duration;
        public float time;
        public final LevelData.Customer customer;
        public final Rules.Rating rating;
        public TossedParcel(float sx, float sy, float sz, float tx, float ty, float tz, float duration, LevelData.Customer customer, Rules.Rating rating) {
            this.sx = sx; this.sy = sy; this.sz = sz;
            this.tx = tx; this.ty = ty; this.tz = tz;
            this.duration = duration; this.customer = customer; this.rating = rating;
        }
    }
    private final List<TossedParcel> tossedParcels = new ArrayList<>();
    private LevelData level;private final Rules.Combo combo=new Rules.Combo();private float x,z,yaw,speed,steering,throttle,time,accumulator,hudClock,invulnerable,runClock,previousZ,smoothElev,prevX,prevZ,prevYaw;private int delivered,inventory,cash,tickets,score,checkpoint,stars;private boolean won,levelBanked;private String vehicleId,hatId;
    private final Set<Integer> respected=new HashSet<>(),violated=new HashSet<>();private float stoppedTime;
    public DispatchGame(SaveStore saves,PlayerInput input,TiltController tilt,GameAudio audio,Host host){this.saves=saves;this.input=input;this.tilt=tilt;this.audio=audio;this.host=host;cash=saves.cash();tickets=saves.tickets();publish();}
    @Override public void create(){try{
        android.util.Log.i("Dispatch", "DispatchGame create() started");
        kit=new MeshKit();assets=new AssetLibrary();PBRShaderConfig config=PBRShaderProvider.createDefaultConfig();config.numDirectionalLights=1;config.numPointLights=0;config.numSpotLights=1;config.numBones=64;
        scene=new SceneManager(new PBRShaderProvider(config),PBRShaderProvider.createDefaultDepth(64));camera=new PerspectiveCamera(56,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());camera.near=.12f;camera.far=1250;scene.setCamera(camera);
        IBLBuilder ibl=IBLBuilder.createOutdoor(new DirectionalLight().set(new Color(.22f,.28f,.42f,1f),-.3f,-.8f,-.2f));diffuse=ibl.buildIrradianceMap(32);specular=ibl.buildRadianceMap(10);ibl.dispose();brdf=new Texture(Gdx.files.classpath("net/mgsx/gltf/shaders/brdfLUT.png"));
        scene.environment.set(PBRCubemapAttribute.createDiffuseEnv(diffuse),PBRCubemapAttribute.createSpecularEnv(specular),PBRTextureAttribute.createBRDFLookupTexture(brdf));
        weather=new WeatherSystem(scene,kit);weather.hour=saves.decimal("native-hour",23.0f);weather.automatic=saves.flag("native-weather-auto",true);weather.dayCycle=saves.flag("native-day-cycle",false);weather.headlights=saves.flag("native-headlights",true);
        String mode=saves.text("native-weather","RAIN");try{weather.mode=WeatherSystem.Mode.valueOf(mode);}catch(Exception ignored){}
        android.util.Log.i("Dispatch", "DispatchGame create() completed successfully");
    }catch(Throwable error){android.util.Log.e("Dispatch", "DispatchGame create() failed", error);fail(error);}}
    @Override public void resize(int width,int height){if(camera!=null){camera.viewportWidth=width;camera.viewportHeight=height;camera.update();}}
    @Override public void render(){try{
        float dt=Math.min(.15f,Gdx.graphics.getDeltaTime());
        if(state==State.LOADING){Gdx.gl.glClearColor(.024f,.045f,.085f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_DEPTH_BUFFER_BIT);if(assets==null)return;
            if(world==null){
                boolean done=assets.loadNext();loading=assets.progress()*.85f;status=assets.status;
                android.util.Log.i("Dispatch", "Asset stage: " + status + " (" + loading + ")");
                if(done){
                    android.util.Log.i("Dispatch", "All assets loaded. Building world scene...");
                    world=new WorldScene(kit,assets);rider=new RiderRig(kit,assets);loadLevel(saves.level());
                }
            }
            else {
                world.stream(x,z);loading=.9f;status="Opening your route";
                if(world.ready()){state=State.MENU;loading=1;android.util.Log.i("Dispatch", "Game reached MENU state!");}
            }
            publish();return;
        }
        if(state==State.ERROR)return;
        if(state==State.PLAYING){
            float simDt=Math.min(dt,0.033f);
            update(simDt);
        }
        if(noticeTime>0){noticeTime-=dt;if(noticeTime<=0)notice="";}
        world.stream(x,z);if(traffic!=null)traffic.setNight(weather.hour>=18.5f||weather.hour<6f||weather.rain>.4f);weather.update(state==State.PLAYING?dt:0,x,z,yaw);
        float rawElev=WorldGrid.elevation(x,z);
        smoothElev=smoothElev+(rawElev-smoothElev)*(1f-(float)Math.exp(-dt*12f));
        rider.update(state==State.PLAYING?dt:0,x,z,yaw,speed,steering,inventory,smoothElev);
        float back=5.8f+Math.min(2.0f,Math.abs(speed)*.10f),sin=(float)Math.sin(yaw),cos=(float)Math.cos(yaw);
        Vector3 target=new Vector3(x-sin*back,smoothElev+3.2f,z-cos*back);
        camera.position.lerp(target,1-(float)Math.exp(-dt*6.5f));camera.up.set(Vector3.Y);camera.lookAt(x+sin*14.0f,1.1f+smoothElev,z+cos*14.0f);camera.fieldOfView=50.0f;camera.update();
        Gdx.gl.glViewport(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());Gdx.gl.glClearColor(weather.sky.r,weather.sky.g,weather.sky.b,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT|GL20.GL_DEPTH_BUFFER_BIT);
        Array<RenderableProvider> visible=scene.getRenderableProviders();visible.clear();world.collect(camera,x,z,visible);traffic.collect(camera,x,z,visible);rider.collect(visible,inventory);collectMarkers(visible);scene.update(dt);scene.renderColors();weather.render(camera,x,z);
        audio.state(state == State.PLAYING ? speed : 0,
                    state == State.PLAYING ? throttle : 0,
                    state == State.PLAYING && input.brake && speed > 2.0f,
                    weather != null ? weather.rain : 0,
                    vehicleId != null ? vehicleId : "bike",
                    state == State.PLAYING);
        hudClock+=dt;if(hudClock>=.08f){hudClock=0;publish();}
    }catch(Throwable error){fail(error);}}
    private void loadLevel(int number){
        level=new LevelData(number);combo.reset();combo.window=level.comboWindow;tossedParcels.clear();
        prevX=x=level.startX;prevZ=z=level.startZ;prevYaw=yaw=level.startYaw*MathUtils.degreesToRadians;smoothElev=WorldGrid.elevation(x,z);
        speed=steering=throttle=0;time=level.timeLimit;delivered=score=checkpoint=stars=0;inventory=level.inventory;won=levelBanked=false;runClock=accumulator=invulnerable=0;respected.clear();violated.clear();stoppedTime=0;
        vehicleId=saves.text("deliver3d_vehicle","bike");hatId=saves.text("deliver3d_hat","cap");rider.equip(vehicleId,hatId);
        world.configure(level.railroad?LevelData.RAILROAD_Z:Float.NaN);traffic=new TrafficSystem(assets,kit,level);traffic.setHornSink((type,intensity)->{});
        float sin=(float)Math.sin(yaw),cos=(float)Math.cos(yaw);
        camera.position.set(x-sin*5.8f,WorldGrid.elevation(x,z)+3.2f,z-cos*5.8f);camera.lookAt(x+sin*14.0f,1.1f+WorldGrid.elevation(x,z),z+cos*14.0f);camera.fieldOfView=50.0f;camera.update();
        makeMarkers();publish();
    }
    private void makeMarkers(){markers.clear();coin=kit.ball(kit.amber,0,0,0,.28f,.28f,.08f);customer=kit.box(kit.amber,0,0,0,.38f,1.65f,.3f);depot=kit.box(kit.parcel,0,0,0,3.4f,3.0f,3.4f);finish=kit.box(kit.amber,level.finishX,4.4f,level.finishZ,15,.2f,.2f);}
    private void collectMarkers(Array<RenderableProvider> out){
        for(LevelData.Coin c:level.coins)if(!c.collected&&Vector2.dst2(x,z,c.x,c.z)<90*90&&camera.frustum.sphereInFrustum(c.x,1,c.z,1)){float gy=WorldGrid.elevation(c.x,c.z);ModelInstance i=new ModelInstance(coin);i.transform.setToTranslation(c.x,gy+.55f+(float)Math.sin(runClock*3+c.z)*.08f,c.z).rotate(Vector3.Y,runClock*100).scale(.28f,.28f,.08f);out.add(i);}
        for(LevelData.Customer c:level.customers){
            if(Vector2.dst2(x,z,c.x,c.z)>=130*130)continue;
            float gy=WorldGrid.elevation(c.x,c.z)+.08f;
            float bounce=(c.celebrateTime>0)?Math.abs((float)Math.sin(c.celebrateTime*9f))*.22f:0;
            float py=gy+.825f+bounce;
            ModelInstance i=new ModelInstance(customer);
            i.transform.setToTranslation(c.x,py,c.z).scale(.36f,1.65f,.3f);
            out.add(i);
            out.add(kit.ball(kit.skin,c.x,py+.955f,c.z,.2f,.24f,.21f));
            if(c.collected){
                out.add(kit.box(kit.parcel,c.x,py+.12f,c.z+(c.side.equals("right")?-0.26f:0.26f),.32f,.26f,.28f));
            }else{
                out.add(kit.ball(kit.lamp,c.x,py+1.26f+(float)Math.sin(runClock*4f)*.06f,c.z,.12f,.12f,.12f));
            }
        }
        for(TossedParcel p:tossedParcels){
            float t=Math.min(1f,p.time/Math.max(0.01f,p.duration));
            float px=p.sx+(p.tx-p.sx)*t,pz=p.sz+(p.tz-p.sz)*t;
            float py=p.sy+(p.ty-p.sy)*t+1.85f*(float)Math.sin(t*Math.PI);
            ModelInstance pi=new ModelInstance(depot);
            pi.transform.setToTranslation(px,py,pz).scale(.10f,.08f,.11f).rotate(Vector3.Y,p.time*650f).rotate(Vector3.X,p.time*380f);
            out.add(pi);
        }
        for(LevelData.Depot d:level.depots)if(!d.collected&&Vector2.dst2(x,z,d.x,d.z)<120*120){float gy=WorldGrid.elevation(d.x,d.z)+.08f;AssetLibrary.Template dt=assets!=null?assets.getProp("depot"):null;if(dt!=null){out.add(dt.create(d.x,gy,d.z,d.x>0?-90f:90f,1.0f));}else{ModelInstance i=new ModelInstance(depot);i.transform.setToTranslation(d.x,gy+1.5f,d.z);out.add(i);}}
        float finishDist=Vector2.dst(x,z,level.finishX,level.finishZ);
        if(finishDist<220){float gy=WorldGrid.elevation(level.finishX,level.finishZ);finish.transform.setToTranslation(level.finishX,gy+4.4f,level.finishZ);out.add(finish);out.add(kit.box(kit.steel,level.finishX-7.5f,gy+2.2f,level.finishZ,.12f,4.4f,.12f));out.add(kit.box(kit.steel,level.finishX+7.5f,gy+2.2f,level.finishZ,.12f,4.4f,.12f));}
    }
    private void update(float dt){
        runClock+=dt;time-=dt;invulnerable=Math.max(0,invulnerable-dt);
        if(combo.tick(dt)!=null)announce("Chain lost");
        if(time<=0){time=0;finish(false,"Shift time is up");return;}
        float manual=input.left?-1:input.right?1:input.touchSteer;
        float targetSteer=Math.abs(manual)>.01f?manual:(input.tilt?tilt.steering():0);
        if(Math.abs(targetSteer)<.015f) targetSteer=0;
        steering=Rules.lerp(steering,targetSteer,1-(float)Math.exp(-dt*16));
        if(Math.abs(steering)<.004f) steering=0;throttle=Rules.lerp(throttle,input.pedal?1:0,1-(float)Math.exp(-dt*12));
        float grip=(weather.rain>.4f?.84f:weather.snow>.4f?.68f:1)*(vehicleId.equals("scooter")?1.18f:vehicleId.equals("superbike")?.92f:1);
        float vehicleSpeed=vehicleId.equals("superbike")?1.24f:vehicleId.equals("gold")?1.12f:vehicleId.equals("scooter")?.92f:1;
        float maximum=level.speed*(combo.fever()?1.25f:1)*vehicleSpeed*(weather.rain>.4f?.92f:weather.snow>.4f?.82f:1)*(WorldGrid.road(x,z)?1:.68f);
        float acceleration=combo.fever()?26:18;if(vehicleId.equals("superbike"))acceleration*=1.2f;
        if(input.brake){speed-=23*grip*dt;speed=Math.max(speed,-Math.min(5.2f,maximum*.34f));}else if(throttle>.01f){speed=Math.min(maximum,speed+acceleration*throttle*dt);}else {float drag=(1.45f+Math.abs(speed)*.045f)*dt;speed=Math.abs(speed)<drag?0:speed-Math.signum(speed)*drag;}
        if(speed>maximum)speed=Math.max(maximum,speed-dt*8);
        float authority=Rules.clamp(.3f+Math.abs(speed)/3.5f,.3f,1),turn=Math.min(2.1f,.45f+Math.abs(speed)/Math.max(1,maximum)*1.6f)*grip*authority;
        yaw=Rules.wrap(yaw-steering*turn*(speed<-.1f?-1:1)*dt);previousZ=z;
        float step=speed*dt;int subdivisions=Math.max(1,(int)Math.ceil(Math.abs(step)/.25f));
        float[] pos=new float[2];
        for(int i=0;i<subdivisions;i++){
            float nx=x+(float)Math.sin(yaw)*step/subdivisions,nz=z+(float)Math.cos(yaw)*step/subdivisions;
            // The rider is held on the street (constrainToRoad in city.js); scraping the kerb bleeds speed instead of ending the shift.
            pos[0]=nx;pos[1]=nz;if(WorldGrid.constrainToRoad(pos)){speed*=1-Math.min(.9f,3.2f*dt/subdivisions);}
            nx=pos[0];nz=pos[1];
            if(Math.abs(nz)>3980||Math.abs(nx)>3980){speed=0;announce("County boundary");break;}
            Collision.Contact wall=world.collision(nx,nz,yaw);
            TrafficSystem tr=traffic;Collision.Contact car=invulnerable<=0?tr.collision(nx,nz,yaw):null;
            if(tr.hitsTrain(nx,nz,yaw)){audio.cue("crash");host.feedback(90);finish(false,"Rail crossing collision · Shift terminated");break;}
            if(car!=null){
                if(combo.fever()){
                    combo.drop("fever_shield");invulnerable=1.8f;speed=Math.max(speed*.65f,3.5f);
                    x+=car.x()*.45f;z+=car.z()*.45f;audio.cue("skid");host.feedback(55);
                    announce("⚡ Fever Shield Deflected Impact!");break;
                }
                boolean highSpeedFrontal=(Math.abs(car.z())>Math.abs(car.x())*.75f&&Math.abs(speed)>7.5f)||Math.abs(speed)>13.0f;
                if(highSpeedFrontal){
                    x+=car.x()*.20f;z+=car.z()*.20f;audio.cue("crash");host.feedback(90);combo.drop("crash");
                    finish(false,"Traffic collision · Shift terminated");break;
                }else{
                    speed*=.32f;x+=car.x()*.42f;z+=car.z()*.42f;invulnerable=1.6f;combo.drop("scrape");time=Math.max(0,time-2.5f);
                    audio.cue("skid");host.feedback(45);announce("Traffic scrape · -2.5s penalty");break;
                }
            }
            if(wall!=null){
                x+=wall.x()*.14f;z+=wall.z()*.14f;
                if(Math.abs(speed)>11.5f){audio.cue("crash");host.feedback(75);finish(false,"Road impact · Shift terminated");}
                else{speed*=-.22f;invulnerable=0.8f;combo.drop("bump");announce("Wall scrape");}
                break;
            }
            x=nx;z=nz;
        }
        if(state!=State.PLAYING)return;
        traffic.update(dt,x,z,speed,yaw);
        if(traffic.nearMiss(x,z,yaw,speed)){award(25,Rules.SCORE_NEAR_MISS,0);combo.extend(Rules.NEAR_MISS_COMBO_EXTEND);audio.cue("combo");announce("Close pass · +25");}
        for(int i=tossedParcels.size()-1;i>=0;i--){
            TossedParcel p=tossedParcels.get(i);p.time+=dt;
            if(p.time>=p.duration){p.customer.celebrateTime=1.8f;tossedParcels.remove(i);}
        }
        for(LevelData.Customer c:level.customers){
            if(c.celebrateTime>0)c.celebrateTime=Math.max(0,c.celebrateTime-dt);
            if(c.collected)continue;float distance=Vector2.dst(x,z,c.x,c.z);Rules.Approach.Result r=c.approach.update(distance,speed);
            if(inventory>0&&r.fire()){Rules.Rating rating=Rules.rate(r.best());c.collected=true;delivered++;inventory--;
                Rules.Combo.Hit hit=combo.hit();if(hit.feverStarted()){audio.cue("combo");announce("⚡ FEVER MODE ACTIVATED! (2.0x)");}
                float m=combo.multiplier();int earnedCash=Math.round((c.reward+rating.cash())*m);
                award(earnedCash,Math.round(rating.score()*m),rating.time());audio.cue("delivery");host.feedback(30);
                announce(rating.label()+" +$"+earnedCash+" · "+delivered+" / "+level.quota);
                float px=x,py=WorldGrid.elevation(x,z)+0.9f,pz=z;float cx=c.x,cy=WorldGrid.elevation(c.x,c.z)+1.1f,cz=c.z;
                tossedParcels.add(new TossedParcel(px,py,pz,cx,cy,cz,0.38f,c,rating));}

            else if(inventory==0&&distance<7.5f&&!c.approach.warned){c.approach.warned=true;announce("Bag empty · visit the depot");}
        }
        for(LevelData.Depot d:level.depots){
            if(!d.collected&&inventory<Rules.CAPACITY){
                if(Math.abs(x-d.x)<6.5f&&Math.abs(z-d.z)<5.0f){
                    d.collected=true;inventory=Rules.CAPACITY;audio.cue("delivery");host.feedback(30);announce("Bag Restocked · "+Rules.CAPACITY+" parcels ready");
                }
            }
        }
        boolean fever=combo.fever();
        for(LevelData.Coin c:level.coins){
            if(c.collected)continue;
            float d2=Vector2.dst2(x,z,c.x,c.z);
            if(fever&&d2<81f){float d=(float)Math.sqrt(d2);float pullSpeed=16.0f*dt;c.x+=(x-c.x)/d*pullSpeed;c.z+=(z-c.z)/d*pullSpeed;}
            if(d2<2.2f){c.collected=true;award(10,Rules.SCORE_COIN,0);combo.extend(0.6f);audio.cue("coin");}
        }
        if(checkpoint<level.checkpoints.length&&z>=level.checkpoints[checkpoint]){checkpoint++;award(Rules.checkpointCash(level.number),Rules.CHECKPOINT_SCORE,Rules.CHECKPOINT_TIME);audio.cue("checkpoint");announce("Checkpoint · +"+Rules.CHECKPOINT_TIME+" seconds");}
        if(Math.abs(x)<7.5f){
            int row=Math.round((z+10)/WorldGrid.TILE);float stopLine=row*WorldGrid.TILE;float until=stopLine-z;boolean red=traffic.red(row);
            if(red&&until>14f&&until<36f&&!respected.contains(row)){if(notice.isEmpty()||notice.contains("Red light"))announce("Red light ahead · Stop at line for +2s bonus");}
            if(red&&until>6f&&until<24f&&Math.abs(speed)<.6f){
                stoppedTime+=dt;
                if(stoppedTime>1.0f&&respected.add(row)){award(35,Rules.SCORE_RED_LIGHT_OBEYED,2.0f);combo.extend(2.5f);audio.cue("checkpoint");host.feedback(30);announce("Safe stop! +$35 · +2.0 seconds");}
            }else stoppedTime=0;
            if(red&&previousZ<stopLine&&z>=stopLine&&violated.add(row)){time=Math.max(0,time-3.0f);combo.drop("red light");host.feedback(45);announce("Red light run! · 3.0s penalty");}
        }
        if(level.railroad&&Math.abs(z-LevelData.RAILROAD_Z)<55f&&traffic.trainWarning()){
            if(notice.isEmpty()||notice.contains("CROSSING"))announce("⚠ RAILROAD CROSSING · TRAIN APPROACHING!");
        }
        if(z>=level.finishZ&&Math.abs(x)<=7.5f){
            if(delivered>=level.quota)finish(true,"Route complete");
            else{z=level.finishZ-1.2f;speed=0;announce((level.quota-delivered)+" deliveries still needed");}
        }
    }
    private void crash(String message){speed=0;combo.drop("crash");invulnerable=2.5f;audio.cue("crash");host.feedback(80);finish(false,message);}
    private void award(int money,int points,float seconds){cash+=money;score+=points;time+=seconds;saves.bank(cash,tickets);}
    private void finish(boolean success,String message){if(state!=State.PLAYING)return;won=success;status=message;state=State.RESULT;input.release();speed=0;if(success&&!levelBanked){levelBanked=true;stars=Rules.starsFor(time,level.timeLimit,combo.best);cash+=Rules.finishRewards(level.number,time).finishReward();tickets+=stars;saves.bank(cash,tickets);saves.put("deliver3d_level",Math.max(saves.level(),level.number+1));saves.put("deliver3d_best_"+level.number,Math.max(score,saves.number("deliver3d_best_"+level.number,0)));audio.cue("finish");}publish();}
    private void announce(String text){notice=text;noticeTime=3;}
    public void command(String command){if(state==State.LOADING||state==State.ERROR)return;switch(command){
        case "start"->{state=State.PLAYING;status="";input.release();audio.resume();announce("Ride close to customers to deliver");}
        case "pause"->{if(state==State.PLAYING){state=State.PAUSED;input.release();audio.pause();}}
        case "resume"->{if(state==State.PAUSED){state=State.PLAYING;audio.resume();}}
        case "restart"->{loadLevel(level.number);state=State.PLAYING;audio.resume();}
        case "next"->{loadLevel(won?level.number+1:level.number);state=State.PLAYING;audio.resume();}
        case "menu"->{state=State.MENU;input.release();speed=0;}
        case "horn"->{audio.cue("horn");traffic.horn(x,z);}
        case "center"->{tilt.center();announce("Steering centered");}
        case "weather"->{weather.automatic=false;weather.mode=WeatherSystem.Mode.values()[(weather.mode.ordinal()+1)%3];saves.put("native-weather",weather.mode.name());saves.put("native-weather-auto",false);}
        case "recover"->{prevX=x=level.startX;prevZ=z=level.startZ;prevYaw=yaw=level.startYaw*MathUtils.degreesToRadians;smoothElev=WorldGrid.elevation(x,z);speed=0;time=Math.max(0,time-5);announce("Reset to road · 5 second penalty");}
    }publish();}
    public void buy(boolean vehicles,String id,int price,int tokenPrice){if(saves.owned(vehicles).contains(id)){saves.put(vehicles?"deliver3d_vehicle":"deliver3d_hat",id);}else {if(cash<price||tickets<tokenPrice){announce("Not enough funds");publish();return;}cash-=price;tickets-=tokenPrice;saves.buy(vehicles,id,cash,tickets);}vehicleId=saves.text("deliver3d_vehicle","bike");hatId=saves.text("deliver3d_hat","cap");rider.equip(vehicleId,hatId);announce("Equipped");publish();}
    public void settings(){input.tilt=saves.flag("native-tilt",true);tilt.configure(saves.decimal("native-tilt-sensitivity",1),saves.flag("native-tilt-invert",false));audio.volumes(saves.decimal("dispatch-volume-master",.75f),saves.decimal("dispatch-volume-sfx",.8f),saves.decimal("dispatch-volume-ambient",.5f));if(weather!=null){weather.automatic=saves.flag("native-weather-auto",true);weather.dayCycle=saves.flag("native-day-cycle",true);weather.headlights=saves.flag("native-headlights",true);}}
    private void publish(){
        String target="Your first delivery";float distance=0,bearing=0,best=Float.MAX_VALUE;ArrayList<Marker> points=new ArrayList<>();
        float routeProgress=0;
        if(level!=null){
            for(LevelData.Customer c:level.customers)if(!c.collected){points.add(new Marker(c.x,c.z,0));float d=Vector2.dst2(x,z,c.x,c.z);if(inventory>0&&d<best){best=d;target=c.name;distance=(float)Math.sqrt(d);bearing=Rules.wrap((float)Math.atan2(c.x-x,c.z-z)-yaw);}}
            for(LevelData.Depot d:level.depots)if(!d.collected){points.add(new Marker(d.x,d.z,1));float range=Vector2.dst2(x,z,d.x,d.z);if(inventory==0&&range<best){best=range;target=d.name;distance=(float)Math.sqrt(range);bearing=Rules.wrap((float)Math.atan2(d.x-x,d.z-z)-yaw);}}
            if(delivered>=level.quota){target="Finish arch";distance=Vector2.dst(x,z,level.finishX,level.finishZ);bearing=Rules.wrap((float)Math.atan2(level.finishX-x,level.finishZ-z)-yaw);}
            float totalRoute=Vector2.dst(level.startX,level.startZ,level.finishX,level.finishZ);
            float distLeft=Vector2.dst(x,z,level.finishX,level.finishZ);
            routeProgress=Rules.clamp(1.0f-distLeft/Math.max(1.0f,totalRoute),0,1);
        }
        snapshot=new Snapshot(state,status,loading,level==null?saves.level():level.number,level==null?"Riverside county":level.district,time,delivered,level==null?3:level.quota,inventory,cash,tickets,score,combo.count,combo.fraction(),combo.fever(),routeProgress,speed,x,z,yaw,steering,target,distance,bearing,weather==null?"CLEAR":weather.mode.name(),weather==null?8.4f:weather.hour,Gdx.graphics==null?0:Gdx.graphics.getFramesPerSecond(),Gdx.graphics==null?0:Gdx.graphics.getBackBufferWidth(),Gdx.graphics==null?0:Gdx.graphics.getBackBufferHeight(),won,stars,notice,Collections.unmodifiableList(points));
    }
    private void fail(Throwable error){
        android.util.Log.e("Dispatch", "CRITICAL ERROR: " + error.getMessage(), error);
        Gdx.app.error("Dispatch","Native game failed",error);
        state=State.ERROR;status=error.getClass().getSimpleName()+": "+error.getMessage();publish();
    }
    @Override public void pause(){input.release();if(state==State.PLAYING)state=State.PAUSED;audio.pause();publish();}
    @Override public void resume(){if(state==State.PLAYING)audio.resume();}
    @Override public void dispose(){if(world!=null)world.close();if(rider!=null)rider.close();if(weather!=null)weather.close();if(scene!=null)scene.dispose();if(assets!=null)assets.close();if(kit!=null)kit.close();if(diffuse!=null)diffuse.dispose();if(specular!=null)specular.dispose();if(brdf!=null)brdf.dispose();}
}

package com.kazisayed.deliverit3d;

import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.Array;
import java.util.*;

/** Adult courier proportions, two-bone limb IK, crank cadence and wheel rotation. */
public final class RiderRig implements AutoCloseable {
    private final MeshKit kit;private final AssetLibrary assets;private final ModelCache frame=new ModelCache();
    private final List<ModelInstance> body=new ArrayList<>();private final Matrix4 root=new Matrix4();
    private final ModelInstance[] wheels=new ModelInstance[2],arms=new ModelInstance[4],legs=new ModelInstance[4],feet=new ModelInstance[2],hands=new ModelInstance[2];
    private ModelInstance torso,head,hat,bag,vehicle,detailedBike,detailedCourier;private final ModelInstance headlightBeam,groundRefl,tailStreak;private final ModelInstance[] cargo=new ModelInstance[4];private AssetLibrary.Template vehicleTemplate;
    private final Vector3 mount=new Vector3(0,.90f,-.14f);private float phase,spin,smoothLean,smoothSteer;private String selected="";private boolean detailed;
    public RiderRig(MeshKit kit,AssetLibrary assets){this.kit=kit;this.assets=assets;
        List<ModelInstance> pieces=new ArrayList<>();Vector3 rear=new Vector3(0,.34f,-.62f),front=new Vector3(0,.34f,.62f),crank=new Vector3(0,.34f,0),saddle=new Vector3(0,.87f,-.2f),fork=new Vector3(0,.86f,.42f);
        tube(pieces,rear,crank,.032f);tube(pieces,rear,saddle,.028f);tube(pieces,saddle,crank,.038f);tube(pieces,saddle,fork,.035f);tube(pieces,crank,fork,.042f);tube(pieces,fork,front,.032f);
        pieces.add(kit.box(kit.rubber,0,.92f,-.23f,.17f,.045f,.27f));tube(pieces,fork,new Vector3(0,1.01f,.40f),.032f);tube(pieces,new Vector3(-.28f,1.02f,.43f),new Vector3(.28f,1.02f,.43f),.028f);
        pieces.add(kit.box(kit.steel,0,.72f,-.62f,.4f,.05f,.55f));pieces.add(kit.box(kit.parcel,0,.91f,-.62f,.4f,.35f,.46f));pieces.add(kit.box(kit.ivory,0,.92f,-.86f,.1f,.34f,.005f));
        pieces.add(kit.ball(kit.lamp,0,.96f,.49f,.065f,.065f,.06f));
        frame.begin();for(ModelInstance i:pieces)frame.add(i);frame.end();
        Model tyre=kit.torus(kit.rubber,.315f,.023f);for(int i=0;i<2;i++)wheels[i]=new ModelInstance(tyre);
        torso=kit.ball(kit.jacket,0,0,0,1,1,1);head=kit.ball(kit.skin,0,0,0,1,1,1);hat=kit.ball(kit.jacket,0,0,0,1,1,1);bag=kit.box(kit.parcel,0,0,0,1,1,1);body.addAll(Arrays.asList(torso,head,hat,bag));
        for(int s=0;s<2;s++){for(int j=0;j<2;j++){arms[s*2+j]=kit.bone(j==0?kit.jacket:kit.skin,new Vector3(),new Vector3(0,1,0),.08f);legs[s*2+j]=kit.bone(kit.trousers,new Vector3(),new Vector3(0,1,0),.11f);body.add(arms[s*2+j]);body.add(legs[s*2+j]);}feet[s]=kit.box(kit.rubber,0,0,0,1,1,1);hands[s]=kit.ball(kit.skin,0,0,0,1,1,1);body.add(feet[s]);body.add(hands[s]);}
        if(assets.deliveryBicycle!=null)detailedBike=new ModelInstance(assets.deliveryBicycle);
        for(int i=0;i<cargo.length;i++)cargo[i]=kit.box(kit.parcel,0,0,0,.30f,.22f,.35f);
        headlightBeam=kit.decal(kit.bikeHeadBeam,0,0,0,3.4f,13.0f);
        groundRefl=kit.decal(kit.carGroundRefl,0,0,0,1.3f,2.4f);
        tailStreak=kit.streak(kit.tailStreak);
    }
    private void tube(List<ModelInstance> p,Vector3 a,Vector3 b,float width){p.add(kit.bone(kit.steel,a,b,width));}
    public void equip(String id,String headwear){
        if(detailedBike==null&&assets.deliveryBicycle!=null)detailedBike=new ModelInstance(assets.deliveryBicycle);
        if(!id.equals(selected)||(!detailed&&id.equals("bike")&&detailedBike!=null)){
            selected=id;detailed=id.equals("bike")&&detailedBike!=null;
            vehicleTemplate=assets.vehicles.get(id.equals("superbike")?"superbike":id.equals("scooter")?"scooter":"");
            vehicle=vehicleTemplate==null?null:vehicleTemplate.create(0,0,0,0,1);
        }
        Model model=assets.couriers.getOrDefault(headwear,assets.couriers.get("cap"));
        if(model!=null)detailedCourier=new ModelInstance(model);
        hat.materials.get(0).set(headwear.equals("crown")?kit.amber:headwear.equals("helmet")?kit.ivory:kit.jacket);
    }
    private void place(ModelInstance i,Vector3 p,float w,float h,float d,float pitch){i.transform.set(root).translate(p).rotate(Vector3.X,pitch).scale(w,h,d);}
    private void segment(ModelInstance i,Vector3 a,Vector3 b,float width){MeshKit.setBone(i,a,b,width);i.transform.mulLeft(root);}
    private static Vector3 elbow(Vector3 a,Vector3 b,float l1,float l2,Vector3 pole){Vector3 axis=new Vector3(b).sub(a);float distance=Rules.clamp(axis.len(),.01f,l1+l2-.002f);axis.nor();float along=(l1*l1-l2*l2+distance*distance)/(2*distance);float height=(float)Math.sqrt(Math.max(.0001f,l1*l1-along*along));Vector3 bend=new Vector3(pole).mulAdd(axis,-pole.dot(axis)).nor();return new Vector3(a).mulAdd(axis,along).mulAdd(bend,height);}
    public void update(float dt,float x,float z,float yaw,float speed,float steering,int inventory,float elev){
        phase+=Math.min(14,Math.abs(speed)*1.55f)*dt;spin+=speed/.34f*dt;
        float targetLean=-steering*Math.min(Math.abs(speed)*.022f,.24f);
        smoothLean=smoothLean+(targetLean-smoothLean)*(1f-(float)Math.exp(-dt*12f));
        root.idt().translate(x,elev,z).rotate(Vector3.Y,yaw*MathUtils.radiansToDegrees).rotate(Vector3.Z,smoothLean*MathUtils.radiansToDegrees);
        float sin=(float)Math.sin(yaw),cos=(float)Math.cos(yaw);
        float lead=5.8f;
        headlightBeam.transform.setToTranslation(x+sin*lead,elev+.038f,z+cos*lead)
            .rotate(Vector3.Y,yaw*MathUtils.radiansToDegrees)
            .scale(3.4f,1f,12.5f);
        groundRefl.transform.setToTranslation(x,elev+.037f,z).rotate(Vector3.Y,yaw*MathUtils.radiansToDegrees).scale(1.3f,1f,2.4f);
        tailStreak.transform.setToTranslation(x-sin*0.8f,elev+.039f,z-cos*0.8f).rotate(Vector3.Y,yaw*MathUtils.radiansToDegrees+180f).scale(1.1f,1f,3.8f);
        for(int i=0;i<cargo.length;i++)cargo[i].transform.set(root).translate(0,.78f+i*.225f,-.66f).scale(.32f,.22f,.38f);
        if(detailed&&detailedBike!=null){
            detailedBike.transform.set(root);
            Node front=detailedBike.getNode("bicycle-front-wheel",true),rear=detailedBike.getNode("bicycle-rear-wheel",true),steer=detailedBike.getNode("bicycle-steer-group",true),crank=detailedBike.getNode("bicycle-crank-group",true);
            if(steer!=null)steer.rotation.set(Vector3.X,12.605f).mul(new Quaternion(Vector3.Y,-steering*26f));
            if(front!=null)front.rotation.set(Vector3.X,-12.605f+spin*MathUtils.radiansToDegrees);
            if(rear!=null)rear.rotation.set(Vector3.X,spin*MathUtils.radiansToDegrees);
            if(crank!=null)crank.rotation.set(Vector3.X,phase*MathUtils.radiansToDegrees);
            detailedBike.calculateTransforms();
            if(detailedCourier!=null){
                detailedCourier.transform.set(root).translate(mount);
                detailedCourier.calculateTransforms();
                return;
            }
        }
        for(int i=0;i<2;i++)wheels[i].transform.set(root).translate(0,.34f,i==0?-.62f:.62f).rotate(Vector3.Y,i==1?-steering*22:0).rotate(Vector3.X,spin*MathUtils.radiansToDegrees);
        float pitch=vehicle==null?22:12;Vector3 torsoCenter=new Vector3(mount).add(0,.20f,.08f),neck=new Vector3(mount).add(0,.45f,.17f);
        place(torso,torsoCenter,.34f,.48f,.23f,pitch);place(head,new Vector3(neck).add(0,.14f,.025f),.17f,.23f,.19f,-8);place(hat,new Vector3(neck).add(0,.235f,.02f),.21f,.13f,.235f,-8);place(bag,new Vector3(torsoCenter).add(0,.04f,-.16f),.28f,.34f,.14f,pitch);
        for(int s=0;s<2;s++){float side=s==0?-1:1;Vector3 shoulder=new Vector3(mount).add(side*.165f,.36f,.13f);Vector3 grip=new Vector3(side*.25f,1.03f,.43f);grip.rotate(Vector3.Y,-steering*20);Vector3 joint=elbow(shoulder,grip,.285f,.265f,new Vector3(side*.25f,-.25f,-1));segment(arms[s*2],shoulder,joint,.095f);segment(arms[s*2+1],joint,grip,.073f);place(hands[s],grip,.075f,.09f,.065f,0);
            float p=phase+s*MathUtils.PI;Vector3 hip=new Vector3(mount).add(side*.095f,-.018f,0);Vector3 ankle=vehicle==null?new Vector3(side*.11f,.411f+.13f*(float)Math.cos(p),-.055f+.13f*(float)Math.sin(p)):new Vector3(side*.18f,.4f,.1f);Vector3 knee=elbow(hip,ankle,.405f,.415f,new Vector3(0,.1f,1));segment(legs[s*2],hip,knee,.13f);segment(legs[s*2+1],knee,ankle,.095f);place(feet[s],new Vector3(ankle).add(0,-.035f,.045f),.095f,.07f,.24f,-5);
        }
        if(vehicle!=null)vehicleTemplate.place(vehicle,x,elev,z,yaw*MathUtils.radiansToDegrees,1);
        if(detailedCourier!=null){
            Vector3 riderMount = (vehicle != null) ? new Vector3(0, .72f, -.12f) : mount;
            detailedCourier.transform.set(root).translate(riderMount);
            detailedCourier.calculateTransforms();
        }
    }
    /** Frame vertices are cached in local space; wrapper applies the cyclist's world transform. */
    public void collect(Array<RenderableProvider> out,int inventory){
        out.add(headlightBeam);
        out.add(groundRefl);
        out.add(tailStreak);
        if(detailed&&detailedBike!=null){
            out.add(detailedBike);
            if(detailedCourier!=null)out.add(detailedCourier);
            else out.addAll(body.toArray(new ModelInstance[0]));
            for(int i=0;i<Math.min(inventory,cargo.length);i++)out.add(cargo[i]);
            return;
        }
        if(vehicle!=null)out.add(vehicle);else {out.add((renderables,pool)->{int start=renderables.size;frame.getRenderables(renderables,pool);for(int i=start;i<renderables.size;i++)renderables.get(i).worldTransform.mulLeft(root);});for(ModelInstance w:wheels)out.add(w);}
        if(detailedCourier!=null)out.add(detailedCourier);else out.addAll(body.toArray(new ModelInstance[0]));
        for(int i=0;i<Math.min(inventory,cargo.length);i++)out.add(cargo[i]);
    }
    @Override public void close(){frame.dispose();}
}

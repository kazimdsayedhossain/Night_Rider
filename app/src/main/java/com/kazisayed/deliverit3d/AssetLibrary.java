package com.kazisayed.deliverit3d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.math.collision.BoundingBox;
import net.mgsx.gltf.loaders.glb.GLBLoader;
import net.mgsx.gltf.scene3d.attributes.*;
import net.mgsx.gltf.scene3d.scene.*;
import java.util.*;

public final class AssetLibrary implements AutoCloseable {
    public static final class Template {
        public final ModelInstance prototype;public final Matrix4 normalization;public final float width,height,length;
        Template(ModelInstance instance,float footprint,float heightLimit,float maxScale,float yaw,boolean zUp,boolean centerXZ,float heightScale){
            prototype=instance;Matrix4 rotation=new Matrix4().rotate(Vector3.Y,yaw);if(zUp)rotation.rotate(Vector3.X,-90);
            BoundingBox raw=instance.calculateBoundingBox(new BoundingBox()).mul(rotation);Vector3 center=raw.getCenter(new Vector3()),size=raw.getDimensions(new Vector3());
            float s=Math.min(maxScale,Math.min(footprint/Math.max(.01f,Math.max(size.x,size.z)),heightLimit/Math.max(.01f,size.y)));
            float ox=centerXZ?-center.x:0, oz=centerXZ?-center.z:0;
            normalization=new Matrix4().scale(s,s*heightScale,s).translate(ox,-raw.min.y,oz).mul(rotation);width=size.x*s;height=size.y*s*heightScale;length=size.z*s;
        }
        Template(ModelInstance instance,float footprint,float heightLimit,float maxScale,float yaw,boolean zUp,boolean centerXZ){
            this(instance,footprint,heightLimit,maxScale,yaw,zUp,centerXZ,1.0f);
        }
        Template(ModelInstance instance,float footprint,float heightLimit,float maxScale,float yaw,boolean zUp){
            this(instance,footprint,heightLimit,maxScale,yaw,zUp,true,1.0f);
        }
        public ModelInstance create(float x,float y,float z,float yaw,float scale){return create(x,y,z,yaw,0f,0f,scale,scale);}
        public ModelInstance create(float x,float y,float z,float yaw,float pitch,float roll,float scale){return create(x,y,z,yaw,pitch,roll,scale,scale);}
        public ModelInstance create(float x,float y,float z,float yaw,float scaleXZ,float scaleY){return create(x,y,z,yaw,0f,0f,scaleXZ,scaleY);}
        public ModelInstance create(float x,float y,float z,float yaw,float pitch,float roll,float scaleXZ,float scaleY){ModelInstance i=new ModelInstance(prototype);place(i,x,y,z,yaw,pitch,roll,scaleXZ,scaleY);return i;}
        public void place(ModelInstance i,float x,float y,float z,float yaw,float scale){place(i,x,y,z,yaw,0f,0f,scale,scale);}
        public void place(ModelInstance i,float x,float y,float z,float yaw,float scaleXZ,float scaleY){place(i,x,y,z,yaw,0f,0f,scaleXZ,scaleY);}
        public void place(ModelInstance i,float x,float y,float z,float yaw,float pitch,float roll,float scaleXZ,float scaleY){
            i.transform.idt().translate(x,y,z).rotate(Vector3.Y,yaw);
            if(pitch!=0f)i.transform.rotate(Vector3.X,pitch);
            if(roll!=0f)i.transform.rotate(Vector3.Z,roll);
            i.transform.scale(scaleXZ,scaleY,scaleXZ).mul(normalization);
        }
        /** Same as place() plus a pitch (radians, nose-up positive) so vehicles follow the road gradient. */
        public void placePitched(ModelInstance i,float x,float y,float z,float yaw,float pitchRad){i.transform.idt().translate(x,y,z).rotate(Vector3.Y,yaw).rotate(Vector3.X,-pitchRad*MathUtils.radiansToDegrees).mul(normalization);}
    }
    public final List<Template> buildings=new ArrayList<>(),houses=new ArrayList<>(),trees=new ArrayList<>(),shrubs=new ArrayList<>();
    public final Map<String,Template> namedBuildings=new LinkedHashMap<>();
    public final Map<String,Template> props=new LinkedHashMap<>();
    public final Map<String,Template> vehicles=new LinkedHashMap<>();public final Map<String,Model> couriers=new LinkedHashMap<>();public Model deliveryBicycle;public Template mountain;
    private final List<SceneAsset> owned=new ArrayList<>();private int stage;public String status="Preparing city assets";
    private static com.badlogic.gdx.graphics.Texture buildingFacadeTexture;

    private static com.badlogic.gdx.graphics.Texture getFallbackFacade() {
        if (buildingFacadeTexture == null) {
            try {
                if (Gdx.files != null && Gdx.files.internal("textures/facade-ceramic-v1.png").exists()) {
                    buildingFacadeTexture = new com.badlogic.gdx.graphics.Texture(Gdx.files.internal("textures/facade-ceramic-v1.png"), true);
                    buildingFacadeTexture.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.MipMapLinearLinear, com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
                    buildingFacadeTexture.setWrap(com.badlogic.gdx.graphics.Texture.TextureWrap.Repeat, com.badlogic.gdx.graphics.Texture.TextureWrap.Repeat);
                }
            } catch (Exception ignored) {}
        }
        return buildingFacadeTexture;
    }

    public static void illuminateWindows(ModelInstance instance){
        if(instance==null||instance.materials==null)return;
        com.badlogic.gdx.graphics.Texture fallback = getFallbackFacade();
        for(com.badlogic.gdx.graphics.g3d.Material m:instance.materials){
            if(m==null||m.id==null)continue;
            String id=m.id.toLowerCase();
            if(id.contains("emit_bulb")||id.contains("window")||id.contains(".006")||id.contains(".007")||id.contains(".010")){
                // Illuminated window panes with warm golden glow
                m.set(net.mgsx.gltf.scene3d.attributes.PBRColorAttribute.createBaseColorFactor(new Color(1f,.88f,.55f,1f)));
                m.set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createEmissive(new Color(1.35f,.86f,.35f,1f)));
                m.set(net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute.createRoughness(.15f));
            } else if(id.contains("glass_curtain")||(id.contains("glass")&&!id.contains("shop"))){
                // Glass curtain / window panes
                m.set(net.mgsx.gltf.scene3d.attributes.PBRColorAttribute.createBaseColorFactor(new Color(.92f,.88f,.78f,1f)));
                m.set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createEmissive(new Color(.95f,.72f,.35f,1f)));
                m.set(net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute.createRoughness(.12f));
            } else if(id.contains("shop")||id.contains("sign")||id.contains("neon")||id.contains("glow")){
                // Storefront windows, signs, and advertisements: retain high-res texture and add warm illumination
                m.set(net.mgsx.gltf.scene3d.attributes.PBRColorAttribute.createBaseColorFactor(Color.WHITE));
                m.set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createEmissive(new Color(.65f,.58f,.45f,1f)));
                m.set(net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute.createRoughness(.30f));
            } else {
                // Building walls, bricks, concrete trim, awnings, doors, roof tiles, parapets:
                // Ensure base texture is preserved at full contrast with crisp color factor
                if(!m.has(net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute.BaseColorTexture) && fallback != null){
                    m.set(net.mgsx.gltf.scene3d.attributes.PBRTextureAttribute.createBaseColorTexture(fallback));
                }
                m.set(net.mgsx.gltf.scene3d.attributes.PBRColorAttribute.createBaseColorFactor(new Color(.94f,.95f,.98f,1f)));
                m.set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createEmissive(new Color(.050f,.058f,.070f,1f)));
                m.set(net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute.createRoughness(.65f));
                m.set(net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute.createMetallic(.04f));
            }
        }
    }

    /**
     * Per-building window palette: every building has active warm window light and shop illumination at night.
     */
    public static void varyWindows(ModelInstance instance,int seed){
        if(instance==null||instance.materials==null)return;
        int roll=(int)(((seed*2654435761L)>>>11)%100);
        float r,g,b;
        if(roll<30){r=1.35f;g=.95f;b=.42f;}          // bright warm gold
        else if(roll<55){r=1.10f;g=.78f;b=.35f;}       // warm amber
        else if(roll<75){r=.85f;g=.65f;b=.30f;}        // soft warm
        else if(roll<88){r=.65f;g=.80f;b=1.00f;}       // cool TV / office
        else {r=.55f;g=.45f;b=.28f;}                   // cozy late-night glow
        for(com.badlogic.gdx.graphics.g3d.Material m:instance.materials){
            if(m==null||m.id==null)continue;
            if(m.has(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Emissive)){
                String id=m.id.toLowerCase();
                if(id.contains("glass")||id.contains("window")||id.contains("shop")||id.contains("glow")||id.contains("emit")||id.contains("bulb")||id.contains("sign")||id.contains(".006")||id.contains(".007")||id.contains(".010"))
                    m.set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createEmissive(new Color(r,g,b,1f)));
            }
        }
    }
    public Template pickBuilding(int variant){return buildings.isEmpty()?null:buildings.get(Math.floorMod(variant,buildings.size()));}

    private SceneAsset load(String name){status="Loading "+name.substring(name.lastIndexOf('/')+1).replace(".glb","");SceneAsset a=new GLBLoader().load(Gdx.files.internal(name));owned.add(a);return a;}
    public boolean loadNext(){
        String[] files={"b1_blue_corner_shop","b2_pink_tower","b3_brick_cafe","b4_music_store","b5_green_bakery","b6_apartment_mart","b7_office_tower"};
        if(stage<files.length){
            String file=files[stage];SceneAsset a=load("buildings/"+file+".glb");
            ModelInstance mi=new ModelInstance(a.scene.model);illuminateWindows(mi);
            float hScale = file.equals("b5_green_bakery") ? 1.55f : file.equals("b3_brick_cafe") ? 1.22f : (file.equals("b1_blue_corner_shop") || file.equals("b4_music_store")) ? 1.18f : 1.0f;
            Template t=new Template(mi,16.5f,42,2.4f,0,false,true,hScale);buildings.add(t);namedBuildings.put(file,t);
        }
        else switch(stage){
            case 7 -> {
                SceneAsset a=load("world/city-architecture.glb");
                for(SceneModel s:a.scenes){
                    ModelInstance mi=new ModelInstance(s.model);illuminateWindows(mi);
                    boolean isHouse = s.name.startsWith("house");
                    boolean isFactory = s.name.contains("factory");
                    boolean isClinic = s.name.contains("clinic");
                    float footprint = isFactory ? 17f : isHouse ? 22f : 24f;
                    float heightLimit = isFactory ? 45f : isHouse ? 28f : 42f;
                    float hScale = isFactory ? 2.4f : isHouse ? 1.75f : isClinic ? 2.4f : 1.25f;
                    Template t = new Template(mi, footprint, heightLimit, 18.0f, 0, false, true, hScale);
                    if(isHouse) houses.add(t);
                    buildings.add(t);
                    namedBuildings.put(s.name, t);
                }
            }
            case 8 -> {
                SceneAsset a=load("world/street-city.glb");
                // Extract apartment blocks and storefronts (Cube.001 to Cube.019)
                for(int i=1;i<=19;i++){
                    String nodeName=String.format(Locale.ROOT,"Cube.%03d",i);
                    try{
                        ModelInstance mi=new ModelInstance(a.scene.model,nodeName,true);
                        illuminateWindows(mi);
                        Template t=new Template(mi,20.0f,40,2.2f,0,false);
                        namedBuildings.put(nodeName,t);
                    }catch(Exception ignored){}
                }
            }
            case 9 -> {
                SceneAsset a=load("buildings/buildings.glb");
                for(int i=1;i<=9;i++){
                    String nodeName=String.format(Locale.ROOT,"Building_%02d",i);
                    try{
                        ModelInstance mi=new ModelInstance(a.scene.model,nodeName,true);
                        illuminateWindows(mi);
                        float hScale = (i == 8 || i == 9) ? 1.75f : 1.0f;
                        Template t=new Template(mi,20.0f,38,2.2f,0,false,true,hScale);
                        namedBuildings.put(nodeName,t);buildings.add(t);
                    }catch(Exception ignored){}
                }
            }
            case 10 -> {
                SceneAsset a=load("world/nature-library.glb");
                for(SceneModel s:a.scenes){
                    String n=s.name;
                    if(n.startsWith("oak")||n.equals("grove-tree")||n.equals("hero-tree"))trees.add(new Template(new ModelInstance(s.model),4.8f,9f,1.0f,0,false,true));
                    else if(n.startsWith("grass"))shrubs.add(new Template(new ModelInstance(s.model),3,1.4f,1,0,false,true));
                }
            }
            case 11 -> {
                SceneAsset a=load("world/mountain-rock.glb");
                mountain=new Template(new ModelInstance(a.scene.model),110,70,50,0,false);
                props.put("rock",new Template(new ModelInstance(a.scene.model),3.8f,2.4f,0.08f,0,false,true));
            }
            case 12 -> vehicle("barracuda","barracuda-440",4.7f,0);
            case 13 -> vehicle("nissan","nissan-r34",4.4f,0);
            case 14 -> vehicle("police","charger-police",4.9f,0);
            case 15 -> vehicle("bus","japanese-bus",9.5f,90);
            case 16 -> vehicle("truck","truck",10.6f,0);
            case 17 -> vehicle("scooter","vespa",1.95f,0);
            case 18 -> vehicle("superbike","harley-bobber",2.35f,90);
            case 19 -> vehicle("guzzi","moto-guzzi",2.25f,-90);
            case 20 -> vehicle("bogdan","bogdan-bus",7.2f,180);
            case 21 -> vehicle("cuda","plymouth-hemi-cuda-1971",4.65f,0);
            case 22 -> {
                SceneAsset a=load("generated/delivery-bicycle.glb");
                deliveryBicycle=a.scene.model;
                for(com.badlogic.gdx.graphics.g3d.Material m:deliveryBicycle.materials){
                    String mid=m.id==null?"":m.id.toLowerCase();
                    if(!mid.contains("tire")&&!mid.contains("rubber")){
                        m.set(PBRFloatAttribute.createRoughness(.14f));
                        m.set(PBRFloatAttribute.createMetallic(.48f));
                    }
                }
            }
            case 23 -> {SceneAsset a=load("generated/courier-cap.glb");couriers.put("cap",a.scene.model);}
            case 24 -> {SceneAsset a=load("generated/courier-helmet.glb");couriers.put("helmet",a.scene.model);}
            case 25 -> {SceneAsset a=load("generated/courier-crown.glb");couriers.put("crown",a.scene.model);}
            case 26 -> prop("barn","generated/barn.glb",28,26,2.4f,0,true,1.4f);
            case 27 -> prop("silo","generated/silo.glb",14,32,2.4f,0,true,1.3f);
            case 28 -> prop("windmill","generated/windmill.glb",16,34,2.2f,0,true,1.3f);
            case 29 -> prop("depot","generated/depot.glb",26,20,3.2f,0,true,2.8f);
            case 30 -> prop("gazebo","generated/gazebo.glb",9,8,1.5f,0,true);
            case 31 -> prop("fountain","generated/fountain.glb",10,6,1.5f,0,true);
            case 32 -> prop("bench","generated/bench.glb",2.4f,1.2f,1.2f,0,true);
            case 33 -> prop("fence","generated/fence.glb",4.0f,1.4f,1.2f,0,false);
            case 34 -> prop("hay-bale","generated/hay-bale.glb",2.2f,1.8f,1.2f,0,true);
            case 35 -> {
                SceneAsset a=load("generated/street-lamp.glb");
                ModelInstance mi=new ModelInstance(a.scene.model);
                for(int idx=0;idx<mi.materials.size;idx++){
                    com.badlogic.gdx.graphics.g3d.Material m=mi.materials.get(idx);
                    if(idx==0){
                        // Stone plinth
                        m.set(PBRColorAttribute.createBaseColorFactor(new Color(.12f,.14f,.16f,1f)));
                        m.set(ColorAttribute.createEmissive(new Color(0f,0f,0f,1f)));
                        m.set(PBRFloatAttribute.createMetallic(0f));
                        m.set(PBRFloatAttribute.createRoughness(.9f));
                    }else if(idx==1){
                        // Black metal pole, collar, arm, fixture housing
                        m.set(PBRColorAttribute.createBaseColorFactor(new Color(.08f,.08f,.09f,1f)));
                        m.set(ColorAttribute.createEmissive(new Color(0f,0f,0f,1f)));
                        m.set(PBRFloatAttribute.createMetallic(.85f));
                        m.set(PBRFloatAttribute.createRoughness(.45f));
                    }else if(idx==2){
                        // 3D Luminaire Bulb: soft warm golden sodium glow (natural, never harsh white)
                        m.set(PBRColorAttribute.createBaseColorFactor(new Color(1f,.86f,.52f,1f)));
                        m.set(ColorAttribute.createEmissive(new Color(2.6f,1.9f,.9f,1f)));
                        m.set(PBRFloatAttribute.createMetallic(0f));
                        m.set(PBRFloatAttribute.createRoughness(.08f));
                    }else if(idx==3||idx==4){
                        // Baked halo / ground-pool meshes of the GLB: hidden. They were PBR-lit alpha quads that
                        // stacked and went blotchy; WorldScene now draws unlit additive pools, camera-facing halos
                        // and wet-road reflection streaks per lamp (see MeshKit / WorldScene.LampFx).
                        m.set(PBRColorAttribute.createBaseColorFactor(new Color(0f,0f,0f,0f)));
                        m.set(ColorAttribute.createEmissive(new Color(0f,0f,0f,1f)));
                        m.set(new BlendingAttribute(GL20.GL_ONE,GL20.GL_ONE,0f));
                        m.set(new DepthTestAttribute(GL20.GL_LEQUAL,false));
                        m.set(new IntAttribute(IntAttribute.CullFace,0));
                    }
                }
                Template lampTemplate=new Template(mi,6.4f,7.5f,1.0f,0,false,false);   // bounds measured WITH the baked meshes (placement unchanged)
                stripBakedLightMeshes(mi);
                props.put("street-lamp",lampTemplate);
            }
            case 36 -> {
                SceneAsset a=load("generated/traffic-light.glb");
                ModelInstance mi=new ModelInstance(a.scene.model);
                for(int idx=0;idx<mi.materials.size;idx++){
                    com.badlogic.gdx.graphics.g3d.Material m=mi.materials.get(idx);
                    if(idx==0){
                        m.set(net.mgsx.gltf.scene3d.attributes.PBRColorAttribute.createBaseColorFactor(new Color(.08f,.08f,.09f,1f)));
                        m.set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createEmissive(new Color(0f,0f,0f,1f)));
                        m.set(net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute.createMetallic(.85f));
                        m.set(net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute.createRoughness(.45f));
                    }else{
                        int mod=(idx-1)%3;
                        if(mod==0){
                            m.set(net.mgsx.gltf.scene3d.attributes.PBRColorAttribute.createBaseColorFactor(new Color(.95f,.15f,.1f,1f)));
                            m.set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createEmissive(new Color(2.4f,.3f,.15f,1f)));
                        }else if(mod==1){
                            m.set(net.mgsx.gltf.scene3d.attributes.PBRColorAttribute.createBaseColorFactor(new Color(1f,.72f,.12f,1f)));
                            m.set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createEmissive(new Color(.4f,.28f,.05f,1f)));
                        }else{
                            m.set(net.mgsx.gltf.scene3d.attributes.PBRColorAttribute.createBaseColorFactor(new Color(.12f,.95f,.35f,1f)));
                            m.set(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.createEmissive(new Color(.3f,2.5f,.7f,1f)));
                        }
                        m.set(net.mgsx.gltf.scene3d.attributes.PBRFloatAttribute.createRoughness(.15f));
                    }
                }
                props.put("traffic-light",new Template(mi,2.4f,6.8f,1.2f,0,false,false));
            }
            case 37 -> prop("finish","generated/finish.glb",14.0f,6.5f,1.2f,0,true);
            case 38 -> prop("train-car","generated/train-car.glb",18.0f,4.5f,1.2f,0,true);
            case 39 -> prop("coin","generated/coin.glb",1.5f,1.5f,1.2f,0,true);
            default -> {status="City ready";return true;}
        }stage++;return false;
    }
    /** Remove the lamp GLB's baked halo / ground-pool / glow-cone parts (the flat translucent squares seen around the
     *  lamp heads). Matches by material slot (>=3), blending, or a halo/pool/glow/cone/light-ish material or node name. */
    private void stripBakedLightMeshes(ModelInstance mi){
        for(int i=0;i<mi.materials.size;i++)if(Gdx.app!=null)Gdx.app.log("street-lamp","material "+i+" id="+mi.materials.get(i).id);
        for(com.badlogic.gdx.graphics.g3d.model.Node n:mi.nodes)stripNode(mi,n);
    }
    private void stripNode(ModelInstance mi,com.badlogic.gdx.graphics.g3d.model.Node n){
        String nid=n.id==null?"":n.id.toLowerCase();
        for(int i=n.parts.size-1;i>=0;i--){
            com.badlogic.gdx.graphics.g3d.Material m=n.parts.get(i).material;String id=m.id==null?"":m.id.toLowerCase();
            boolean slot=mi.materials.indexOf(m,true)>=3;
            boolean name=id.contains("halo")||id.contains("pool")||id.contains("glow")||id.contains("cone")||id.contains("beam")||nid.contains("halo")||nid.contains("pool")||nid.contains("glow")||nid.contains("cone")||nid.contains("illum");
            boolean blended=m.has(BlendingAttribute.Type)&&((BlendingAttribute)m.get(BlendingAttribute.Type)).blended&&((BlendingAttribute)m.get(BlendingAttribute.Type)).opacity>0f;
            if(slot||name||blended)n.parts.removeIndex(i);
        }
        for(com.badlogic.gdx.graphics.g3d.model.Node c:n.getChildren())stripNode(mi,c);
    }
    private void vehicle(String id,String file,float size,float yaw){
        SceneAsset a=load("vehicles/"+file+".glb");
        ModelInstance mi=new ModelInstance(a.scene.model);
        for(int idx=0;idx<mi.materials.size;idx++){
            com.badlogic.gdx.graphics.g3d.Material m=mi.materials.get(idx);
            String mid=m.id==null?"":m.id.toLowerCase();
            if(mid.contains("glass")||mid.contains("wind")||mid.contains("window")){
                m.set(PBRFloatAttribute.createRoughness(.04f));
                m.set(PBRFloatAttribute.createMetallic(.40f));
            }else if(mid.contains("tire")||mid.contains("rubber")||mid.contains("wheel")){
                m.set(PBRFloatAttribute.createRoughness(.85f));
                m.set(PBRFloatAttribute.createMetallic(.02f));
            }else{
                // High-gloss automotive clearcoat finish for vehicle paint reflections
                m.set(PBRFloatAttribute.createRoughness(.12f));
                m.set(PBRFloatAttribute.createMetallic(.45f));
            }
        }
        vehicles.put(id,new Template(mi,size,8,1000,yaw,false,true));
    }
    private void prop(String id,String file,float footprint,float height,float maxScale,float yaw,boolean centerXZ,float heightScale){SceneAsset a=load(file);props.put(id,new Template(new ModelInstance(a.scene.model),footprint,height,maxScale,yaw,false,centerXZ,heightScale));}
    private void prop(String id,String file,float footprint,float height,float maxScale,float yaw,boolean centerXZ){prop(id,file,footprint,height,maxScale,yaw,centerXZ,1.0f);}
    public Template getBuilding(String id){
        if("city-core-set".equals(id)) {
            Template t = namedBuildings.get("landmark-factory");
            if (t != null) return t;
            id = "b7_office_tower";
        } else if("street-frontage-set".equals(id)) {
            Template t = namedBuildings.get("landmark-housing");
            if (t != null) return t;
            id = "b6_apartment_mart";
        }
        Template t=namedBuildings.get(id);
        if(t!=null)return t;
        t=props.get(id);
        if(t!=null)return t;
        if(!buildings.isEmpty())return buildings.get(0);
        return null;
    }
    public Template getProp(String id){return props.get(id);}
    public float progress(){return Math.min(1,stage/40f);}
    @Override public void close(){
        for(SceneAsset a:owned)a.dispose();
        if(buildingFacadeTexture!=null){buildingFacadeTexture.dispose();buildingFacadeTexture=null;}
    }
}

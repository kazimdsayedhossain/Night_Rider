package com.kazisayed.deliverit3d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.utils.*;
import com.badlogic.gdx.math.*;
import net.mgsx.gltf.scene3d.attributes.*;
import java.util.*;

/** Shared materials and geometry. Instances never own or dispose their source assets. */
public final class MeshKit implements AutoCloseable {
    public static final long ATTR=VertexAttributes.Usage.Position|VertexAttributes.Usage.Normal|VertexAttributes.Usage.TextureCoordinates;
    private final List<Model> owned=new ArrayList<>();private final List<Texture> textures=new ArrayList<>();
    public final Material asphalt,sidewalk,grass,earth,stone,steel,rubber,ivory,amber,skin,jacket,trousers,glass,water,wood,lamp,parcel,snow,lightPool,gravel,headBeam,bikeHeadBeam,puddle,carGroundRefl,haloMat,lampStreak,tailStreak,brakeStreak,headStreak;
    public final Model cube,sphere,cylinder;private final Map<Material,Model> cubes=new IdentityHashMap<>(),spheres=new IdentityHashMap<>();
    public MeshKit(){
        // Rain-slicked wet asphalt: dark wet tint, moderately glossy, with patchy roughness
        // (puddly sheen in the ruts, drier patches between) so the wetness reads as a surface and not a mirror.
        asphalt=textured("cracked-asphalt.jpg",.38f,new Color(.66f,.70f,.73f,1f));
        asphalt.set(PBRFloatAttribute.createMetallic(.08f));
        asphalt.set(PBRFloatAttribute.createRoughness(.38f));
        addRoughnessPatches(asphalt,.43f,3);

        sidewalk=textured("sidewalk-pavers-v1.png",.46f,new Color(.84f,.87f,.90f,1f));
        sidewalk.set(PBRFloatAttribute.createMetallic(.05f));
        sidewalk.set(PBRFloatAttribute.createRoughness(.46f));
        addRoughnessPatches(sidewalk,.31f,17);

        grass=textured("grass-lawn-v1.jpg",.95f,Color.WHITE);
        earth=textured("grass-lush-v1.jpg",.95f,Color.WHITE);
        stone=material("787670",.88f,0);
        steel=material("454d51",.28f,.82f);
        rubber=material("171b1d",.9f,0);
        ivory=material("f4f2ea",.6f,0);
        amber=material("ffc430",.2f,.35f);
        amber.set(ColorAttribute.createEmissive(new Color(1.8f,1.25f,.25f,1f)));
        skin=material("ab785c",.85f,0);
        jacket=material("596064",.92f,0);
        trousers=material("283136",.92f,0);
        glass=material("354a55",.13f,.45f);
        water=new Material(
            PBRColorAttribute.createBaseColorFactor(new Color(.28f,.72f,.85f,1f)),
            PBRTextureAttribute.createBaseColorTexture(waterTexture()),
            PBRFloatAttribute.createMetallic(.18f),
            PBRFloatAttribute.createRoughness(.05f)
        );
        addWaterRoughness(water);
        wood=material("6c5945",.94f,0);
        gravel=material("57544f",.96f,0);
        parcel=material("bb925b",1,0);
        snow=material("e4e7e5",.83f,0);
        lamp=material("ffe4b0",.3f,0);
        lamp.set(ColorAttribute.createEmissive(new Color(3.0f,2.18f,1.08f,1f)));

        // ---- Light decals (same shapes / opacities as the Three.js reference) -----------------------------------
        // All of them are unlit + additive (ONE,ONE) with the intensity baked into the texture RGB, so overlapping
        // pools/beams add up like real light instead of PBR-shading a flat quad and stacking alpha.
        // Palette follows the JS materials: lamp pool 0xffd2a0 @ .22, bike beam @ .26, car beam @ .14,
        // halo 0xffbd7c @ .38, lamp streak 0xffc890 @ .42, tail 0xff2a18 @ .5 (brake .85), head 0xffeccc @ .18.
        lightPool=glow(poolTexture(.36f,new float[]{1f,.82f,.63f}));
        bikeHeadBeam=glow(bikeBeamTexture(.26f));
        headBeam=glow(carBeamTexture(.17f));
        haloMat=glow(haloTexture(.57f,new float[]{1f,.74f,.49f}));
        float[] single=streakLuma(new float[]{.5f},3), dual=streakLuma(new float[]{.2f,.8f},11);
        lampStreak=glow(tint(single,.63f,new float[]{1f,.78f,.56f}));
        tailStreak=glow(tint(dual,.50f,new float[]{1f,.165f,.094f}));
        brakeStreak=glow(tint(dual,.85f,new float[]{1f,.165f,.094f}));
        headStreak=glow(tint(dual,.18f,new float[]{1f,.925f,.80f}));

        // ---- Wet Asphalt Vehicle Ground Reflection: soft contact shadow + glossy wet road reflection sheen -------
        carGroundRefl=new Material(
            PBRTextureAttribute.createBaseColorTexture(carReflTexture()),
            new BlendingAttribute(GL20.GL_SRC_ALPHA,GL20.GL_ONE_MINUS_SRC_ALPHA,.82f),
            new DepthTestAttribute(GL20.GL_LEQUAL,false),
            new IntAttribute(IntAttribute.CullFace,0),
            PBRFloatAttribute.createMetallic(.18f),
            PBRFloatAttribute.createRoughness(.12f)
        );

        // ---- Puddles: dark, feathered, only slightly glossier than the wet road (JS roadPuddleMaterial) ---------
        // A near-mirror puddle reflects one flat patch of sky glow as a bright oval, so it stays dark & semi-opaque.
        puddle=new Material(
            PBRTextureAttribute.createBaseColorTexture(puddleTexture()),
            PBRColorAttribute.createBaseColorFactor(new Color(.024f,.038f,.052f,1f)),
            new BlendingAttribute(GL20.GL_SRC_ALPHA,GL20.GL_ONE_MINUS_SRC_ALPHA,.62f),
            new DepthTestAttribute(GL20.GL_LEQUAL,false),
            new IntAttribute(IntAttribute.CullFace,0),
            PBRFloatAttribute.createMetallic(0),
            PBRFloatAttribute.createRoughness(.28f)
        );

        cube=boxModel(steel);sphere=sphereModel(skin);cylinder=own(new ModelBuilder().createCylinder(1,1,1,12,steel,ATTR));
    }
    public static Material material(String hex,float rough,float metal){return new Material(PBRColorAttribute.createBaseColorFactor(Color.valueOf(hex)),PBRFloatAttribute.createMetallic(metal),PBRFloatAttribute.createRoughness(rough));}
    public Material textured(String filename,float rough){return textured(filename,rough,Color.WHITE);}
    public Material textured(String filename,float rough,Color tint){Texture t=new Texture(Gdx.files.internal("textures/"+filename),true);t.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);t.setWrap(Texture.TextureWrap.Repeat,Texture.TextureWrap.Repeat);textures.add(t);return new Material(PBRColorAttribute.createBaseColorFactor(tint),PBRTextureAttribute.createBaseColorTexture(t),PBRFloatAttribute.createMetallic(0),PBRFloatAttribute.createRoughness(rough));}
    public Model own(Model model){owned.add(model);return model;}
    public Model boxModel(Material m){return cubes.computeIfAbsent(m,k->own(new ModelBuilder().createBox(1,1,1,m,ATTR)));}
    public Model sphereModel(Material m){return spheres.computeIfAbsent(m,k->own(new ModelBuilder().createSphere(1,1,1,14,10,m,ATTR)));}
    public ModelInstance box(Material m,float x,float y,float z,float w,float h,float d){ModelInstance i=new ModelInstance(boxModel(m));i.transform.setToTranslation(x,y,z).scale(w,h,d);return i;}
    public ModelInstance ball(Material m,float x,float y,float z,float w,float h,float d){ModelInstance i=new ModelInstance(sphereModel(m));i.transform.setToTranslation(x,y,z).scale(w,h,d);return i;}
    public ModelInstance bone(Material m,Vector3 from,Vector3 to,float width){ModelInstance i=new ModelInstance(cylinder);i.materials.get(0).set(m);setBone(i,from,to,width);return i;}
    public static void setBone(ModelInstance i,Vector3 from,Vector3 to,float width){Vector3 direction=new Vector3(to).sub(from);float len=direction.len();Quaternion q=new Quaternion().setFromCross(Vector3.Y,direction.nor());i.transform.set(new Vector3(from).add(to).scl(.5f),q,new Vector3(width,len,width));}
    public static void rect(MeshPartBuilder p,float x,float z,float y,float w,float d,float repeat){
        float uMax=w/repeat,vMax=d/repeat;
        float hx=w*.5f,hz=d*.5f;
        MeshPartBuilder.VertexInfo v0=new MeshPartBuilder.VertexInfo().setPos(x-hx,y,z+hz).setNor(0,1,0).setUV(0,vMax);
        MeshPartBuilder.VertexInfo v1=new MeshPartBuilder.VertexInfo().setPos(x+hx,y,z+hz).setNor(0,1,0).setUV(uMax,vMax);
        MeshPartBuilder.VertexInfo v2=new MeshPartBuilder.VertexInfo().setPos(x+hx,y,z-hz).setNor(0,1,0).setUV(uMax,0);
        MeshPartBuilder.VertexInfo v3=new MeshPartBuilder.VertexInfo().setPos(x-hx,y,z-hz).setNor(0,1,0).setUV(0,0);
        p.rect(v0,v1,v2,v3);
    }
    /** Flat, textured decal quad lying on the ground (centre x,z; width along x, length along z). */
    public ModelInstance decal(Material m,float x,float y,float z,float w,float l){ModelInstance i=shared(groundQuads.computeIfAbsent(m,k->{ModelBuilder b=new ModelBuilder();b.begin();MeshPartBuilder p=b.part("decal",GL20.GL_TRIANGLES,ATTR,m);p.rect(new MeshPartBuilder.VertexInfo().setPos(-.5f,0,.5f).setNor(0,1,0).setUV(0,1),new MeshPartBuilder.VertexInfo().setPos(.5f,0,.5f).setNor(0,1,0).setUV(1,1),new MeshPartBuilder.VertexInfo().setPos(.5f,0,-.5f).setNor(0,1,0).setUV(1,0),new MeshPartBuilder.VertexInfo().setPos(-.5f,0,-.5f).setNor(0,1,0).setUV(0,0));return own(b.end());}));i.transform.setToTranslation(x,y,z).scale(w,1,l);return i;}
    public Model torus(Material m,float radius,float tube){
        ModelBuilder builder=new ModelBuilder();builder.begin();MeshPartBuilder p=builder.part("wheel",GL20.GL_TRIANGLES,ATTR,m);
        for(int a=0;a<32;a++)for(int b=0;b<8;b++){
            MeshPartBuilder.VertexInfo[] v=new MeshPartBuilder.VertexInfo[4];
            int[] aa={a,a+1,a+1,a},bb={b,b,b+1,b+1};
            for(int k=0;k<4;k++){double u=aa[k]*Math.PI/16,t=bb[k]*Math.PI/4;float nx=(float)Math.sin(t),ny=(float)(Math.cos(t)*Math.cos(u)),nz=(float)(Math.cos(t)*Math.sin(u));v[k]=new MeshPartBuilder.VertexInfo().setPos(tube*nx,(radius+tube*(float)Math.cos(t))*(float)Math.cos(u),(radius+tube*(float)Math.cos(t))*(float)Math.sin(u)).setNor(nx,ny,nz).setUV(aa[k]/32f,bb[k]/8f);}p.rect(v[0],v[1],v[2],v[3]);
        }return own(builder.end());
    }

    // ===================================================================================================================
    // Light-decal helpers
    // ===================================================================================================================
    private static final float[] WHITE3={1f,1f,1f};
    private final Color nightTint=new Color(1,1,1,1);
    private final List<Material> glowMaterials=new ArrayList<>();
    private final Map<Material,Model> groundQuads=new IdentityHashMap<>(),streakQuads=new IdentityHashMap<>(),billboardQuads=new IdentityHashMap<>();

    /** Unlit additive material: output = texture.rgb added to the framebuffer (intensity is baked in the texture). */
    private Material glow(Texture t){
        Material m=new Material(
            PBRTextureAttribute.createBaseColorTexture(t),
            PBRColorAttribute.createBaseColorFactor(new Color(1,1,1,1)),
            new PBRFlagAttribute(PBRFlagAttribute.Unlit),
            new BlendingAttribute(GL20.GL_ONE,GL20.GL_ONE,1f),
            new DepthTestAttribute(GL20.GL_LEQUAL,false),
            new IntAttribute(IntAttribute.CullFace,0));
        glowMaterials.add(m);return m;
    }
    /** 0 = daylight (all emitted-light decals off), 1 = full night. Called every frame by WeatherSystem. */
    public void setNight(float n){
        n=Math.max(0,Math.min(1,n));if(Math.abs(n-nightTint.r)<.002f)return;nightTint.set(n,n,n,1);
        for(Material m:glowMaterials)((PBRColorAttribute)m.get(PBRColorAttribute.BaseColorFactor)).color.set(nightTint);
    }
    public float night(){return nightTint.r;}

    private Texture upload(Pixmap pix,boolean repeat){
        Texture t=new Texture(pix,true);t.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);
        Texture.TextureWrap w=repeat?Texture.TextureWrap.Repeat:Texture.TextureWrap.ClampToEdge;t.setWrap(w,w);
        textures.add(t);pix.dispose();return t;
    }
    private static Pixmap pixmap(int w,int h){Pixmap p=new Pixmap(w,h,Pixmap.Format.RGBA8888);p.setBlending(Pixmap.Blending.None);return p;}
    private static float clamp01(float v){return v<0?0:v>1?1:v;}
    private static float lerp(float a,float b,float t){return a+(b-a)*t;}
    private static float smooth(float a,float b,float x){float t=clamp01((x-a)/(b-a));return t*t*(3-2*t);}
    /** Piecewise-linear gradient lookup, like canvas addColorStop. */
    private static float stops(float d,float[] pos,float[] val){
        if(d<=pos[0])return val[0];
        for(int i=1;i<pos.length;i++)if(d<=pos[i])return lerp(val[i-1],val[i],(d-pos[i-1])/(pos[i]-pos[i-1]));
        return val[val.length-1];
    }
    private static int b8(float v){return Math.min(255,Math.max(0,Math.round(v*255f)));}
    /** premultiplied additive pixel: rgb = colour * coverage * opacity, alpha unused (ONE,ONE). */
    private static void put(Pixmap p,int x,int y,float r,float g,float b,float a,float opacity,float[] tint){
        float k=a*opacity;p.drawPixel(x,y,(b8(r*tint[0]*k)<<24)|(b8(g*tint[1]*k)<<16)|(b8(b*tint[2]*k)<<8)|255);
    }

    /** Street lamp ground pool (JS createStreetLightPoolTexture): inverse-square-ish warm falloff, desaturated core. */
    private Texture poolTexture(float opacity,float[] tint){
        final int S=256;Pixmap p=pixmap(S,S);
        float[] pos={0f,.16f,.40f,.70f,1f},al={.58f,.38f,.15f,.04f,0f};
        float[] cr={1f,1f,1f,.94f,.94f},cg={.925f,.847f,.73f,.60f,.60f},cb={.78f,.62f,.41f,.28f,.28f};
        for(int y=0;y<S;y++)for(int x=0;x<S;x++){
            float dx=(x+.5f-S/2f)/(S/2f),dy=(y+.5f-S/2f)/(S/2f),d=(float)Math.sqrt(dx*dx+dy*dy)/.957f;
            float a=stops(d,pos,al);a*=1f-smooth(.9f,1f,d); // guarantee a zero edge
            put(p,x,y,stops(d,pos,cr),stops(d,pos,cg),stops(d,pos,cb),a,opacity,tint);
        }
        return upload(p,false);
    }
    /** Two-wheeler beam: soft forward ellipse + hotspot on the road in front of the tyre. Row 0 = tyre end. */
    private Texture bikeBeamTexture(float opacity){
        final int W=256,H=512;Pixmap p=pixmap(W,H);
        float[] pos={0f,.25f,.55f,.85f,1f},al={.65f,.38f,.16f,.03f,0f};
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            float cy=H-1-y; // canvas y (JS): hotspot sits near the tyre = bottom of the canvas = row 0 here
            float dx=(x-128f)/(205f*.54f),dy=(cy-292f)/(205f*1.28f),d=(float)Math.sqrt(dx*dx+dy*dy);
            float a1=stops(d,pos,al)*(1f-smooth(.88f,1f,d));
            float c1r=1f,c1g=lerp(.99f,.84f,clamp01(d)),c1b=lerp(.92f,.45f,clamp01(d));
            float ex=(x-128f)/(68f*.46f),ey=(cy-377f)/(68f*.90f),e=(float)Math.sqrt(ex*ex+ey*ey);
            float a2=stops(e,new float[]{0f,.38f,1f},new float[]{.68f,.28f,0f})*(1f-smooth(.88f,1f,e));
            float c2g=lerp(1f,.92f,clamp01(e)),c2b=lerp(.97f,.67f,clamp01(e));
            // core over main (premultiplied "over")
            float ar=a2+a1*(1f-a2);if(ar<=1e-5f){put(p,x,y,0,0,0,0,1,WHITE3);continue;}
            float r=(1f*a2+c1r*a1*(1f-a2))/ar,g=(c2g*a2+c1g*a1*(1f-a2))/ar,b=(c2b*a2+c1b*a1*(1f-a2))/ar;
            put(p,x,y,r,g,b,ar,opacity,WHITE3);
        }
        return upload(p,false);
    }
    /** Car beam: two projector lobes, each with a small hotspot near the bumper. Row 0 = bumper end. */
    private Texture carBeamTexture(float opacity){
        final int W=384,H=512;Pixmap p=pixmap(W,H);
        float[] pos={0f,.25f,.60f,.88f,1f},al={.52f,.28f,.10f,.02f,0f};
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            float cy=H-1-y,pr=0,pg=0,pb=0;
            for(int side=-1;side<=1;side+=2){
                float cx=192f+side*56f,lx=cx+side*14f;
                float dx=(x-lx)/(175f*.56f),dy=(cy-275f)/(175f*1.25f),d=(float)Math.sqrt(dx*dx+dy*dy);
                float a=stops(d,pos,al)*(1f-smooth(.88f,1f,d));
                float k=clamp01(d);float cr=1f,cg=lerp(.97f,.89f,k),cb=lerp(.88f,.72f,k); // warm-white halogen, not blue
                float hx=(x-cx)/(52f*.46f),hy=(cy-385f)/(52f*.86f),h=(float)Math.sqrt(hx*hx+hy*hy);
                float ah=stops(h,new float[]{0f,.4f,1f},new float[]{.58f,.22f,0f})*(1f-smooth(.88f,1f,h));
                float ao=ah+a*(1f-ah);
                // accumulate premultiplied light additively between the two lobes (they only slightly overlap)
                float or=1f*ah+cr*a*(1f-ah),og=1f*ah+cg*a*(1f-ah),ob=.97f*ah+cb*a*(1f-ah);
                pr+=or;pg+=og;pb+=ob;
            }
            float a=Math.min(1f,Math.max(pr,Math.max(pg,pb)));
            if(a<=1e-5f){put(p,x,y,0,0,0,0,1,WHITE3);continue;}
            float s=Math.min(1f,a)/Math.max(pr,Math.max(pg,pb));
            put(p,x,y,pr*s/a,pg*s/a,pb*s/a,a,opacity,WHITE3);
        }
        return upload(p,false);
    }
    /** Lamp / tail-light halo (JS createLampHaloTexture): white core + wide faint wet-air scatter. */
    private Texture haloTexture(float opacity,float[] tint){
        final int S=128;Pixmap p=pixmap(S,S);
        float[] pos={0f,.06f,.18f,.45f,1f},al={1f,.85f,.30f,.08f,0f};
        float[] cr={1f,1f,1f,1f,1f},cg={1f,.933f,.804f,.667f,.667f},cb={1f,.804f,.549f,.353f,.353f};
        for(int y=0;y<S;y++)for(int x=0;x<S;x++){
            float dx=(x+.5f-S/2f)/(S/2f),dy=(y+.5f-S/2f)/(S/2f),d=(float)Math.sqrt(dx*dx+dy*dy);
            float a=stops(d,pos,al)*(1f-smooth(.92f,1f,d));
            put(p,x,y,stops(d,pos,cr),stops(d,pos,cg),stops(d,pos,cb),a,opacity,tint);
        }
        return upload(p,false);
    }
    // --- reflection streaks (JS createReflectionStreakTexture), row 0 = foot of the streak at the light -------------
    private static float[] streakLuma(float[] lobes,int seed){
        final int W=128,H=256;float[] out=new float[W*H];
        int a=seed;float phase;{a+=0x6D2B79F5;int t=a;t=(t^(t>>>15))*(t|1);t^=t+(t^(t>>>7))*(t|61);phase=(((t^(t>>>14))&0xffffffffL)/4294967296.0f)*6.28f;}
        float sigma0=lobes.length>1?.035f:.05f;
        for(int y=0;y<H;y++){
            float t=y/(float)(H-1),along=(float)Math.pow(1f-t,1.7f);
            float ripple=.72f+.28f*(float)(Math.sin(t*46+phase)*Math.sin(t*17+phase*2f));
            float sigma=sigma0*(1f+t*1.4f);
            for(int x=0;x<W;x++){
                float u=x/(float)(W-1),g=0;
                for(float c:lobes)g+=(float)Math.exp(-((u-c)*(u-c))/(2*sigma*sigma));
                out[y*W+x]=Math.min(1f,along*ripple*g);
            }
        }
        return out;
    }
    private Texture tint(float[] luma,float opacity,float[] colour){
        final int W=128,H=256;Pixmap p=pixmap(W,H);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){float v=luma[y*W+x];put(p,x,y,1f,1f,1f,v,opacity,colour);}
        return upload(p,false);
    }
    /** Organic, feathered puddle outline (JS getPuddleTexture shape). White RGB, coverage in alpha. */
    private Texture puddleTexture(){
        final int S=256;Pixmap p=pixmap(S,S);
        for(int y=0;y<S;y++)for(int x=0;x<S;x++){
            float dx=x+.5f-128f,dy=(y+.5f-128f)/.75f;
            float ang=(float)Math.atan2(dy,dx),dist=(float)Math.sqrt(dx*dx+dy*dy);
            float edge=96f+(float)Math.sin(ang*3)*12f+(float)Math.cos(ang*2)*8f+(float)Math.sin(ang*5)*4f;
            float inside=smooth(0f,.30f,1f-dist/edge);              // wide feather: no hard "sticker" edge
            float body=stops(dist/110f,new float[]{0f,.55f,.85f,1f},new float[]{.95f,.82f,.45f,0f});
            float a=inside*clamp01(body*1.05f);
            p.drawPixel(x,y,0xffffff00|b8(a));
        }
        return upload(p,false);
    }
    /** Wet asphalt contact reflection underneath vehicles: feathered dark footprint with glossy wet specular sheen */
    private Texture carReflTexture(){
        final int W=256,H=512;Pixmap p=pixmap(W,H);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            float dx=(x+.5f-W/2f)/(W/2f),dy=(y+.5f-H/2f)/(H/2f);
            float d=(float)Math.sqrt(dx*dx+dy*dy);
            if(d>=1.0f)continue;
            float falloff=1f-smooth(0.32f,1.0f,d);
            int a=b8(falloff*0.75f);
            p.drawPixel(x,y,(a>0)?((0x06<<24)|(0x0a<<16)|(0x14<<8)|a):0);
        }
        return upload(p,false);
    }
    private Texture waterTexture(){
        final int S=512;Pixmap p=pixmap(S,S);
        final float pi2=(float)(Math.PI*2.0);
        for(int y=0;y<S;y++)for(int x=0;x<S;x++){
            float u=x/(float)S, v=y/(float)S;
            float ax=u*pi2, ay=v*pi2;
            // Primary deep swells
            float w1=(float)(Math.sin(ax*2f+ay)*Math.cos(ay*2f-ax));
            // Secondary crossing wave ripples
            float w2=(float)(Math.sin(ax*4f-ay*3f+1.2f)*Math.cos(ax*3f+ay*4f-0.7f));
            // Finer surface capillary ripples
            float w3=(float)(Math.sin(ax*8f+ay*6f+2.5f)*Math.sin(ax*6f-ay*8f));
            float w4=(float)(Math.sin(ax*12f-ay*10f)*Math.cos(ax*10f+ay*12f)*0.5f);
            // Shallow refracted caustic web patterns
            float c1=(float)Math.abs(Math.sin(ax*4f+Math.cos(ay*3f)));
            float c2=(float)Math.abs(Math.cos(ay*4f+Math.sin(ax*3f)));
            float caustic=(float)Math.pow(1.0-Math.min(1.0,c1*c2),2.4);
            float c3=(float)Math.abs(Math.sin(ax*8f+Math.cos(ay*6f)));
            float c4=(float)Math.abs(Math.cos(ay*8f+Math.sin(ax*6f)));
            float fineCaustic=(float)Math.pow(1.0-Math.min(1.0,c3*c4),2.0)*0.5f;

            float h=0.48f+0.22f*w1+0.16f*w2+0.09f*w3+0.05f*w4+0.16f*(caustic-0.28f)+0.08f*(fineCaustic-0.15f);
            h=clamp01(h);

            // Aquatic palette: deep emerald/teal abyss -> turquoise shallow -> aqua crest -> white foam tips
            float r,g,b;
            if(h<0.35f){
                float t=h/0.35f;
                r=lerp(0.03f,0.08f,t); g=lerp(0.18f,0.38f,t); b=lerp(0.30f,0.52f,t);
            }else if(h<0.72f){
                float t=(h-0.35f)/(0.72f-0.35f);
                r=lerp(0.08f,0.28f,t); g=lerp(0.38f,0.68f,t); b=lerp(0.52f,0.80f,t);
            }else if(h<0.87f){
                float t=(h-0.72f)/(0.87f-0.72f);
                r=lerp(0.28f,0.62f,t); g=lerp(0.68f,0.88f,t); b=lerp(0.80f,0.95f,t);
            }else{
                float t=(h-0.87f)/(1.0f-0.87f);
                r=lerp(0.62f,0.92f,t); g=lerp(0.88f,0.98f,t); b=lerp(0.95f,1.0f,t);
            }
            p.drawPixel(x,y,(b8(r)<<24)|(b8(g)<<16)|(b8(b)<<8)|255);
        }
        return upload(p,true);
    }
    private void addWaterRoughness(Material m){
        final int S=256;Pixmap q=pixmap(S,S);
        final float pi2=(float)(Math.PI*2.0);
        for(int y=0;y<S;y++)for(int x=0;x<S;x++){
            float u=x/(float)S, v=y/(float)S;
            float ax=u*pi2, ay=v*pi2;
            float w1=(float)(Math.sin(ax*2f+ay)*Math.cos(ay*2f-ax));
            float w2=(float)(Math.sin(ax*4f-ay*3f+1.2f)*Math.cos(ax*3f+ay*4f-0.7f));
            float c1=(float)Math.abs(Math.sin(ax*4f+Math.cos(ay*3f)));
            float c2=(float)Math.abs(Math.cos(ay*4f+Math.sin(ax*3f)));
            float caustic=(float)Math.pow(1.0-Math.min(1.0,c1*c2),2.4);
            float h=clamp01(0.50f+0.28f*w1+0.20f*w2+0.16f*(caustic-0.28f));

            // Mirror-smooth water on crests and caustic edges (G=roughness multiplier)
            // Metallic is higher on crests to reflect the sky and lamps sharply (B=metallic multiplier)
            float roughMult=h>0.58f?lerp(0.70f,0.15f,(h-0.58f)/0.42f):lerp(1.10f,0.70f,h/0.58f);
            float metalMult=h>0.58f?lerp(1.0f,1.60f,(h-0.58f)/0.42f):1.0f;
            q.drawPixel(x,y,(255<<24)|(b8(clamp01(roughMult))<<16)|(b8(clamp01(metalMult*0.5f))<<8)|255);
        }
        PBRTextureAttribute a=PBRTextureAttribute.createMetallicRoughnessTexture(upload(q,true));
        m.set(a);
    }
    /** Tileable value-noise roughness map: G = roughness multiplier (patchy wet/dry), B = metallic multiplier. */
    private void addRoughnessPatches(Material m,float uvScale,int seed){
        final int S=256;Pixmap q=pixmap(S,S);
        for(int y=0;y<S;y++)for(int x=0;x<S;x++){
            float n=0,amp=.55f,tot=0;
            for(int o=0;o<4;o++){int f=4<<o;n+=amp*tileNoise(x/(float)S*f,y/(float)S*f,f,seed+o*31);tot+=amp;amp*=.52f;}
            n/=tot;float r=.60f+.40f*smooth(.30f,.72f,n);
            q.drawPixel(x,y,(255<<24)|(b8(r)<<16)|(255<<8)|255); // R=255, G=roughness multiplier, B=255 (metallic multiplier), A=255
        }
        PBRTextureAttribute a=PBRTextureAttribute.createMetallicRoughnessTexture(upload(q,true));a.scaleU=uvScale;a.scaleV=uvScale;m.set(a);
    }
    private static float tileNoise(float x,float y,int period,int seed){
        int x0=(int)Math.floor(x),y0=(int)Math.floor(y);float fx=x-x0,fy=y-y0;fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy);
        float a=lat(x0,y0,period,seed),b=lat(x0+1,y0,period,seed),c=lat(x0,y0+1,period,seed),d=lat(x0+1,y0+1,period,seed);
        return lerp(lerp(a,b,fx),lerp(c,d,fx),fy);
    }
    private static float lat(int x,int y,int period,int seed){
        x=Math.floorMod(x,period);y=Math.floorMod(y,period);
        int h=x*374761393+y*668265263+seed*1274126177;h=(h^(h>>>13))*1274126177;h^=h>>>16;return (h&0xffff)/65535f;
    }

    // ===================================================================================================================
    // Shared light-quad geometry
    // ===================================================================================================================
    private static final long QATTR=ATTR;
    /** Camera-facing quad (lamp halo). Place with {@link #faceCamera}. */
    public ModelInstance billboard(Material m){
        return shared(billboardQuads.computeIfAbsent(m,k->{
            ModelBuilder b=new ModelBuilder();b.begin();MeshPartBuilder p=b.part("halo",GL20.GL_TRIANGLES,QATTR,m);
            p.rect(new MeshPartBuilder.VertexInfo().setPos(-.5f,-.5f,0).setNor(0,0,1).setUV(0,1),new MeshPartBuilder.VertexInfo().setPos(.5f,-.5f,0).setNor(0,0,1).setUV(1,1),new MeshPartBuilder.VertexInfo().setPos(.5f,.5f,0).setNor(0,0,1).setUV(1,0),new MeshPartBuilder.VertexInfo().setPos(-.5f,.5f,0).setNor(0,0,1).setUV(0,0));
            return own(b.end());}));
    }
    /** Streak quad lying on the ground: foot (the light) at local z=0, running to z=1; scale z by the length. */
    public ModelInstance streak(Material m){
        // uv row 0 (v=0) is the foot of the streak => z=0 maps to v=0
        return shared(streakQuads.computeIfAbsent(m,k->{
            ModelBuilder b=new ModelBuilder();b.begin();MeshPartBuilder p=b.part("streak",GL20.GL_TRIANGLES,QATTR,m);
            p.rect(new MeshPartBuilder.VertexInfo().setPos(-.5f,0,1).setNor(0,1,0).setUV(0,1),new MeshPartBuilder.VertexInfo().setPos(.5f,0,1).setNor(0,1,0).setUV(1,1),new MeshPartBuilder.VertexInfo().setPos(.5f,0,0).setNor(0,1,0).setUV(1,0),new MeshPartBuilder.VertexInfo().setPos(-.5f,0,0).setNor(0,1,0).setUV(0,0));
            return own(b.end());}));
    }
    /** ModelInstance copies every material; point the instance back at the source model's (shared) materials so
     *  per-frame changes (wetness, night tint, brake light) reach every instance. */
    public static void shareMaterials(ModelInstance inst,Model src){
        for(int i=0;i<inst.nodes.size&&i<src.nodes.size;i++)shareNode(inst.nodes.get(i),src.nodes.get(i));
    }
    private static void shareNode(com.badlogic.gdx.graphics.g3d.model.Node a,com.badlogic.gdx.graphics.g3d.model.Node b){
        for(int i=0;i<a.parts.size&&i<b.parts.size;i++)a.parts.get(i).material=b.parts.get(i).material;
        for(int i=0;i<a.getChildCount()&&i<b.getChildCount();i++)shareNode(a.getChild(i),b.getChild(i));
    }
    /** Swap the material of every part (e.g. tail streak -> brake streak). */
    public static void useMaterial(ModelInstance inst,Material m){
        for(com.badlogic.gdx.graphics.g3d.model.Node n:inst.nodes)for(com.badlogic.gdx.graphics.g3d.model.NodePart p:n.parts)p.material=m;
    }
    private static ModelInstance shared(Model m){ModelInstance i=new ModelInstance(m);shareMaterials(i,m);return i;}
    private static final Vector3 TMP=new Vector3();
    public static void faceCamera(ModelInstance i,Camera camera,float x,float y,float z,float size){
        i.transform.setToWorld(TMP.set(x,y,z),new Vector3(camera.direction).scl(-1f),camera.up).scale(size,size,1f);
    }
    @Override public void close(){for(Model m:owned)m.dispose();for(Texture t:textures)t.dispose();}
}

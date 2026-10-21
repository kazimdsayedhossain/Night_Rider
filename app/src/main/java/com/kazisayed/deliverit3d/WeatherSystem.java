package com.kazisayed.deliverit3d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.glutils.*;
import com.badlogic.gdx.math.*;
import net.mgsx.gltf.scene3d.scene.SceneManager;
import net.mgsx.gltf.scene3d.attributes.*;
import net.mgsx.gltf.scene3d.lights.*;
import java.util.Random;

public final class WeatherSystem implements AutoCloseable {
    public enum Mode {CLEAR,RAIN,SNOW}
    public Mode mode=Mode.RAIN;public boolean automatic=true,dayCycle=false,headlights=true;public float hour=23.0f,rain=1.0f,snow;private float changeClock,clock;
    public final Color sky=new Color();private final SceneManager scene;private final MeshKit kit;private final DirectionalLightEx sun=new DirectionalLightEx();private final DirectionalLightEx moonFill=new DirectionalLightEx();private final SpotLightEx headlight=new SpotLightEx();
    private final NightEnvironment nightEnv=new NightEnvironment();private boolean ibl;/** Set false before constructing to skip the procedural night IBL. */ public static boolean USE_NIGHT_IBL=true;private final Color tmpA=new Color();
    private final Mesh particles;private final ShaderProgram shader;private final float[] vertices=new float[1200*4];private final float[] seeds=new float[1200*3];
    public WeatherSystem(SceneManager scene,MeshKit kit){this.scene=scene;this.kit=kit;scene.environment.add(sun);scene.environment.add(moonFill);headlight.setDeg(new Color(1f,.88f,.58f,1),new Vector3(),new Vector3(0,-.16f,1),46,9,36,42f);scene.environment.add(headlight);
        ibl=USE_NIGHT_IBL&&nightEnv.attach(scene.environment);
        particles=new Mesh(false,1200,0,VertexAttribute.Position(),VertexAttribute.ColorPacked());shader=new ShaderProgram("attribute vec3 a_position; attribute vec4 a_color; uniform mat4 u_proj; uniform float u_size; varying vec4 v_color; void main(){v_color=a_color;gl_Position=u_proj*vec4(a_position,1.0);gl_PointSize=u_size;}","#ifdef GL_ES\nprecision mediump float;\n#endif\nvarying vec4 v_color; void main(){vec2 p=gl_PointCoord-vec2(.5);float a=1.0-smoothstep(.2,.5,length(p));gl_FragColor=vec4(v_color.rgb,v_color.a*a);}");if(!shader.isCompiled())throw new IllegalStateException(shader.getLog());Random random=new Random(719);for(int i=0;i<seeds.length;i++)seeds[i]=random.nextFloat();}
    public void update(float dt,float x,float z,float yaw){clock+=dt;changeClock+=dt;if(dayCycle)hour=(hour+dt/45)%24;if(automatic&&changeClock>110){changeClock=0;mode=Mode.values()[(mode.ordinal()+1)%3];}
        float blend=1-(float)Math.exp(-dt*.65);rain=Rules.lerp(rain,mode==Mode.RAIN?1:0,blend);snow=Rules.lerp(snow,mode==Mode.SNOW?1:0,blend);
        float sunHeight=(float)Math.sin((hour-6)/24*MathUtils.PI2),day=Rules.clamp((sunHeight+.13f)/.4f,0,1),cloud=rain*.55f+snow*.38f;
        sky.set(.020f,.038f,.075f,1).lerp(new Color(.56f,.70f,.76f,1),day).lerp(new Color(.012f,.020f,.042f,1),cloud*(1-day));
        // Key directional moonlight from one angle
        sun.direction.set(-.35f,-Math.max(.25f,Math.abs(sunHeight)),-.25f).nor();sun.color.set(.45f,.52f,.65f,1).mul(.45f+day*.55f);sun.color.a=1;
        // Soft diffused wrap-around moonlight from opposite angle so side and back building walls are never black
        moonFill.direction.set(.35f,-.42f,.30f).nor();moonFill.color.set(.35f,.42f,.55f,1).mul(.32f+day*.38f);moonFill.color.a=1;
        // night factor drives every emitted-light decal (lamp pools/halos/streaks, headlight beams)
        float night=1f-Rules.clamp(day*1.7f,0,1);kit.setNight(night);
        // with the night IBL attached it supplies ambient fill across all surfaces and orientations
        scene.setAmbientLight((ibl?.38f:.42f)+day*.30f);scene.environment.set(ColorAttribute.createFog(sky));scene.environment.set(FogAttribute.createFog(65f,210f,1.25f));
        // wet road: darker, tinted, glossy but patchy (roughness map in MeshKit); dries out in clear weather
        float wet=Rules.clamp(rain+snow*.25f,0,1);
        ((PBRFloatAttribute)kit.asphalt.get(PBRFloatAttribute.Roughness)).value=Rules.lerp(.80f,.38f,wet);
        ((PBRFloatAttribute)kit.asphalt.get(PBRFloatAttribute.Metallic)).value=Rules.lerp(.02f,.08f,wet);
        ((PBRColorAttribute)kit.asphalt.get(PBRColorAttribute.BaseColorFactor)).color.set(Color.WHITE).lerp(tmpA.set(.66f,.70f,.73f,1f),wet);
        ((PBRFloatAttribute)kit.sidewalk.get(PBRFloatAttribute.Roughness)).value=Rules.lerp(.80f,.46f,wet);   // slightly reflective, wet walkways
        ((PBRColorAttribute)kit.sidewalk.get(PBRColorAttribute.BaseColorFactor)).color.set(Color.WHITE).lerp(tmpA.set(.84f,.87f,.90f,1f),wet);
        ((com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute)kit.puddle.get(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type)).opacity=.62f*Rules.clamp(rain*1.4f,0,1);
        // dynamic water surface ripples: gently scroll UV offset across river and park ponds
        PBRTextureAttribute waterTex=(PBRTextureAttribute)kit.water.get(PBRTextureAttribute.BaseColorTexture);
        if(waterTex!=null){
            waterTex.offsetU=(waterTex.offsetU+dt*0.028f)%1.0f;
            waterTex.offsetV=(waterTex.offsetV+dt*0.018f)%1.0f;
            PBRTextureAttribute waterMR=(PBRTextureAttribute)kit.water.get(PBRTextureAttribute.MetallicRoughnessTexture);
            if(waterMR!=null){waterMR.offsetU=waterTex.offsetU;waterMR.offsetV=waterTex.offsetV;}
        }
        headlight.position.set(x,1.05f+WorldGrid.elevation(x,z),z);headlight.direction.set((float)Math.sin(yaw),-.16f,(float)Math.cos(yaw)).nor();headlight.intensity=headlights?(day<.5f||rain>.3f?34:7):0;
    }
    public void render(Camera camera,float x,float z){float amount=Math.max(rain,snow);if(amount<.02f)return;int count=(int)(1200*amount);float packed=new Color(.86f,.91f,.95f,mode==Mode.SNOW?.7f:.45f).toFloatBits();for(int i=0;i<count;i++){float sx=seeds[i*3],sy=seeds[i*3+1],sz=seeds[i*3+2];float drop=mode==Mode.SNOW?1.7f:16;vertices[i*4]=x+(sx-.5f)*36+(mode==Mode.SNOW?(float)Math.sin(clock+sx*20)*.5f:0);vertices[i*4+1]=((sy*18-clock*drop)%18+18)%18;vertices[i*4+2]=z+(sz-.5f)*36;vertices[i*4+3]=packed;}particles.setVertices(vertices,0,count*4);Gdx.gl.glEnable(GL20.GL_BLEND);Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA,GL20.GL_ONE_MINUS_SRC_ALPHA);Gdx.gl.glDepthMask(false);shader.bind();shader.setUniformMatrix("u_proj",camera.combined);shader.setUniformf("u_size",mode==Mode.SNOW?9:5.5f);particles.render(shader,GL20.GL_POINTS,0,count);Gdx.gl.glDepthMask(true);Gdx.gl.glDisable(GL20.GL_BLEND);}
    @Override public void close(){nightEnv.close();particles.dispose();shader.dispose();}
}

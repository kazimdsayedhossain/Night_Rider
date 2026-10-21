package com.kazisayed.deliverit3d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.Environment;
import net.mgsx.gltf.scene3d.attributes.*;

/**
 * Procedural rainy-night image-based lighting (port of the JS createNightEnvironment): a dark blue dome,
 * a warm wet-air glow on the horizon, a skyline of silhouetted blocks with lit windows and a few sodium
 * pin-pricks. Without an environment map a PBR road has nothing to reflect, so wet asphalt reads flat and
 * dull; with it, the road and puddles pick up the glow of the skyline and the lamps' colour.
 *
 * One small cubemap is used for both the diffuse and the specular slot. Its mip chain (auto generated) acts as
 * the blur for rough surfaces. If anything fails, {@link #attach} returns false and the scene keeps working.
 */
public final class NightEnvironment implements AutoCloseable {
    private Cubemap cube;private Texture brdf;

    public boolean attach(Environment env) {
        try {
            cube = build(128);
            brdf = new Texture(Gdx.files.classpath("net/mgsx/gltf/shaders/brdfLUT.png"));
            brdf.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            env.set(new PBRTextureAttribute(PBRTextureAttribute.BRDFLUTTexture, brdf));
            env.set(PBRCubemapAttribute.createSpecularEnv(cube));
            env.set(PBRCubemapAttribute.createDiffuseEnv(cube));
            return true;
        } catch (Throwable t) {
            if (Gdx.app != null) Gdx.app.error("NightEnvironment", "IBL unavailable, continuing without it", t);
            close();return false;
        }
    }

    private static float hash(int a, int b) { int h = a * 374761393 + b * 668265263; h = (h ^ (h >>> 13)) * 1274126177; h ^= h >>> 16; return (h & 0xffff) / 65535f; }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static Cubemap build(int size) {
        Pixmap[] faces = new Pixmap[6];
        for (int f = 0; f < 6; f++) {
            Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888); p.setBlending(Pixmap.Blending.None);
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                float sc = 2f * (x + .5f) / size - 1f, tc = 2f * (y + .5f) / size - 1f, dx, dy, dz;
                switch (f) {
                    case 0 -> { dx = 1; dy = -tc; dz = -sc; }  case 1 -> { dx = -1; dy = -tc; dz = sc; }
                    case 2 -> { dx = sc; dy = 1; dz = tc; }    case 3 -> { dx = sc; dy = -1; dz = -tc; }
                    case 4 -> { dx = sc; dy = -tc; dz = 1; }   default -> { dx = -sc; dy = -tc; dz = -1; }
                }
                float l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz); dx /= l; dy /= l; dz /= l;
                float[] c = sample(dx, dy, dz);
                p.drawPixel(x, y, (b8(c[0]) << 24) | (b8(c[1]) << 16) | (b8(c[2]) << 8) | 255);
            }
            faces[f] = p;
        }
        Cubemap cm = new Cubemap(faces[0], faces[1], faces[2], faces[3], faces[4], faces[5], true);
        cm.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        for (Pixmap p : faces) p.dispose();
        return cm;
    }
    private static int b8(float v) { return Math.min(255, Math.max(0, Math.round(v * 255f))); }

    /** Linear-ish radiance for a view direction: rich atmospheric night dome with soft horizon sodium glow. */
    private static float[] sample(float x, float y, float z) {
        float az = (float) Math.atan2(x, z);
        float r, g, b;
        if (y >= 0) {
            float t = (float) Math.pow(y, .45f);
            r = lerp(.38f, .20f, t); g = lerp(.42f, .26f, t); b = lerp(.56f, .40f, t);   // warm horizon haze -> deep atmospheric navy zenith
        } else {
            float t = (float) Math.pow(-y, .6f);
            r = lerp(.26f, .15f, t); g = lerp(.28f, .17f, t); b = lerp(.36f, .22f, t);   // ground & wet street bounce
        }
        // wet-air sodium glow hugging the horizon from city lamps
        float glow = (float) Math.exp(-Math.abs(y) * 7f) * (.60f + .40f * (float) Math.sin(az * 3f + 1f));
        r += .18f * glow; g += .12f * glow; b += .06f * glow;
        // skyline: columns ~3.2 degrees wide, heights 0.05..0.30
        if (y > -.02f && y < .34f) {
            int col = (int) Math.floor((az + 3.14159f) / .056f);
            float h = .05f + .25f * hash(col, 7) * hash(col / 3, 11);
            if (y < h) {
                r = .14f; g = .16f; b = .20f;
                int row = (int) Math.floor(y / .014f);
                float lit = hash(col, row + 100);
                if (lit > .60f && (row & 1) == 0) {
                    float warm = hash(col + 9, row);
                    float k = .55f + 1.2f * hash(row, col + 3);
                    r = (.95f + .05f * warm) * k * .65f; g = (.68f + .20f * warm) * k * .65f; b = (.35f + .25f * (1 - warm)) * k * .65f;
                }
            }
        }
        // sodium pin-pricks low on the horizon (far lamps)
        if (y > 0f && y < .05f) {
            int col = (int) Math.floor((az + 3.14159f) / .02f);
            if (hash(col, 55) > .90f) { r = 1.0f; g = .75f; b = .38f; }
        }
        return new float[]{r, g, b};
    }

    @Override public void close() { if (cube != null) { cube.dispose(); cube = null; } if (brdf != null) { brdf.dispose(); brdf = null; } }
}

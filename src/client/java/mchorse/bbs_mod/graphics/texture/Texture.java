package mchorse.bbs_mod.graphics.texture;

import com.mojang.blaze3d.opengl.GlStateManager;
import mchorse.bbs_mod.utils.resources.Pixels;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

/**
 * Texture class
 * 
 * This class is responsible for managing a state of a texture
 */
public class Texture
{
    public int id;
    public int target;

    public int width;
    public int height;

    private boolean mipmap;
    private boolean clearable;

    private AnimatedTexture parent;
    private TextureFormat format = TextureFormat.RGBA_U8;
    private int filter;

    public static Pixels pixelsFromTexture(Texture texture)
    {
        if (!texture.isValid())
        {
            return null;
        }

        ByteBuffer buffer = MemoryUtil.memAlloc(texture.width * texture.height * 4);

        texture.bind();
        GL11.glGetTexImage(texture.target, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        texture.unbind();

        return new Pixels(buffer, texture.width, texture.height);
    }

    public static Texture textureFromPixels(Pixels pixels, int filter)
    {
        Texture texture = new Texture();

        texture.setFilter(filter);
        texture.uploadTexture(pixels);
        texture.unbind();

        return texture;
    }

    public Texture()
    {
        /* TODO(1.21.11 render): verify at runtime. TextureUtil.generateTextureId() was removed in
         * the 1.21.5 GPU rewrite; GlStateManager moved to com.mojang.blaze3d.opengl and exposes
         * _genTexture() as the direct-GL replacement. Functions while the mod still uses raw GL
         * textures; the eventual GpuTexture migration will replace this whole class. */
        this.id = GlStateManager._genTexture();
        this.target = GL11.GL_TEXTURE_2D;

        this.bind();
    }

    public void setParent(AnimatedTexture parent)
    {
        this.parent = parent;
    }

    public AnimatedTexture getParent()
    {
        return this.parent;
    }

    public void setClearable(boolean clearable)
    {
        this.clearable = clearable;
    }

    public boolean isClearable()
    {
        return this.clearable;
    }

    public TextureFormat getFormat()
    {
        return this.format;
    }

    public boolean isMipmap()
    {
        return this.mipmap;
    }

    public boolean isReallyMipmap()
    {
        return this.mipmap && this.getParameter(GL30.GL_TEXTURE_MAX_LEVEL) > 0;
    }

    public boolean isValid()
    {
        return this.id >= 0;
    }

    /* NOTE(1.21.11 render): bind/unbind/delete MUST go through GlStateManager, not raw GL.
     * The 1.21.1 original used raw GL11.glBindTexture and it was fine, but the 1.21.5+ GPU rewrite
     * made the new GL backend (GlCommandEncoder / RenderPassImpl) — which vanilla's glyph-atlas
     * UPLOAD and SAMPLING now run through — trust GlStateManager's per-unit bound-texture cache and
     * SKIP the real glBindTexture when the cache says the id is already bound. Raw binds here mutate
     * real GL state without updating that cache, desyncing it; vanilla then skips a needed bind for
     * whichever glyph is being baked at that moment, so that one atlas tile samples foreign GPU
     * memory (observed: the '5' glyph rendering as garbage). Routing through GlStateManager keeps the
     * cache coherent. The unit arg is a raw 0-based index, so it must become the GL_TEXTURE0+unit enum
     * — passing the raw index also caused the GL_INVALID_ENUM "exceeds max combined texture image
     * units" spam. (target is always GL_TEXTURE_2D, which _bindTexture targets.) */
    public void bind()
    {
        GlStateManager._bindTexture(this.id);
    }

    public void bind(int unit)
    {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GlStateManager._bindTexture(this.id);
    }

    public void unbind()
    {
        GlStateManager._bindTexture(0);
    }

    public void unbind(int unit)
    {
        GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
        GlStateManager._bindTexture(0);
    }

    public void setFormat(TextureFormat format)
    {
        this.format = format;
    }

    public int getFilter()
    {
        return this.filter;
    }

    public boolean isLinear()
    {
        return this.filter == GL30.GL_LINEAR || this.filter == GL30.GL_LINEAR_MIPMAP_NEAREST;
    }

    public int getParameter(int parameter)
    {
        return GL11.glGetTexParameteri(this.target, parameter);
    }

    public void setFilterMipmap(boolean linear, boolean mipmap)
    {
        int filter = linear ? GL11.GL_LINEAR : GL11.GL_NEAREST;

        this.setFilter(filter);

        if (!this.isMipmap())
        {
            this.generateMipmap();
        }

        this.setParameter(GL30.GL_TEXTURE_MAX_LEVEL, mipmap ? 4 : 0);
    }

    public void setFilter(int filter)
    {
        this.filter = filter;

        this.setParameter(GL11.GL_TEXTURE_MAG_FILTER, filter);
        this.setParameter(GL11.GL_TEXTURE_MIN_FILTER, filter);
    }

    public void setWrap(int mode)
    {
        this.setParameter(GL11.GL_TEXTURE_WRAP_S, mode);
        this.setParameter(GL11.GL_TEXTURE_WRAP_T, mode);
    }

    public void setParameter(int param, int value)
    {
        GL11.glTexParameteri(this.target, param, value);
    }

    public void delete()
    {
        /* Use GlStateManager._deleteTexture (not raw GL11.glDeleteTextures): it also walks the
         * per-unit bound-texture cache and resets any entry holding this id. Otherwise the cache
         * keeps claiming a freed id is bound, and when the driver recycles that id (e.g. for a new
         * glyph-atlas page) vanilla skips the real bind → corruption. */
        GlStateManager._deleteTexture(this.id);
        this.id = -1;
    }

    public void setSize(int width, int height)
    {
        this.width = width;
        this.height = height;

        GL11.glTexImage2D(this.target, 0, this.format.internal, width, height, 0, this.format.format, this.format.type, 0);
    }

    public void updateTexture(Pixels pixels)
    {
        this.updateTexture(this.target, pixels);
    }

    public void updateTexture(int target, Pixels pixels)
    {
        this.uploadTexture(target, 0, pixels.width, pixels.height, pixels.getBuffer());
    }

    public void uploadTexture(Pixels pixels)
    {
        this.uploadTexture(this.target, pixels);
    }

    public void uploadTexture(int target, Pixels pixels)
    {
        this.uploadTexture(target, 0, pixels);
    }

    public void uploadTexture(int target, int level, Pixels pixels)
    {
        /* Some textures might not be pixel aligned. For example FunkyFight's 398x444 avatar
         * wasn't aligned, and it caused some interesting visual issues when loading
         * the texture. This fixes it.
         *
         * https://www.khronos.org/opengl/wiki/Pixel_Transfer#Pixel_layout */
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);

        this.setFormat(pixels.bits == 4 ? TextureFormat.RGBA_U8 : TextureFormat.RGB_U8);
        this.uploadTexture(target, level, pixels.width, pixels.height, pixels.getBuffer());

        pixels.delete();
    }

    public void uploadTexture(int target, int level, int w, int h, ByteBuffer buffer)
    {
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, w);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);

        GL11.glTexImage2D(target, level, this.format.internal, w, h, 0, this.format.format, this.format.type, buffer);

        if (level == 0)
        {
            this.width = w;
            this.height = h;
        }
    }

    public void generateMipmap()
    {
        this.mipmap = true;

        GL30.glGenerateMipmap(this.target);
    }
}
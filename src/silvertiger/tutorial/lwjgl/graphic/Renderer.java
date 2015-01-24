/*
 * The MIT License (MIT)
 *
 * Copyright © 2014, Heiko Brumme
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package silvertiger.tutorial.lwjgl.graphic;

import java.awt.Color;
import java.awt.FontFormatException;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import silvertiger.tutorial.lwjgl.math.Matrix4f;
import silvertiger.tutorial.lwjgl.text.Font;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL20.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL20.GL_VERTEX_SHADER;

/**
 * This class is performing the rendering process.
 *
 * @author Heiko Brumme
 */
public class Renderer {

    private VertexArrayObject vao;
    private VertexBufferObject vbo;
    private Shader vertexShader;
    private Shader fragmentShader;
    private ShaderProgram program;
    private Font font;

    private boolean defaultContext;

    private FloatBuffer vertices;
    private int numVertices;
    private boolean drawing;

    /**
     * Initializes the renderer.
     *
     * @param defaultContext Specifies if the OpenGL context is 3.2 compatible
     */
    public void init(boolean defaultContext) {
        this.defaultContext = defaultContext;

        /* Create batch */
        if (defaultContext) {
            /* Generate Vertex Array Object */
            vao = new VertexArrayObject();
            vao.bind();
        } else {
            vao = null;
        }

        /* Generate Vertex Buffer Object */
        vbo = new VertexBufferObject();
        vbo.bind(GL_ARRAY_BUFFER);

        /* Create FloatBuffer */
        vertices = BufferUtils.createFloatBuffer(4096);

        /* Set stuff */
        numVertices = 0;
        drawing = false;

        /* Load shaders */
        if (defaultContext) {
            vertexShader = Shader.loadShader(GL_VERTEX_SHADER, "resources/default_vertex.glsl");
            fragmentShader = Shader.loadShader(GL_FRAGMENT_SHADER, "resources/default_fragment.glsl");
        } else {
            vertexShader = Shader.loadShader(GL_VERTEX_SHADER, "resources/legacy_vertex.glsl");
            fragmentShader = Shader.loadShader(GL_FRAGMENT_SHADER, "resources/legacy_fragment.glsl");
        }

        /* Create shader program */
        program = new ShaderProgram();
        program.attachShader(vertexShader);
        program.attachShader(fragmentShader);
        if (defaultContext) {
            program.bindFragmentDataLocation(0, "fragColor");
        }
        program.link();
        program.use();

        /* Get width and height of framebuffer */
        long window = GLFW.glfwGetCurrentContext();
        IntBuffer widthBuffer = BufferUtils.createIntBuffer(1);
        IntBuffer heightBuffer = BufferUtils.createIntBuffer(1);
        GLFW.glfwGetFramebufferSize(window, widthBuffer, heightBuffer);
        int width = widthBuffer.get();
        int height = heightBuffer.get();

        specifyVertexAttributes();

        /* Set texture uniform */
        int uniTex = program.getUniformLocation("texImage");
        program.setUniform(uniTex, 0);

        /* Set model matrix to identity matrix */
        Matrix4f model = new Matrix4f();
        int uniModel = program.getUniformLocation("model");
        program.setUniform(uniModel, model);

        /* Set view matrix to identity matrix */
        Matrix4f view = new Matrix4f();
        int uniView = program.getUniformLocation("view");
        program.setUniform(uniView, view);

        /* Set projection matrix to an orthographic projection */
        Matrix4f projection = Matrix4f.orthographic(0f, width, 0f, height, -1f, 1f);
        int uniProjection = program.getUniformLocation("projection");
        program.setUniform(uniProjection, projection);

        /* Enable blending */
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        /* Create font */
        try {
            font = new Font(new FileInputStream("resources/Inconsolata.otf"));
        } catch (IOException | FontFormatException ex) {
            font = new Font();
        }
    }

    /**
     * Clears the drawing area.
     */
    public void clear() {
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    }

    /**
     * Begin rendering.
     */
    public void begin() {
        if (drawing) {
            throw new IllegalStateException("Renderer is already drawing!");
        }
        drawing = true;
        numVertices = 0;
    }

    /**
     * End rendering.
     */
    public void end() {
        if (!drawing) {
            throw new IllegalStateException("Renderer isn't drawing!");
        }
        drawing = false;
        flush();
    }

    /**
     * Flushes the data to the GPU to let it get rendered.
     */
    public void flush() {
        if (numVertices > 0) {
            vertices.flip();

            if (vao != null) {
                vao.bind();
            } else {
                vbo.bind(GL_ARRAY_BUFFER);
                specifyVertexAttributes();
            }
            program.use();

            vbo.bind(GL_ARRAY_BUFFER);
            vbo.uploadData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

            glDrawArrays(GL_TRIANGLES, 0, numVertices);

            vertices.clear();
            numVertices = 0;
        }
    }

    /**
     * Calculates total width of a text.
     *
     * @param text The text
     * @return Total width of the text
     */
    public int getTextWidth(CharSequence text) {
        return font.getWidth(text);
    }

    /**
     * Calculates total height of a text.
     *
     * @param text The text
     * @return Total width of the text
     */
    public int getTextHeight(CharSequence text) {
        return font.getHeight(text);
    }

    /**
     * Draw text at the specified position.
     *
     * @param text Text to draw
     * @param x X coordinate of the text position
     * @param y Y coordinate of the text position
     */
    public void drawText(CharSequence text, float x, float y) {
        font.drawText(this, text, x, y);
    }

    /**
     * Draw text at the specified position and color.
     *
     * @param text Text to draw
     * @param x X coordinate of the text position
     * @param y Y coordinate of the text position
     * @param c Color to use
     */
    public void drawText(CharSequence text, float x, float y, Color c) {
        font.drawText(this, text, x, y, c);
    }

    /**
     * Draws a texture on specified coordinates.
     *
     * @param texture The texture to draw
     * @param x X position of the texture
     * @param y Y position of the texture
     */
    public void drawTexture(Texture texture, float x, float y) {
        drawTexture(texture, x, y, Color.WHITE);
    }

    /**
     * Draws a texture on specified coordinates and with specified color.
     *
     * @param texture The texture to draw
     * @param x X position of the texture
     * @param y Y position of the texture
     * @param c The color to use
     */
    public void drawTexture(Texture texture, float x, float y, Color c) {
        /* Vertex positions */
        float x1 = x;
        float y1 = y;
        float x2 = x1 + texture.getWidth();
        float y2 = y1 + texture.getHeight();

        /* Texture coordinates */
        float u1 = 0f;
        float v1 = 0f;
        float u2 = 1f;
        float v2 = 1f;

        drawTextureRegion(texture, x1, y1, x2, y2, u1, v1, u2, v2, c);
    }

    /**
     * Draws a texture region on specified coordinates.
     *
     * @param texture The texture to use
     * @param x X position of the texture
     * @param y Y position of the texture
     * @param regX X position of the texture region
     * @param regY Y position of the texture region
     * @param regWidth Width of the texture region
     * @param regHeight Height of the texture region
     */
    public void drawTextureRegion(Texture texture, float x, float y, float regX, float regY, float regWidth, float regHeight) {
        drawTextureRegion(texture, x, y, regX, regY, regWidth, regHeight, Color.WHITE);
    }

    /**
     * Draws a texture region on specified coordinates.
     *
     * @param texture The texture to use
     * @param x X position of the texture
     * @param y Y position of the texture
     * @param regX X position of the texture region
     * @param regY Y position of the texture region
     * @param regWidth Width of the texture region
     * @param regHeight Height of the texture region
     * @param c The color to use
     */
    public void drawTextureRegion(Texture texture, float x, float y, float regX, float regY, float regWidth, float regHeight, Color c) {
        /* Vertex positions */
        float x1 = x;
        float y1 = y;
        float x2 = x + regWidth;
        float y2 = y + regHeight;

        /* Texture coordinates */
        float u1 = regX / texture.getWidth();
        float v1 = regY / texture.getHeight();
        float u2 = (regX + regWidth) / texture.getWidth();
        float v2 = (regY + regHeight) / texture.getHeight();

        drawTextureRegion(texture, x1, y1, x2, y2, u1, v1, u2, v2, c);
    }

    /**
     * Draws a texture region on specified coordinates.
     *
     * @param texture Texture to use
     * @param x1 Bottom left x position
     * @param y1 Bottom left y position
     * @param x2 Top right x position
     * @param y2 Top right y position
     * @param u1 Bottom left u coordinate
     * @param v1 Bottom left v coordinate
     * @param u2 Top right u coordinate
     * @param v2 Top right v coordinate
     */
    public void drawTextureRegion(Texture texture, float x1, float y1, float x2, float y2, float u1, float v1, float u2, float v2) {
        drawTextureRegion(texture, x1, y1, x2, y2, u1, v1, u2, v2, Color.WHITE);
    }

    /**
     * Draws a texture region on specified coordinates.
     *
     * @param texture Texture to use
     * @param x1 Bottom left x position
     * @param y1 Bottom left y position
     * @param x2 Top right x position
     * @param y2 Top right y position
     * @param u1 Bottom left u coordinate
     * @param v1 Bottom left v coordinate
     * @param u2 Top right u coordinate
     * @param v2 Top right v coordinate
     * @param c The color to use
     */
    public void drawTextureRegion(Texture texture, float x1, float y1, float x2, float y2, float u1, float v1, float u2, float v2, Color c) {
        float r = c.getRed() / 255f;
        float g = c.getGreen() / 255f;
        float b = c.getBlue() / 255f;

        texture.bind();

        vertices.put(x1).put(y1).put(r).put(g).put(b).put(u1).put(v1);
        vertices.put(x1).put(y2).put(r).put(g).put(b).put(u1).put(v2);
        vertices.put(x2).put(y2).put(r).put(g).put(b).put(u2).put(v2);

        vertices.put(x1).put(y1).put(r).put(g).put(b).put(u1).put(v1);
        vertices.put(x2).put(y2).put(r).put(g).put(b).put(u2).put(v2);
        vertices.put(x2).put(y1).put(r).put(g).put(b).put(u2).put(v1);

        numVertices += 6;
    }

    /**
     * Dispose renderer and clean up its used data.
     */
    public void dispose() {
        if (vao != null) {
            vao.delete();
        }
        vbo.delete();
        vertexShader.delete();
        fragmentShader.delete();
        program.delete();
        font.dispose();
    }

    /**
     * Shows if the OpenGL context supports version 3.2.
     *
     * @return true, if OpenGL context supports version 3.2, else false
     */
    public boolean hasDefaultContext() {
        return defaultContext;
    }

    /**
     * Specifies the vertex pointers.
     */
    private void specifyVertexAttributes() {
        /* Specify Vertex Pointer */
        int posAttrib = program.getAttributeLocation("position");
        program.enableVertexAttribute(posAttrib);
        program.pointVertexAttribute(posAttrib, 2, 7 * Float.BYTES, 0);

        /* Specify Color Pointer */
        int colAttrib = program.getAttributeLocation("color");
        program.enableVertexAttribute(colAttrib);
        program.pointVertexAttribute(colAttrib, 3, 7 * Float.BYTES, 2 * Float.BYTES);

        /* Specify Texture Pointer */
        int texAttrib = program.getAttributeLocation("texcoord");
        program.enableVertexAttribute(texAttrib);
        program.pointVertexAttribute(texAttrib, 2, 7 * Float.BYTES, 5 * Float.BYTES);
    }
}

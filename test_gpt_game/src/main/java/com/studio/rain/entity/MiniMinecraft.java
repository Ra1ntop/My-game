package com.studio.rain.entity;

import com.studio.rain.entity.dw.SimplexNoise;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;

import java.nio.*;
import java.util.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

public class MiniMinecraft {

    private long window;
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;

    private static final int MAP_WIDTH = 256;
    private static final int MAP_HEIGHT = 128; // Изменено на 128
    private static final int MAP_DEPTH = 256;
    private static final int CHUNK_SIZE = 16;
    private static final int CHUNK_HEIGHT = 128; // Изменено на 128
    private static final int RENDER_DISTANCE = 2;

    private Map<ChunkPosition, Chunk> chunks = new HashMap<>();

    private float x = MAP_WIDTH / 2f;
    private float y = MAP_HEIGHT + 5;
    private float z = MAP_DEPTH / 2f;
    private float rotX = 0;
    private float rotY = 0;
    private float velocityY = 0;
    private boolean onGround = false;

    private int selectedBlock = 1;

    public void run() {
        init();
        loop();
        glfwFreeCallbacks(window);
        glfwDestroyWindow(window);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }

    private void init() {
        GLFWErrorCallback.createPrint(System.err).set();
        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(WIDTH, HEIGHT, "Mini Minecraft", NULL, NULL);
        if (window == NULL)
            throw new RuntimeException("Failed to create the GLFW window");

        glfwSetKeyCallback(window, (window, key, scancode, action, mods) -> {
            if (key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE)
                glfwSetWindowShouldClose(window, true);
        });

        glfwSetFramebufferSizeCallback(window, (window, width, height) -> {
            glViewport(0, 0, width, height);
        });

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        glfwShowWindow(window);

        GL.createCapabilities();
        glEnable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        gluPerspective(45.0f, (float) WIDTH / HEIGHT, 0.1f, 1000.0f);
        glMatrixMode(GL_MODELVIEW);

        generateWorld();

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        glfwSetCursorPosCallback(window, (windowHandle, xpos, ypos) -> {
            float sensitivity = 0.1f;
            float dx = (float) xpos - WIDTH / 2;
            float dy = (float) ypos - HEIGHT / 2;

            rotY += dx * sensitivity;
            rotX += dy * sensitivity;

            if (rotX > 90) rotX = 90;
            if (rotX < -90) rotX = -90;

            glfwSetCursorPos(window, WIDTH / 2, HEIGHT / 2);
        });

        glClearColor(0.5f, 0.7f, 1.0f, 1.0f);

        glfwSetMouseButtonCallback(window, (windowHandle, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_1 && action == GLFW_PRESS) {
                placeBlock();
            }
            if (button == GLFW_MOUSE_BUTTON_2 && action == GLFW_PRESS) {
                breakBlock();
            }
        });
    }

    private void loop() {
        while (!glfwWindowShouldClose(window)) {
            glfwPollEvents();
            processInput();

            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            glLoadIdentity();
            glRotatef(rotX, 1, 0, 0);
            glRotatef(rotY, 0, 1, 0);
            glTranslatef(-x, -y, -z);

            renderWorld();

            glfwSwapBuffers(window);
        }
    }

    private void processInput() {
        float speed = 0.1f;
        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
            x += Math.sin(Math.toRadians(rotY)) * speed;
            z -= Math.cos(Math.toRadians(rotY)) * speed;
        }
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
            x -= Math.sin(Math.toRadians(rotY)) * speed;
            z += Math.cos(Math.toRadians(rotY)) * speed;
        }
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
            x -= Math.cos(Math.toRadians(rotY)) * speed;
            z -= Math.sin(Math.toRadians(rotY)) * speed;
        }
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
            x += Math.cos(Math.toRadians(rotY)) * speed;
            z += Math.sin(Math.toRadians(rotY)) * speed;
        }
        if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS && onGround) {
            velocityY = 1.0f;
            onGround = false;
        }

        y += velocityY;
        velocityY -= 0.01f;
        if (velocityY < -0.3f) velocityY = -0.3f;

        if (isBlockAt((int) x, (int) y - 2, (int) z)) {
            onGround = true;
            velocityY = 0;
            y = (int) y;
        } else {
            onGround = false;
        }

        if (glfwGetKey(window, GLFW_KEY_1) == GLFW_PRESS) selectedBlock = 1;
        else if (glfwGetKey(window, GLFW_KEY_2) == GLFW_PRESS) selectedBlock = 2;
        else if (glfwGetKey(window, GLFW_KEY_3) == GLFW_PRESS) selectedBlock = 3;
        else if (glfwGetKey(window, GLFW_KEY_4) == GLFW_PRESS) selectedBlock = 4;

        if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_1) == GLFW_PRESS) placeBlock();
        if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_2) == GLFW_PRESS) breakBlock();
    }

    private void renderWorld() {
        int playerChunkX = (int) Math.floor(x / CHUNK_SIZE);
        int playerChunkZ = (int) Math.floor(z / CHUNK_SIZE);

        for (int dx = -RENDER_DISTANCE; dx <= RENDER_DISTANCE; dx++) {
            for (int dz = -RENDER_DISTANCE; dz <= RENDER_DISTANCE; dz++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;
                ChunkPosition chunkPos = new ChunkPosition(chunkX, chunkZ);

                Chunk chunk = chunks.get(chunkPos);
                if (chunk == null) {
                    chunk = new Chunk(chunkX, chunkZ);
                    generateChunk(chunk);
                    chunks.put(chunkPos, chunk);
                }

                if (isChunkVisible(chunk)) {
                    chunk.render();
                }
            }
        }
    }

    private void generateChunk(Chunk chunk) {
        SimplexNoise noise = new SimplexNoise(new Random(chunk.x * 31 + chunk.z));
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                double frequency = 0.05;
                double amplitude = 32;
                double heightNoise = noise.noise((chunk.x * CHUNK_SIZE + x) * frequency, (chunk.z * CHUNK_SIZE + z) * frequency) * amplitude;
                int height = (int) (64 + heightNoise);
                for (int y = 0; y < CHUNK_HEIGHT; y++) {
                    if (y < height - 4) {
                        chunk.setBlock(x, y, z, 1); // Stone
                    } else if (y < height - 1) {
                        chunk.setBlock(x, y, z, 3); // Dirt
                    } else if (y == height - 1) {
                        chunk.setBlock(x, y, z, 2); // Grass
                    }
                }
            }
        }
    }

    private boolean isChunkVisible(Chunk chunk) {
        float chunkCenterX = chunk.x * CHUNK_SIZE + CHUNK_SIZE / 2f;
        float chunkCenterZ = chunk.z * CHUNK_SIZE + CHUNK_SIZE / 2f;

        float dx = chunkCenterX - x;
        float dz = chunkCenterZ - z;
        float distance = (float) Math.sqrt(dx * dx + dz * dz);

        return distance < RENDER_DISTANCE * CHUNK_SIZE;
    }

    private void generateWorld() {
        Random random = new Random();
        for (int chunkX = -RENDER_DISTANCE; chunkX <= RENDER_DISTANCE; chunkX++) {
            for (int chunkZ = -RENDER_DISTANCE; chunkZ <= RENDER_DISTANCE; chunkZ++) {
                Chunk chunk = new Chunk(chunkX, chunkZ);
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    for (int z = 0; z < CHUNK_SIZE; z++) {
                        int height = 64 + random.nextInt(20); // Генерируемая высота мира 64
                        for (int y = 0; y < CHUNK_HEIGHT; y++) {
                            if (y < height - 4) {
                                chunk.setBlock(x, y, z, 1); // Stone
                            } else if (y < height - 1) {
                                chunk.setBlock(x, y, z, 2); // Dirt
                            } else if (y == height - 1) {
                                chunk.setBlock(x, y, z, 3); // Grass
                            }
                        }
                    }
                }
                chunks.put(new ChunkPosition(chunkX, chunkZ), chunk);
            }
        }
    }

    private boolean isBlockAt(int x, int y, int z) {
        int chunkX = x / CHUNK_SIZE;
        int chunkZ = z / CHUNK_SIZE;
        ChunkPosition chunkPos = new ChunkPosition(chunkX, chunkZ);

        if (chunks.containsKey(chunkPos)) {
            Chunk chunk = chunks.get(chunkPos);
            return chunk.getBlock(x % CHUNK_SIZE, y, z % CHUNK_SIZE) != 0;
        }
        return false;
    }

    private void placeBlock() {
        int[] targetedBlock = getTargetedBlock();
        if (targetedBlock != null) {
            int tx = targetedBlock[0];
            int ty = targetedBlock[1];
            int tz = targetedBlock[2];

            float dirX = (float) Math.sin(Math.toRadians(rotY)) * (float) Math.cos(Math.toRadians(-rotX));
            float dirY = (float) Math.sin(Math.toRadians(-rotX));
            float dirZ = (float) -Math.cos(Math.toRadians(rotY)) * (float) Math.cos(Math.toRadians(-rotX));

            int placeX = (int) (x + dirX * 1.5);
            int placeY = (int) (y + dirY * 1.5);
            int placeZ = (int) (z + dirZ * 1.5);

            setBlock(placeX, placeY, placeZ, selectedBlock);
        }
    }

    private void breakBlock() {
        int[] targetedBlock = getTargetedBlock();
        if (targetedBlock != null) {
            setBlock(targetedBlock[0], targetedBlock[1], targetedBlock[2], 0);
        }
    }

    private void setBlock(int x, int y, int z, int blockType) {
        int chunkX = x / CHUNK_SIZE;
        int chunkZ = z / CHUNK_SIZE;
        ChunkPosition chunkPos = new ChunkPosition(chunkX, chunkZ);

        if (chunks.containsKey(chunkPos)) {
            Chunk chunk = chunks.get(chunkPos);
            chunk.setBlock(x % CHUNK_SIZE, y, z % CHUNK_SIZE, blockType);
            chunk.updateGeometry();
        }
    }

    private int[] getTargetedBlock() {
        for (int dist = 0; dist < 5; dist++) {
            int tx = (int) (x + Math.sin(Math.toRadians(rotY)) * dist);
            int ty = (int) (y - Math.sin(Math.toRadians(rotX)) * dist);
            int tz = (int) (z - Math.cos(Math.toRadians(rotY)) * dist);

            if (isBlockAt(tx, ty, tz)) {
                return new int[]{tx, ty, tz};
            }
        }
        return null;
    }

    private void gluPerspective(float fovy, float aspect, float zNear, float zFar) {
        float ymax = zNear * (float) Math.tan(fovy * Math.PI / 360.0);
        float ymin = -ymax;
        float xmin = ymin * aspect;
        float xmax = ymax * aspect;

        glFrustum(xmin, xmax, ymin, ymax, zNear, zFar);
    }

    private static class ChunkPosition {
        int x, z;

        ChunkPosition(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChunkPosition that = (ChunkPosition) o;
            return x == that.x && z == that.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, z);
        }
    }

    private class Chunk {
        private int x, z;
        private int[][][] blocks = new int[CHUNK_SIZE][CHUNK_HEIGHT][CHUNK_SIZE];
        private int displayList;
        private boolean needsUpdate = true;

        Chunk(int x, int z) {
            this.x = x;
            this.z = z;
            this.displayList = glGenLists(1);
        }

        void generate() {
            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    for (int y = 0; y < CHUNK_HEIGHT; y++) {
                        if (y < 7) {
                            blocks[x][y][z] = 1; // Stone
                        } else if (y < 15) {
                            blocks[x][y][z] = 2; // Dirt
                        } else if (y == 15) {
                            blocks[x][y][z] = 3; // Grass
                        } else {
                            blocks[x][y][z] = 0; // Air
                        }
                    }
                }
            }
            needsUpdate = true;
        }

        int getBlock(int x, int y, int z) {
            if (y < 0 || y >= CHUNK_HEIGHT) return 0;
            return blocks[x][y][z];


        }

        void setBlock(int x, int y, int z, int blockType) {
            if (y >= 0 && y < CHUNK_HEIGHT) {
                blocks[x][y][z] = blockType;
                needsUpdate = true;
            }
        }

        void updateGeometry() {
            if (!needsUpdate) return;

            glNewList(displayList, GL_COMPILE);
            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int y = 0; y < CHUNK_HEIGHT; y++) {
                    for (int z = 0; z < CHUNK_SIZE; z++) {
                        if (blocks[x][y][z] != 0) {
                            renderBlock(x, y, z, blocks[x][y][z]);
                        }
                    }
                }
            }
            glEndList();

            needsUpdate = false;
        }

        void render() {
            if (needsUpdate) {
                updateGeometry();
            }
            glPushMatrix();
            glTranslatef(x * CHUNK_SIZE, 0, z * CHUNK_SIZE);
            glCallList(displayList);
            glPopMatrix();
        }

        private void renderBlock(float x, float y, float z, int type) {
            glColor3f(1.0f, 1.0f, 1.0f);

            switch (type) {
                case 1: glColor3f(0.5f, 0.5f, 0.5f); break; // Stone
                case 2: glColor3f(0.2f, 0.8f, 0.2f); break; // Dirt
                case 3: glColor3f(0.4f, 0.3f, 0.2f); break; // Grass
                case 4: glColor3f(1.0f, 1.0f, 0.0f); break; // Highlight
                default: glColor3f(1.0f, 1.0f, 1.0f); break;
            }

            glBegin(GL_QUADS);
            // Front Face
            glVertex3f(x, y, z + 1);
            glVertex3f(x + 1, y, z + 1);
            glVertex3f(x + 1, y + 1, z + 1);
            glVertex3f(x, y + 1, z + 1);

            // Back Face
            glVertex3f(x, y, z);
            glVertex3f(x, y + 1, z);
            glVertex3f(x + 1, y + 1, z);
            glVertex3f(x + 1, y, z);

            // Top Face
            glVertex3f(x, y + 1, z);
            glVertex3f(x, y + 1, z + 1);
            glVertex3f(x + 1, y + 1, z + 1);
            glVertex3f(x + 1, y + 1, z);

            // Bottom Face
            glVertex3f(x, y, z);
            glVertex3f(x + 1, y, z);
            glVertex3f(x + 1, y, z + 1);
            glVertex3f(x, y, z + 1);

            // Left Face
            glVertex3f(x, y, z);
            glVertex3f(x, y + 1, z);
            glVertex3f(x, y + 1, z + 1);
            glVertex3f(x, y, z + 1);

            // Right Face
            glVertex3f(x + 1, y, z);
            glVertex3f(x + 1, y, z + 1);
            glVertex3f(x + 1, y + 1, z + 1);
            glVertex3f(x + 1, y + 1, z);
            glEnd();
        }
    }

    public static void main(String[] args) {
        new MiniMinecraft().run();
    }
}
package com.studio.rain.entity;

import com.studio.rain.entity.dw.SimplexNoise;
import org.lwjgl.BufferUtils;
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
    private float y = 65;
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
            glTranslatef(-x, -y-1, -z);
            renderWorld();
            drawCrosshair();
            glfwSwapBuffers(window); // Swap the color buffers
        }
    }

    private void processInput() {
        float speed = 0.1f;
        float newX = x;
        float newZ = z;

        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
            newX += Math.sin(Math.toRadians(rotY)) * speed;
            newZ -= Math.cos(Math.toRadians(rotY)) * speed;
        }
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
            newX -= Math.sin(Math.toRadians(rotY)) * speed;
            newZ += Math.cos(Math.toRadians(rotY)) * speed;
        }
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
            newX -= Math.cos(Math.toRadians(rotY)) * speed;
            newZ -= Math.sin(Math.toRadians(rotY)) * speed;
        }
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
            newX += Math.cos(Math.toRadians(rotY)) * speed;
            newZ += Math.sin(Math.toRadians(rotY)) * speed;
        }

        // Проверка коллизий по X и Z
        if (!isBlockAt((int)newX, (int)y, (int)z) && !isBlockAt((int)newX, (int)y - 1, (int)z)) {
            x = newX;
        }
        if (!isBlockAt((int)x, (int)y, (int)newZ) && !isBlockAt((int)x, (int)y - 1, (int)newZ)) {
            z = newZ;
        }

        if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS && onGround) {
            velocityY = 0.2f;
            onGround = false;
        }

        y += velocityY;
        velocityY -= 0.01f;
        if (velocityY < -0.3f) velocityY = -0.3f;

        if (isBlockAt((int) x, (int) y - 2, (int) z) || isBlockAt((int) x, (int) y - 1, (int) z)) {
            onGround = true;
            velocityY = 0;
            y = (float)Math.ceil(y - 1) + 1; // Устанавливаем Y на верх блока + 1
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
        if (y < 0 || y >= CHUNK_HEIGHT) return false;
        int chunkX = Math.floorDiv(x, CHUNK_SIZE);
        int chunkZ = Math.floorDiv(z, CHUNK_SIZE);
        ChunkPosition chunkPos = new ChunkPosition(chunkX, chunkZ);

        if (chunks.containsKey(chunkPos)) {
            Chunk chunk = chunks.get(chunkPos);
            return chunk.getBlock(Math.floorMod(x, CHUNK_SIZE), y, Math.floorMod(z, CHUNK_SIZE)) != 0;
        }
        return false;
    }

    private void drawCrosshair() {
        glPushMatrix();
        glLoadIdentity();

        glDisable(GL_DEPTH_TEST);
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, WIDTH, HEIGHT, 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // Установите цвет крестика на белый
        glColor3f(1.0f, 1.0f, 1.0f);

        // Рисуем горизонтальную линию
        glBegin(GL_LINES);
        glVertex2f(WIDTH / 2 - 10, HEIGHT / 2);
        glVertex2f(WIDTH / 2 + 10, HEIGHT / 2);
        glEnd();

        // Рисуем вертикальную линию
        glBegin(GL_LINES);
        glVertex2f(WIDTH / 2, HEIGHT / 2 - 10);
        glVertex2f(WIDTH / 2, HEIGHT / 2 + 10);
        glEnd();

        glEnable(GL_DEPTH_TEST);
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }

    private long lastBlockPlacementTime = 0;
    private static final long BLOCK_PLACEMENT_COOLDOWN = 250;

    private void placeBlock() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBlockPlacementTime < BLOCK_PLACEMENT_COOLDOWN) {
            return; // Еще не прошло достаточно времени с момента последней установки блока
        }

        RaycastResult result = raycast(5.0f);
        if (result != null) {
            int placeX = result.x + result.face.getOffsetX();
            int placeY = result.y + result.face.getOffsetY();
            int placeZ = result.z + result.face.getOffsetZ();

            if (!isBlockIntersectingPlayer(placeX, placeY, placeZ)) {
                setBlock(placeX, placeY, placeZ, selectedBlock);
                lastBlockPlacementTime = currentTime; // Обновляем время последней установки блока

            }
        }
    }
    private void breakBlock() {
        RaycastResult result = raycast(5.0f);
        if (result != null) {
            setBlock(result.x, result.y, result.z, 0);
        }
    }

    private RaycastResult raycast(float maxDist) {
        float[] start = new float[]{x, y + 1.62f, z}; // Добавляем смещение для глаз игрока
        float[] dir = getPlayerLookVector();
        float stepSize = 0.05f;
        int steps = (int) (maxDist / stepSize);

        for (int i = 0; i < steps; i++) {
            float[] currentPos = new float[]{
                    start[0] + dir[0] * stepSize * i,
                    start[1] + dir[1] * stepSize * i,
                    start[2] + dir[2] * stepSize * i
            };

            int blockX = (int) Math.floor(currentPos[0]);
            int blockY = (int) Math.floor(currentPos[1]);
            int blockZ = (int) Math.floor(currentPos[2]);

            if (isBlockAt(blockX, blockY, blockZ)) {
                BlockFace face = getHitFace(currentPos, new float[]{blockX, blockY, blockZ});
                return new RaycastResult(blockX, blockY, blockZ, face);
            }
        }

        return null;
    }

    private float[] getPlayerLookVector() {
        float dirX = (float) (Math.sin(Math.toRadians(rotY)) * Math.cos(Math.toRadians(rotX)));
        float dirY = (float) -Math.sin(Math.toRadians(rotX));
        float dirZ = (float) (-Math.cos(Math.toRadians(rotY)) * Math.cos(Math.toRadians(rotX)));

        float length = (float) Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        return new float[]{dirX / length, dirY / length, dirZ / length};
    }
    private boolean isBlockIntersectingPlayer(int blockX, int blockY, int blockZ) {
        float playerMinX = x - 0.3f;
        float playerMaxX = x + 0.3f;
        float playerMinY = y - 1.8f;
        float playerMaxY = y;
        float playerMinZ = z - 0.3f;
        float playerMaxZ = z + 0.3f;

        return (blockX < playerMaxX && blockX + 1 > playerMinX) &&
                (blockY < playerMaxY && blockY + 1 > playerMinY) &&
                (blockZ < playerMaxZ && blockZ + 1 > playerMinZ);
    }

    private RaycastResult raycastBlock(float[] start, float[] dir, float maxDist) {
        float stepSize = 0.1f;
        int steps = (int) (maxDist / stepSize);

        for (int i = 0; i < steps; i++) {
            float[] currentPos = new float[]{
                    start[0] + dir[0] * stepSize * i,
                    start[1] + dir[1] * stepSize * i,
                    start[2] + dir[2] * stepSize * i
            };

            int blockX = (int) Math.floor(currentPos[0]);
            int blockY = (int) Math.floor(currentPos[1]);
            int blockZ = (int) Math.floor(currentPos[2]);

            if (isBlockAt(blockX, blockY, blockZ)) {
                BlockFace face = getHitFace(currentPos, new float[]{blockX, blockY, blockZ});
                return new RaycastResult(blockX, blockY, blockZ, face);
            }
        }

        return null;
    }


    private BlockFace getHitFace(float[] hitPoint, float[] blockPos) {
        float epsilon = 0.001f;
        float dx = hitPoint[0] - blockPos[0];
        float dy = hitPoint[1] - blockPos[1];
        float dz = hitPoint[2] - blockPos[2];

        if (Math.abs(dx) < epsilon) return BlockFace.WEST;
        if (Math.abs(dx - 1) < epsilon) return BlockFace.EAST;
        if (Math.abs(dy) < epsilon) return BlockFace.DOWN;
        if (Math.abs(dy - 1) < epsilon) return BlockFace.UP;
        if (Math.abs(dz) < epsilon) return BlockFace.NORTH;
        if (Math.abs(dz - 1) < epsilon) return BlockFace.SOUTH;

        // Если не попали точно в грань, выбираем ближайшую
        float[] distances = {dx, 1-dx, dy, 1-dy, dz, 1-dz};
        int minIndex = 0;
        for (int i = 1; i < 6; i++) {
            if (distances[i] < distances[minIndex]) {
                minIndex = i;
            }
        }

        switch (minIndex) {
            case 0: return BlockFace.WEST;
            case 1: return BlockFace.EAST;
            case 2: return BlockFace.DOWN;
            case 3: return BlockFace.UP;
            case 4: return BlockFace.NORTH;
            case 5: return BlockFace.SOUTH;
            default: return BlockFace.UP; // Никогда не должно произойти
        }
    }


    private void placeBlock1() {
        int[] targetedBlock = getTargetedBlock();
        if (targetedBlock != null) {
            int tx = targetedBlock[0];
            int ty = targetedBlock[1];
            int tz = targetedBlock[2];

            float dirX = (float) Math.sin(Math.toRadians(rotY)) * (float) Math.cos(Math.toRadians(-rotX));
            float dirY = (float) Math.sin(Math.toRadians(-rotX));
            float dirZ = (float) -Math.cos(Math.toRadians(rotY)) * (float) Math.cos(Math.toRadians(-rotX));

            int placeX = (int) (x + dirX * 1.5);
            int placeY = (int) ((y) + dirY * 1.5); // Вычитаем 2 из y, чтобы учесть новую высоту игрока
            int placeZ = (int) (z + dirZ * 1.5);

            setBlock(placeX, placeY, placeZ, selectedBlock);
        }
    }

    private void breakBlock1() {
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
            int ty = (int) ((y) - Math.sin(Math.toRadians(rotX)) * dist); // Вычитаем 2 из y
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
        private boolean needsUpdate = true;
        private FloatBuffer vertexBuffer;
        private FloatBuffer colorBuffer;
        private int vertexCount;

        Chunk(int x, int z) {
            this.x = x;
            this.z = z;
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

        private boolean isBlockVisible(int x, int y, int z) {
            return (x == 0 || x == CHUNK_SIZE - 1 || y == 0 || y == CHUNK_HEIGHT - 1 || z == 0 || z == CHUNK_SIZE - 1)
                    || (getBlock(x+1, y, z) == 0) || (getBlock(x-1, y, z) == 0)
                    || (getBlock(x, y+1, z) == 0) || (getBlock(x, y-1, z) == 0)
                    || (getBlock(x, y, z+1) == 0) || (getBlock(x, y, z-1) == 0);
        }

        void updateGeometry() {
            if (!needsUpdate) return;

            List<Float> vertices = new ArrayList<>();
            List<Float> colors = new ArrayList<>();

            for (int x = 0; x < CHUNK_SIZE; x++) {
                for (int y = 0; y < CHUNK_HEIGHT; y++) {
                    for (int z = 0; z < CHUNK_SIZE; z++) {
                        if (blocks[x][y][z] != 0 && isBlockVisible(x, y, z)) {
                            addBlockToMesh(x, y, z, blocks[x][y][z], vertices, colors);
                        }
                    }
                }
            }

            vertexBuffer = BufferUtils.createFloatBuffer(vertices.size());
            for (Float v : vertices) {
                vertexBuffer.put(v);
            }
            vertexBuffer.flip();

            colorBuffer = BufferUtils.createFloatBuffer(colors.size());
            for (Float c : colors) {
                colorBuffer.put(c);
            }
            colorBuffer.flip();

            vertexCount = vertices.size() / 3;

            needsUpdate = false;
        }

        private void addBlockToMesh(int x, int y, int z, int type, List<Float> vertices, List<Float> colors) {
            float[] blockVertices = {
                    // Front face
                    x, y, z+1,
                    x+1, y, z+1,
                    x+1, y+1, z+1,
                    x, y+1, z+1,
                    // Back face
                    x, y, z,
                    x, y+1, z,
                    x+1, y+1, z,
                    x+1, y, z,
                    // Top face
                    x, y+1, z,
                    x, y+1, z+1,
                    x+1, y+1, z+1,
                    x+1, y+1, z,
                    // Bottom face
                    x, y, z,
                    x+1, y, z,
                    x+1, y, z+1,
                    x, y, z+1,
                    // Left face
                    x, y, z,
                    x, y+1, z,
                    x, y+1, z+1,
                    x, y, z+1,
                    // Right face
                    x+1, y, z,
                    x+1, y, z+1,
                    x+1, y+1, z+1,
                    x+1, y+1, z
            };

            for (float v : blockVertices) {
                vertices.add(v);
            }

            float[] blockColor = getBlockColor(type);
            for (int i = 0; i < blockVertices.length / 3; i++) {
                colors.add(blockColor[0]);
                colors.add(blockColor[1]);
                colors.add(blockColor[2]);
            }
        }

        private float[] getBlockColor(int type) {
            switch (type) {
                case 1: return new float[]{0.5f, 0.5f, 0.5f}; // Stone
                case 2: return new float[]{0.2f, 0.8f, 0.2f}; // Dirt
                case 3: return new float[]{0.4f, 0.3f, 0.2f}; // Grass
                case 4: return new float[]{1.0f, 1.0f, 0.0f}; // Highlight
                default: return new float[]{1.0f, 1.0f, 1.0f};
            }
        }

        void render() {
            if (needsUpdate) {
                updateGeometry();
            }

            glPushMatrix();
            glTranslatef(x * CHUNK_SIZE, 0, z * CHUNK_SIZE);

            glEnableClientState(GL_VERTEX_ARRAY);
            glEnableClientState(GL_COLOR_ARRAY);

            glVertexPointer(3, GL_FLOAT, 0, vertexBuffer);
            glColorPointer(3, GL_FLOAT, 0, colorBuffer);

            glDrawArrays(GL_QUADS, 0, vertexCount);

            glDisableClientState(GL_VERTEX_ARRAY);
            glDisableClientState(GL_COLOR_ARRAY);

            // Рендерим подсвеченную грань
            RaycastResult highlightedBlock = raycast(5.0f);
            if (highlightedBlock != null &&
                    highlightedBlock.x / CHUNK_SIZE == this.x &&
                    highlightedBlock.z / CHUNK_SIZE == this.z) {
                renderHighlightedFace(highlightedBlock);
            }

            glPopMatrix();
        }

        private void renderHighlightedFace(RaycastResult result) {
            glDisable(GL_TEXTURE_2D);
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            glBegin(GL_QUADS);
            glColor4f(1.0f, 1.0f, 1.0f, 0.4f);

            float x = result.x % CHUNK_SIZE;
            float y = result.y;
            float z = result.z % CHUNK_SIZE;
            float e = 0.002f; // небольшое смещение для предотвращения z-fighting

            // Рисуем все грани с небольшим смещением
            // UP
            glVertex3f(x - e, y + 1 + e, z - e);
            glVertex3f(x + 1 + e, y + 1 + e, z - e);
            glVertex3f(x + 1 + e, y + 1 + e, z + 1 + e);
            glVertex3f(x - e, y + 1 + e, z + 1 + e);

            // DOWN
            glVertex3f(x - e, y - e, z - e);
            glVertex3f(x - e, y - e, z + 1 + e);
            glVertex3f(x + 1 + e, y - e, z + 1 + e);
            glVertex3f(x + 1 + e, y - e, z - e);

            // NORTH
            glVertex3f(x - e, y - e, z - e);
            glVertex3f(x + 1 + e, y - e, z - e);
            glVertex3f(x + 1 + e, y + 1 + e, z - e);
            glVertex3f(x - e, y + 1 + e, z - e);

            // SOUTH
            glVertex3f(x - e, y - e, z + 1 + e);
            glVertex3f(x - e, y + 1 + e, z + 1 + e);
            glVertex3f(x + 1 + e, y + 1 + e, z + 1 + e);
            glVertex3f(x + 1 + e, y - e, z + 1 + e);

            // WEST
            glVertex3f(x - e, y - e, z - e);
            glVertex3f(x - e, y + 1 + e, z - e);
            glVertex3f(x - e, y + 1 + e, z + 1 + e);
            glVertex3f(x - e, y - e, z + 1 + e);

            // EAST
            glVertex3f(x + 1 + e, y - e, z - e);
            glVertex3f(x + 1 + e, y - e, z + 1 + e);
            glVertex3f(x + 1 + e, y + 1 + e, z + 1 + e);
            glVertex3f(x + 1 + e, y + 1 + e, z - e);

            glEnd();

            glDisable(GL_BLEND);
            glEnable(GL_TEXTURE_2D);
        }

    }



    public static void main(String[] args) {
        new MiniMinecraft().run();
    }

    private enum BlockFace {
        UP(0, 1, 0),
        DOWN(0, -1, 0),
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1),
        WEST(-1, 0, 0),
        EAST(1, 0, 0);

        private final int offsetX, offsetY, offsetZ;

        BlockFace(int offsetX, int offsetY, int offsetZ) {
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
        }

        public int getOffsetX() { return offsetX; }
        public int getOffsetY() { return offsetY; }
        public int getOffsetZ() { return offsetZ; }
    }



    private static class RaycastResult {
        int x, y, z;
        BlockFace face;

        RaycastResult(int x, int y, int z, BlockFace face) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.face = face;
        }
    }
}




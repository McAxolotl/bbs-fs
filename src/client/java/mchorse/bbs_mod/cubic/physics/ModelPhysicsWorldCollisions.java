package mchorse.bbs_mod.cubic.physics;

import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.joml.Vector3f;

public final class ModelPhysicsWorldCollisions
{
    private static final float EPS = 1.0e-6f;
    private static final int MAX_POINT_REP = 4;
    private static final int MAX_CAPSULE_REP = 3;
    private static final float NORMAL_SMOOTHING_ALPHA = 0.35F;

    private ModelPhysicsWorldCollisions()
    {
    }

    public static void resolve(World world, Vector3f[] pos, Vector3f[] prev, Vector3f[] normals, int from, int to, float radius, float friction, float contactSlop, float sleepEps, float staticFrictionEps, float collisionEps)
    {
        if (world == null || pos == null || prev == null || normals == null || from < 0 || to > pos.length || to > prev.length || to > normals.length || from >= to || radius <= 0F)
        {
            return;
        }

        BlockPos.Mutable mutable = new BlockPos.Mutable();

        applyWorldCollisions(world, mutable, pos, prev, from, to, radius, friction, collisionEps, sleepEps, staticFrictionEps);
        applyWorldCollisions(world, mutable, pos, prev, from, to, radius, friction, collisionEps, sleepEps, staticFrictionEps);

        if (contactSlop > 0F)
        {
            applyWorldContactFriction(world, mutable, pos, prev, normals, from, to, radius, contactSlop, friction, sleepEps, staticFrictionEps);
        }
    }

    public static boolean hasFullCubeInAabb(World world, BlockPos.Mutable mutable, int minBX, int minBY, int minBZ, int maxBX, int maxBY, int maxBZ)
    {
        if (world == null)
        {
            return false;
        }

        for (int x = minBX; x <= maxBX; x++)
        {
            for (int y = minBY; y <= maxBY; y++)
            {
                for (int z = minBZ; z <= maxBZ; z++)
                {
                    mutable.set(x, y, z);

                    if (!world.isChunkLoaded(mutable))
                    {
                        continue;
                    }

                    BlockState block = world.getBlockState(mutable);

                    if (block == null)
                    {
                        continue;
                    }

                    if (block.isFullCube(world, mutable))
                    {
                        return true;
                    }

                    VoxelShape shape = block.getCollisionShape(world, mutable, ShapeContext.absent());
                    if (!shape.isEmpty())
                    {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static void applyWorldCollisions(World world, BlockPos.Mutable mutable, Vector3f[] pos, Vector3f[] prev, int from, int to, float radius, float friction, float collisionEps, float sleepEps, float staticFrictionEps)
    {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (int i = from; i < to; i++)
        {
            Vector3f p = pos[i];
            Vector3f s = prev[i];
            float x1 = p.x - radius;
            float y1 = p.y - radius;
            float z1 = p.z - radius;
            float x2 = p.x + radius;
            float y2 = p.y + radius;
            float z2 = p.z + radius;

            float sx1 = s.x - radius;
            float sy1 = s.y - radius;
            float sz1 = s.z - radius;
            float sx2 = s.x + radius;
            float sy2 = s.y + radius;
            float sz2 = s.z + radius;

            if (sx1 < x1) x1 = sx1;
            if (sy1 < y1) y1 = sy1;
            if (sz1 < z1) z1 = sz1;
            if (sx2 > x2) x2 = sx2;
            if (sy2 > y2) y2 = sy2;
            if (sz2 > z2) z2 = sz2;

            if (x1 < minX) minX = x1;
            if (y1 < minY) minY = y1;
            if (z1 < minZ) minZ = z1;
            if (x2 > maxX) maxX = x2;
            if (y2 > maxY) maxY = y2;
            if (z2 > maxZ) maxZ = z2;
        }

        int minBlockX = MathHelper.floor(minX);
        int minBlockY = MathHelper.floor(minY);
        int minBlockZ = MathHelper.floor(minZ);
        int maxBlockX = MathHelper.floor(maxX);
        int maxBlockY = MathHelper.floor(maxY);
        int maxBlockZ = MathHelper.floor(maxZ);

        for (int i = from; i < to; i++)
        {
            Vector3f p = pos[i];
            Vector3f s = prev[i];

            float xMin = p.x;
            float yMin = p.y;
            float zMin = p.z;
            float xMax = p.x;
            float yMax = p.y;
            float zMax = p.z;

            if (s.x < xMin) xMin = s.x;
            if (s.y < yMin) yMin = s.y;
            if (s.z < zMin) zMin = s.z;
            if (s.x > xMax) xMax = s.x;
            if (s.y > yMax) yMax = s.y;
            if (s.z > zMax) zMax = s.z;

            int bx1 = MathHelper.floor(xMin - radius);
            int by1 = MathHelper.floor(yMin - radius);
            int bz1 = MathHelper.floor(zMin - radius);
            int bx2 = MathHelper.floor(xMax + radius);
            int by2 = MathHelper.floor(yMax + radius);
            int bz2 = MathHelper.floor(zMax + radius);

            if (bx1 < minBlockX) bx1 = minBlockX;
            if (by1 < minBlockY) by1 = minBlockY;
            if (bz1 < minBlockZ) bz1 = minBlockZ;
            if (bx2 > maxBlockX) bx2 = maxBlockX;
            if (by2 > maxBlockY) by2 = maxBlockY;
            if (bz2 > maxBlockZ) bz2 = maxBlockZ;

            float dxs = p.x - s.x;
            float dys = p.y - s.y;
            float dzs = p.z - s.z;
            boolean hasMotion = dxs * dxs + dys * dys + dzs * dzs > EPS * EPS;

            float bestT = Float.POSITIVE_INFINITY;
            float bestNx = 0F;
            float bestNy = 0F;
            float bestNz = 0F;

            if (hasMotion)
            {
                for (int x = bx1; x <= bx2; x++)
                {
                    for (int y = by1; y <= by2; y++)
                    {
                        for (int z = bz1; z <= bz2; z++)
                        {
                            mutable.set(x, y, z);

                            if (!world.isChunkLoaded(mutable))
                            {
                                continue;
                            }

                            BlockState state = world.getBlockState(mutable);

                            if (state == null)
                            {
                                continue;
                            }

                            if (state.isFullCube(world, mutable))
                            {
                                float minBX = x;
                                float minBY = y;
                                float minBZ = z;
                                float maxBX = x + 1F;
                                float maxBY = y + 1F;
                                float maxBZ = z + 1F;

                                float exMinX = minBX - radius;
                                float exMinY = minBY - radius;
                                float exMinZ = minBZ - radius;
                                float exMaxX = maxBX + radius;
                                float exMaxY = maxBY + radius;
                                float exMaxZ = maxBZ + radius;

                                float tmin = 0F;
                                float tmax = 1F;
                                int axis = -1;
                                float axisSign = 0F;

                                if (Math.abs(dxs) < EPS)
                                {
                                    if (s.x < exMinX || s.x > exMaxX) continue;
                                }
                                else
                                {
                                    float inv = 1F / dxs;
                                    float t1 = (exMinX - s.x) * inv;
                                    float t2 = (exMaxX - s.x) * inv;
                                    float enter = t1;
                                    float exit = t2;
                                    if (enter > exit)
                                    {
                                        float tmp = enter; enter = exit; exit = tmp;
                                    }
                                    if (enter > tmin)
                                    {
                                        tmin = enter;
                                        axis = 0;
                                        axisSign = dxs > 0F ? -1F : 1F;
                                    }
                                    if (exit < tmax) tmax = exit;
                                    if (tmin > tmax) continue;
                                }

                                if (Math.abs(dys) < EPS)
                                {
                                    if (s.y < exMinY || s.y > exMaxY) continue;
                                }
                                else
                                {
                                    float inv = 1F / dys;
                                    float t1 = (exMinY - s.y) * inv;
                                    float t2 = (exMaxY - s.y) * inv;
                                    float enter = t1;
                                    float exit = t2;
                                    if (enter > exit)
                                    {
                                        float tmp = enter; enter = exit; exit = tmp;
                                    }
                                    if (enter > tmin)
                                    {
                                        tmin = enter;
                                        axis = 1;
                                        axisSign = dys > 0F ? -1F : 1F;
                                    }
                                    if (exit < tmax) tmax = exit;
                                    if (tmin > tmax) continue;
                                }

                                if (Math.abs(dzs) < EPS)
                                {
                                    if (s.z < exMinZ || s.z > exMaxZ) continue;
                                }
                                else
                                {
                                    float inv = 1F / dzs;
                                    float t1 = (exMinZ - s.z) * inv;
                                    float t2 = (exMaxZ - s.z) * inv;
                                    float enter = t1;
                                    float exit = t2;
                                    if (enter > exit)
                                    {
                                        float tmp = enter; enter = exit; exit = tmp;
                                    }
                                    if (enter > tmin)
                                    {
                                        tmin = enter;
                                        axis = 2;
                                        axisSign = dzs > 0F ? -1F : 1F;
                                    }
                                    if (exit < tmax) tmax = exit;
                                    if (tmin > tmax) continue;
                                }

                                if (tmax < 0F || tmin > 1F)
                                {
                                    continue;
                                }

                                float thit = tmin < 0F ? 0F : tmin;

                                if (thit < bestT)
                                {
                                    bestT = thit;
                                    bestNx = 0F;
                                    bestNy = 0F;
                                    bestNz = 0F;
                                    if (axis == 0) bestNx = axisSign;
                                    else if (axis == 1) bestNy = axisSign;
                                    else if (axis == 2) bestNz = axisSign;
                                }
                            }
                            else
                            {
                                VoxelShape shape = state.getCollisionShape(world, mutable, ShapeContext.absent());
                                if (shape.isEmpty())
                                {
                                    continue;
                                }

                                for (Box box : shape.getBoundingBoxes())
                                {
                                    float minBX = (float) (x + box.minX);
                                    float minBY = (float) (y + box.minY);
                                    float minBZ = (float) (z + box.minZ);
                                    float maxBX = (float) (x + box.maxX);
                                    float maxBY = (float) (y + box.maxY);
                                    float maxBZ = (float) (z + box.maxZ);

                                    float exMinX = minBX - radius;
                                    float exMinY = minBY - radius;
                                    float exMinZ = minBZ - radius;
                                    float exMaxX = maxBX + radius;
                                    float exMaxY = maxBY + radius;
                                    float exMaxZ = maxBZ + radius;

                                    float tmin = 0F;
                                    float tmax = 1F;
                                    int axis = -1;
                                    float axisSign = 0F;

                                    if (Math.abs(dxs) < EPS)
                                    {
                                        if (s.x < exMinX || s.x > exMaxX) continue;
                                    }
                                    else
                                    {
                                        float inv = 1F / dxs;
                                        float t1 = (exMinX - s.x) * inv;
                                        float t2 = (exMaxX - s.x) * inv;
                                        float enter = t1;
                                        float exit = t2;
                                        if (enter > exit)
                                        {
                                            float tmp = enter; enter = exit; exit = tmp;
                                        }
                                        if (enter > tmin)
                                        {
                                            tmin = enter;
                                            axis = 0;
                                            axisSign = dxs > 0F ? -1F : 1F;
                                        }
                                        if (exit < tmax) tmax = exit;
                                        if (tmin > tmax) continue;
                                    }

                                    if (Math.abs(dys) < EPS)
                                    {
                                        if (s.y < exMinY || s.y > exMaxY) continue;
                                    }
                                    else
                                    {
                                        float inv = 1F / dys;
                                        float t1 = (exMinY - s.y) * inv;
                                        float t2 = (exMaxY - s.y) * inv;
                                        float enter = t1;
                                        float exit = t2;
                                        if (enter > exit)
                                        {
                                            float tmp = enter; enter = exit; exit = tmp;
                                        }
                                        if (enter > tmin)
                                        {
                                            tmin = enter;
                                            axis = 1;
                                            axisSign = dys > 0F ? -1F : 1F;
                                        }
                                        if (exit < tmax) tmax = exit;
                                        if (tmin > tmax) continue;
                                    }

                                    if (Math.abs(dzs) < EPS)
                                    {
                                        if (s.z < exMinZ || s.z > exMaxZ) continue;
                                    }
                                    else
                                    {
                                        float inv = 1F / dzs;
                                        float t1 = (exMinZ - s.z) * inv;
                                        float t2 = (exMaxZ - s.z) * inv;
                                        float enter = t1;
                                        float exit = t2;
                                        if (enter > exit)
                                        {
                                            float tmp = enter; enter = exit; exit = tmp;
                                        }
                                        if (enter > tmin)
                                        {
                                            tmin = enter;
                                            axis = 2;
                                            axisSign = dzs > 0F ? -1F : 1F;
                                        }
                                        if (exit < tmax) tmax = exit;
                                        if (tmin > tmax) continue;
                                    }

                                    if (tmax < 0F || tmin > 1F)
                                    {
                                        continue;
                                    }

                                    float thit = tmin < 0F ? 0F : tmin;

                                    if (thit < bestT)
                                    {
                                        bestT = thit;
                                        bestNx = 0F;
                                        bestNy = 0F;
                                        bestNz = 0F;
                                        if (axis == 0) bestNx = axisSign;
                                        else if (axis == 1) bestNy = axisSign;
                                        else if (axis == 2) bestNz = axisSign;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (bestT != Float.POSITIVE_INFINITY)
            {
                p.x = s.x + dxs * bestT;
                p.y = s.y + dys * bestT;
                p.z = s.z + dzs * bestT;

                p.x += bestNx * collisionEps;
                p.y += bestNy * collisionEps;
                p.z += bestNz * collisionEps;

                applyCollisionFriction(prev[i], p, bestNx, bestNy, bestNz, friction, sleepEps, staticFrictionEps);
            }

            Vector3f push = new Vector3f();
            Vector3f normal = new Vector3f();

            for (int rep = 0; rep < MAX_POINT_REP; rep++)
            {
                float bestPen = 0F;
                float bestPx = 0F;
                float bestPy = 0F;
                float bestPz = 0F;
                float bestCNx = 0F;
                float bestCNy = 0F;
                float bestCNz = 0F;

                for (int x = bx1; x <= bx2; x++)
                {
                    for (int y = by1; y <= by2; y++)
                    {
                        for (int z = bz1; z <= bz2; z++)
                        {
                            mutable.set(x, y, z);
                            if (!world.isChunkLoaded(mutable)) continue;
                            BlockState state = world.getBlockState(mutable);
                            if (state == null) continue;

                            if (state.isFullCube(world, mutable))
                            {
                                float pen = querySphereAabbPenetration(p.x, p.y, p.z, radius, x, y, z, x + 1F, y + 1F, z + 1F, collisionEps, push, normal);
                                if (pen > bestPen)
                                {
                                    bestPen = pen;
                                    bestPx = push.x;
                                    bestPy = push.y;
                                    bestPz = push.z;
                                    bestCNx = normal.x;
                                    bestCNy = normal.y;
                                    bestCNz = normal.z;
                                }
                            }
                            else
                            {
                                VoxelShape shape = state.getCollisionShape(world, mutable, ShapeContext.absent());
                                if (shape.isEmpty()) continue;
                                for (Box box : shape.getBoundingBoxes())
                                {
                                    float minBX = (float) (x + box.minX);
                                    float minBY = (float) (y + box.minY);
                                    float minBZ = (float) (z + box.minZ);
                                    float maxBX = (float) (x + box.maxX);
                                    float maxBY = (float) (y + box.maxY);
                                    float maxBZ = (float) (z + box.maxZ);
                                    float pen = querySphereAabbPenetration(p.x, p.y, p.z, radius, minBX, minBY, minBZ, maxBX, maxBY, maxBZ, collisionEps, push, normal);
                                    if (pen > bestPen)
                                    {
                                        bestPen = pen;
                                        bestPx = push.x;
                                        bestPy = push.y;
                                        bestPz = push.z;
                                        bestCNx = normal.x;
                                        bestCNy = normal.y;
                                        bestCNz = normal.z;
                                    }
                                }
                            }
                        }
                    }
                }

                if (bestPen <= 0F)
                {
                    break;
                }

                p.x += bestPx;
                p.y += bestPy;
                p.z += bestPz;
                applyCollisionFriction(prev[i], p, bestCNx, bestCNy, bestCNz, friction, sleepEps, staticFrictionEps);
            }
        }

        applyWorldCapsuleDepenetration(world, mutable, pos, prev, from, to, radius, friction, collisionEps, sleepEps, staticFrictionEps, minBlockX, minBlockY, minBlockZ, maxBlockX, maxBlockY, maxBlockZ);
    }

    private static float querySphereAabbPenetration(float px, float py, float pz, float radius, float minBX, float minBY, float minBZ, float maxBX, float maxBY, float maxBZ, float collisionEps, Vector3f outPush, Vector3f outNormal)
    {
        float cx = MathHelper.clamp(px, minBX, maxBX);
        float cy = MathHelper.clamp(py, minBY, maxBY);
        float cz = MathHelper.clamp(pz, minBZ, maxBZ);

        float dx = px - cx;
        float dy = py - cy;
        float dz = pz - cz;

        float lenSq = dx * dx + dy * dy + dz * dz;

        if (lenSq <= EPS * EPS)
        {
            float dMinX = px - minBX;
            float dMaxX = maxBX - px;
            float dMinY = py - minBY;
            float dMaxY = maxBY - py;
            float dMinZ = pz - minBZ;
            float dMaxZ = maxBZ - pz;

            float penMinX = radius - dMinX;
            float penMaxX = radius - dMaxX;
            float penMinY = radius - dMinY;
            float penMaxY = radius - dMaxY;
            float penMinZ = radius - dMinZ;
            float penMaxZ = radius - dMaxZ;

            float best = Float.POSITIVE_INFINITY;
            float nx = 0F;
            float ny = 0F;
            float nz = 0F;
            float push = 0F;

            if (penMinX > 0F && penMinX < best) { best = penMinX; push = -penMinX; nx = -1F; ny = 0F; nz = 0F; }
            if (penMaxX > 0F && penMaxX < best) { best = penMaxX; push = penMaxX; nx = 1F; ny = 0F; nz = 0F; }
            if (penMinY > 0F && penMinY < best) { best = penMinY; push = -penMinY; nx = 0F; ny = -1F; nz = 0F; }
            if (penMaxY > 0F && penMaxY < best) { best = penMaxY; push = penMaxY; nx = 0F; ny = 1F; nz = 0F; }
            if (penMinZ > 0F && penMinZ < best) { best = penMinZ; push = -penMinZ; nx = 0F; ny = 0F; nz = -1F; }
            if (penMaxZ > 0F && penMaxZ < best) { best = penMaxZ; push = penMaxZ; nx = 0F; ny = 0F; nz = 1F; }

            if (best == Float.POSITIVE_INFINITY)
            {
                return 0F;
            }

            float p = Math.abs(push) + collisionEps;
            outPush.set(nx * p, ny * p, nz * p);
            outNormal.set(nx, ny, nz);
            return best;
        }

        float len = (float) Math.sqrt(lenSq);
        float penetration = radius - len;
        if (penetration <= 0F)
        {
            return 0F;
        }

        float invLen = 1F / len;
        float nx = dx * invLen;
        float ny = dy * invLen;
        float nz = dz * invLen;

        float push = penetration + collisionEps;
        outPush.set(nx * push, ny * push, nz * push);
        outNormal.set(nx, ny, nz);
        return penetration;
    }

    private static void applyWorldCapsuleDepenetration(World world, BlockPos.Mutable mutable, Vector3f[] pos, Vector3f[] prev, int from, int to, float radius, float friction, float collisionEps, float sleepEps, float staticFrictionEps, int minBlockX, int minBlockY, int minBlockZ, int maxBlockX, int maxBlockY, int maxBlockZ)
    {
        if (to - from < 1 || radius <= 0F)
        {
            return;
        }

        for (int rep = 0; rep < 3; rep++)
        {
            boolean changed = false;

            for (int i = from; i < to; i++)
            {
                int ia = i - 1;
                int ib = i;

                if (ia < 0 || ib < 0 || ib >= pos.length)
                {
                    continue;
                }

                Vector3f a = pos[ia];
                Vector3f b = pos[ib];

                float wa = ia == 0 ? 0F : 1F;
                float wb = 1F;
                float invW = 1F / (wa + wb);
                wa *= invW;
                wb *= invW;

                float minX = a.x < b.x ? a.x : b.x;
                float minY = a.y < b.y ? a.y : b.y;
                float minZ = a.z < b.z ? a.z : b.z;
                float maxX = a.x > b.x ? a.x : b.x;
                float maxY = a.y > b.y ? a.y : b.y;
                float maxZ = a.z > b.z ? a.z : b.z;

                int bx1 = MathHelper.floor(minX - radius);
                int by1 = MathHelper.floor(minY - radius);
                int bz1 = MathHelper.floor(minZ - radius);
                int bx2 = MathHelper.floor(maxX + radius);
                int by2 = MathHelper.floor(maxY + radius);
                int bz2 = MathHelper.floor(maxZ + radius);

                if (bx1 < minBlockX) bx1 = minBlockX;
                if (by1 < minBlockY) by1 = minBlockY;
                if (bz1 < minBlockZ) bz1 = minBlockZ;
                if (bx2 > maxBlockX) bx2 = maxBlockX;
                if (by2 > maxBlockY) by2 = maxBlockY;
                if (bz2 > maxBlockZ) bz2 = maxBlockZ;

                float dx = b.x - a.x;
                float dy = b.y - a.y;
                float dz = b.z - a.z;
                float dLenSq = dx * dx + dy * dy + dz * dz;

                float bestPen = 0F;
                float bestPx = 0F;
                float bestPy = 0F;
                float bestPz = 0F;
                float bestNx = 0F;
                float bestNy = 0F;
                float bestNz = 0F;

                for (int x = bx1; x <= bx2; x++)
                {
                    for (int y = by1; y <= by2; y++)
                    {
                        for (int z = bz1; z <= bz2; z++)
                        {
                            mutable.set(x, y, z);

                            if (!world.isChunkLoaded(mutable))
                            {
                                continue;
                            }

                            BlockState state = world.getBlockState(mutable);

                            if (state == null)
                            {
                                continue;
                            }

                            Vector3f push = new Vector3f();
                            Vector3f normal = new Vector3f();
                            if (state.isFullCube(world, mutable))
                            {
                                float tHit = closestSegmentAabbT(a.x, a.y, a.z, dx, dy, dz, dLenSq, x, y, z, x + 1F, y + 1F, z + 1F);
                                float cpx = a.x + dx * tHit;
                                float cpy = a.y + dy * tHit;
                                float cpz = a.z + dz * tHit;
                                float pen = querySphereAabbPenetration(cpx, cpy, cpz, radius, x, y, z, x + 1F, y + 1F, z + 1F, collisionEps, push, normal);
                                if (pen > bestPen)
                                {
                                    bestPen = pen;
                                    bestPx = push.x;
                                    bestPy = push.y;
                                    bestPz = push.z;
                                    bestNx = normal.x;
                                    bestNy = normal.y;
                                    bestNz = normal.z;
                                }
                            }
                            else
                            {
                                VoxelShape shape = state.getCollisionShape(world, mutable, ShapeContext.absent());
                                if (!shape.isEmpty())
                                {
                                    for (Box box : shape.getBoundingBoxes())
                                    {
                                        float minBX = (float) (x + box.minX);
                                        float minBY = (float) (y + box.minY);
                                        float minBZ = (float) (z + box.minZ);
                                        float maxBX = (float) (x + box.maxX);
                                        float maxBY = (float) (y + box.maxY);
                                        float maxBZ = (float) (z + box.maxZ);
                                        float tHit = closestSegmentAabbT(a.x, a.y, a.z, dx, dy, dz, dLenSq, minBX, minBY, minBZ, maxBX, maxBY, maxBZ);
                                        float cpx = a.x + dx * tHit;
                                        float cpy = a.y + dy * tHit;
                                        float cpz = a.z + dz * tHit;
                                        float pen = querySphereAabbPenetration(cpx, cpy, cpz, radius, minBX, minBY, minBZ, maxBX, maxBY, maxBZ, collisionEps, push, normal);
                                        if (pen > bestPen)
                                        {
                                            bestPen = pen;
                                            bestPx = push.x;
                                            bestPy = push.y;
                                            bestPz = push.z;
                                            bestNx = normal.x;
                                            bestNy = normal.y;
                                            bestNz = normal.z;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (bestPen > 0F)
                {
                    if (wa > 0F)
                    {
                        a.x += bestPx * wa;
                        a.y += bestPy * wa;
                        a.z += bestPz * wa;
                        applyCollisionFriction(prev[ia], a, bestNx, bestNy, bestNz, friction, sleepEps, staticFrictionEps);
                    }
                    b.x += bestPx * wb;
                    b.y += bestPy * wb;
                    b.z += bestPz * wb;
                    applyCollisionFriction(prev[ib], b, bestNx, bestNy, bestNz, friction, sleepEps, staticFrictionEps);
                    changed = true;
                }
            }

            if (!changed)
            {
                break;
            }
        }
    }
    private static float closestSegmentAabbT(float ax, float ay, float az, float dx, float dy, float dz, float dLenSq, float minBX, float minBY, float minBZ, float maxBX, float maxBY, float maxBZ)
    {
        if (dLenSq <= EPS * EPS)
        {
            return 0F;
        }

        float t = 0.5F;
        for (int it = 0; it < 3; it++)
        {
            float px = ax + dx * t;
            float py = ay + dy * t;
            float pz = az + dz * t;

            float qx = MathHelper.clamp(px, minBX, maxBX);
            float qy = MathHelper.clamp(py, minBY, maxBY);
            float qz = MathHelper.clamp(pz, minBZ, maxBZ);

            float tx = qx - ax;
            float ty = qy - ay;
            float tz = qz - az;
            t = (tx * dx + ty * dy + tz * dz) / dLenSq;
            if (t < 0F) t = 0F;
            else if (t > 1F) t = 1F;
        }
        return t;
    }

    private static void applyWorldContactFriction(World world, BlockPos.Mutable mutable, Vector3f[] pos, Vector3f[] prev, Vector3f[] normals, int from, int to, float radius, float slop, float friction, float sleepEps, float staticFrictionEps)
    {
        float search = radius + slop;

        if (search <= 0F)
        {
            return;
        }

        for (int i = from; i < to; i++)
        {
            Vector3f p = pos[i];
            Vector3f pr = prev[i];

            Vector3f normal = normals[i];
            float ox = normal.x;
            float oy = normal.y;
            float oz = normal.z;

            if (!findClosestBlockNormal(world, mutable, p.x, p.y, p.z, search, normal))
            {
                normal.set(0F, 0F, 0F);
                continue;
            }

            float nx = normal.x;
            float ny = normal.y;
            float nz = normal.z;

            float oLenSq = ox * ox + oy * oy + oz * oz;
            if (oLenSq > EPS * EPS)
            {
                float dot = ox * nx + oy * ny + oz * nz;
                if (dot < 0F)
                {
                    ox = -ox;
                    oy = -oy;
                    oz = -oz;
                }

                float a = NORMAL_SMOOTHING_ALPHA;
                nx = ox * (1F - a) + nx * a;
                ny = oy * (1F - a) + ny * a;
                nz = oz * (1F - a) + nz * a;

                float nLenSq = nx * nx + ny * ny + nz * nz;
                if (nLenSq > EPS * EPS)
                {
                    float inv = 1F / (float) Math.sqrt(nLenSq);
                    nx *= inv;
                    ny *= inv;
                    nz *= inv;
                }

                normal.set(nx, ny, nz);
            }

            float vx = p.x - pr.x;
            float vy = p.y - pr.y;
            float vz = p.z - pr.z;

            float vn = vx * nx + vy * ny + vz * nz;

            if (vn < 0F)
            {
                vx -= nx * vn;
                vy -= ny * vn;
                vz -= nz * vn;
            }

            float vtSq = vx * vx + vy * vy + vz * vz;

            if (vtSq <= sleepEps * sleepEps)
            {
                continue;
            }

            float vt = (float) Math.sqrt(vtSq);
            float stickFactor = 1F;

            if (vt <= staticFrictionEps)
            {
                stickFactor = vt / staticFrictionEps;
                if (stickFactor < 0.2F)
                {
                    stickFactor = 0.2F;
                }
            }

            float f = MathHelper.clamp(friction, 0F, 1F);
            float scale = (1F - f) * stickFactor;
            vx *= scale;
            vy *= scale;
            vz *= scale;

            pr.x = p.x - vx;
            pr.y = p.y - vy;
            pr.z = p.z - vz;
        }
    }

    private static boolean findClosestFullCubeNormal(World world, BlockPos.Mutable mutable, float px, float py, float pz, float search, Vector3f outNormal)
    {
        if (world == null || outNormal == null || search <= 0F)
        {
            return false;
        }

        float bestDistSq = Float.POSITIVE_INFINITY;
        float bestNx = 0F;
        float bestNy = 0F;
        float bestNz = 0F;

        float searchSq = search * search;

        int bx = MathHelper.floor(px);
        int by = MathHelper.floor(py);
        int bz = MathHelper.floor(pz);

        for (int di = 0; di < 7; di++)
        {
            int x = bx;
            int y = by;
            int z = bz;

            if (di == 1) x++;
            else if (di == 2) x--;
            else if (di == 3) y++;
            else if (di == 4) y--;
            else if (di == 5) z++;
            else if (di == 6) z--;

            mutable.set(x, y, z);

            if (!world.isChunkLoaded(mutable))
            {
                continue;
            }

            BlockState state = world.getBlockState(mutable);

            if (state == null)
            {
                continue;
            }

            if (state.isFullCube(world, mutable))
            {
                float minBX = x;
                float minBY = y;
                float minBZ = z;
                float maxBX = x + 1F;
                float maxBY = y + 1F;
                float maxBZ = z + 1F;

                float cx = MathHelper.clamp(px, minBX, maxBX);
                float cy = MathHelper.clamp(py, minBY, maxBY);
                float cz = MathHelper.clamp(pz, minBZ, maxBZ);

                float dx = px - cx;
                float dy = py - cy;
                float dz = pz - cz;

                float d2 = dx * dx + dy * dy + dz * dz;

                if (d2 <= searchSq && d2 < bestDistSq)
                {
                    float nx;
                    float ny;
                    float nz;

                    if (d2 <= EPS * EPS)
                    {
                        float dMinX = px - minBX;
                        float dMaxX = maxBX - px;
                        float dMinY = py - minBY;
                        float dMaxY = maxBY - py;
                        float dMinZ = pz - minBZ;
                        float dMaxZ = maxBZ - pz;

                        float best = dMinX;
                        nx = -1F; ny = 0F; nz = 0F;

                        if (dMaxX < best) { best = dMaxX; nx = 1F; ny = 0F; nz = 0F; }
                        if (dMinY < best) { best = dMinY; nx = 0F; ny = -1F; nz = 0F; }
                        if (dMaxY < best) { best = dMaxY; nx = 0F; ny = 1F; nz = 0F; }
                        if (dMinZ < best) { best = dMinZ; nx = 0F; ny = 0F; nz = -1F; }
                        if (dMaxZ < best) { nx = 0F; ny = 0F; nz = 1F; }
                    }
                    else
                    {
                        float inv = 1F / (float) Math.sqrt(d2);
                        nx = dx * inv;
                        ny = dy * inv;
                        nz = dz * inv;
                    }

                    bestDistSq = d2;
                    bestNx = nx;
                    bestNy = ny;
                    bestNz = nz;
                }

                continue;
            }

            VoxelShape shape = state.getCollisionShape(world, mutable, ShapeContext.absent());
            if (shape.isEmpty())
            {
                continue;
            }

            for (Box box : shape.getBoundingBoxes())
            {
                float minBX = (float) (x + box.minX);
                float minBY = (float) (y + box.minY);
                float minBZ = (float) (z + box.minZ);
                float maxBX = (float) (x + box.maxX);
                float maxBY = (float) (y + box.maxY);
                float maxBZ = (float) (z + box.maxZ);

                float cx = MathHelper.clamp(px, minBX, maxBX);
                float cy = MathHelper.clamp(py, minBY, maxBY);
                float cz = MathHelper.clamp(pz, minBZ, maxBZ);

                float dx = px - cx;
                float dy = py - cy;
                float dz = pz - cz;

                float d2 = dx * dx + dy * dy + dz * dz;

                if (d2 > searchSq || d2 >= bestDistSq)
                {
                    continue;
                }

                float nx;
                float ny;
                float nz;

                if (d2 <= EPS * EPS)
                {
                    float dMinX = px - minBX;
                    float dMaxX = maxBX - px;
                    float dMinY = py - minBY;
                    float dMaxY = maxBY - py;
                    float dMinZ = pz - minBZ;
                    float dMaxZ = maxBZ - pz;

                    float best = dMinX;
                    nx = -1F; ny = 0F; nz = 0F;

                    if (dMaxX < best) { best = dMaxX; nx = 1F; ny = 0F; nz = 0F; }
                    if (dMinY < best) { best = dMinY; nx = 0F; ny = -1F; nz = 0F; }
                    if (dMaxY < best) { best = dMaxY; nx = 0F; ny = 1F; nz = 0F; }
                    if (dMinZ < best) { best = dMinZ; nx = 0F; ny = 0F; nz = -1F; }
                    if (dMaxZ < best) { nx = 0F; ny = 0F; nz = 1F; }
                }
                else
                {
                    float inv = 1F / (float) Math.sqrt(d2);
                    nx = dx * inv;
                    ny = dy * inv;
                    nz = dz * inv;
                }

                bestDistSq = d2;
                bestNx = nx;
                bestNy = ny;
                bestNz = nz;
            }
        }

        if (bestDistSq == Float.POSITIVE_INFINITY)
        {
            return false;
        }

        outNormal.set(bestNx, bestNy, bestNz);

        if (outNormal.lengthSquared() > EPS * EPS)
        {
            outNormal.normalize();
        }

        return true;
    }

    private static boolean findClosestBlockNormal(World world, BlockPos.Mutable mutable, float px, float py, float pz, float search, Vector3f outNormal)
    {
        return findClosestFullCubeNormal(world, mutable, px, py, pz, search, outNormal);
    }

    @FunctionalInterface
    private interface BoxVisitor
    {
        boolean visit(float minBX, float minBY, float minBZ, float maxBX, float maxBY, float maxBZ);
    }

    private static boolean forEachCollisionBox(World world, BlockPos.Mutable mutable, int x, int y, int z, BoxVisitor visitor)
    {
        mutable.set(x, y, z);

        if (!world.isChunkLoaded(mutable))
        {
            return false;
        }

        BlockState state = world.getBlockState(mutable);
        if (state == null)
        {
            return false;
        }

        if (state.isFullCube(world, mutable))
        {
            return visitor.visit(x, y, z, x + 1F, y + 1F, z + 1F);
        }

        VoxelShape shape = state.getCollisionShape(world, mutable, ShapeContext.absent());
        if (shape.isEmpty())
        {
            return false;
        }

        for (Box box : shape.getBoundingBoxes())
        {
            if (visitor.visit((float) (x + box.minX), (float) (y + box.minY), (float) (z + box.minZ),
                (float) (x + box.maxX), (float) (y + box.maxY), (float) (z + box.maxZ)))
            {
                return true;
            }
        }

        return true;
    }

    private static void applyCollisionFriction(Vector3f prev, Vector3f pos, float nx, float ny, float nz, float friction, float sleepEps, float staticFrictionEps)
    {
        float vx = pos.x - prev.x;
        float vy = pos.y - prev.y;
        float vz = pos.z - prev.z;

        float dot = vx * nx + vy * ny + vz * nz;

        if (dot < 0F)
        {
            vx -= nx * dot;
            vy -= ny * dot;
            vz -= nz * dot;
        }

        float speedSq = vx * vx + vy * vy + vz * vz;

        float speed = (float) Math.sqrt(speedSq);
        float stickFactor = 1F;

        if (speed <= staticFrictionEps)
        {
            stickFactor = speed / staticFrictionEps;
            if (stickFactor < 0.2F)
            {
                stickFactor = 0.2F;
            }
        }

        float f = MathHelper.clamp(friction, 0F, 1F);
        float scale = (1F - f) * stickFactor;
        vx *= scale;
        vy *= scale;
        vz *= scale;

        prev.x = pos.x - vx;
        prev.y = pos.y - vy;
        prev.z = pos.z - vz;
    }
}


package com.uky.corpses;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.vecmath.Vector3f;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.uky.corpses.blood.Blood;
import com.uky.corpses.corpse.Mat4;
import com.uky.corpses.corpse.Poses;
import com.uky.corpses.corpse.Ragdoll;
import com.uky.corpses.corpse.RagdollWorld;
import com.uky.corpses.corpse.Settle;
import com.uky.corpses.corpse.Skeleton;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.registry.IEntityAdditionalSpawnData;
import io.netty.buffer.ByteBuf;

/**
 * The body left after a death, lying or sitting against a wall the way its owner went down. Holds everything they
 * carried. The owner right-clicks it to get the things back; anyone else, once the lock runs out, searches it (hold
 * right-click) and rummages through a chest. Sneak-click drags it. The body goes when it's empty. Nothing can hurt it.
 */
public class EntityCorpse extends Entity implements IInventory, IEntityAdditionalSpawnData {

    /** Slot ids: 0.. = InventoryPlayer.mainInventory (Backhand's offhand is its last index), ARMOR+i = armor. */
    public static final int ARMOR = 100;
    /** On the back, sitting against a wall, face down, on the side. */
    public static final int LIE = Poses.LIE, SIT = Poses.SIT, PRONE = Poses.PRONE, SIDE = Poses.SIDE;
    /** Burnt black by fire, or scorched by a blast. */
    public static final int BURNT = 1, SCORCHED = 2;

    /** Ticks of held right-click before a stranger gets the chest. */
    /** Ticks a search takes (CorpseConfig.searchSeconds). */
    public static int search() {
        return CorpseConfig.searchSeconds * 20;
    }

    private static final int SIZE = 54;

    // DataWatcher ids; 0-1 are vanilla's. ARMOR_W..ARMOR_W+3 hold armor stacks for the renderer.
    // Watcher ids stop at 31. FROM_*: where the player stood when they died, relative to where the body lies.
    private static final int OWNER = 20, POSE = 21, COUNT = 22, XP = 23, AGE = 24, LOCK = 25, ARMOR_W = 26,
        CHARRED = 30, LOOK = 31, FRESH = 18, HELD = 19, FROM_YAW = 17, SITU = 16, BEND = 15, GRID = 14, FROM_X = 13,
        FROM_Y = 12, FROM_Z = 11, FLIGHT = 10, APEX = 9, LEAN = 8, TUCK = 7, DRAG = 6, SKELETAL = 5;

    public static final class Entry {

        public final int slot; // -1: no known slot
        public final ItemStack stack;

        public Entry(int slot, ItemStack stack) {
            this.slot = slot;
            this.stack = stack;
        }
    }

    public UUID ownerId;
    private ItemStack[] items = new ItemStack[SIZE];
    /** Player slot each stack came from, -1 unknown. */
    private final int[] home = new int[SIZE];
    public int xp;
    public long created;
    private long cleanupBorn = -1;
    /** Ticks only the owner may open it; -1 forever. Fixed at death so a config change can't unlock old bodies. */
    public long lockTicks;
    private final CorpseLifecycle lifecycle = new CorpseLifecycle();

    public boolean isSkeletal() {
        return dataWatcher.getWatchableObjectByte(SKELETAL) != 0;
    }

    public boolean cleanupExpired() {
        return com.uky.corpses.corpse.PhysicsConfig
            .expired(worldObj.getTotalWorldTime(), cleanupBorn < 0 ? worldObj.getTotalWorldTime() : cleanupBorn);
    }

    public int loadedAgeTicks() {
        return lifecycle.ageTicks;
    }

    /** entityInit done: Entity's constructor calls setPosition before the watcher exists. No initializer on purpose. */
    private boolean ready;
    private final Map<UUID, int[]> search = new HashMap<>(); // {ticks searched}: runs on its own (tickSearch)
    private final Set<UUID> searchedBy = new HashSet<>();
    private EntityPlayer dragger;
    private double dragX, dragZ;
    private int dragPing;
    /** Hotbar slot selected at death: the stack that came from there is shown in the body's hand. */
    private int heldSlot = -1;
    /** Client only: the settling animation of a fresh body. */
    public final Settle settle = new Settle();
    private Ragdoll ragdoll;
    private double ragX, ragY, ragZ;
    private long ragGeneration = UUID.randomUUID()
        .getLeastSignificantBits(), ragSequence;
    private long physicalLife = (ragGeneration & Long.MAX_VALUE) | 1L;

    public int ampHiddenMask() {
        return dataWatcher.getWatchableObjectByte(4) & 61;
    }

    public void ampHiddenMask(int mask) {
        dataWatcher.updateObject(4, (byte) (mask & 61));
        if (ragdoll != null) ragdoll.hideParts(mask);
    }

    public int severedMask() {
        return ragdoll == null ? 0 : ragdoll.severed();
    }

    public UUID physicalOwner() {
        return ownerId;
    }

    public long physicalLife() {
        return physicalLife;
    }

    public void physicalLife(long value) {
        if (value > 0) physicalLife = value;
    }

    public void disposePhysics() {
        if (ragdoll != null) ragdoll.disposePhysics();
    }

    private long lastRagSequence = -1;
    private int ragPacketAt, ragPacketInterval = 4, ragServerTick;
    private int dragPart = Skeleton.HEAD, interactionPart = Skeleton.BODY, walkPushAt;
    private float ragImpact;
    private boolean voidFloor;
    final NBTTagCompound voidReturn = new NBTTagCompound();

    public boolean ragdollActive() {
        return ragdoll != null;
    }

    public Skeleton.Shape physicalShape(float partial) {
        if (ragdoll != null) ragdoll.hideParts(ampHiddenMask());
        if (ragdoll == null) return null;
        if (worldObj.isRemote)
            partial = MathHelper.clamp_float((ticksExisted - ragPacketAt + partial) / ragPacketInterval, 0, 1);
        return ragdoll.shape(partial, ragX - posX, ragY - posY, ragZ - posZ);
    }

    /** The body as it lies (both sides: hitboxes); remade when what it depends on changes. */
    private Skeleton.Shape shape;
    private long shapeKey = Long.MIN_VALUE;
    /** Client only: ticksExisted when it last hit the floor hard (the death camera shakes). */
    public int impactAt = -1;
    /** Client only: one entity per limb piece, what the crosshair actually hits (see CorpsePart). */
    private CorpsePart[] parts;
    private final float[] resting = new float[Settle.N];
    /** Going away for good (despawn without drops). */
    private boolean silent;
    /** Set when the body lies as a block (Corpses.asBlock): never spawned, the tile ticks, saves and draws it. */
    public TileCorpse host;
    /** Called after every sync(): the hosting tile sends itself again. */
    public Runnable changed;

    public EntityCorpse(World world) {
        super(world);
        Arrays.fill(home, -1);
        setSize(0.7F, 0.4F);
        isImmuneToFire = true;
    }

    @Override
    protected void entityInit() {
        dataWatcher.addObject(OWNER, "");
        dataWatcher.addObject(POSE, (byte) LIE);
        dataWatcher.addObject(COUNT, 0);
        dataWatcher.addObject(XP, 0);
        dataWatcher.addObject(AGE, 0);
        dataWatcher.addObject(LOCK, 0);
        for (int i = 0; i < 4; i++) dataWatcher.addObjectByDataType(ARMOR_W + i, 5);
        dataWatcher.addObject(CHARRED, (byte) 0);
        // how it lies (Poses.look): the same picture on every client
        dataWatcher.addObject(LOOK, Poses.choose(rand, LIE, Poses.GENERIC));
        // Settle mode it comes down in: 1 fell, 2 thrown, 3 died downed, 4 from a height. 0: just lies (loaded).
        // A client's own copy stays quiet until told.
        dataWatcher.addObject(FRESH, (byte) (worldObj.isRemote ? 0 : Settle.FALL));
        dataWatcher.addObjectByDataType(HELD, 5);
        dataWatcher.addObject(FROM_YAW, 0F);
        dataWatcher.addObject(SITU, (byte) 0);
        dataWatcher.addObject(GRID, (byte) 0);
        dataWatcher.addObject(BEND, (byte) 0);
        dataWatcher.addObject(FROM_X, 0F);
        dataWatcher.addObject(FROM_Y, 0F);
        dataWatcher.addObject(FROM_Z, 0F);
        dataWatcher.addObject(FLIGHT, (byte) 0);
        dataWatcher.addObject(APEX, 0F);
        dataWatcher.addObject(LEAN, (byte) 0);
        dataWatcher.addObject(TUCK, (byte) 0);
        dataWatcher.addObject(DRAG, 0);
        dataWatcher.addObject(4, (byte) 0);
        dataWatcher.addObject(SKELETAL, (byte) 0);
        ready = true;
    }

    /**
     * The body's own box, for falling, floating, dragging and fitting into a spot: 1.8 long along its yaw (always a
     * multiple of 90), the trunk's width; what the limbs reach beyond it is hit through CorpsePart and BlockCorpse.
     */
    @Override
    public void setPosition(double x, double y, double z) {
        super.setPosition(x, y, z);
        if (!ready) return;
        if (ragdoll != null) {
            Skeleton.Shape physical = physicalShape(1);
            double[] b = physical.bounds(Skeleton.BODY);
            for (int part = 0; part < Skeleton.PARTS; part++) if ((ragdoll.severed() & 1 << part) == 0) {
                double[] limb = physical.bounds(part);
                for (int axis = 0; axis < 3; axis++) {
                    b[axis] = Math.min(b[axis], limb[axis]);
                    b[axis + 3] = Math.max(b[axis + 3], limb[axis + 3]);
                }
            }
            boundingBox.setBounds(x + b[0], y + b[1], z + b[2], x + b[3], y + b[4], z + b[5]);
            return;
        }
        boolean sit = pose() == SIT, alongX = (MathHelper.floor_double(rotationYaw / 90F + 0.5) & 1) == 1;
        if (grid() && sit) { // its block
            double bx = MathHelper.floor_double(x), bz = MathHelper.floor_double(z);
            boundingBox.setBounds(bx, y, bz, bx + 1, y + 1, bz + 1);
            return;
        }
        double along = grid() ? 1 : 0.9, across = grid() ? 0.5 : 0.35; // on the grid: exactly its two blocks
        double hx = sit ? 0.4 : alongX ? along : across, hz = sit ? 0.4 : alongX ? across : along;
        boundingBox.setBounds(x - hx, y, z - hz, x + hx, y + (sit ? 1.0 : grid() ? 0.5 : 0.4), z + hz);
    }

    /** How it lies now; remade when the pose, look, ground or yaw changed. */
    public Skeleton.Shape shape() {
        if (ragdoll != null) return physicalShape(1);
        long key = ((long) pose() << 56) ^ ((long) look() << 24)
            ^ ((long) situation() << 16)
            ^ (dataWatcher.getWatchableObjectByte(BEND) & 255) << 8
            ^ Math.round(rotationYaw) & 0xFFFF
            ^ (long) (dataWatcher.getWatchableObjectByte(LEAN) & 255) << 40
            ^ (long) (tuck() & 255) << 48;
        if (shape == null || key != shapeKey) {
            shapeKey = key;
            shape = Skeleton.rest(pose(), rotationYaw, look(), situation(), bend(), lean(), tuck());
        }
        return shape;
    }

    /** Rendering, picking, camera and server interaction share the authoritative physical pose. */
    public Skeleton.Shape drawnShape(float partial) {
        return ragdoll == null ? shape() : physicalShape(partial);
    }

    /** Died in fire or lava (BURNT, drawn black) or in a blast (SCORCHED, sooty). */
    public boolean charred() {
        return dataWatcher.getWatchableObjectByte(CHARRED) != 0;
    }

    public int burn() {
        return dataWatcher.getWatchableObjectByte(CHARRED);
    }

    public void setBurn(int how) {
        dataWatcher.updateObject(CHARRED, (byte) how);
    }

    /** Poses.look: which variant, mirrored, jitter, cause. */
    public int look() {
        return dataWatcher.getWatchableObjectInt(LOOK);
    }

    public void setLook(int look) {
        dataWatcher.updateObject(LOOK, look);
        setPosition(posX, posY, posZ);
    }

    /** Another pose than first chosen (the ground didn't allow it): a variant of it for the same death. */
    public void repose(int pose) {
        setPose(pose);
        setLook(Poses.choose(rand, pose, Poses.causeOf(look())));
    }

    /** Degrees it lies tilted along a step or slope (Skeleton.posed). */
    public float lean() {
        return dataWatcher.getWatchableObjectByte(LEAN);
    }

    public void setLean(float degrees) {
        dataWatcher.updateObject(LEAN, (byte) Math.max(-80, Math.min(80, Math.round(degrees))));
    }

    /** Limbs pulled in away from blocks, a bit per Skeleton part. */
    public int tuck() {
        return dataWatcher.getWatchableObjectByte(TUCK) & 255;
    }

    public void setTuck(int mask) {
        dataWatcher.updateObject(TUCK, (byte) mask);
    }

    /** Resting limb angles. */
    public float[] resting() {
        Poses.rest(pose(), look(), situation(), bend(), tuck(), resting);
        return resting;
    }

    /** What the body holds in its hand, null for nothing. */
    public ItemStack held() {
        return dataWatcher.getWatchableObjectItemStack(HELD);
    }

    /** Hotbar slot that was selected at death; before or after fill. */
    public void setHeldSlot(int slot) {
        heldSlot = slot;
        sync();
    }

    /**
     * Where the player was when they died: clients start the body there, standing (or lying, if downed) the way they
     * were, and bring it to where it rests. Before spawning, after it's placed.
     */
    public void from(double x, double y, double z, float yaw) {
        dataWatcher.updateObject(FROM_X, (float) (x - posX));
        dataWatcher.updateObject(FROM_Y, (float) (y - posY));
        dataWatcher.updateObject(FROM_Z, (float) (z - posZ));
        dataWatcher.updateObject(FROM_YAW, yaw);
    }

    /** How it comes down on clients (Settle mode). Before spawning. */
    public void comeDown(int mode) {
        dataWatcher.updateObject(FRESH, (byte) mode);
    }

    /** Died while downed, lying there already: no fall, the limbs go slack while it turns square. */
    public void dying() {
        comeDown(Settle.DYING);
    }

    /** Lies on the block grid (Situation found whole blocks for it): the box is whole blocks. */
    public boolean grid() {
        return dataWatcher.getWatchableObjectByte(GRID) != 0;
    }

    public void setGrid(boolean g) {
        dataWatcher.updateObject(GRID, (byte) (g ? 1 : 0));
        setPosition(posX, posY, posZ);
    }

    /** Situation.* the ground put it in, and how far (radians, signed) it bends what hangs over an edge. */
    public int situation() {
        return dataWatcher.getWatchableObjectByte(SITU);
    }

    public float bend() {
        return dataWatcher.getWatchableObjectByte(BEND) / 20F;
    }

    public void setSituation(int situation, float bend) {
        dataWatcher.updateObject(SITU, (byte) situation);
        dataWatcher.updateObject(BEND, (byte) Math.round(bend * 20));
        setPosition(posX, posY, posZ);
    }

    /** Thrown flat from a height: slams into the floor. Before spawning. */
    public void splat() {
        comeDown(Settle.SPLAT);
    }

    /** Blown off its feet: flies `ticks` ticks along an arc `apex` above the straight line, then lands. */
    public void thrown(int ticks, float apex) {
        comeDown(Settle.THROWN);
        dataWatcher.updateObject(FLIGHT, (byte) Math.min(60, ticks));
        dataWatcher.updateObject(APEX, apex);
    }

    /** Drowned or died in lava: comes up from where they died to the surface over `ticks`, limp. Before spawning. */
    public void floats(int ticks) {
        comeDown(Settle.FLOAT);
        dataWatcher.updateObject(FLIGHT, (byte) Math.min(80, ticks));
    }

    /** Client: told to come down but hasn't started yet (the next tick does): not drawn until then. */
    public boolean pending() {
        return ragdoll == null && !settle.began()
            && dataWatcher.getWatchableObjectByte(FRESH) != 0
            && ticksExisted < 40;
    }

    /**
     * Client: where it is drawn this frame relative to its position: a frame (lifted to the floor) into out, the limbs'
     * angles into angles, the offset of the whole into off.
     */
    public Mat4 frameNow(float partial, float[] angles, double[] off, Mat4 out) {
        if (ragdoll != null) {
            Skeleton.Shape physical = physicalShape(partial);
            off[0] = off[1] = off[2] = 0;
            System.arraycopy(physical.angles, 0, angles, 0, angles.length);
            return out.set(physical.frame);
        }
        int pose = pose();
        float roll = Poses.roll(pose, look()), k = 1, spin = Float.NaN, hips = 0;
        off[0] = off[1] = off[2] = 0;
        if (settle.active()) {
            settle.limbs(partial, angles);
            k = settle.flying(partial) ? settle.progress(partial) : settle.tip(partial);
            spin = settle.spin(partial);
            hips = settle.hips(partial);
            settle.offset(partial, off);
        } else {
            System.arraycopy(resting(), 0, angles, 0, Settle.N);
            if (settle.held() != 0) settle.heldLimbs(angles); // what stopped against a block stays there
        }
        // the floor holds the body up by where its arms rest, not where they swing: an arm passing through the floor
        // dips into it (and the probe stops it) instead of lifting the whole body; the same once it lies still
        float[] liftBy = liftAngles;
        System.arraycopy(angles, 0, liftBy, 0, Settle.N);
        System.arraycopy(resting(), 3, liftBy, 3, 6);
        Skeleton.posed(out, pose, drawYaw(partial), k, roll, spin, hips, angles, situation(), lean(), liftBy);
        return out;
    }

    private final float[] liftAngles = new float[Settle.N];

    /** Scratch for the probe: the limbs aren't asked about from more than one thread. */
    private final Mat4 probeFrame = new Mat4(), probePart = new Mat4();
    private final float[] probeAngles = new float[Settle.N];
    private final double[] probeOff = new double[3], probeAt = new double[3];
    /** Limb (Settle's order) to Skeleton part. */
    private static final int[] LIMB_PART = { Skeleton.HEAD, Skeleton.RARM, Skeleton.LARM, Skeleton.RLEG,
        Skeleton.LLEG };

    /** Whether the far end of a limb (the crown of the head, a hand, a foot) is inside a block. */
    private final Settle.Probe probe = (limb, partial) -> {
        try {
            return blocked(limb, partial);
        } catch (RuntimeException e) { // a modded block that can't be asked without an entity: nothing there
            return false;
        }
    };

    private boolean blocked(int limb, float partial) {
        frameNow(partial, probeAngles, probeOff, probeFrame);
        int part = LIMB_PART[limb];
        Skeleton.part(probeFrame, part, probeAngles, probePart);
        float[] lo = Skeleton.min(part), hi = Skeleton.max(part);
        if (part == Skeleton.HEAD) probePart.apply(0, lo[1] + 1, 0, probeAt);
        else probePart.apply((lo[0] + hi[0]) / 2, hi[1] - 1, (lo[2] + hi[2]) / 2, probeAt);
        double x = posX + probeOff[0] + probeAt[0], y = posY + probeOff[1] + probeAt[1],
            z = posZ + probeOff[2] + probeAt[2], r = 0.06;
        // the floor it lies on is let in a little, as the resting pose sinks into it
        return !worldObj.func_147461_a(AxisAlignedBB.getBoundingBox(x - r, y + 0.08, z - r, x + r, y + 0.14, z + r))
            .isEmpty();
    }

    /** Client: the yaw it's drawn at, mid-turn while it comes round from how the player stood. */
    public float drawYaw(float partial) {
        return settle.active() ? settle.yaw(partial, rotationYaw) : rotationYaw;
    }

    public static boolean isExplosion(DamageSource s) {
        return s.isExplosion() || "explosion".equals(s.damageType)
            || "explosion.player".equals(s.damageType)
            || "fireball".equals(s.damageType);
    }

    public String owner() {
        return dataWatcher.getWatchableObjectString(OWNER);
    }

    public void setOwner(String name) {
        dataWatcher.updateObject(OWNER, name);
    }

    public int pose() {
        return dataWatcher.getWatchableObjectByte(POSE);
    }

    public void setPose(int pose) {
        dataWatcher.updateObject(POSE, (byte) pose);
        setPosition(posX, posY, posZ);
    }

    /** Client side: stacks, xp, minutes since death, minutes of lock left (-1 forever). */
    public int count() {
        return dataWatcher.getWatchableObjectInt(COUNT);
    }

    public int xpShown() {
        return dataWatcher.getWatchableObjectInt(XP);
    }

    public int ageMinutes() {
        return dataWatcher.getWatchableObjectInt(AGE);
    }

    public int lockMinutes() {
        return dataWatcher.getWatchableObjectInt(LOCK);
    }

    /** 0 boots .. 3 helmet, as in armorInventory. */
    public ItemStack armor(int i) {
        return dataWatcher.getWatchableObjectItemStack(ARMOR_W + i);
    }

    /** For a block body's packet: how it is coming down right now (never saved: a loaded body just lies). */
    void writeFresh(NBTTagCompound tag) {
        tag.setByte("fresh", dataWatcher.getWatchableObjectByte(FRESH));
        tag.setFloat("fromYaw", dataWatcher.getWatchableObjectFloat(FROM_YAW));
        tag.setFloat("fromX", dataWatcher.getWatchableObjectFloat(FROM_X));
        tag.setFloat("fromY", dataWatcher.getWatchableObjectFloat(FROM_Y));
        tag.setFloat("fromZ", dataWatcher.getWatchableObjectFloat(FROM_Z));
        tag.setByte("flight", dataWatcher.getWatchableObjectByte(FLIGHT));
        tag.setFloat("apex", dataWatcher.getWatchableObjectFloat(APEX));
    }

    void readFresh(NBTTagCompound tag) {
        dataWatcher.updateObject(FRESH, tag.getByte("fresh"));
        dataWatcher.updateObject(FROM_YAW, tag.getFloat("fromYaw"));
        dataWatcher.updateObject(FROM_X, tag.getFloat("fromX"));
        dataWatcher.updateObject(FROM_Y, tag.getFloat("fromY"));
        dataWatcher.updateObject(FROM_Z, tag.getFloat("fromZ"));
        dataWatcher.updateObject(FLIGHT, tag.getByte("flight"));
        dataWatcher.updateObject(APEX, tag.getFloat("apex"));
    }

    /**
     * Boxes around the limbs as the body lies, in world coordinates: a block body's collision and ray target, the
     * client's CorpseParts.
     */
    public List<AxisAlignedBB> parts() {
        Skeleton.Shape s = drawnShape(1);
        if (boxes == null || boxesOf != s || boxX != posX || boxY != posY || boxZ != posZ) {
            boxes = s.boxes(posX, posY, posZ);
            boxesOf = s;
            boxX = posX;
            boxY = posY;
            boxZ = posZ;
        }
        return boxes;
    }

    private List<AxisAlignedBB> boxes;
    private Skeleton.Shape boxesOf;
    private double boxX, boxY, boxZ;

    /** owner's body in box, lying as an entity or as a block; null if none. */
    public static EntityCorpse find(World w, AxisAlignedBB box, String owner) {
        for (Object o : w.getEntitiesWithinAABB(EntityCorpse.class, box)) {
            EntityCorpse c = (EntityCorpse) o;
            if (!c.isDead && owner.equals(c.owner())) return c;
        }
        for (Object o : w.loadedTileEntityList) if (o instanceof TileCorpse) {
            EntityCorpse c = ((TileCorpse) o).body;
            if (c != null && !c.isDead
                && owner.equals(c.owner())
                && box.isVecInside(Vec3.createVectorHelper(c.posX, c.posY + 0.1, c.posZ))) return c;
        }
        return null;
    }

    /**
     * Client: owner's body that has just come into being in box (coming down, or under maxAge ticks old since it
     * began), as an entity or a block; the nearest to the box's middle. Null if none.
     */
    public static EntityCorpse findFresh(World w, AxisAlignedBB box, String owner, int maxAge) {
        double cx = (box.minX + box.maxX) / 2, cy = (box.minY + box.maxY) / 2, cz = (box.minZ + box.maxZ) / 2;
        EntityCorpse best = null;
        List<EntityCorpse> all = new java.util.ArrayList<>();
        for (Object o : w.getEntitiesWithinAABB(EntityCorpse.class, box)) all.add((EntityCorpse) o);
        for (Object o : w.loadedTileEntityList) if (o instanceof TileCorpse && ((TileCorpse) o).body != null) {
            EntityCorpse c = ((TileCorpse) o).body;
            if (box.isVecInside(Vec3.createVectorHelper(c.posX, c.posY + 0.1, c.posZ))) all.add(c);
        }
        for (EntityCorpse c : all) {
            if (c.isDead || !owner.equals(c.owner())
                || !(c.ragdollActive() || c.settle.began())
                || c.ticksExisted > maxAge) continue;
            if (best == null || c.getDistanceSq(cx, cy, cz) < best.getDistanceSq(cx, cy, cz)) best = c;
        }
        return best;
    }

    public boolean isOwner(EntityPlayer p) {
        return ownerId == null || ownerId.equals(p.getUniqueID());
    }

    /** Ticks until anyone may open it; 0 when open, Long.MAX_VALUE when owner-only forever. */
    public long lockLeft() {
        return ownerId != null && OriginalGameplay.ownerOnlyLooting
            && !(isSkeletal() && OriginalGameplay.othersLootWhenSkeletal) ? Long.MAX_VALUE : 0;
    }

    /** Server: p takes hold of the body (null: lets go); clients see who (draggedBy) for the HUD. */
    private void drag(EntityPlayer p) {
        if (p == dragger) return;
        worldObj.playSoundAtEntity(this, p != null ? "step.cloth" : "dig.cloth", 0.8F, p != null ? 0.7F : 0.5F);
        dragger = p;
        if (p == null && ragdoll != null) ragdoll.hold(-1, null);
        dataWatcher.updateObject(DRAG, p != null ? p.getEntityId() : 0);
    }

    /** Entity id of whoever is dragging the body; 0 when nobody. */
    public int draggedBy() {
        return dataWatcher.getWatchableObjectInt(DRAG);
    }

    public int selectedPart() {
        return interactionPart;
    }

    void selectPart(int part) {
        interactionPart = part >= 0 && part < Skeleton.PARTS ? part : Skeleton.BODY;
    }

    public boolean canOpen(EntityPlayer p) {
        return CorpseLifecycle.mayLoot(
            ownerId,
            p.getUniqueID(),
            OriginalGameplay.ownerOnlyLooting,
            isSkeletal(),
            OriginalGameplay.othersLootWhenSkeletal);
    }

    @Override
    public boolean interactFirst(EntityPlayer p) {
        if (worldObj.isRemote) return true;
        if (p.isSneaking() && CorpseConfig.drag) {
            // a body lying as blocks is lifted off them first: from then on it's an entity, dragged as any
            if (host != null) {
                host.release();
                worldObj.playSoundAtEntity(this, "dig.gravel", 0.6F, 0.6F);
            }
            // the click repeats while held: only a fresh one stops the drag
            if (dragger == p && ticksExisted - dragPing > 8) drag(null);
            else if (dragger == null) {
                drag(p);
                dragPart = interactionPart;
                double dx = posX - p.posX, dz = posZ - p.posZ, len = Math.sqrt(dx * dx + dz * dz);
                double k = len > 2 ? 2 / len : 1; // stay well inside the 3 block break distance
                dragX = dx * k;
                dragZ = dz * k;
            }
            dragPing = ticksExisted;
        } else if (!canOpen(p)) {
            long left = lockLeft();
            p.addChatMessage(
                left == Long.MAX_VALUE ? new ChatComponentTranslation("ukycorpses.msg.lockedForever", owner())
                    : new ChatComponentTranslation("ukycorpses.msg.locked", owner(), (left + 1199) / 1200));
        } else open(p);
        return true;
    }

    /** Blocks (squared) a searcher may step away before the search drops. */
    public static final double SEARCH_REACH = 4.5 * 4.5;

    /** Searches going on: on while the searcher stays close and on their feet, then the body opens to them. */
    private void tickSearch() {
        if (search.isEmpty()) return;
        List<EntityPlayer> done = new ArrayList<>();
        for (Iterator<Map.Entry<UUID, int[]>> it = search.entrySet()
            .iterator(); it.hasNext();) {
            Map.Entry<UUID, int[]> e = it.next();
            EntityPlayer p = worldObj.func_152378_a(e.getKey());
            if (p == null || !p.isEntityAlive() || Downed.is(p) || p.getDistanceSq(posX, posY, posZ) > SEARCH_REACH) {
                it.remove();
                continue;
            }
            int[] s = e.getValue();
            if (++s[0] % 16 == 0) worldObj.playSoundAtEntity( // rummaging on
                this,
                rand.nextBoolean() ? "dig.cloth" : "step.cloth",
                0.5F,
                0.6F + rand.nextFloat() * 0.3F);
            if (s[0] >= search()) done.add(p);
        }
        for (EntityPlayer p : done) open(p);
    }

    private void open(EntityPlayer p) {
        search.remove(p.getUniqueID());
        searchedBy.add(p.getUniqueID());
        // going through the pockets: cloth and leather, no chest lid
        worldObj.playSoundAtEntity(this, "mob.horse.leather", 0.5F, 0.75F);
        worldObj.playSoundAtEntity(this, "step.cloth", 0.8F, 0.7F);
        if (host != null) p.openGui(UkyCorpses.instance, 1, worldObj, host.xCoord, host.yCoord, host.zCoord);
        else p.openGui(UkyCorpses.instance, 0, worldObj, getEntityId(), 0, 0);
    }

    /** Lays entries into the chest in order; what doesn't fit lands on the ground. */
    public void fill(List<Entry> entries) {
        for (Entry e : entries) store(e.stack, e.slot);
        sync();
    }

    /**
     * Chest layout follows the player's: 0-35 main inventory and hotbar by their own slot, 36-39 armour (boots
     * first), 40-53 everything else (Backhand's offhand, mod slots, unknown). GuiCorpse draws it that way.
     */
    public static final int ARMOR_BOX = 36, EXTRA_BOX = 40;

    private static int boxFor(int slot) {
        if (slot >= 0 && slot < ARMOR_BOX) return slot;
        if (slot >= ARMOR && slot < ARMOR + 4) return ARMOR_BOX + slot - ARMOR;
        return -1;
    }

    private void store(ItemStack s, int slot) {
        int want = boxFor(slot);
        if (want >= 0 && items[want] == null) {
            items[want] = s;
            home[want] = slot;
            return;
        }
        for (int j = EXTRA_BOX; j < EXTRA_BOX + SIZE; j++) { // the extra row first, then any gap
            int i = j % SIZE;
            if (items[i] != null) continue;
            items[i] = s;
            home[i] = slot;
            return;
        }
        worldObj.spawnEntityInWorld(new EntityItem(worldObj, posX, posY + 0.3, posZ, s));
    }

    private boolean empty() {
        for (ItemStack s : items) if (s != null) return false;
        return true;
    }

    /** Moves everything that fits into the player. Owner gets original slots first. True when nothing is left. */
    public boolean giveTo(EntityPlayer p) {
        InventoryPlayer inv = p.inventory;
        if (isOwner(p)) for (int n = 0; n < SIZE; n++) {
            int h = home[n];
            if (items[n] == null || h < 0) continue;
            ItemStack[] arr = h >= ARMOR ? inv.armorInventory : inv.mainInventory;
            int i = h >= ARMOR ? h - ARMOR : h;
            if (i < arr.length && arr[i] == null) {
                arr[i] = items[n];
                items[n] = null;
            }
        }
        // slot taken (picked something up after respawn) or not the owner: any free slot, rest stays here
        for (int n = 0; n < SIZE; n++) {
            if (items[n] == null) continue;
            inv.addItemStackToInventory(items[n]);
            if (items[n].stackSize <= 0) items[n] = null;
        }
        if (xp > 0 && empty()) {
            p.addExperience(xp);
            xp = 0;
        }
        inv.markDirty();
        sync();
        return empty();
    }

    /** Server: refresh what clients see. Call after items or xp change. */
    public void sync() {
        int n = 0;
        ItemStack[] worn = new ItemStack[4];
        ItemStack hand = null;
        for (int i = 0; i < SIZE; i++) if (items[i] != null) {
            n++;
            if (home[i] >= ARMOR && home[i] < ARMOR + 4) worn[home[i] - ARMOR] = items[i];
            else if (heldSlot >= 0 && home[i] == heldSlot) hand = items[i];
        }
        dataWatcher.updateObject(COUNT, n);
        dataWatcher.updateObject(XP, xp);
        for (int i = 0; i < 4; i++) show(ARMOR_W + i, worn[i]);
        show(HELD, hand);
        tickClock();
        if (changed != null) changed.run();
        com.uky.corpses.pc.PcCorpses.sync(this);
    }

    /** Copies: the watcher compares by identity and the real stacks get handed out later. */
    private void show(int id, ItemStack want) {
        ItemStack shown = dataWatcher.getWatchableObjectItemStack(id);
        if (want == null ? shown != null : shown == null || !ItemStack.areItemStacksEqual(shown, want))
            dataWatcher.updateObject(id, want == null ? null : want.copy());
    }

    @Override
    public int getSizeInventory() {
        return SIZE;
    }

    @Override
    public ItemStack getStackInSlot(int i) {
        return items[i];
    }

    @Override
    public ItemStack decrStackSize(int i, int n) {
        ItemStack s = items[i];
        if (s == null) return null;
        if (s.stackSize <= n) {
            items[i] = null;
            taken(s);
        } else s = s.splitStack(n);
        markDirty();
        return s;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int i) {
        return null;
    }

    @Override
    public void setInventorySlotContents(int i, ItemStack s) {
        if (items[i] != s) {
            if (s == null) taken(items[i]);
            home[i] = -1;
        }
        items[i] = s;
        markDirty();
    }

    private void taken(ItemStack s) {
        if (s != null && s.getItem() instanceof ItemArmor && !worldObj.isRemote)
            worldObj.playSoundAtEntity(this, "mob.horse.armor", 0.6F, 1F);
    }

    @Override
    public String getInventoryName() {
        return owner();
    }

    @Override
    public boolean hasCustomInventoryName() {
        return true;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public void markDirty() {
        if (!worldObj.isRemote) sync();
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer p) {
        return !isDead && p.getDistanceSqToEntity(this) <= 64 && canOpen(p);
    }

    @Override
    public void openInventory() {}

    /**
     * The window closed on p. Its owner having taken everything back, the tape of this death (theirs, of this body:
     * same owner, same moment) has nothing left to tell, and goes from their inventory (CorpseConfig.tapeGone).
     */
    void retrieved(EntityPlayer p) {
        if (worldObj.isRemote || !CorpseConfig.tapeGone || !isOwner(p) || !empty()) return;
        ItemStack[] inv = p.inventory.mainInventory;
        boolean gone = false;
        for (int i = 0; i < inv.length; i++) {
            ItemStack s = inv[i];
            if (s == null || s.getItem() != UkyCorpses.tape || !s.hasTagCompound()) continue;
            NBTTagCompound t = s.getTagCompound();
            if (owner().equals(t.getString("owner")) && t.getLong("created") == created) {
                inv[i] = null;
                gone = true;
            }
        }
        if (!gone) return;
        p.inventory.markDirty();
        p.addChatMessage(new ChatComponentTranslation("ukycorpses.msg.tapeGone"));
    }

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int i, ItemStack s) {
        return true;
    }

    private void tickClock() {
        long left = lockLeft();
        dataWatcher.updateObject(AGE, lifecycle.ageTicks / 1200);
        dataWatcher.updateObject(LOCK, left == Long.MAX_VALUE ? -1 : (int) ((left + 1199) / 1200));
    }

    /**
     * Client: where the server last said it is, and ticks left to get there. The server alone moves a body (floats,
     * falls, is dragged); a client glides after it instead of running its own physics against it, which jerked.
     */
    private double toX, toY, toZ;
    private int toSteps;

    @Override
    public void setPositionAndRotation2(double x, double y, double z, float yaw, float pitch, int steps) {
        if (ragdoll != null) return;
        toX = x;
        toY = y;
        toZ = z;
        toSteps = 3;
    }

    @Override
    protected void kill() {
        // Vanilla void removal must not destroy an inventory while it is being lifted.
        if (posY >= -64) super.kill();
    }

    @Override
    public void onUpdate() {
        VoidReturn.tick(this);
        super.onUpdate();
        if (!tickBody()) return;
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;
        if (ragdoll != null) {
            if (worldObj.isRemote) placeParts();
            else tickPhysical();
            return;
        }
        if (worldObj.isRemote) {
            if (toSteps > 0) {
                setPosition(
                    posX + (toX - posX) / toSteps,
                    posY + (toY - posY) / toSteps,
                    posZ + (toZ - posZ) / toSteps);
                toSteps--;
            }
            return;
        }
        if (VoidReturn.lifting(this)) {
            moveEntity(motionX, motionY, motionZ);
            if (ticksExisted % 20 == 0) tickClock();
            return;
        }
        double dx = motionX, dz = motionZ; // sideways motion only from being pushed
        motionX *= onGround ? 0.5 : 0.98;
        motionZ *= onGround ? 0.5 : 0.98;
        if (dragger != null) {
            if (!dragger.isEntityAlive() || !dragger.isSneaking()
                || dragger.worldObj != worldObj
                || getDistanceSqToEntity(dragger) > 9) drag(null);
            else {
                PotionEffect slow = dragger.getActivePotionEffect(Potion.moveSlowdown);
                if (slow == null || slow.getDuration() < 20 || slow.getAmplifier() < 1)
                    dragger.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40, 1, true));
                dx = (dragger.posX + dragX - posX) * 0.35;
                dz = (dragger.posZ + dragZ - posZ) * 0.35;
            }
        }
        if (posY > 1) {
            if (worldObj.isMaterialInBB(boundingBox, Material.water)
                || worldObj.isMaterialInBB(boundingBox, Material.lava)) {
                // rise until the top peeks out, then hold
                int bx = MathHelper.floor_double(posX), by = MathHelper.floor_double(boundingBox.maxY),
                    bz = MathHelper.floor_double(posZ);
                motionY = worldObj.getBlock(bx, by, bz)
                    .getMaterial()
                    .isLiquid() ? 0.03 : 0;
            } else motionY -= 0.04;
            moveEntity(dx, motionY, dz);
            motionY *= 0.98;
            if (onGround) motionY = 0;
        } else { // void: stay at the bottom where it can still be reached
            motionY = 0;
            if (dx != 0 || dz != 0) moveEntity(dx, 0, dz);
        }
        if (ticksExisted % 20 == 0) tickClock();
    }

    /**
     * The living part of a tick, shared with the block host: settling starts on clients, the "fresh" flag clears,
     * despawn. False once gone.
     */
    boolean tickBody() {
        if (!worldObj.isRemote) {
            if (cleanupBorn < 0) cleanupBorn = worldObj.getTotalWorldTime();
            if (com.uky.corpses.corpse.PhysicsConfig.expired(worldObj.getTotalWorldTime(), cleanupBorn)) {
                Arrays.fill(items, null);
                xp = 0;
                silent = true;
                setDead();
                return false;
            }
            if (!com.uky.corpses.corpse.PhysicsConfig.playerRagdolls && ragdoll != null) {
                disposePhysics();
                ragdoll = null;
            }
        }

        if (worldObj.isRemote) {
            setPosition(posX, posY, posZ); // pose and yaw may land after the spawn placed the box
            // fresh is 0 until the server's value arrives, which can be a tick late: hence the window
            int fresh = dataWatcher.getWatchableObjectByte(FRESH);
            if (ragdoll == null && !settle.began() && fresh != 0 && ticksExisted < 40) beginSettle(fresh);
            if (ragdoll == null && settle.active()) {
                settle.advance(ticksExisted, resting(), probe);
                float hit = settle.takeImpact();
                boolean landed = settle.takeLanding();
                if (hit > 0.3F || landed) impactAt = ticksExisted;
                if (hit > 0 || landed) com.uky.corpses.client.CorpseFx.impact(this, hit, landed);
            }
            if (host == null) placeParts();
        } else {
            if (ticksExisted == 60 && dataWatcher.getWatchableObjectByte(FRESH) != 0)
                dataWatcher.updateObject(FRESH, (byte) 0);
            tickSearch();
            boolean noItems = empty();
            if (noItems && xp > 0) {
                UkyCorpses.dropXp(worldObj, posX, posY, posZ, xp);
                xp = 0;
                sync();
            }
            boolean expired = lifecycle.tick(
                noItems,
                OriginalGameplay.corpseSkeletonTicks,
                com.uky.corpses.corpse.PhysicsConfig.corpseLifetimeMinutes > 0 ? Integer.MAX_VALUE
                    : OriginalGameplay.corpseExpiryTicks,
                com.uky.corpses.corpse.PhysicsConfig.ragdollLifetime);
            if (isSkeletal() != lifecycle.skeletal) {
                dataWatcher.updateObject(SKELETAL, (byte) (lifecycle.skeletal ? 1 : 0));
                if (changed != null) changed.run();
                com.uky.corpses.pc.PcCorpses.sync(this);
            }
            if (expired) {
                silent = false;
                setDead();
                return false;
            }
        }
        if (!worldObj.isRemote && host != null && ticksExisted % 20 == 0) tickClock();
        return !isDead;
    }

    /** Client: start coming down the way the server said, from where and how the player was. */
    private void beginSettle(int mode) {
        int cause = Poses.causeOf(look());
        float[] init = mode == Settle.DYING ? Settle.alive(pose()) : Settle.stance(owner());
        boolean lastMove = (mode == Settle.FALL || mode == Settle.DYING) && burn() == 0
            && (cause == Poses.GENERIC || cause == Poses.SHOT || cause == Poses.MELEE || cause == Poses.SICK);
        settle.begin(
            ticksExisted,
            getEntityId(),
            mode,
            pose(),
            Poses.roll(pose(), look()),
            init,
            dataWatcher.getWatchableObjectFloat(FROM_X),
            dataWatcher.getWatchableObjectFloat(FROM_Y),
            dataWatcher.getWatchableObjectFloat(FROM_Z),
            dataWatcher.getWatchableObjectFloat(FROM_YAW),
            dataWatcher.getWatchableObjectByte(FLIGHT),
            dataWatcher.getWatchableObjectFloat(APEX),
            lastMove);
    }

    /** New deaths use server physics; old block corpses keep their saved legacy geometry. */
    public void beginPhysical() {
        if (worldObj.isRemote || ragdoll != null
            || host != null
            || !Ragdoll.enabled
            || !com.uky.corpses.corpse.PhysicsConfig.playerRagdolls) return;
        int mode = dataWatcher.getWatchableObjectByte(FRESH);
        if (mode == Settle.FLOAT) return;
        if (mode != 0) beginSettle(mode);
        float[] angles = new float[Settle.N];
        double[] offset = new double[3];
        Mat4 frame = frameNow(mode == 0 ? 1 : 0, angles, offset, new Mat4());
        frame.moved(offset[0], offset[1], offset[2]);
        ragX = posX;
        ragY = posY;
        ragZ = posZ;
        float seconds = Math.max(.4F, dataWatcher.getWatchableObjectByte(FLIGHT) * .05F);
        Vector3f velocity = new Vector3f(
            mode == Settle.THROWN ? (float) -offset[0] / seconds : 0,
            mode == Settle.THROWN ? (float) -offset[1] / seconds + 10 * seconds : mode == Settle.SPLAT ? -6 : 0,
            mode == Settle.THROWN ? (float) -offset[2] / seconds : 0);
        if (velocity.length() > 24) velocity.scale(24 / velocity.length());
        ragdoll = new Ragdoll(frame, angles, velocity, pose(), getEntityId());
        setGrid(false);
        RagdollWorld.refresh(ragdoll, worldObj, this, ragX, ragY, ragZ);
        followPhysicalTorso();
    }

    /** Start at the player's actual feet and carry its velocity into the solver, without a landing target. */
    public void startPhysical(Vector3f momentum, float yaw, float pitch, float headYaw) {
        if (worldObj.isRemote || ragdoll != null
            || !com.uky.corpses.corpse.PhysicsConfig.playerRagdolls
            || !validImpulse(momentum)) return;
        float[] angles = new float[Settle.N];
        angles[0] = (float) Math.toRadians(pitch);
        angles[1] = (float) Math.toRadians(headYaw - yaw);
        Mat4 frame = new Mat4();
        Skeleton.place(frame, pose(), yaw, 0, 0, Float.NaN, 0);
        ragX = posX;
        ragY = posY;
        ragZ = posZ;
        ragdoll = new Ragdoll(frame, angles, momentum, pose(), getEntityId());
        dataWatcher.updateObject(FRESH, (byte) 0);
        RagdollWorld.refresh(ragdoll, worldObj, this, ragX, ragY, ragZ);
        followPhysicalTorso();
    }

    public void applyDeathMotion(Vector3f impulse, Vector3f kick, double[] blast) {
        if (worldObj.isRemote || ragdoll == null) return;
        if (kick != null && validImpulse(kick)) {
            Vector3f center = blast == null ? null
                : new Vector3f((float) (blast[0] - ragX), (float) (blast[1] - ragY), (float) (blast[2] - ragZ));
            ragdoll.velocityKick(kick, center);
        } else if (impulse != null && validImpulse(impulse)) ragdoll.deathHit(Skeleton.BODY, impulse);
    }

    /** Continue a live/downed body's exact transforms and velocities through the inventory handoff. */
    public boolean inheritPhysical(float[] state, int mask, double x, double y, double z) {
        if (worldObj.isRemote || host != null
            || !com.uky.corpses.corpse.PhysicsConfig.playerRagdolls
            || !Ragdoll.validState(state)
            || !validRagdollAnchor(x, y, z)
            || (mask & ~61) != 0) return false;
        ragX = x;
        ragY = y;
        ragZ = z;
        ragdoll = new Ragdoll(state, mask);
        setGrid(false);
        RagdollWorld.refresh(ragdoll, worldObj, this, ragX, ragY, ragZ);
        followPhysicalTorso();
        return true;
    }

    private void tickPhysical() {
        ragdoll.hideParts(ampHiddenMask());
        if (!voidFloor && !ragdoll.sleeping() || ticksExisted % 10 == 0)
            RagdollWorld.refresh(ragdoll, worldObj, this, ragX, ragY, ragZ);
        if (dragger != null) {
            if (!dragger.isEntityAlive() || !dragger.isSneaking()
                || dragger.worldObj != worldObj
                || Downed.is(dragger)
                || getDistanceSqToEntity(dragger) > 49) drag(null);
            else {
                PotionEffect slow = dragger.getActivePotionEffect(Potion.moveSlowdown);
                if (slow == null || slow.getDuration() < 20 || slow.getAmplifier() < 1)
                    dragger.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 40, 1, true));
                Vec3 look = dragger.getLookVec();
                double length = Math.sqrt(look.xCoord * look.xCoord + look.zCoord * look.zCoord);
                double reach = length > .01 ? .55 / length : 0;
                ragdoll.hold(
                    dragPart,
                    new Vector3f(
                        (float) (dragger.posX + look.xCoord * reach - ragX),
                        (float) (dragger.posY + .62 - ragY),
                        (float) (dragger.posZ + look.zCoord * reach - ragZ)));
            }
        }
        if (ticksExisted - walkPushAt >= 10)
            for (Object object : worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(.3, .2, .3))) {
                EntityPlayer player = (EntityPlayer) object;
                double dx = player.posX - player.prevPosX, dz = player.posZ - player.prevPosZ;
                if (player == dragger || !player.isEntityAlive() || Downed.is(player) || dx * dx + dz * dz < .0004)
                    continue;
                impulsePart(Skeleton.BODY, new Vector3f((float) (dx * 25), 1, (float) (dz * 25)));
                walkPushAt = ticksExisted;
                break;
            }
        if (!voidFloor || dragger != null) ragImpact = Math.max(ragImpact, Math.min(100, ragdoll.tick()));
        // Like legacy corpses, inventory stays reachable at the bottom instead of falling into the void forever.
        float[] physicalState = ragdoll.state();
        double torsoY = ragY + physicalState[Skeleton.BODY * 13 + 1];
        if (!VoidReturn.lifting(this) && torsoY < 1) {
            float raise = (float) (1 - torsoY);
            for (int part = 0; part < Skeleton.PARTS; part++) {
                physicalState[part * 13 + 1] += raise;
                Arrays.fill(physicalState, part * 13 + 7, part * 13 + 13, 0);
            }
            ragdoll.applyState(physicalState, ragdoll.severed());
            voidFloor = true;
        } else if (torsoY > 1.05 || VoidReturn.lifting(this)) voidFloor = false;
        followPhysicalTorso();
        motionX = motionY = motionZ = 0;
        if (ticksExisted % (voidFloor || ragdoll.sleeping() ? 20 : 4) == 0) sendRagdoll();
        if (ticksExisted % 20 == 0) tickClock();
    }

    void guideVoidFlight(double vx, double vy, double vz, float spin, boolean kick) {
        drag(null);
        voidFloor = false;
        if (ragdoll != null)
            ragdoll.guideFlight(new Vector3f((float) vx, (float) vy, (float) vz), VoidReturn.acceleration, spin, kick);
        else {
            noClip = true;
            motionX = vx / 20;
            motionY = vy / 20;
            motionZ = vz / 20;
        }
    }

    void finishVoidFlight() {
        if (ragdoll != null) ragdoll.endFlight();
        noClip = false;
        fallDistance = 0;
    }

    boolean voidFlightClear() {
        return worldObj.func_147461_a(boundingBox.expand(.1, .1, .1))
            .isEmpty() && !worldObj.isAnyLiquid(boundingBox);
    }

    boolean belowVoidBoundary() {
        if (posY > VoidReturn.triggerY + 1) return false;
        return (ragdoll == null ? posY : ragY + ragdoll.state()[Skeleton.BODY * 13 + 1]) <= VoidReturn.triggerY + .02;
    }

    void pinToVoidFloor() {
        if (ragdoll != null) {
            float[] state = ragdoll.state();
            float raise = (float) (1 - ragY - state[Skeleton.BODY * 13 + 1]);
            for (int part = 0; part < Skeleton.PARTS; part++) {
                state[part * 13 + 1] += raise;
                Arrays.fill(state, part * 13 + 7, part * 13 + 13, 0);
            }
            ragdoll.applyState(state, ragdoll.severed());
            followPhysicalTorso();
            voidFloor = true;
        } else setPosition(posX, 1, posZ);
        motionX = motionY = motionZ = 0;
    }

    void returnFromVoid(double x, double y, double z) {
        drag(null);
        boolean throwing = "THROW".equals(VoidReturn.mode);
        double angle = rand.nextDouble() * Math.PI * 2;
        float vx = throwing ? (float) Math.cos(angle) * VoidReturn.sidewaysSpeed : 0;
        float vz = throwing ? (float) Math.sin(angle) * VoidReturn.sidewaysSpeed : 0;
        float vy = throwing ? VoidReturn.upwardSpeed : 0;
        if (ragdoll != null) {
            float[] state = ragdoll.state();
            int mask = ragdoll.severed();
            int torso = Skeleton.BODY * 13;
            float ox = state[torso], oy = state[torso + 1], oz = state[torso + 2];
            for (int part = 0; part < Skeleton.PARTS; part++) {
                int i = part * 13;
                state[i] -= ox;
                state[i + 1] += .85F - oy;
                state[i + 2] -= oz;
                state[i + 7] = vx;
                state[i + 8] = vy;
                state[i + 9] = vz;
                state[i + 10] = throwing ? VoidReturn.angularSpeed : 0;
                state[i + 11] = 0;
                state[i + 12] = throwing ? VoidReturn.angularSpeed * .3F : 0;
            }
            disposePhysics();
            ragX = x;
            ragY = y;
            ragZ = z;
            ragdoll = new Ragdoll(state, mask);
            ragdoll.hideParts(ampHiddenMask());
            RagdollWorld.refresh(ragdoll, worldObj, this, ragX, ragY, ragZ);
            followPhysicalTorso();
            sendRagdoll();
        } else {
            setPosition(x, y + .1, z);
            motionX = vx / 20;
            motionY = vy / 20;
            motionZ = vz / 20;
        }
        voidFloor = false;
        fallDistance = 0;
        prevPosX = lastTickPosX = posX;
        prevPosY = lastTickPosY = posY;
        prevPosZ = lastTickPosZ = posZ;
        worldObj.playSoundEffect(x, y, z, "random.fizz", .35F, .7F);
    }

    private void followPhysicalTorso() {
        Skeleton.Shape physical = ragdoll.shape(1, 0, 0, 0);
        double[] at = physical.parts[Skeleton.BODY].apply(0, 6, 0, new double[3]);
        setPosition(ragX + at[0], ragY + at[1], ragZ + at[2]);
    }

    private void sendRagdoll() {
        if (worldObj.isRemote || ragdoll == null || UkyCorpses.net == null) return;
        UkyCorpses.net.sendToAllAround(
            new MsgRagdoll(
                this,
                ragGeneration,
                ++ragSequence,
                ragX,
                ragY,
                ragZ,
                ragdoll.severed(),
                ragdoll.state(),
                ragImpact),
            new NetworkRegistry.TargetPoint(worldObj.provider.dimensionId, posX, posY, posZ, 96));
        ragImpact = 0;
    }

    public void impulsePart(int part, Vector3f impulse) {
        if (worldObj.isRemote || isDead || part < 0 || part >= Skeleton.PARTS || !validImpulse(impulse)) return;
        if (ragdoll == null) beginPhysical();
        if (ragdoll == null) return;
        Vector3f safe = new Vector3f(impulse);
        if (safe.length() > 150) safe.scale(150 / safe.length());
        ragdoll.impulse(part, safe);
        voidFloor = false;
        sendRagdoll();
    }

    public boolean severPart(int part, Vector3f impulse) {
        if (worldObj.isRemote || isDead
            || part == Skeleton.BODY
            || part < 0
            || part >= Skeleton.PARTS
            || !validImpulse(impulse)) return false;
        if (ragdoll == null) beginPhysical();
        if (ragdoll == null) return false;
        Vector3f safe = new Vector3f(impulse);
        if (safe.length() > 150) safe.scale(150 / safe.length());
        if (!ragdoll.severVelocity(part, safe)) return false;
        voidFloor = false;
        sendRagdoll();
        return true;
    }

    private static boolean validImpulse(Vector3f v) {
        return v != null && Float.isFinite(v.x)
            && Float.isFinite(v.y)
            && Float.isFinite(v.z)
            && Math.abs(v.x) <= 10000
            && Math.abs(v.y) <= 10000
            && Math.abs(v.z) <= 10000;
    }

    public static boolean validRagdollAnchor(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y)
            && Double.isFinite(z)
            && Math.abs(x) <= 30000000
            && Math.abs(z) <= 30000000
            && Math.abs(y) <= 4096;
    }

    /** Client main thread; old packets cannot attach a prior world's body to a reused entity id. */
    void acceptRagdollSnapshot(long generation, long sequence, int serverTick, double x, double y, double z,
        int severed, float[] state, float impact) {
        if (!worldObj.isRemote || generation != ragGeneration
            || sequence <= lastRagSequence
            || !Ragdoll.validState(state)
            || !validRagdollAnchor(x, y, z)
            || (severed & ~61) != 0) return;
        boolean first = ragdoll == null || Math.abs(ragX - x) + Math.abs(ragY - y) + Math.abs(ragZ - z) > 8;
        if (first) ragdoll = new Ragdoll(state, severed);
        else ragdoll.applyState(state, severed);
        ragPacketInterval = first ? 4 : Math.max(1, Math.min(20, serverTick - ragServerTick));
        ragPacketAt = ticksExisted;
        ragServerTick = serverTick;
        lastRagSequence = sequence;
        ragX = x;
        ragY = y;
        ragZ = z;
        followPhysicalTorso();
        lastTickPosX = prevPosX = posX;
        lastTickPosY = prevPosY = posY;
        lastTickPosZ = prevPosZ = posZ;
        if (impact > 0) {
            if (impact > .3F) impactAt = ticksExisted;
            com.uky.corpses.client.CorpseFx.impact(this, impact, false);
        }
    }

    @Override
    public void writeSpawnData(ByteBuf buf) {
        buf.writeBoolean(ownerId != null);
        if (ownerId != null) buf.writeLong(ownerId.getMostSignificantBits())
            .writeLong(ownerId.getLeastSignificantBits());
        buf.writeLong(physicalLife);
        buf.writeLong(ragGeneration)
            .writeBoolean(ragdoll != null);
        if (ragdoll == null) return;
        buf.writeDouble(ragX)
            .writeDouble(ragY)
            .writeDouble(ragZ)
            .writeByte(ragdoll.severed())
            .writeLong(ragSequence)
            .writeInt(ticksExisted);
        for (float value : ragdoll.state()) buf.writeFloat(value);
    }

    @Override
    public void readSpawnData(ByteBuf buf) {
        if (buf.readableBytes() < 1) return;
        boolean hasOwner = buf.readBoolean();
        if (buf.readableBytes() < (hasOwner ? 16 : 0) + 17) return;
        if (hasOwner) ownerId = new UUID(buf.readLong(), buf.readLong());
        physicalLife = buf.readLong();
        ragGeneration = buf.readLong();
        if (!buf.readBoolean() || buf.readableBytes() < 8 * 3 + 1 + 8 + 4 + 78 * 4) return;
        double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
        int mask = buf.readUnsignedByte();
        long sequence = buf.readLong();
        int serverTick = buf.readInt();
        float[] state = new float[78];
        for (int i = 0; i < state.length; i++) state[i] = buf.readFloat();
        acceptRagdollSnapshot(ragGeneration, sequence, serverTick, x, y, z, mask, state, 0);
    }

    /** Client: the limb pieces follow the body; same id as the body, so a click on one reaches the server as it. */
    private void placeParts() {
        List<AxisAlignedBB> boxes = parts();
        if (parts == null || parts.length != boxes.size()) {
            parts = new CorpsePart[boxes.size()];
            for (int i = 0; i < parts.length; i++) parts[i] = new CorpsePart(this, i);
        }
        for (int i = 0; i < parts.length; i++) parts[i].fit(boxes.get(i));
    }

    @Override
    public Entity[] getParts() {
        return parts;
    }

    /** /kill and the like: the things land on the ground instead of vanishing with the body. */
    @Override
    public void setDead() {
        if (!worldObj.isRemote && !isDead) com.uky.corpses.pc.PcCorpses.removed(this);
        if (!worldObj.isRemote && !isDead && !silent) {
            for (int i = 0; i < SIZE; i++) if (items[i] != null) {
                worldObj.spawnEntityInWorld(new EntityItem(worldObj, posX, posY + 0.3, posZ, items[i]));
                items[i] = null;
            }
            UkyCorpses.dropXp(worldObj, posX, posY + 0.3, posZ, xp);
            xp = 0;
        }
        disposePhysics();
        super.setDead();
        dragger = null;
        ragdoll = null;
        parts = null;
        if (host != null && !worldObj.isRemote) host.gone();
    }

    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (!worldObj.isRemote && ragdoll != null && amount > 0 && Float.isFinite(amount)) {
            Entity source = src.getSourceOfDamage();
            if (source != null && src.isProjectile()) {
                double dx = posX - source.posX, dy = posY - source.posY, dz = posZ - source.posZ;
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (length > .01) {
                    double force = Math.min(100, amount * 8) / length;
                    impulsePart(
                        Skeleton.BODY,
                        new Vector3f((float) (dx * force), (float) (dy * force + 3), (float) (dz * force)));
                }
            }
        }
        return false;
    }

    /**
     * Where the server aims from a player's eyes to tell whether they see the body: a click on it counts from 6 blocks
     * if so, only from 3 if not, and is dropped without a word past that. Its chest, not the floor it lies on, which
     * a slab, a stair or the grass it lies in hides.
     */
    @Override
    public float getEyeHeight() {
        return (float) Math.max(0.2, shape().parts[Skeleton.BODY].apply(0, 6, 0, new double[3])[1]);
    }

    /** Corpse blows push the physical part or sever it through AMP; ordinary hits never destroy the inventory. */
    @Override
    public boolean hitByEntity(Entity e) {
        if (worldObj.isRemote || isDead || !(e instanceof EntityPlayer)) return true;
        EntityPlayer p = (EntityPlayer) e;
        Amputation.hit(this, p, interactionPart);
        Vec3 look = p.getLookVec();
        float damage = (float) p.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.attackDamage)
            .getAttributeValue();
        float strength = (float) Math.min(50, 75 * Math.sqrt(Math.max(1, damage) / 5));
        impulsePart(
            interactionPart,
            new Vector3f(
                (float) look.xCoord * strength,
                (float) look.yCoord * strength + strength * .18F,
                (float) look.zCoord * strength));
        double[] at = shape().parts[Skeleton.BODY].apply(0, 6, 0, new double[3]);
        double x = posX + at[0], y = posY + Math.max(0.2, at[1]), z = posZ + at[2];
        worldObj.playSoundEffect(x, y, z, "mob.slime.attack", 0.6F, 0.6F + rand.nextFloat() * 0.3F);
        worldObj.playSoundEffect(x, y, z, "dig.cloth", 0.7F, 0.7F + rand.nextFloat() * 0.2F);
        return true;
    }

    /** Hacked or dug apart: what it carried spills, and what's left of it is bones and rotten flesh. */
    public void breakApart() {
        if (worldObj.isRemote || isDead) return;
        double[] at = shape().parts[Skeleton.BODY].apply(0, 6, 0, new double[3]);
        double x = posX + at[0], y = posY + Math.max(0.2, at[1]), z = posZ + at[2];
        boolean burnt = burn() == BURNT;
        if (CorpseConfig.bones) drop(new ItemStack(Items.bone, 2 + rand.nextInt(3)), x, y, z);
        if (CorpseConfig.flesh && !burnt) drop(new ItemStack(Items.rotten_flesh, 1 + rand.nextInt(3)), x, y, z);
        worldObj.playSoundEffect(x, y, z, "mob.skeleton.death", 0.8F, 0.7F);
        worldObj.playSoundEffect(x, y, z, "mob.slime.big", 0.8F, 0.6F);
        bits(x, y, z, 24);
        if (!burnt) Blood.spawn(worldObj, x, posY, z, 1.2F);
        setDead(); // the things and the experience spill
    }

    private void drop(ItemStack s, double x, double y, double z) {
        EntityItem ei = new EntityItem(worldObj, x, y, z, s);
        ei.delayBeforeCanPickup = 10;
        worldObj.spawnEntityInWorld(ei);
    }

    /** Bits flying off it: bone and (unless burnt) blood. */
    private void bits(double x, double y, double z, int n) {
        if (!(worldObj instanceof WorldServer)) return;
        WorldServer w = (WorldServer) worldObj;
        w.func_147487_a(
            "blockcrack_" + Block.getIdFromBlock(Blocks.quartz_block) + "_0",
            x,
            y,
            z,
            n / 2,
            0.25,
            0.15,
            0.25,
            0.05);
        if (burn() != BURNT) w.func_147487_a(
            "blockcrack_" + Block.getIdFromBlock(Blocks.redstone_block) + "_0",
            x,
            y,
            z,
            n,
            0.25,
            0.15,
            0.25,
            0.08);
    }

    /** The ray target for right-click: the limbs on a client (CorpseParts), the whole box elsewhere. */
    @Override
    public boolean canBeCollidedWith() {
        return !isDead && parts == null;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public float getShadowSize() {
        return 0.6F;
    }

    @Override
    protected void writeEntityToNBT(NBTTagCompound tag) {
        tag.setTag("VoidReturn", voidReturn.copy());
        lifecycle.save(tag);
        tag.setByte("AmpHiddenMask", (byte) ampHiddenMask());
        tag.setLong("physicalLife", physicalLife);
        tag.setString("owner", owner());
        if (ownerId != null) tag.setString("ownerId", ownerId.toString());
        tag.setByte("pose", (byte) pose());
        tag.setBoolean("charred", charred());
        tag.setByte("burn", (byte) burn());
        tag.setInteger("look", look());
        tag.setByte("lean", dataWatcher.getWatchableObjectByte(LEAN));
        tag.setByte("tuck", (byte) tuck());
        tag.setByte("situation", (byte) situation());
        tag.setBoolean("grid", grid());
        tag.setByte("bend", dataWatcher.getWatchableObjectByte(BEND));
        tag.setInteger("heldSlot", heldSlot);
        tag.setInteger("xp", xp);
        tag.setLong("created", created);
        tag.setLong("CleanupBorn", cleanupBorn < 0 ? worldObj.getTotalWorldTime() : cleanupBorn);
        tag.setLong("lock", lockTicks);
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < SIZE; i++) if (items[i] != null) {
            NBTTagCompound t = items[i].writeToNBT(new NBTTagCompound());
            t.setInteger("Slot", home[i]);
            t.setInteger("Box", i);
            list.appendTag(t);
        }
        tag.setTag("items", list);
        if (ragdoll != null) {
            NBTTagList physical = new NBTTagList();
            for (float value : ragdoll.state()) physical.appendTag(new NBTTagFloat(value));
            tag.setTag("ragdollState", physical);
            tag.setDouble("ragdollX", ragX);
            tag.setDouble("ragdollY", ragY);
            tag.setDouble("ragdollZ", ragZ);
            tag.setByte("ragdollSevered", (byte) ragdoll.severed());
        }
    }

    @Override
    protected void readEntityFromNBT(NBTTagCompound tag) {
        for (Object key : new java.util.HashSet<>(voidReturn.func_150296_c())) voidReturn.removeTag((String) key);
        NBTTagCompound savedReturn = tag.getCompoundTag("VoidReturn");
        for (Object key : savedReturn.func_150296_c()) voidReturn.setTag(
            (String) key,
            savedReturn.getTag((String) key)
                .copy());
        ampHiddenMask(tag.getByte("AmpHiddenMask"));
        lifecycle.load(tag);
        dataWatcher.updateObject(SKELETAL, (byte) (lifecycle.skeletal ? 1 : 0));
        if (tag.hasKey("physicalLife")) physicalLife = tag.getLong("physicalLife");
        setOwner(tag.getString("owner"));
        ownerId = tag.hasKey("ownerId") ? UUID.fromString(tag.getString("ownerId")) : null;
        setPose(tag.getByte("pose"));
        setBurn(tag.hasKey("burn") ? tag.getByte("burn") : tag.getBoolean("charred") ? BURNT : 0);
        if (tag.hasKey("look")) dataWatcher.updateObject(LOOK, tag.getInteger("look"));
        else if (tag.hasKey("variant")) dataWatcher.updateObject(LOOK, Poses.fromOld(tag.getByte("variant")));
        // else the random one stays
        dataWatcher.updateObject(LEAN, tag.getByte("lean"));
        dataWatcher.updateObject(TUCK, tag.getByte("tuck"));
        setSituation(tag.getByte("situation"), tag.getByte("bend") / 20F);
        setGrid(tag.getBoolean("grid"));
        dataWatcher.updateObject(FRESH, (byte) 0); // loaded, not just fallen: no settling
        heldSlot = tag.hasKey("heldSlot") ? tag.getInteger("heldSlot") : -1;
        xp = tag.getInteger("xp");
        created = tag.getLong("created");
        cleanupBorn = tag.hasKey("CleanupBorn") ? tag.getLong("CleanupBorn")
            : Math.max(0, worldObj.getTotalWorldTime() - lifecycle.ageTicks);
        lockTicks = tag.getLong("lock");
        items = new ItemStack[SIZE];
        Arrays.fill(home, -1);
        NBTTagList list = tag.getTagList("items", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound t = list.getCompoundTagAt(i);
            ItemStack s = ItemStack.loadItemStackFromNBT(t);
            // item from a removed mod: skip instead of crashing
            if (s == null) continue;
            int slot = t.hasKey("Slot") ? t.getInteger("Slot") : -1, box = t.hasKey("Box") ? t.getInteger("Box") : -1;
            if (box >= 0 && box < SIZE && items[box] == null) {
                items[box] = s;
                home[box] = slot;
            } else store(s, slot);
        }
        ragdoll = null;
        if (com.uky.corpses.corpse.PhysicsConfig.playerRagdolls && tag.hasKey("ragdollState", 9)) {
            NBTTagList physical = tag.getTagList("ragdollState", 5);
            if (physical.tagCount() == 78) {
                float[] state = new float[78];
                for (int i = 0; i < state.length; i++) state[i] = physical.func_150308_e(i);
                double x = tag.getDouble("ragdollX"), y = tag.getDouble("ragdollY"), z = tag.getDouble("ragdollZ");
                int mask = tag.getByte("ragdollSevered") & 255;
                if (Ragdoll.validState(state) && validRagdollAnchor(x, y, z) && (mask & ~61) == 0) {
                    ragX = x;
                    ragY = y;
                    ragZ = z;
                    ragdoll = new Ragdoll(state, mask);
                    followPhysicalTorso();
                } else UkyCorpses.LOG.warn("Ignoring invalid physical pose for corpse {}", owner());
            }
        }
        sync();
    }
}

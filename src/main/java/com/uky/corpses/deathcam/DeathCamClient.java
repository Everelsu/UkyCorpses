package com.uky.corpses.deathcam;

import java.util.List;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.ITickableSound;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.shader.Shader;
import net.minecraft.client.shader.ShaderGroup;
import net.minecraft.client.shader.ShaderUniform;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent17;
import net.minecraftforge.common.MinecraftForge;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import com.uky.corpses.EntityCorpse;
import com.uky.corpses.MsgDeath;
import com.uky.corpses.UkyCorpses;
import com.uky.corpses.client.BodyCam;
import com.uky.corpses.client.CorpseClient;
import com.uky.corpses.client.Palette;
import com.uky.corpses.client.ReactionClient;
import com.uky.corpses.client.RenderCorpse;
import com.uky.corpses.client.Shaders;
import com.uky.corpses.client.ViewEye;
import com.uky.corpses.corpse.Settle;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.ObfuscationReflectionHelper;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Dying, seen from inside your own body, the way FirstPersonModel shows the living one. When the local player dies the
 * "You died" screen is held back and the view stays theirs: their eye, fixed in their torso, goes down with the body
 * (the player is drawn as the body from the very tick they die, see JustDied and Settle; out of the crawl camera if
 * they lay downed), the body seen whole from there but for the head. The view comes to rest on a view per pose (along
 * yourself past the arm on your back, along the floor face down) and the mouse looks around from it. Black bars slide
 * in; in them what killed you, where your body lies with how much in it, and "LMB: respawn" (or by itself after a
 * while). Colour drains a little, the world's sounds go dull, a heartbeat slows and stops.
 *
 * The view is a throwaway entity in renderViewEntity (no mixins here); its roll goes through EntityRenderer.camRoll.
 * The blur is built once ahead of time, so dying doesn't stall the game while it loads. Anything that goes wrong falls
 * back to the plain screen.
 */
public final class DeathCamClient {

    /** Nothing; dying on screen; asked the server to respawn, view held; the vanilla death screen over the view. */
    private static final int IDLE = 0, PLAY = 1, RESPAWN = 2, VANILLA = 3;
    /** Seconds: the prompt shows; hardcore's own screen comes by itself; to black before respawn; black lifting. */
    private static final float PROMPT = 1.2F, HARDCORE = 6F, OUT = 0.25F, IN = 0.4F;
    /** Heartbeats, slowing down (seconds), and the last breath. */
    private static final float[] BEATS = { 0.2F, 1.1F, 2.2F, 3.6F, 5.4F };
    private static final float BREATH = 6.6F;
    /** How far the mouse looks off the rest view (degrees, sideways and up/down), easing off the last SOFT of it. */
    private static final float LOOK_YAW = 80, LOOK_PITCH = 35, SOFT = 15, UP_LIMIT = -65, DOWN_LIMIT = 60;
    /** The spring the view turns to its rest on (per second), and the fastest it turns (degrees a second). */
    private static final float STIFF = 4.5F, MAX_TURN = 200;
    private static final int QUOTES = 20;
    private static final ResourceLocation BLUR = new ResourceLocation("ukycorpses", "shaders/post/death.json"),
        HEART = new ResourceLocation("ukycorpses", "heart"),
        BREATH_SOUND = new ResourceLocation("ukycorpses", "breath");
    private static final String[] ROLL = { "camRoll", "field_78495_O" }, PREV_ROLL = { "prevCamRoll", "field_78505_P" },
        FOV = { "debugCamFOV", "field_78493_M" }, PREV_FOV = { "prevDebugCamFOV", "field_78494_N" },
        LIST = { "listShaders", "field_148031_d" };
    private static final Random rand = new Random();
    private static DeathCamClient instance;

    private int state = IDLE;
    /** The dead player of this scene; the last one that had theirs; the last player entity seen alive. */
    private EntityPlayer player, done, alive;
    /** No scene (camera off): asked the server to respawn this player; ticks spent waiting for it. */
    private EntityPlayer respawning;
    private int respawnWait;
    private ViewEye eye;
    private int view, slot, ticks, sinceRespawn, beat, searchedAt;
    private boolean hardcore, downedDeath, breathed, asked;
    private float partial, respawnAt, vanillaAt;
    private long lastFrame, startedAt;
    /** Scene seconds the body is down: the mouse is free, colour and sound start going. */
    private float landAt;
    /** Where the view was when it started: the game's camera (0.1 behind its eye), yaw, pitch. */
    private final double[] start = new double[3];
    private float startYaw, startPitch;

    // ---- the camera ----
    private final BodyCam.Pose src = new BodyCam.Pose();
    /** What the eye is in now (the body, or the player drawn as it until it comes). */
    private Object srcWho;
    private EntityCorpse body;
    private final double[] eyePos = new double[3], cam = new double[3], base = new double[3], shift = new double[3],
        fwd = new double[3], eyeAt = new double[3];
    private boolean haveBase, haveEye;
    /** The shake's jolt this frame (degrees), kept while paused. */
    private float joltPitch, joltRoll;
    /** Up over the water's surface (floating), eased. */
    private double lift;
    private final float[] rest = new float[3];
    private float baseYaw, basePitch, velYaw, velPitch, offYaw, offPitch, roll, looked, lookedAt, shake;
    /** The player has looked around: the view stays where they put it. */
    private boolean frozen;
    private int impactSeen;
    private float fov0 = Float.NaN;

    // ---- the way out ----
    private boolean want;
    /** Ticks left before it respawns by itself, counted while nothing's open; -1 before the prompt. */
    private int autoLeft = -1;
    private boolean drained;
    /** The black lifting off the respawned player (System.nanoTime it started, 0 not yet), from how dark it was. */
    private boolean fadeIn;
    private long fadeInAt, fadeInWait;
    private float blackWas;

    // ---- the card ----
    private String quote, cause;
    private CorpseClient.Death causeOf;
    /** UkyUI's look over this scene (UkyScene), null for our own bars; and whether the way back in is UkyUI's. */
    private UkyScene uky;
    private boolean ukyIn;

    /** The player's angles while particles are drawn facing the camera (restored after the frame). */
    private EntityPlayer mirrored;
    private float keptYaw, keptPitch, keptPrevYaw, keptPrevPitch;

    /** Built ahead of time (at the first tick in a world), installed while the camera runs. */
    private ShaderGroup blur;
    private boolean blurOn, blurFailed, passesFailed;
    private List<?> passes;
    private int blurW, blurH;

    public static void init() {
        DeathCamClient c = new DeathCamClient();
        instance = c;
        MinecraftForge.EVENT_BUS.register(c);
        FMLCommonHandler.instance()
            .bus()
            .register(c);
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getResourceManager() instanceof IReloadableResourceManager)
            ((IReloadableResourceManager) mc.getResourceManager()).registerReloadListener(new Reload());
    }

    /** The death camera has the view (running, or holding it on the respawn screen). */
    public static boolean busy() {
        return instance != null && instance.state != IDLE;
    }

    // ---- starting and ending ----

    private boolean canBegin(Minecraft mc, EntityPlayer p) {
        return DeathCam.enabled && p == alive && p != done && mc.theWorld != null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onGui(GuiOpenEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        if (state == PLAY && e.gui instanceof GuiContainer) { // something trying to open a machine for the dead
            e.setCanceled(true);
            return;
        }
        if (!(e.gui instanceof GuiGameOver) || p == null || mc.theWorld == null || state == VANILLA) return;
        if (state == IDLE && p.getHealth() <= 0 && canBegin(mc, p)) try {
            begin(mc, p);
        } catch (Throwable t) {
            UkyCorpses.LOG.warn("Death camera failed to start, plain death screen", t);
            reset(mc);
            done = p;
            return;
        }
        if (state != IDLE) {
            // closing chat or the pause menu while dead asks for the death screen: let it just close
            if (mc.currentScreen != null) e.gui = null;
            else e.setCanceled(true);
            return;
        }
        // no scene (camera off, already had it, joined dead): straight back to life. Hardcore keeps its own screen; a
        // server that doesn't answer within 5 s gets the vanilla screen as a way out.
        // With UkyUI the death screen is its scene, and that comes back by itself.
        if (!DeathCam.autoRespawn || UkyScene.on()
            || mc.theWorld.getWorldInfo()
                .isHardcoreModeEnabled()
            || respawnWait > 100
            || p == done) return;
        e.setCanceled(true);
        if (respawning == p) return; // asked already; vanilla asks for the screen every tick until it happens
        respawning = p;
        respawnWait = 0;
        mc.thePlayer.respawnPlayer();
    }

    private void begin(Minecraft mc, EntityPlayer p) {
        player = p;
        hardcore = mc.theWorld.getWorldInfo()
            .isHardcoreModeEnabled();
        downedDeath = BodyCam.diedDowned(p);
        // from exactly where the view was: the game's camera, or the crawl camera if they lay downed
        EntityLivingBase v = mc.renderViewEntity != null ? mc.renderViewEntity : p;
        startYaw = v.rotationYaw;
        startPitch = v.rotationPitch;
        double[] f = forward(startYaw, startPitch, new double[3]);
        start[0] = v.posX - 0.1 * f[0];
        start[1] = v.posY - (v.yOffset - 1.62F) - 0.1 * f[1];
        start[2] = v.posZ - 0.1 * f[2];
        eye = new ViewEye(mc.theWorld, p, true);
        slot = p.inventory.currentItem;
        view = mc.gameSettings.thirdPersonView;
        mc.gameSettings.thirdPersonView = 0;
        srcWho = null;
        body = null;
        searchedAt = -1;
        haveBase = haveEye = false;
        joltPitch = joltRoll = 0;
        shift[0] = shift[1] = shift[2] = 0;
        lift = 0;
        baseYaw = startYaw;
        basePitch = startPitch;
        velYaw = velPitch = offYaw = offPitch = roll = looked = 0;
        lookedAt = -1;
        frozen = false;
        shake = downedDeath ? 0 : 0.6F;
        impactSeen = -1;
        fov0 = Float.NaN;
        want = false;
        autoLeft = -1;
        drained = false;
        fadeIn = false;
        ticks = beat = 0;
        breathed = false;
        partial = 0;
        lastFrame = 0;
        landAt = downedDeath ? 0 : 0.85F;
        startedAt = System.currentTimeMillis();
        uky = UkyScene.on() ? new UkyScene(fresh(CorpseClient.lastDeath) != null) : null;
        quote = DeathCam.quotes ? "ukycorpses.deathquote." + (1 + rand.nextInt(QUOTES)) : null;
        cause = null;
        causeOf = null;
        RenderCorpse.resetOwn();
        eye.place(v.posX, start[1] + 0.1 * f[1], v.posZ, startYaw, startPitch);
        mc.renderViewEntity = eye;
        KeyBinding.unPressAllKeys(); // a mouse button held at death would mine and click on through the scene
        drain(mc.gameSettings.keyBindJump);
        state = PLAY;
        installBlur(mc);
    }

    /** Back to the player's own view, everything put back. */
    private void reset(Minecraft mc) {
        restoreAngles();
        roll(mc, 0);
        extraFov(mc, 0);
        uninstallBlur(mc);
        if (eye != null) {
            if (mc.renderViewEntity == eye) mc.renderViewEntity = mc.thePlayer;
            mc.gameSettings.thirdPersonView = view;
        }
        eye = null;
        if (RenderCorpse.noHead != null && (RenderCorpse.noHead == srcWho || RenderCorpse.noHead == player)) {
            RenderCorpse.noHead = null;
            RenderCorpse.eyeAt = null;
            RenderCorpse.viewShift[0] = RenderCorpse.viewShift[1] = RenderCorpse.viewShift[2] = 0;
        }
        RenderCorpse.resetOwn();
        if (player != null) player.ignoreFrustumCheck = false;
        srcWho = null;
        body = null;
        uky = null;
        if (state != IDLE) KeyBinding.unPressAllKeys();
        state = IDLE;
    }

    /** To black first; the server is asked once it's black (respawnTick), so the new place is never seen cut in. */
    private void respawn(Minecraft mc) {
        state = RESPAWN;
        sinceRespawn = 0;
        asked = false;
        respawnAt = time();
        KeyBinding.unPressAllKeys();
    }

    /** Hardcore, or respawning by a click turned off: the vanilla screen, over the view that stays. */
    private void openVanilla(Minecraft mc) {
        state = VANILLA;
        vanillaAt = time();
        mc.displayGuiScreen(new GuiGameOver());
    }

    // ---- the blur: built before it's needed, sharpened and greyed out by its uniforms ----

    private void prepareBlur(Minecraft mc) {
        if (blur != null || blurFailed || !DeathCam.blur || !shadersWork()) return;
        try {
            blur = new ShaderGroup(mc.getTextureManager(), mc.getResourceManager(), mc.getFramebuffer(), BLUR);
            blur.createBindFramebuffers(blurW = mc.displayWidth, blurH = mc.displayHeight);
            passes = null;
            passesFailed = false;
        } catch (Exception ex) {
            blurFailed = true;
            blur = null;
            UkyCorpses.LOG.warn("Death blur not loaded", ex);
        }
    }

    private static boolean shadersWork() {
        return OpenGlHelper.shadersSupported && OpenGlHelper.isFramebufferEnabled();
    }

    private void installBlur(Minecraft mc) {
        EntityRenderer r = mc.entityRenderer;
        prepareBlur(mc);
        if (blur == null || !shadersWork() || Shaders.get(r) != null) return; // someone's own effect stays
        if (blurW != mc.displayWidth || blurH != mc.displayHeight)
            blur.createBindFramebuffers(blurW = mc.displayWidth, blurH = mc.displayHeight);
        uniforms(0, 1);
        Shaders.set(r, blur);
        blurOn = true;
    }

    private void uninstallBlur(Minecraft mc) {
        if (!blurOn) return;
        blurOn = false;
        if (Shaders.get(mc.entityRenderer) == blur) Shaders.set(mc.entityRenderer, null);
    }

    /** F4 or the super secret settings deleted it while it was in: built again for the next death. */
    private void checkBlur(Minecraft mc) {
        if (blurOn && Shaders.get(mc.entityRenderer) != blur) {
            blurOn = false;
            blur = null;
            passes = null;
        }
    }

    /** A resource reload deletes the installed group (and may leave it in the slot): ours goes, and is built anew. */
    private static final class Reload implements IResourceManagerReloadListener {

        @Override
        public void onResourceManagerReload(IResourceManager m) {
            DeathCamClient c = instance;
            if (c == null || c.blur == null) return;
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.entityRenderer != null && Shaders.get(mc.entityRenderer) == c.blur)
                Shaders.set(mc.entityRenderer, null);
            else c.blur.deleteShaderGroup(); // the reload deletes only what's installed
            c.blurOn = false;
            c.blur = null;
            c.passes = null;
        }
    }

    /** Blur radius and colour saturation of the death blur's passes; quietly nothing if they can't be reached. */
    private void uniforms(float radius, float saturation) {
        if (blur == null || passesFailed) return;
        try {
            if (passes == null) passes = ObfuscationReflectionHelper.getPrivateValue(ShaderGroup.class, blur, LIST);
            for (Object o : passes) {
                Shader s = (Shader) o;
                ShaderUniform r = s.getShaderManager()
                    .func_147991_a("Radius");
                if (r != null) r.func_148090_a(radius);
                ShaderUniform c = s.getShaderManager()
                    .func_147991_a("Saturation");
                if (c != null) c.func_148090_a(saturation);
            }
        } catch (Exception ex) {
            passesFailed = true; // the json's own values stay
        }
    }

    /** The view's roll (degrees, positive: the picture turns counter-clockwise), set for this frame exactly. */
    private static void roll(Minecraft mc, float deg) {
        try {
            ObfuscationReflectionHelper.setPrivateValue(EntityRenderer.class, mc.entityRenderer, deg, ROLL);
            ObfuscationReflectionHelper.setPrivateValue(EntityRenderer.class, mc.entityRenderer, deg, PREV_ROLL);
        } catch (Exception ex) {
            // no roll then
        }
    }

    /** Degrees added to the field of view this frame. */
    private static void extraFov(Minecraft mc, float deg) {
        try {
            ObfuscationReflectionHelper.setPrivateValue(EntityRenderer.class, mc.entityRenderer, deg, FOV);
            ObfuscationReflectionHelper.setPrivateValue(EntityRenderer.class, mc.entityRenderer, deg, PREV_FOV);
        } catch (Exception ex) {
            // nothing added then
        }
    }

    // ---- ticks ----

    private float time() {
        return (ticks + partial) / 20F;
    }

    /** Seconds from the click to black, when the server is asked. */
    private float out() {
        return uky != null ? UkyScene.EXIT : OUT;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        if (e.phase == TickEvent.Phase.START) {
            if (mc.theWorld == null || p == null) {
                if (state != IDLE) reset(mc);
                fadeIn = false;
                return;
            }
            if (mc.currentScreen == null) prepareBlur(mc); // ahead of any death
            if (p.getHealth() > 0) alive = p;
            // vanilla tips the dead over in red and pops them into smoke at 20; the body is drawn instead
            else p.deathTime = 0;
            if (respawning != null && (p != respawning || p.getHealth() > 0)) {
                respawning = null;
                respawnWait = 0;
            } else if (respawning != null) respawnWait++;
            if (state != IDLE && p != player) { // a new player: respawned (or left and came back)
                boolean back = state == RESPAWN;
                ukyIn = uky != null;
                done = player;
                reset(mc);
                if (back) { // the black lifts once the world is there
                    fadeIn = true;
                    fadeInAt = 0;
                    fadeInWait = System.nanoTime();
                }
            } else if (state != IDLE && p.getHealth() > 0) reset(mc); // not dead after all
            if (state == PLAY || state == RESPAWN) {
                p.inventory.currentItem = slot;
                p.motionX = p.motionY = p.motionZ = 0;
                GameSettings g = mc.gameSettings;
                for (KeyBinding k : new KeyBinding[] { g.keyBindAttack, g.keyBindUseItem, g.keyBindPickBlock })
                    if (k.getKeyCode() != 0) KeyBinding.setKeyBindState(k.getKeyCode(), false);
            }
            return;
        }
        if (mc.theWorld == null || p == null) return;
        if (state == IDLE) {
            // died with a screen open: vanilla asks for the death screen only once it's closed, so start here
            if (p.getHealth() <= 0 && canBegin(mc, p)) try {
                begin(mc, p);
                if (mc.currentScreen instanceof GuiContainer) p.closeScreen();
            } catch (Throwable t) {
                UkyCorpses.LOG.warn("Death camera failed to start, plain death screen", t);
                reset(mc);
                done = p;
            }
            return;
        }
        boolean paused = mc.isGamePaused();
        if (!paused) ticks++;
        float t = ticks / 20F;
        if (state == PLAY) {
            if (!paused) try {
                sounds(mc, t);
            } catch (RuntimeException ex) { // no heartbeat then
                beat = BEATS.length;
                breathed = true;
            }
            if (t >= PROMPT && !drained) {
                drained = true;
                drain(mc.gameSettings.keyBindJump);
            }
            // the count starts once it's all over (the body still, the heart stopped) and then just runs: looking
            // around doesn't hold it up
            if (autoLeft < 0 && t >= PROMPT && !animating(t))
                autoLeft = DeathCam.seconds > 0 ? DeathCam.seconds * 20 : Integer.MAX_VALUE;
            boolean free = mc.currentScreen == null && Display.isActive();
            if (autoLeft > 0 && autoLeft != Integer.MAX_VALUE && !paused && free && --autoLeft == 0) want = true;
            if (hardcore && t >= HARDCORE && free) want = true;
            if (want && t >= PROMPT && free) {
                if (hardcore || !DeathCam.autoRespawn) openVanilla(mc);
                else respawn(mc);
            }
            want = false;
        } else if (state == RESPAWN) {
            if (!asked && time() - respawnAt >= out()) {
                asked = true;
                mc.thePlayer.respawnPlayer();
            } else if (asked && !paused && ++sinceRespawn > 100) { // the server doesn't answer: its own screen
                done = player;
                reset(mc);
            }
        }
    }

    /** Still dying: the body coming down or the last hand still up, the heart still beating. */
    private boolean animating(float t) {
        if (t < BEATS[BEATS.length - 1]) return true;
        EntityCorpse c = body;
        return c != null && !c.isDead && c.settle.active();
    }

    private void sounds(Minecraft mc, float t) {
        if (beat < BEATS.length && t >= BEATS[beat]) {
            mc.getSoundHandler()
                .playSound(new Flat(HEART, 0.7F - 0.45F * beat / (BEATS.length - 1), 1));
            beat++;
        }
        if (!breathed && t >= BREATH) {
            breathed = true;
            mc.getSoundHandler()
                .playSound(new Flat(BREATH_SOUND, 0.3F, 0.9F));
        }
    }

    // ---- the view ----

    @SubscribeEvent
    public void onRender(TickEvent.RenderTickEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.phase == TickEvent.Phase.START) {
            if (state == IDLE) return;
            try {
                partial = e.renderTickTime;
                frame(mc);
            } catch (Throwable t) {
                UkyCorpses.LOG.warn("Death camera broke, plain death screen", t);
                reset(mc);
                done = player;
            }
            return;
        }
        restoreAngles();
        if (mc.theWorld == null) {
            fadeIn = false;
            return;
        }
        // the black: going down before the respawn, lifting off the new life once its world is there
        float a = 0;
        int how = BLACK;
        if (state == RESPAWN) {
            blackWas = a = Math.min(1, (time() - respawnAt) / out());
            if (uky != null && a < 1) how = COLLAPSE; // ends black, held black until the server answers
        } else if (fadeIn) {
            long now = System.nanoTime();
            boolean waiting = mc.currentScreen instanceof GuiDownloadTerrain && now - fadeInWait < 2_000_000_000L;
            if (waiting) a = 1;
            else {
                if (fadeInAt == 0) fadeInAt = now;
                float p = (now - fadeInAt) / 1e9F / (ukyIn ? UkyScene.ENTER : IN);
                if (p >= 1) fadeIn = false;
                a = (ukyIn ? 1 : blackWas) * (1 - p);
            }
            if (ukyIn) how = ENTER;
        }
        if (a > 0) black(mc, how, a, state == RESPAWN && time() - respawnAt > 2);
    }

    private void frame(Minecraft mc) {
        if (eye == null || mc.thePlayer != player || mc.theWorld != eye.worldObj) return;
        long now = System.nanoTime();
        float dt = lastFrame == 0 || mc.isGamePaused() ? 0 : Math.min(0.05F, (now - lastFrame) / 1e9F);
        lastFrame = now;
        float t = time();
        mc.gameSettings.thirdPersonView = 0;
        checkBlur(mc);

        // the mouse: taken before the game turns the dead player with it
        float dYaw = 0, dPitch = 0;
        if (state == PLAY && mc.currentScreen == null && mc.inGameHasFocus && Display.isActive()) {
            mc.mouseHelper.mouseXYChange();
            float f = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F, k = f * f * f * 8;
            if (DeathCam.lookAround) {
                dYaw = mc.mouseHelper.deltaX * k * 0.15F;
                dPitch = -mc.mouseHelper.deltaY * k * 0.15F * (mc.gameSettings.invertMouse ? -1 : 1);
            }
        }

        // what the eye is in, and where it is
        boolean have = source(mc);
        if (have) BodyCam.restView(src, rest);
        else {
            rest[0] = startYaw;
            rest[1] = startPitch;
            rest[2] = 0;
        }
        if (body != null && body.impactAt > impactSeen) { // the floor came up hard
            impactSeen = body.impactAt;
            shake = Math.max(shake, 0.5F);
            if (!downedDeath) landAt = Math.min(landAt, t);
        }

        // where it looks: to the rest view on a spring, then wherever the player looks from there
        if (!frozen && dt > 0) {
            float dy = MathHelper.wrapAngleTo180_float(rest[0] - baseYaw), dp = rest[1] - basePitch;
            velYaw = clampAbs(velYaw + (dy * STIFF * STIFF - 2 * STIFF * velYaw) * dt, MAX_TURN);
            velPitch = clampAbs(velPitch + (dp * STIFF * STIFF - 2 * STIFF * velPitch) * dt, MAX_TURN);
            baseYaw += velYaw * dt;
            basePitch += velPitch * dt;
        }
        float gain = downedDeath ? 1 : smooth((t - landAt) / 0.4F);
        if (gain > 0 && (dYaw != 0 || dPitch != 0)) {
            dYaw *= gain;
            dPitch *= gain;
            offYaw = soft(offYaw, dYaw, LOOK_YAW);
            offPitch = soft(offPitch, dPitch, LOOK_PITCH);
            looked += Math.abs(dYaw) + Math.abs(dPitch);
            if (looked >= 3 && !frozen) { // the script never fights the mouse
                frozen = true;
                lookedAt = t;
                velYaw = velPitch = 0;
            }
        }
        float yaw = baseYaw + offYaw;
        float pitch = Math
            .max(Math.min(UP_LIMIT, basePitch), Math.min(Math.max(DOWN_LIMIT, basePitch), basePitch + offPitch));
        offPitch = pitch - basePitch;
        // a few degrees of tilt lying down; level again once the player looks around
        float rollTo = lookedAt >= 0 ? 0 : rest[2] * smooth((t - landAt * 0.5F) / 1F);
        roll += (rollTo - roll) * (1 - (float) Math.exp(-4 * dt));
        shake *= (float) Math.exp(-4 * dt);
        if (dt > 0) { // <= 1.5 degrees; still while paused
            joltPitch = (rand.nextFloat() - 0.5F) * shake * 3;
            joltRoll = (rand.nextFloat() - 0.5F) * shake * 3;
        }
        float jp = joltPitch, jr = joltRoll;
        double[] f = forward(yaw, pitch + jp, fwd);
        double fx = f[0], fy = f[1], fz = f[2];

        // the eye: in the torso, easing in from where the view was; over the water's surface; off the walls
        if (have) {
            float decay = (float) Math.exp(-6 * dt);
            for (int i = 0; i < 3; i++) {
                shift[i] *= decay;
                base[i] = eyePos[i] + shift[i];
            }
            haveBase = true;
        } else if (!haveBase) System.arraycopy(start, 0, base, 0, 3);
        System.arraycopy(base, 0, cam, 0, 3);
        double liftTo = body != null && RenderCorpse.afloat(body) ? BodyCam.surface(mc.theWorld, cam, fy) : 0;
        lift += (liftTo - lift) * (1 - Math.exp(-8 * dt));
        cam[1] += lift;
        double cx = cam[0], cy = cam[1], cz = cam[2];
        if (!BodyCam.clear(mc.theWorld, cam, BodyCam.nearCorner(mc.gameSettings.fovSetting, aspect(mc)) + 0.02, 0.35)) {
            // inside something solid (buried, the void): stays where it was
            if (haveEye) System.arraycopy(eyeAt, 0, cam, 0, 3);
        }
        if (have) { // the body goes with the eye, so the two never part (but not where the eye is held in place)
            boolean held = Math.abs(cam[0] - cx) + Math.abs(cam[1] - cy) + Math.abs(cam[2] - cz) > 0.5;
            RenderCorpse.viewShift[0] = (held ? cx : cam[0]) - eyePos[0];
            RenderCorpse.viewShift[1] = (held ? cy : cam[1]) - eyePos[1];
            RenderCorpse.viewShift[2] = (held ? cz : cam[2]) - eyePos[2];
        }
        System.arraycopy(cam, 0, eyeAt, 0, 3);
        haveEye = true;
        RenderCorpse.noHead = have ? srcWho : null;
        RenderCorpse.eyeAt = have ? eyeAt : null;
        if (body != null) body.ignoreFrustumCheck = true;
        // drawn as their body, from a box at their feet the size of a fist: from the eye that box is often out of view
        player.ignoreFrustumCheck = true;

        // the game's camera sits 0.1 behind the point it's given
        eye.place(cam[0] + 0.1 * fx, cam[1] + 0.1 * fy, cam[2] + 0.1 * fz, yaw, pitch + jp);
        mc.renderViewEntity = eye;
        roll(mc, roll + jr);
        extraFov(mc, 0);
        float u = smooth((t - landAt) / 3F);
        // whole pixels only: vanilla's blur divides by 2r+1 but samples fewer for a fractional r, which darkens
        uniforms(u >= 0.5F ? 1 : 0, 1 - 0.55F * u);
        mirrorAngles(yaw, pitch + jp);
    }

    /** Which body the eye is in this frame, into src and eyePos; the view eases from where it was when that changes. */
    private boolean source(Minecraft mc) {
        EntityCorpse c = body(mc);
        Object who = null;
        if (c != null) {
            BodyCam.corpse(c, partial, src);
            who = c;
            Settle s = c.settle;
            if (who != srcWho && s.active()) { // when it will be down
                if (s.mode() == Settle.DYING) landAt = 0;
                else if (s.mode() == Settle.SPLAT) landAt = Math.min(landAt, 0.35F);
                else if (s.mode() == Settle.THROWN || s.mode() == Settle.FLOAT)
                    landAt = Math.max(landAt, s.flight() / 20F);
            }
        } else if (ReactionClient.pose(player, partial, src) || BodyCam.provisional(player, partial, src)) who = player;
        if (who == null) return false;
        BodyCam.eye(src, eyePos);
        if (who != srcWho) {
            double[] from = srcWho == null || !haveBase ? start : base;
            for (int i = 0; i < 3; i++) shift[i] = from[i] - eyePos[i];
            srcWho = who;
        }
        return true;
    }

    /** The player's fresh body (not an old one of theirs lying around here), or null while it hasn't come. */
    private EntityCorpse body(Minecraft mc) {
        if (body != null && !body.isDead && (body.host == null || !body.host.isInvalid() && body.host.body == body))
            return body;
        body = null;
        if (searchedAt == ticks) return null; // once a tick is enough
        searchedAt = ticks;
        // well above too: a drowned body comes up to the surface
        AxisAlignedBB box = AxisAlignedBB
            .getBoundingBox(start[0] - 16, start[1] - 12, start[2] - 16, start[0] + 16, start[1] + 48, start[2] + 16);
        body = EntityCorpse.findFresh(mc.theWorld, box, player.getCommandSenderName(), ticks + 40);
        return body;
    }

    /** Particles turn to face the player, not the camera: for the frame they're drawn in, the player looks along it. */
    private void mirrorAngles(float yaw, float pitch) {
        EntityPlayer p = player;
        if (p == null || p != Minecraft.getMinecraft().thePlayer) return;
        if (mirrored == null) {
            mirrored = p;
            keptYaw = p.rotationYaw;
            keptPitch = p.rotationPitch;
            keptPrevYaw = p.prevRotationYaw;
            keptPrevPitch = p.prevRotationPitch;
        }
        p.rotationYaw = p.prevRotationYaw = keptYaw + MathHelper.wrapAngleTo180_float(yaw - keptYaw);
        p.rotationPitch = p.prevRotationPitch = pitch;
    }

    private void restoreAngles() {
        EntityPlayer p = mirrored;
        if (p == null) return;
        mirrored = null;
        p.rotationYaw = keptYaw;
        p.rotationPitch = keptPitch;
        p.prevRotationYaw = keptPrevYaw;
        p.prevRotationPitch = keptPrevPitch;
    }

    /** off moved by d, never past ±limit, slowing down over the last SOFT degrees on the way out. */
    private static float soft(float off, float d, float limit) {
        if (d * off > 0 && Math.abs(off) > limit - SOFT) d *= Math.max(0, (limit - Math.abs(off)) / SOFT);
        return Math.max(-limit, Math.min(limit, off + d));
    }

    private static float clampAbs(float v, float max) {
        return Math.max(-max, Math.min(max, v));
    }

    private static float smooth(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    /** The unit vector a camera at Minecraft yaw and pitch looks along. */
    private static double[] forward(float yaw, float pitch, double[] out) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        out[0] = -Math.sin(y) * Math.cos(p);
        out[1] = -Math.sin(p);
        out[2] = Math.cos(y) * Math.cos(p);
        return out;
    }

    private static double aspect(Minecraft mc) {
        return mc.displayHeight > 0 ? mc.displayWidth / (double) mc.displayHeight : 16 / 9.0;
    }

    // ---- the bars, the card, the black ----

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post e) {
        if (e.type != RenderGameOverlayEvent.ElementType.ALL || state == IDLE) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        try {
            overlay(e);
        } catch (RuntimeException ex) {
            if (!overlayFailed) UkyCorpses.LOG.warn("Death camera overlay broke", ex);
            overlayFailed = true;
        } finally {
            GL11.glPopAttrib();
            GL11.glColor4f(1, 1, 1, 1);
        }
    }

    private boolean overlayFailed;

    private void overlay(RenderGameOverlayEvent.Post e) {
        Minecraft mc = Minecraft.getMinecraft();
        int w = e.resolution.getScaledWidth(), h = e.resolution.getScaledHeight();
        float t = time();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        boolean open = mc.currentScreen != null;
        if (uky != null) {
            if (state == VANILLA) return; // UkyUI's own screen has taken over
            if (!uky.settled(t)) uky.retheme(fresh(CorpseClient.lastDeath) != null);
            float exit = state == RESPAWN ? Math.min(1, (t - respawnAt) / UkyScene.EXIT) : 0;
            uky.draw(mc.fontRenderer, w, h, t, landAt, exit, UkyScene.pulse(t, BEATS), lines(t), !open);
            return;
        }
        if (!open) {
            // a dark red flash at the blow (not for bleeding out), the edges going dark as it ends
            if (!downedDeath && t < 0.2F) rect(0, 0, w, h, 0x7A0000, 0.35F * (1 - t / 0.2F));
            vignette(w, h, 0.45F * smooth((t - landAt) / 3F));
        }
        float slide = state == VANILLA ? 1 - Math.min(1, (t - vanillaAt) / 0.3F) : ease(t / 0.4F);
        float bar = Math.max(h * 0.08F, 24) * slide;
        if (bar > 0.5F) {
            int bg = look(BG, 0);
            rect(0, 0, w, bar, bg, 1);
            rect(0, h - bar, w, h, bg, 1);
        }
        if (!open && state != VANILLA && slide > 0.99F) card(mc, w, h, Math.max(h * 0.08F, 24), t);
    }

    /** Top: the title (a quote later). Bottom: what killed you and where the body is; how to go on. */
    private void card(Minecraft mc, int w, int h, float bar, float t) {
        FontRenderer font = mc.fontRenderer;
        // the title, at one and a half times; a quote takes its place after a while
        float q = quote == null ? 0 : smooth((t - 3F) / 0.5F);
        String title = StatCollector
            .translateToLocal(hardcore ? "ukycorpses.deathcam.titleHardcore" : "ukycorpses.deathcam.title");
        float ta = appear(t, 0.4F) * (1 - q);
        if (ta > 0.02F) {
            GL11.glPushMatrix();
            GL11.glTranslatef(w / 2F, bar / 2F - 6, 0);
            GL11.glScalef(1.5F, 1.5F, 1);
            text(font, title, -font.getStringWidth(title) / 2F, 0, look(DANGER, 0xE04848), ta);
            GL11.glPopMatrix();
        }
        if (q > 0.02F) {
            String s = font.trimStringToWidth("§o" + StatCollector.translateToLocal(quote), w - 16);
            text(font, s, (w - font.getStringWidth(s)) / 2F, bar / 2F - 4, look(DIM, 0xB0B0B0), q);
        }
        float y1 = h - bar + Math.max(3, bar / 2F - 10), y2 = y1 + 11;
        // right: how to go on
        String go, after = null;
        if (state == RESPAWN)
            go = time() - respawnAt > 2 ? StatCollector.translateToLocal("ukycorpses.deathcam.waiting") : "";
        else {
            go = StatCollector.translateToLocalFormatted(
                hardcore || !DeathCam.autoRespawn ? "ukycorpses.deathcam.continue" : "ukycorpses.deathcam.respawn",
                StatCollector.translateToLocal("ukycorpses.key.lmb"));
            if (autoLeft > 0 && autoLeft != Integer.MAX_VALUE)
                after = StatCollector.translateToLocalFormatted("ukycorpses.deathcam.auto", (autoLeft + 19) / 20);
        }
        float ga = appear(t, PROMPT);
        int goW = font.getStringWidth(go);
        text(font, go, w - 8 - goW, y1, look(TEXT, 0xFFFFFF), ga);
        if (after != null) text(font, after, w - 8 - font.getStringWidth(after), y2, look(DIM, 0x909090), ga);
        // left: what killed you, and what became of your things
        int room = w - 24 - Math.max(goW, after == null ? 0 : font.getStringWidth(after));
        String why = cause();
        if (why != null) text(font, font.trimStringToWidth(why, room), 8, y1, look(TEXT, 0xE0E0E0), appear(t, 0.7F));
        String things = things(t);
        if (things != null)
            text(font, font.trimStringToWidth(things, room), 8, y2, look(DIM, 0xA0A0A0), appear(t, 0.9F));
    }

    /** The card's words, for UkyUI's look. */
    private UkyScene.Lines lines(float t) {
        UkyScene.Lines l = new UkyScene.Lines();
        l.hardcore = hardcore;
        l.cause = cause();
        l.things = things(t);
        l.promptIn = appear(t, PROMPT);
        if (state == PLAY && t >= PROMPT) {
            l.prompt = StatCollector.translateToLocalFormatted(
                hardcore || !DeathCam.autoRespawn ? "ukycorpses.deathcam.continue" : "ukycorpses.deathcam.respawn",
                StatCollector.translateToLocal("ukycorpses.key.lmb"));
            if (autoLeft > 0 && autoLeft != Integer.MAX_VALUE)
                l.after = StatCollector.translateToLocalFormatted("ukycorpses.deathcam.auto", (autoLeft + 19) / 20);
        }
        return l;
    }

    /** The death message the server sent for this death, with how it ended if they lay downed. */
    private String cause() {
        CorpseClient.Death d = fresh(CorpseClient.lastDeath);
        if (d == null) return null;
        if (causeOf != d) {
            causeOf = d;
            try {
                IChatComponent c = IChatComponent.Serializer.func_150699_a(d.cause);
                cause = c == null ? "" : c.getFormattedText();
            } catch (RuntimeException ex) {
                cause = "";
            }
            if (!d.why.isEmpty()) cause += " §7→ " + StatCollector.translateToLocal("ukycorpses.deathcam." + d.why);
        }
        return cause.isEmpty() ? null : cause;
    }

    /** Where the body lies and what's in it; or what became of the things without one. */
    private String things(float t) {
        CorpseClient.Death d = fresh(CorpseClient.lastDeath);
        CorpseClient.Body b = CorpseClient.lastBody;
        if (b != null && b.at >= startedAt - 10_000 && (d == null || b.at >= d.at - 1000)) {
            StringBuilder s = new StringBuilder(
                StatCollector.translateToLocalFormatted("ukycorpses.deathcam.body", b.x, b.y, b.z, b.items, b.levels));
            if (b.lost > 0) s.append(" · ")
                .append(StatCollector.translateToLocalFormatted("ukycorpses.deathcam.lost", b.lost));
            if (b.lock > 0) s.append(" · ")
                .append(StatCollector.translateToLocalFormatted("ukycorpses.deathcam.locked", b.lock));
            else if (b.lock < 0) s.append(" · ")
                .append(StatCollector.translateToLocal("ukycorpses.deathcam.lockedForever"));
            return s.toString();
        }
        if (d == null) return null;
        if (d.kind == MsgDeath.KEPT) return StatCollector.translateToLocal("ukycorpses.deathcam.kept");
        if (d.kind == MsgDeath.DROPPED) return StatCollector.translateToLocal("ukycorpses.deathcam.dropped");
        return t > 1.5F ? StatCollector.translateToLocal("ukycorpses.deathcam.nothing") : null;
    }

    /** d if it's about this death (it comes just before the death itself), else null. */
    private CorpseClient.Death fresh(CorpseClient.Death d) {
        return d != null && d.at >= startedAt - 10_000 ? d : null;
    }

    /** UkyUI's colour (client.Palette) when it's installed, else ours. */
    private static final String DANGER = "danger", TEXT = "text", DIM = "textDim", BG = "background";

    private static int look(String name, int own) {
        return Palette.get(name, own);
    }

    private static float appear(float t, float at) {
        return smooth((t - at) / 0.25F);
    }

    private static float ease(float x) {
        x = Math.max(0, Math.min(1, x));
        return 1 - (1 - x) * (1 - x) * (1 - x);
    }

    private static void text(FontRenderer font, String s, float x, float y, int rgb, float alpha) {
        text(font, s, x, y, rgb, alpha, true);
    }

    static void text(FontRenderer font, String s, float x, float y, int rgb, float alpha, boolean shadow) {
        int a = (int) (Math.min(1, alpha) * 255);
        if (a <= 4 || s.isEmpty()) return; // FontRenderer reads alpha below 4 as "none given" and draws opaque
        GL11.glPushMatrix();
        GL11.glTranslatef(x - (int) x, y - (int) y, 0);
        font.drawString(s, (int) x, (int) y, a << 24 | rgb, shadow);
        GL11.glPopMatrix();
        GL11.glEnable(GL11.GL_BLEND);
    }

    /** A rectangle in floats (whole-pixel rectangles step visibly at large GUI scales), colour and alpha. */
    private static void rect(float x0, float y0, float x1, float y1, int rgb, float alpha) {
        if (alpha <= 0) return;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_F((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F, alpha);
        t.addVertex(x0, y1, 0);
        t.addVertex(x1, y1, 0);
        t.addVertex(x1, y0, 0);
        t.addVertex(x0, y0, 0);
        t.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /** Clear inside, darkening toward the corners to alpha. */
    private static void vignette(int w, int h, float alpha) {
        if (alpha <= 0.01F) return;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        Tessellator tes = Tessellator.instance;
        tes.startDrawing(GL11.GL_TRIANGLE_STRIP);
        double cx = w / 2.0, cy = h / 2.0, rx = w * 0.72, ry = h * 0.72;
        for (int i = 0; i <= 48; i++) {
            double a = i * Math.PI * 2 / 48, cos = Math.cos(a), sin = Math.sin(a);
            tes.setColorRGBA_F(0, 0, 0, 0);
            tes.addVertex(cx + cos * rx * 0.6, cy + sin * ry * 0.6, 0);
            tes.setColorRGBA_F(0, 0, 0, alpha);
            tes.addVertex(cx + cos * rx * 1.45, cy + sin * ry * 1.45, 0);
        }
        tes.draw();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    /** What covers the screen around the respawn: plain black; UkyUI's collapse to a line; UkyUI's way back in. */
    private static final int BLACK = 0, COLLAPSE = 1, ENTER = 2;

    /** Over everything, even the menus: the black around the respawn (how: BLACK, COLLAPSE, ENTER). */
    private void black(Minecraft mc, int how, float alpha, boolean waiting) {
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int w = sr.getScaledWidth(), h = sr.getScaledHeight();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, sr.getScaledWidth_double(), sr.getScaledHeight_double(), 0, 1000, 3000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0, 0, -2000);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        if (how == COLLAPSE) UkyScene.collapse(w, h, alpha);
        else if (how == ENTER) UkyScene.enter(w, h, 1 - alpha);
        else Gui.drawRect(0, 0, w, h, (int) (Math.min(1, alpha) * 255) << 24);
        if (waiting) {
            GL11.glEnable(GL11.GL_BLEND);
            String s = StatCollector.translateToLocal("ukycorpses.deathcam.waiting");
            text(mc.fontRenderer, s, (w - mc.fontRenderer.getStringWidth(s)) / 2F, h / 2F - 4, 0xA0A0A0, alpha);
        }
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
        GL11.glColor4f(1, 1, 1, 1);
    }

    // ---- input, field of view, sound ----

    /** No clicks into the world while dead; a click (once the prompt shows) is the way back. Releases go through. */
    @SubscribeEvent
    public void onMouse(MouseEvent e) {
        if (state != PLAY && state != RESPAWN) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen != null || !mc.inGameHasFocus) return; // the click that takes focus back stays the game's
        if (e.dwheel != 0) e.setCanceled(true);
        if (e.button >= 0 && e.buttonstate) {
            e.setCanceled(true);
            if (state == PLAY && (e.button == 0 || e.button == 1) && time() >= PROMPT) want = true;
        }
    }

    /** Jump or Enter go on too; the keys that would drop, open or pick things do nothing while dead. */
    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent e) {
        if (state != PLAY && state != RESPAWN) return;
        GameSettings g = Minecraft.getMinecraft().gameSettings;
        drain(g.keyBindDrop);
        drain(g.keyBindInventory);
        drain(g.keyBindPickBlock);
        drain(g.keyBindAttack);
        drain(g.keyBindUseItem);
        drain(g.keyBindTogglePerspective);
        for (KeyBinding k : g.keyBindsHotbar) drain(k);
        if (state != PLAY || !Keyboard.getEventKeyState() || Keyboard.isRepeatEvent() || time() < PROMPT) return;
        int key = Keyboard.getEventKey();
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER
            || key > 0 && key == g.keyBindJump.getKeyCode()) want = true;
    }

    private static void drain(KeyBinding k) {
        while (k.isPressed()) {}
    }

    /** The field of view eases back to plain from whatever it was at death (sprint, the downed slowness). */
    @SubscribeEvent
    public void onFov(FOVUpdateEvent e) {
        if (state == IDLE || e.entity != player) return;
        if (Float.isNaN(fov0)) fov0 = e.newfov;
        e.newfov = fov0 + (1 - fov0) * smooth(time() / 2F);
    }

    /** The world's sounds dull and quieten as the body goes still; long or ongoing sounds are left alone. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onSound(PlaySoundEvent17 e) {
        if (state == IDLE || !DeathCam.muffle) return;
        float k = 1 - 0.6F * smooth((time() - landAt) / 1.2F);
        ISound s = e.result;
        if (k > 0.999F || s == null
            || s instanceof Flat
            || s instanceof ITickableSound
            || s.canRepeat()
            || s.getAttenuationType() == ISound.AttenuationType.NONE
            || e.category == null) return;
        if (e.category == SoundCategory.BLOCKS || e.category == SoundCategory.MOBS
            || e.category == SoundCategory.ANIMALS
            || e.category == SoundCategory.PLAYERS
            || e.category == SoundCategory.WEATHER
            || e.category == SoundCategory.AMBIENT) e.result = new Muffled(s, k, 1 - (1 - k) / 6);
    }

    /** A sound with no place: heard the same wherever the camera is (the heart, the breath). */
    private static final class Flat implements ISound {

        private final ResourceLocation at;
        private final float volume, pitch;

        Flat(ResourceLocation at, float volume, float pitch) {
            this.at = at;
            this.volume = volume;
            this.pitch = pitch;
        }

        @Override
        public ResourceLocation getPositionedSoundLocation() {
            return at;
        }

        @Override
        public boolean canRepeat() {
            return false;
        }

        @Override
        public int getRepeatDelay() {
            return 0;
        }

        @Override
        public float getVolume() {
            return volume;
        }

        @Override
        public float getPitch() {
            return pitch;
        }

        @Override
        public float getXPosF() {
            return 0;
        }

        @Override
        public float getYPosF() {
            return 0;
        }

        @Override
        public float getZPosF() {
            return 0;
        }

        @Override
        public AttenuationType getAttenuationType() {
            return AttenuationType.NONE;
        }
    }

    /** Another sound, quieter and lower. */
    private static final class Muffled implements ISound {

        private final ISound s;
        private final float volume, pitch;

        Muffled(ISound s, float volume, float pitch) {
            this.s = s;
            this.volume = volume;
            this.pitch = pitch;
        }

        @Override
        public ResourceLocation getPositionedSoundLocation() {
            return s.getPositionedSoundLocation();
        }

        @Override
        public boolean canRepeat() {
            return s.canRepeat();
        }

        @Override
        public int getRepeatDelay() {
            return s.getRepeatDelay();
        }

        @Override
        public float getVolume() {
            return s.getVolume() * volume;
        }

        @Override
        public float getPitch() {
            return s.getPitch() * pitch;
        }

        @Override
        public float getXPosF() {
            return s.getXPosF();
        }

        @Override
        public float getYPosF() {
            return s.getYPosF();
        }

        @Override
        public float getZPosF() {
            return s.getZPosF();
        }

        @Override
        public AttenuationType getAttenuationType() {
            return s.getAttenuationType();
        }
    }

    // ---- what the dead player must not do or show while the camera runs ----

    @SubscribeEvent
    public void onHand(RenderHandEvent e) {
        if (state != IDLE) e.setCanceled(true);
    }

    @SubscribeEvent
    public void onHighlight(DrawBlockHighlightEvent e) {
        if (state != IDLE) e.setCanceled(true);
    }

    /** High: ahead of mods that draw these themselves (UkyUI's chat). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onHud(RenderGameOverlayEvent.Pre e) {
        if (state == IDLE) return;
        switch (e.type) {
            case CHAT: // the death message is already on the card; chat opened by hand still shows
                if (Minecraft.getMinecraft().currentScreen == null) e.setCanceled(true);
                break;
            case HELMET: // also where Xaero's minimap draws by default; any of these it may be told to use
            case BOSSHEALTH:
            case TEXT:
            case PORTAL:
            case CROSSHAIRS:
            case ARMOR:
            case HEALTH:
            case FOOD:
            case AIR:
            case HOTBAR:
            case EXPERIENCE:
            case HEALTHMOUNT:
            case JUMPBAR:
                e.setCanceled(true);
            default:
        }
    }
}

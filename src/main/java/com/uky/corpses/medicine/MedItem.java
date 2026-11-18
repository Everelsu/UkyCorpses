package com.uky.corpses.medicine;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.google.common.collect.Multimap;
import com.uky.corpses.Downed;

/** Every item of the medicine set: what it does is picked by kind, pills by name. */
public final class MedItem extends Item {

    public enum Kind {
        HEAL,
        RAG,
        PILL,
        SYRINGE,
        INJECTOR,
        DEFIB,
        SCANNER,
        CLUB,
        PLAIN
    }

    public final Kind kind;
    private final String name;
    /** As registered; the config may say otherwise (use(), heal()). */
    private final int baseUse;
    private final float baseHeal;

    MedItem(String name, Kind kind, int stack, int use, float heal) {
        this.name = name;
        this.kind = kind;
        this.baseUse = use;
        this.baseHeal = heal;
        setUnlocalizedName("ukycorpses." + name);
        setTextureName("ukycorpses:" + name);
        setMaxStackSize(stack);
        setCreativeTab(kind == Kind.PLAIN ? null : Medicine.tab);
        if (kind == Kind.DEFIB) setMaxDamage(3);
        if (kind == Kind.CLUB) {
            setMaxDamage(250);
            setFull3D();
        }
    }

    /**
     * Holds on someone else (right-click on a player): holder -> the one it is for. Both sides keep it (the client
     * for the HUD); the server's decides. Weak, so a holder who leaves takes the entry along.
     */
    public static final Map<EntityPlayer, EntityPlayer> targets = Collections.synchronizedMap(new WeakHashMap<>());

    /** Lang key (ukycorpses.med.*) of the line shown while this is held down; null: none. */
    public String useKey() {
        switch (kind) {
            case HEAL:
                return name.equals("medkit") ? "medkit" : "bandaging";
            case RAG:
                return "rag";
            case INJECTOR:
                return "injector";
            case SYRINGE:
                return "revive";
            default:
                return null;
        }
    }

    @Override
    public int getMaxItemUseDuration(ItemStack s) {
        return use();
    }

    @Override
    public EnumAction getItemUseAction(ItemStack s) {
        return kind == Kind.PILL && !giving(s) ? EnumAction.eat : EnumAction.none;
    }

    /** s is being put into someone else: no chewing, no crumbs. */
    private static boolean giving(ItemStack s) {
        synchronized (targets) {
            for (EntityPlayer p : targets.keySet()) if (p.getItemInUse() == s) return true;
        }
        return false;
    }

    /** Whether this works on another player (right-click on them), as the config says. */
    public boolean onOthers() {
        return kind == Kind.PILL ? Medicine.forceFeed
            : (kind == Kind.HEAL || kind == Kind.RAG || kind == Kind.INJECTOR) && Medicine.treatOthers;
    }

    /** Pills and the injector put into someone else: theirs to fight off. */
    boolean forced() {
        return kind == Kind.PILL || kind == Kind.INJECTOR;
    }

    /**
     * Right-click on another player: bandage, medkit, rag and injector treat them, a pill is fed to them. Both sides
     * start the same hold (health is synced, bleeding is not: the server checks that at the end).
     */
    @Override
    public boolean itemInteractionForEntity(ItemStack s, EntityPlayer p, EntityLivingBase e) {
        if (!(e instanceof EntityPlayer) || e == p || Downed.is(p) || p.isPotionActive(Medicine.STUN)) return false;
        EntityPlayer t = (EntityPlayer) e;
        if (!onOthers() || t.isDead || Downed.is(t)) return false;
        if (kind == Kind.HEAL && t.getHealth() >= t.getMaxHealth()) return false;
        // in creative vanilla hands this a copy; the hold must be on the stack really held, or it stops next tick
        int ticks = kind == Kind.PILL ? Medicine.forceFeedTicks : use();
        targets.put(p, t); // before the hold starts: getItemUseAction asks at once
        p.setItemInUse(p.getCurrentEquippedItem(), ticks);
        if (t instanceof EntityPlayerMP) {
            com.uky.corpses.UkyCorpses.net
                .sendTo(new MsgFeed(p.getEntityId(), s.copy(), ticks, forced() && Medicine.resist), (EntityPlayerMP) t);
        }
        return true;
    }

    /** Let go before it was done: the one it was for is told it's over. */
    @Override
    public void onPlayerStoppedUsing(ItemStack s, World w, EntityPlayer p, int left) {
        EntityPlayer t = targets.remove(p);
        if (!w.isRemote) Medicine.feedOver(p, t);
    }

    /** A hold on someone else stops when they get away or go down. */
    @Override
    public void onUsingTick(ItemStack s, EntityPlayer p, int left) {
        if (p.worldObj.isRemote || !(p instanceof EntityPlayerMP)) return;
        EntityPlayer t = targets.get(p);
        if (t == null) return;
        double r = Medicine.othersRange;
        if (t.isDead || Downed.is(t) || t.worldObj != p.worldObj || t.getDistanceSqToEntity(p) > r * r)
            Medicine.stopHold((EntityPlayerMP) p);
    }

    /** Both sides decide the same way, from synced state, so the client starts the hold too. */
    @Override
    public ItemStack onItemRightClick(ItemStack s, World w, EntityPlayer p) {
        if (Downed.is(p) || p.isPotionActive(Medicine.STUN)) return s;
        targets.remove(p); // this hold is for p
        switch (kind) {
            case HEAL:
                if (p.getHealth() < p.getMaxHealth()) p.setItemInUse(s, use());
                break;
            case RAG:
                if (p.isPotionActive(Medicine.BLEEDING)) p.setItemInUse(s, use());
                break;
            case PILL:
            case INJECTOR:
                p.setItemInUse(s, use());
                break;
            case SYRINGE:
                if (downedNear(p) != null) p.setItemInUse(s, use());
                break;
            case DEFIB:
                EntityPlayer t = downedNear(p);
                if (t == null) break;
                p.swingItem();
                if (t instanceof EntityPlayerMP && Downed.reviveNow((EntityPlayerMP) t, true)) {
                    t.addPotionEffect(new PotionEffect(Potion.confusion.id, 60, 3, true));
                    if (!p.capabilities.isCreativeMode) s.damageItem(1, p);
                    w.playSoundAtEntity(t, "random.explode", 0.3F, 1.9F);
                }
                break;
            default:
        }
        return s;
    }

    @Override
    public ItemStack onEaten(ItemStack s, World w, EntityPlayer p) {
        if (w.isRemote) {
            targets.remove(p);
            return s;
        }
        EntityPlayer t = targets.remove(p);
        Medicine.feedOver(p, t);
        EntityPlayer who = t != null ? t : p; // the one it goes into
        boolean used = true;
        switch (kind) {
            case HEAL:
                who.heal(heal()); // caps at max
                who.removePotionEffect(Medicine.BLEEDING.id);
                w.playSoundAtEntity(who, "dig.cloth", 1, 1);
                break;
            case RAG:
                used = who == p || who.isPotionActive(Medicine.BLEEDING);
                who.removePotionEffect(Medicine.BLEEDING.id);
                w.playSoundAtEntity(who, "dig.cloth", 1, 0.8F);
                break;
            case PILL:
                Medicine.pill(who, name);
                break;
            case SYRINGE:
                EntityPlayer d = downedNear(p);
                used = d instanceof EntityPlayerMP && Downed.reviveNow((EntityPlayerMP) d, true);
                break;
            case INJECTOR:
                who.addPotionEffect(new PotionEffect(Medicine.ADRENALINE.id, 540, 0, true));
                Medicine.protect(who, 400);
                w.playSoundAtEntity(who, "random.click", 1, 0.7F);
                break;
            default:
                used = false;
        }
        if (used && !p.capabilities.isCreativeMode) s.stackSize--;
        return s;
    }

    /** Nearest downed player within 3 blocks. Downed.is works on both sides. */
    static EntityPlayer downedNear(EntityPlayer p) {
        EntityPlayer best = null;
        double d = 9;
        for (Object o : p.worldObj.playerEntities) {
            EntityPlayer t = (EntityPlayer) o;
            double e = t.getDistanceSqToEntity(p);
            if (t != p && !t.isDead && Downed.is(t) && e <= d) {
                best = t;
                d = e;
            }
        }
        return best;
    }

    @Override
    public boolean hitEntity(ItemStack s, EntityLivingBase target, EntityLivingBase hitter) {
        if (kind != Kind.CLUB) return false;
        s.damageItem(1, hitter);
        if (target instanceof EntityPlayerMP) Medicine.clubbed((EntityPlayerMP) target, hitter);
        return true;
    }

    /** 2 hearts: the hand's 1 plus 3. */
    @Override
    public Multimap<String, AttributeModifier> getItemAttributeModifiers() {
        Multimap<String, AttributeModifier> m = super.getItemAttributeModifiers();
        if (kind == Kind.CLUB) m.put(
            SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName(),
            new AttributeModifier(field_111210_e, "Weapon modifier", 3, 0));
        return m;
    }

    /**
     * What it does, in a line: the hearts it gives back for the healing kinds, the rest from the lang file
     * (item.ukycorpses.(name).fx); null if nothing to say. The tooltip shows it, and the HUD while it's applied.
     */
    public String effect() {
        if (kind == Kind.HEAL) return StatCollector.translateToLocalFormatted(
            "ukycorpses.tip.heal",
            heal() % 2 == 0 ? String.valueOf((int) heal() / 2) : String.valueOf(heal() / 2));
        String key = getUnlocalizedName() + ".fx";
        if (kind == Kind.CLUB) return StatCollector.translateToLocalFormatted(
            key,
            Medicine.clubStunChance,
            Medicine.clubStunSeconds,
            Math.min(100, Medicine.clubStunChance * 2));
        return StatCollector.canTranslate(key) ? StatCollector.translateToLocal(key) : null;
    }

    /** Ticks it's held to apply: the config's, else as registered. */
    private int use() {
        Integer t = Medicine.useTicks.get(name);
        return t != null ? t : baseUse;
    }

    /** Half hearts it heals: the config's, else as registered. */
    private float heal() {
        Float h = Medicine.heals.get(name);
        return h != null ? h : baseHeal;
    }

    /** Ticks it's held to apply; 0 for what works at once. */
    public int useTicks() {
        return use();
    }

    /** What it is, what it does (green when it heals), how long it takes to apply. */
    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack s, EntityPlayer p, List tip, boolean adv) {
        String key = getUnlocalizedName() + ".desc";
        if (StatCollector.canTranslate(key))
            tip.add(EnumChatFormatting.GRAY + "" + EnumChatFormatting.ITALIC + StatCollector.translateToLocal(key));
        String fx = effect();
        if (fx != null) tip.add((kind == Kind.HEAL ? EnumChatFormatting.GREEN : EnumChatFormatting.AQUA) + fx);
        if (onOthers()) tip.add(
            EnumChatFormatting.YELLOW + StatCollector
                .translateToLocal(kind == Kind.PILL ? "ukycorpses.tip.feedOther" : "ukycorpses.tip.treatOther"));
        if (use() > 0) tip.add(
            EnumChatFormatting.DARK_GRAY
                + StatCollector.translateToLocalFormatted("ukycorpses.tip.use", String.format("%.1f", use() / 20F)));
    }
}

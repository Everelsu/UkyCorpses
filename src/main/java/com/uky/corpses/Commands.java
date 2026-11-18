package com.uky.corpses;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import com.uky.corpses.blood.Blood;
import com.uky.corpses.corpse.Poses;
import com.uky.corpses.medicine.Medicine;

/**
 * /corpses: the mod from the chat, for running a server and for trying things out without
 * dying for it. Alone it lists its subcommands, each line clickable into the chat box; every subcommand answers.
 */
final class Commands extends CommandBase {

    private static final String[] SUBS = { "help", "status", "on", "off", "list", "clear", "reload", "config", "body",
        "dummy", "down", "up", "fx", "blood", "near", "unlock", "retrieve" };
    /** By Poses constant. */
    private static final String[] POSES = { "lie", "sit", "prone", "side" },
        CAUSES = { "generic", "fall", "fire", "blast", "shot", "melee", "drown", "sick" },
        FX = { "bleeding", "shock", "stun", "euphoria", "inversion", "adrenaline", "clear" };
    /** Lines /corpses list shows before "and N more". */
    private static final int LIST = 15;

    @Override
    public String getCommandName() {
        return UkyCorpses.COMMAND;
    }

    @Override
    public List<String> getCommandAliases() {
        return Collections.singletonList("ukycorpse");
    }

    @Override
    public String getCommandUsage(ICommandSender s) {
        return "ukycorpses.cmd.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender s, String[] a) {
        if (a.length == 1) return getListOfStringsMatchingLastWord(a, SUBS);
        String[] players = MinecraftServer.getServer()
            .getAllUsernames();
        switch (a[0]) {
            case "retrieve":
            case "retrievecorpse":
                return new com.uky.corpses.pc.RetrieveCorpseCommand().addTabCompletionOptions(s, a);
            case "dummy":
            case "body":
                if (a.length == 2) return getListOfStringsMatchingLastWord(a, concat(POSES, "any"));
                if (a.length == 3) return getListOfStringsMatchingLastWord(a, CAUSES);
                if (a.length == 4) return getListOfStringsMatchingLastWord(a, "full");
                return null;
            case "fx":
                if (a.length == 2) return getListOfStringsMatchingLastWord(a, FX);
                if (a.length == 3) return getListOfStringsMatchingLastWord(a, "10", "30", "60", "300");
                if (a.length == 4) return getListOfStringsMatchingLastWord(a, players);
                return null;
            case "down":
            case "up":
            case "list":
                return a.length == 2 ? getListOfStringsMatchingLastWord(a, players) : null;
            case "config":
                if (a.length == 2) return getListOfStringsMatchingLastWord(a, keys());
                if (a.length == 3) {
                    Property p = property(a[1]);
                    if (p == null) return null;
                    if (p.isBooleanValue()) return getListOfStringsMatchingLastWord(a, "true", "false");
                    String[] valid = p.getValidValues();
                    return getListOfStringsMatchingLastWord(a, valid != null ? valid : new String[] { p.getString() });
                }
                return null;
            default:
                return null;
        }
    }

    @Override
    public void processCommand(ICommandSender s, String[] a) {
        String sub = a.length == 0 ? "help" : a[0];
        switch (sub) {
            case "retrieve":
            case "retrievecorpse":
                new com.uky.corpses.pc.RetrieveCorpseCommand().processCommand(s, a);
                break;
            case "help":
                help(s);
                break;
            case "status":
                status(s);
                break;
            case "on":
            case "off":
                Corpses.setEnabled(sub.equals("on"));
                status(s);
                break;
            case "list":
                list(s, a.length > 1 ? a[1] : null);
                break;
            case "clear":
                List<EntityCorpse> all = Corpses.loaded();
                for (EntityCorpse b : all) b.setDead(); // the things land on the ground, block bodies take their blocks
                say(s, "ukycorpses.cmd.cleared", all.size());
                break;
            case "reload":
                CorpseConfig.reload();
                say(s, "ukycorpses.cmd.reloaded");
                break;
            case "config":
                config(s, a);
                break;
            case "body":
                body(getCommandSenderAsPlayer(s), a);
                break;
            case "dummy":
                dummy(getCommandSenderAsPlayer(s), a);
                break;
            case "down": {
                EntityPlayerMP p = a.length > 1 ? getPlayer(s, a[1]) : getCommandSenderAsPlayer(s);
                if (Downed.is(p)) say(s, "ukycorpses.cmd.alreadyDown", p.getCommandSenderName());
                else {
                    Downed.down(p, DamageSource.generic);
                    say(s, "ukycorpses.cmd.down", p.getCommandSenderName());
                }
                break;
            }
            case "up": {
                EntityPlayerMP p = a.length > 1 ? getPlayer(s, a[1]) : getCommandSenderAsPlayer(s);
                say(
                    s,
                    Downed.reviveNow(p, false) ? "ukycorpses.cmd.up" : "ukycorpses.cmd.notDown",
                    p.getCommandSenderName());
                break;
            }
            case "fx":
                fx(s, a);
                break;
            case "blood": {
                EntityPlayerMP p = getCommandSenderAsPlayer(s);
                float size = a.length > 1 ? (float) parseDoubleBounded(s, a[1], 0.3, 4) : 1.6F;
                Blood.pool(p.worldObj, p.posX, p.boundingBox.minY + 0.3, p.posZ, size, 0, 40);
                say(s, Blood.on() ? "ukycorpses.cmd.blood" : "ukycorpses.cmd.bloodOff");
                break;
            }
            case "near":
                near(getCommandSenderAsPlayer(s));
                break;
            case "unlock": {
                EntityCorpse b = nearest(getCommandSenderAsPlayer(s));
                if (b == null) say(s, "ukycorpses.cmd.none");
                else {
                    b.lockTicks = 0;
                    b.sync();
                    say(s, "ukycorpses.cmd.unlocked", b.owner());
                }
                break;
            }
            default:
                say(s, "ukycorpses.cmd.unknown", sub);
        }
    }

    // ---- what there is, how the mod stands ----

    /** One line a subcommand: what to type and what it does; a click puts it in the chat box. */
    private static void help(ICommandSender s) {
        s.addChatMessage(
            new ChatComponentTranslation("ukycorpses.cmd.help")
                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GOLD)));
        for (String sub : SUBS) {
            if (sub.equals("help")) continue;
            String typed = "/" + UkyCorpses.COMMAND + " " + sub + " ";
            IChatComponent line = new ChatComponentText(typed).setChatStyle(
                new ChatStyle().setColor(EnumChatFormatting.YELLOW)
                    .setChatClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, typed))
                    .setChatHoverEvent(
                        new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            new ChatComponentTranslation("ukycorpses.cmd.click"))));
            line.appendSibling(
                new ChatComponentTranslation("ukycorpses.cmd." + sub + ".args")
                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY)));
            line.appendSibling(
                new ChatComponentTranslation("ukycorpses.cmd." + sub + ".desc")
                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.WHITE)));
            s.addChatMessage(line);
        }
    }

    private static void status(ICommandSender s) {
        say(
            s,
            "ukycorpses.cmd.status",
            onOff(Corpses.enabled),
            Corpses.loaded()
                .size(),
            onOff(Corpses.asBlock),
            onOff(CorpseConfig.downed),
            CorpseConfig.mode,
            CorpseConfig.ownerLockMinutes);
    }

    /** Loaded bodies (or one player's), nearest world first; a click on one suggests a teleport there. */
    private static void list(ICommandSender s, String owner) {
        List<EntityCorpse> all = new ArrayList<>();
        for (EntityCorpse b : Corpses.loaded()) if (owner == null || owner.equalsIgnoreCase(b.owner())) all.add(b);
        if (all.isEmpty()) {
            say(s, "ukycorpses.cmd.none");
            return;
        }
        say(s, "ukycorpses.cmd.list", all.size());
        for (int i = 0; i < Math.min(LIST, all.size()); i++) {
            EntityCorpse b = all.get(i);
            int x = MathHelper.floor_double(b.posX), y = MathHelper.floor_double(b.posY),
                z = MathHelper.floor_double(b.posZ);
            IChatComponent line = new ChatComponentTranslation(
                "ukycorpses.cmd.listLine",
                b.owner(),
                x,
                y,
                z,
                b.worldObj.provider.dimensionId,
                b.count(),
                new ChatComponentTranslation(b.host != null ? "ukycorpses.cmd.block" : "ukycorpses.cmd.entity"));
            line.setChatStyle(
                new ChatStyle()
                    .setChatClickEvent(
                        new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp " + x + " " + (y + 1) + " " + z))
                    .setChatHoverEvent(
                        new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            new ChatComponentTranslation("ukycorpses.cmd.click"))));
            s.addChatMessage(line);
        }
        if (all.size() > LIST) say(s, "ukycorpses.cmd.more", all.size() - LIST);
    }

    // ---- the settings ----

    /**
     * config: the categories. config (category): its settings and their values. config (category.key): that one, its
     * default and what it may be. config (category.key) (value): set, saved and in effect.
     */
    private static void config(ICommandSender s, String[] a) {
        Configuration c = CorpseConfig.config();
        if (a.length < 2) {
            say(s, "ukycorpses.cmd.configCategories", String.join(", ", categories()));
            return;
        }
        if (c.hasCategory(a[1])) {
            ConfigCategory cat = c.getCategory(a[1]);
            say(s, "ukycorpses.cmd.configCategory", new ChatComponentTranslation("ukycorpses.config." + a[1]));
            for (Property p : cat.getOrderedValues()) if (p.showInGui()) show(s, a[1] + "." + p.getName(), p);
            return;
        }
        Property p = property(a[1]);
        if (p == null) {
            say(s, "ukycorpses.cmd.configUnknown", a[1]);
            return;
        }
        if (a.length < 3) {
            show(s, a[1], p);
            if (p.comment != null && !p.comment.isEmpty()) s.addChatMessage(
                new ChatComponentText(p.comment).setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY)));
            return;
        }
        String v = a[2];
        if (!fits(p, v)) {
            say(s, "ukycorpses.cmd.configBad", v, allowed(p));
            return;
        }
        p.set(p.isBooleanValue() ? String.valueOf(Boolean.parseBoolean(v)) : v);
        CorpseConfig.config()
            .save();
        CorpseConfig.sync();
        say(s, "ukycorpses.cmd.configSet", a[1], p.getString());
        if (p.requiresMcRestart()) say(s, "ukycorpses.cmd.configRestart");
    }

    private static void show(ICommandSender s, String key, Property p) {
        IChatComponent line = new ChatComponentTranslation(
            "ukycorpses.cmd.configValue",
            key,
            p.getString(),
            p.getDefault(),
            new ChatComponentTranslation(p.getLanguageKey()));
        line.setChatStyle(
            new ChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/" + UkyCorpses.COMMAND + " config " + key + " "))
                .setChatHoverEvent(
                    new HoverEvent(
                        HoverEvent.Action.SHOW_TEXT,
                        new ChatComponentText(p.comment == null ? "" : p.comment))));
        s.addChatMessage(line);
    }

    private static boolean fits(Property p, String v) {
        try {
            if (p.isBooleanValue()) return v.equals("true") || v.equals("false");
            if (p.isIntValue()) {
                int i = Integer.parseInt(v);
                return i >= Integer.parseInt(p.getMinValue()) && i <= Integer.parseInt(p.getMaxValue());
            }
            if (p.isDoubleValue()) {
                double d = Double.parseDouble(v);
                return d >= Double.parseDouble(p.getMinValue()) && d <= Double.parseDouble(p.getMaxValue());
            }
        } catch (NumberFormatException ex) {
            return false;
        }
        String[] valid = p.getValidValues();
        return valid == null || Arrays.asList(valid)
            .contains(v);
    }

    private static String allowed(Property p) {
        if (p.isBooleanValue()) return "true, false";
        if (p.isIntValue() || p.isDoubleValue()) return p.getMinValue() + " .. " + p.getMaxValue();
        String[] valid = p.getValidValues();
        return valid == null ? "*" : String.join(", ", valid);
    }

    /** "category.key" (the category may have dots of its own: penalty.easy.keepXpPercent). */
    private static Property property(String key) {
        int dot = key.lastIndexOf('.');
        if (dot <= 0) return null;
        Configuration c = CorpseConfig.config();
        String cat = key.substring(0, dot);
        if (!c.hasCategory(cat)) return null;
        Property p = c.getCategory(cat)
            .get(key.substring(dot + 1));
        return p != null && p.showInGui() ? p : null;
    }

    private static List<String> categories() {
        List<String> out = new ArrayList<>(
            CorpseConfig.config()
                .getCategoryNames());
        Collections.sort(out);
        return out;
    }

    private static String[] keys() {
        List<String> out = new ArrayList<>();
        Configuration c = CorpseConfig.config();
        for (String cat : categories()) {
            out.add(cat);
            for (Property p : c.getCategory(cat)
                .getOrderedValues()) if (p.showInGui()) out.add(cat + "." + p.getName());
        }
        return out.toArray(new String[0]);
    }

    // ---- trying things out ----

    /** body [pose|any] [cause] [full]: dressed in copies of your armour; full: copies of everything you carry. */
    private static void body(EntityPlayerMP p, String[] a) {
        double[] at = Downed.spot(p);
        String pose = a.length > 1 ? a[1] : "any";
        if (!pose.equals("any")) at[4] = index(POSES, pose);
        else if (at[4] == EntityCorpse.PRONE) at[4] = Downed.fallPose(p, DamageSource.generic);
        int cause = a.length > 2 ? index(CAUSES, a[2]) : Poses.GENERIC;
        boolean full = a.length > 3 && a[3].equals("full");
        // a blast a step in front: thrown back from it
        double yaw = Math.toRadians(p.rotationYaw);
        double[] blast = cause == Poses.BLAST
            ? new double[] { p.posX - Math.sin(yaw) * 1.5, p.boundingBox.minY, p.posZ + Math.cos(yaw) * 1.5, 4 }
            : null;
        List<EntityCorpse.Entry> things = new ArrayList<>();
        ItemStack[] armor = p.inventory.armorInventory, main = p.inventory.mainInventory;
        for (int i = 0; i < armor.length; i++)
            if (armor[i] != null) things.add(new EntityCorpse.Entry(EntityCorpse.ARMOR + i, armor[i].copy()));
        if (full) for (int i = 0; i < main.length; i++)
            if (main[i] != null) things.add(new EntityCorpse.Entry(i, main[i].copy()));
        EntityCorpse b = UkyCorpses.instance.layOut(
            p,
            at,
            cause,
            cause == Poses.FIRE,
            cause == Poses.FALL,
            blast,
            p.renderYawOffset,
            p.inventory.currentItem,
            things,
            0,
            0);
        say(
            p,
            "ukycorpses.cmd.body",
            POSES[b.pose()],
            CAUSES[cause],
            MathHelper.floor_double(b.posX),
            MathHelper.floor_double(b.posY),
            MathHelper.floor_double(b.posZ));
    }

    /** Owner of the dummy bodies: nobody real, so whoever finds one has to search it. */
    static final String DUMMY = "Dummy";

    /**
     * dummy [pose] [cause]: a body that isn't yours, unlocked, with a handful of things in it, to try the search on.
     */
    private static void dummy(EntityPlayerMP p, String[] a) {
        double[] at = Downed.spot(p);
        String pose = a.length > 1 ? a[1] : "any";
        if (!pose.equals("any")) at[4] = index(POSES, pose);
        else at[4] = p.worldObj.rand.nextInt(POSES.length);
        int cause = a.length > 2 ? index(CAUSES, a[2]) : Poses.GENERIC;
        java.util.Random r = p.worldObj.rand;
        List<EntityCorpse.Entry> things = new ArrayList<>();
        Object[][] loot = { { net.minecraft.init.Items.iron_sword, 1 }, { net.minecraft.init.Items.bread, 6 },
            { net.minecraft.init.Items.arrow, 24 }, { net.minecraft.init.Items.bow, 1 },
            { net.minecraft.init.Items.gold_ingot, 4 }, { net.minecraft.init.Items.apple, 3 },
            { net.minecraft.init.Items.coal, 8 }, { Medicine.BANDAGE, 3 }, { Medicine.RAG, 2 } };
        int slot = 0;
        for (Object[] l : loot) if (r.nextFloat() < 0.7F)
            things.add(new EntityCorpse.Entry(slot++, new ItemStack((net.minecraft.item.Item) l[0], (Integer) l[1])));
        if (r.nextBoolean()) things.add(
            new EntityCorpse.Entry(EntityCorpse.ARMOR + 2, new ItemStack(net.minecraft.init.Items.leather_chestplate)));
        EntityCorpse b = UkyCorpses.instance
            .layOut(p, at, cause, cause == Poses.FIRE, cause == Poses.FALL, null, p.renderYawOffset, 0, things, 7, 0);
        b.setOwner(DUMMY);
        b.ownerId = java.util.UUID.nameUUIDFromBytes(("ukycorpses:" + DUMMY).getBytes());
        b.sync();
        say(
            p,
            "ukycorpses.cmd.dummy",
            POSES[b.pose()],
            CAUSES[cause],
            MathHelper.floor_double(b.posX),
            MathHelper.floor_double(b.posY),
            MathHelper.floor_double(b.posZ));
    }

    /** fx name|clear [seconds] [player]. */
    private static void fx(ICommandSender s, String[] a) {
        Potion[] ours = { Medicine.BLEEDING, Medicine.SHOCK, Medicine.STUN, Medicine.EUPHORIA, Medicine.INVERSION,
            Medicine.ADRENALINE };
        int i = index(FX, a.length > 1 ? a[1] : "");
        EntityPlayerMP p = a.length > 3 ? getPlayer(s, a[3]) : getCommandSenderAsPlayer(s);
        if (i == ours.length) {
            for (Potion o : ours) p.removePotionEffect(o.id);
            say(s, "ukycorpses.cmd.fxClear", p.getCommandSenderName());
            return;
        }
        int seconds = a.length > 2 ? parseIntBounded(s, a[2], 1, 3600) : 30;
        p.addPotionEffect(new PotionEffect(ours[i].id, seconds * 20, 0));
        say(s, "ukycorpses.cmd.fx", p.getCommandSenderName(), new ChatComponentTranslation(ours[i].getName()), seconds);
    }

    private static void near(EntityPlayerMP p) {
        EntityCorpse b = nearest(p);
        if (b == null) {
            say(p, "ukycorpses.cmd.none");
            return;
        }
        long lock = b.lockLeft();
        say(
            p,
            "ukycorpses.cmd.near1",
            b.owner(),
            new ChatComponentTranslation(b.host != null ? "ukycorpses.cmd.block" : "ukycorpses.cmd.entity"),
            MathHelper.floor_double(b.posX),
            MathHelper.floor_double(b.posY),
            MathHelper.floor_double(b.posZ));
        int look = b.look();
        say(
            p,
            "ukycorpses.cmd.near2",
            POSES[b.pose()],
            Poses.variant(look) + (Poses.flipped(look) ? "'" : ""),
            CAUSES[Math.max(0, Math.min(CAUSES.length - 1, Poses.causeOf(look)))],
            b.situation(),
            b.burn());
        say(
            p,
            "ukycorpses.cmd.near3",
            b.count(),
            b.xp,
            lock == Long.MAX_VALUE ? new ChatComponentTranslation("ukycorpses.cmd.lockForever")
                : lock == 0 ? new ChatComponentTranslation("ukycorpses.cmd.lockOpen") : (lock + 1199) / 1200);
    }

    /** The body nearest p within 16 blocks in its world; null if none. */
    private static EntityCorpse nearest(EntityPlayerMP p) {
        EntityCorpse best = null;
        double far = 16 * 16;
        for (EntityCorpse b : Corpses.loaded()) {
            if (b.worldObj != p.worldObj) continue;
            double d = b.getDistanceSqToEntity(p);
            if (d < far) {
                far = d;
                best = b;
            }
        }
        return best;
    }

    // ---- bits ----

    private static IChatComponent onOff(boolean on) {
        return new ChatComponentTranslation(on ? "ukycorpses.cmd.on" : "ukycorpses.cmd.off");
    }

    private static int index(String[] names, String name) {
        int i = Arrays.asList(names)
            .indexOf(name);
        if (i < 0) throw new WrongUsageException("ukycorpses.cmd.usage");
        return i;
    }

    private static String[] concat(String[] a, String b) {
        String[] out = Arrays.copyOf(a, a.length + 1);
        out[a.length] = b;
        return out;
    }

    private static void say(ICommandSender s, String key, Object... args) {
        s.addChatMessage(new ChatComponentTranslation(key, args));
    }
}

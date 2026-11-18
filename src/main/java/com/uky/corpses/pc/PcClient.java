package com.uky.corpses.pc;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.IIcon;

import com.uky.corpses.EntityCorpse;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Per-stack direction with ordinary atlas icons: vanilla owns all inventory/hand/drop transforms. */
@SideOnly(Side.CLIENT)
public final class PcClient {

    private static final IIcon[] icons = new IIcon[32];
    private static final Map<Entity, CompassAngle> angles = new WeakHashMap<>();

    public static void register(IIconRegister atlas) {
        for (int i = 0; i < icons.length; i++)
            icons[i] = atlas.registerIcon(String.format("ukycorpses:corpse_compass_%02d", i));
    }

    public static NBTTagCompound target(ItemStack stack) {
        NBTTagCompound target = CorpseCompass.target(stack);
        Minecraft mc = Minecraft.getMinecraft();
        if (!CorpseCompass.valid(target) || mc.theWorld == null
            || target.getInteger("Dim") != mc.theWorld.provider.dimensionId) return target;
        java.util.UUID id = CorpseCompass.id(stack), owner = PcStore.uuid(target, "Owner");
        for (Object candidate : mc.theWorld.loadedEntityList) if (candidate instanceof EntityCorpse) {
            EntityCorpse body = (EntityCorpse) candidate;
            if (body.isDead) continue;
            if (body.getUniqueID()
                .equals(id)
                || owner != null && owner.equals(body.physicalOwner())
                    && target.getLong("Life") == body.physicalLife()) {
                NBTTagCompound current = (NBTTagCompound) target.copy();
                current.setDouble("X", body.posX);
                current.setDouble("Y", body.posY);
                current.setDouble("Z", body.posZ);
                return current;
            }
        }
        return target;
    }

    public static IIcon icon(ItemStack stack, Entity holder) {
        NBTTagCompound target = target(stack);
        float rotation = 0;
        if (holder != null && holder.worldObj != null) {
            CompassAngle angle = angles.computeIfAbsent(holder, key -> new CompassAngle());
            long ticks = holder.worldObj.getTotalWorldTime();
            rotation = CorpseCompass.valid(target) && target.getInteger("Dim") == holder.dimension
                ? angle.towards(
                    ticks,
                    holder.rotationYaw,
                    target.getDouble("X") - holder.posX,
                    target.getDouble("Z") - holder.posZ)
                : angle.spinning(
                    ticks,
                    CorpseCompass.id(stack) == null ? 0
                        : CorpseCompass.id(stack)
                            .hashCode());
        }
        return icons[((int) Math.floor(rotation * 32 + .5) + 16) & 31];
    }

    public static void open(ItemStack stack) {
        if (CorpseCompass.valid(CorpseCompass.target(stack))) Minecraft.getMinecraft()
            .displayGuiScreen(new CompassScreen(stack.copy()));
    }

    private PcClient() {}
}

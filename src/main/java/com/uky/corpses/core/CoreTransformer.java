package com.uky.corpses.core;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** 1.7.10 equivalents of the original SurfaceBlood, AMP and dragging mixins. */
public final class CoreTransformer implements IClassTransformer {

    private static final String HOOKS = "com/uky/corpses/core/Hooks";

    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || transformedName == null) return bytes;
        // JNI method names and the transformer itself must remain untouched.
        if (transformedName.startsWith("com.github.stephengold.joltjni.")
            || transformedName.startsWith("com.uky.corpses.core.")) return bytes;
        boolean stack = transformedName.equals("net.minecraft.item.ItemStack");
        boolean render = transformedName.equals("net.minecraft.client.renderer.entity.Render");
        boolean gui = transformedName.equals("net.minecraft.client.renderer.entity.RenderItem");
        boolean held = transformedName.equals("net.minecraft.client.renderer.ItemRenderer");
        boolean player = transformedName.equals("net.minecraft.client.renderer.entity.RenderPlayer");
        boolean serverPlayer = transformedName.equals("net.minecraft.entity.player.EntityPlayerMP");
        boolean teleportCommand = transformedName.equals("net.minecraft.command.server.CommandTeleport");
        boolean look = transformedName.equals("net.minecraft.entity.Entity");
        boolean biped = transformedName.equals("net.minecraft.client.model.ModelBiped")
            || transformedName.equals("com.hbm.render.model.ModelArmorBase");
        boolean part = transformedName.equals("net.minecraft.client.model.ModelRenderer");
        boolean damage = transformedName.equals("net.minecraft.entity.EntityLivingBase")
            || transformedName.equals("net.minecraft.entity.player.EntityPlayer");
        if (!stack && !render
            && !gui
            && !held
            && !player
            && !serverPlayer
            && !teleportCommand
            && !look
            && !biped
            && !damage
            && !part) return bytes;
        ClassNode type = new ClassNode(Opcodes.ASM5);
        new ClassReader(bytes).accept(type, ClassReader.EXPAND_FRAMES);
        boolean changed = false;
        for (MethodNode method : type.methods) {
            if (player && named(method, "doRender", "func_76986_a")
                && method.desc.startsWith("(Lnet/minecraft/client/entity/AbstractClientPlayer;")
                && !contains(method, "playerRenderPre")) {
                for (AbstractInsnNode at = method.instructions.getFirst(); at != null; at = at.getNext())
                    if (at instanceof MethodInsnNode
                        && ((MethodInsnNode) at).owner.equals("cpw/mods/fml/common/eventhandler/EventBus")
                        && ((MethodInsnNode) at).name.equals("post")) {
                            method.instructions
                                .set(at, call("playerRenderPre", "(Ljava/lang/Object;Ljava/lang/Object;)Z"));
                            changed = true;
                            break;
                        }
            }
            if (serverPlayer && named(method, "setPositionAndUpdate", "func_70634_a")
                && method.desc.equals("(DDD)V")
                && !contains(method, "teleported")) {
                InsnList code = new InsnList();
                code.add(new VarInsnNode(Opcodes.ALOAD, 0));
                code.add(call("teleported", "(Ljava/lang/Object;)V"));
                method.instructions.insert(code);
                changed = true;
            }
            if (teleportCommand && !contains(method, "teleported")) {
                for (AbstractInsnNode at = method.instructions.getFirst(); at != null; at = at.getNext())
                    if (at instanceof MethodInsnNode && namedCall((MethodInsnNode) at, "mountEntity", "func_70078_a")) {
                        InsnList code = new InsnList();
                        code.add(new InsnNode(Opcodes.DUP2));
                        code.add(new InsnNode(Opcodes.POP));
                        code.add(call("teleported", "(Ljava/lang/Object;)V"));
                        method.instructions.insertBefore(at, code);
                        changed = true;
                    }
            }
            if (held && named(method, "renderItem", "func_78443_a")
                && Type.getArgumentTypes(method.desc).length == 4
                && !contains(method, "heldOverlay")) {
                for (AbstractInsnNode at = method.instructions.getFirst(); at != null; at = at.getNext())
                    if (at instanceof MethodInsnNode
                        && namedCall((MethodInsnNode) at, "renderItemIn2D", "func_78439_a")) {
                            InsnList code = new InsnList();
                            code.add(new VarInsnNode(Opcodes.ALOAD, 2));
                            code.add(call("heldOverlay", "(Ljava/lang/Object;)V"));
                            method.instructions.insert(at, code);
                            changed = true;
                            break;
                        }
            }
            if (player && named(method, "renderFirstPersonArm", "func_82441_a")
                && Type.getArgumentTypes(method.desc).length == 1
                && !contains(method, "firstPersonArm")) {
                InsnList code = new InsnList();
                code.add(new VarInsnNode(Opcodes.ALOAD, 1));
                code.add(call("firstPersonArm", "(Ljava/lang/Object;)V"));
                method.instructions.insert(code);
                changed = true;
            }
            if (part && method.desc.equals("(F)V") && !contains(method, "modelPart")) {
                for (AbstractInsnNode at = method.instructions.getFirst(); at != null; at = at.getNext())
                    if (at instanceof MethodInsnNode && ((MethodInsnNode) at).owner.equals("org/lwjgl/opengl/GL11")
                        && ((MethodInsnNode) at).name.equals("glCallList")) {
                            InsnList code = new InsnList();
                            code.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            code.add(new VarInsnNode(Opcodes.FLOAD, 1));
                            code.add(call("modelPart", "(Ljava/lang/Object;F)I"));
                            code.add(new InsnNode(Opcodes.SWAP));
                            method.instructions.insertBefore(at, code);
                            method.instructions.insert(at, call("modelPartEnd", "(I)V"));
                            changed = true;
                        }
            }
            if (damage && named(method, "damageEntity", "func_70665_d")
                && method.desc.endsWith(";F)V")
                && !contains(method, "ampDamage")) {
                injectDamage(method);
                changed = true;
            }
            String hook = null;
            if (stack && named(method, "copy", "func_77946_l")
                && Type.getArgumentTypes(method.desc).length == 0
                && method.desc.endsWith(";")) hook = "copied";
            if (render && named(method, "bindTexture", "func_110776_a")
                && method.desc.startsWith("(L")
                && Type.getArgumentTypes(method.desc).length == 1) hook = "texture";
            if (gui && named(method, "renderItemAndEffectIntoGUI", "func_82406_b")
                && Type.getArgumentTypes(method.desc).length == 5) hook = "inventoryOverlay";
            if (look && named(method, "setAngles", "func_70082_c") && method.desc.equals("(FF)V")) hook = "lookScale";
            if (biped && named(method, "setRotationAngles", "func_78087_a")
                && Type.getArgumentTypes(method.desc).length == 7) hook = "dragArms";
            if (held && named(method, "renderItemInFirstPerson", "func_78440_a") && method.desc.equals("(F)V"))
                hook = "dragHands";
            if (hook == null || contains(method, hook)) continue;
            if (hook.equals("dragHands")) {
                InsnList code = new InsnList();
                org.objectweb.asm.tree.LabelNode vanilla = new org.objectweb.asm.tree.LabelNode();
                code.add(new VarInsnNode(Opcodes.FLOAD, 1));
                code.add(call(hook, "(F)Z"));
                code.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFEQ, vanilla));
                code.add(new InsnNode(Opcodes.RETURN));
                code.add(vanilla);
                // ClassReader expands all existing frames; mixing in a compressed frame corrupts their offsets.
                code.add(
                    new org.objectweb.asm.tree.FrameNode(
                        Opcodes.F_NEW,
                        2,
                        new Object[] { type.name, Opcodes.FLOAT },
                        0,
                        new Object[0]));
                method.instructions.insert(code);
                changed = true;
                continue;
            }
            if (hook.equals("lookScale")) {
                InsnList code = new InsnList();
                for (int local = 1; local <= 2; local++) {
                    code.add(new VarInsnNode(Opcodes.FLOAD, local));
                    code.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    code.add(call(hook, "(Ljava/lang/Object;)F"));
                    code.add(new InsnNode(Opcodes.FMUL));
                    code.add(new VarInsnNode(Opcodes.FSTORE, local));
                }
                method.instructions.insert(code);
                changed = true;
                continue;
            }
            if (hook.equals("texture")) {
                InsnList code = new InsnList();
                code.add(new VarInsnNode(Opcodes.ALOAD, 1));
                code.add(call(hook, "(Ljava/lang/Object;)Ljava/lang/Object;"));
                code.add(new TypeInsnNode(Opcodes.CHECKCAST, Type.getArgumentTypes(method.desc)[0].getInternalName()));
                code.add(new VarInsnNode(Opcodes.ASTORE, 1));
                method.instructions.insert(code);
                changed = true;
                continue;
            }
            for (AbstractInsnNode at = method.instructions.getFirst(); at != null; at = at.getNext()) {
                if (at.getOpcode() != (hook.equals("copied") ? Opcodes.ARETURN : Opcodes.RETURN)) continue;
                InsnList code = new InsnList();
                if (hook.equals("copied")) {
                    code.add(new InsnNode(Opcodes.DUP));
                    code.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    code.add(new InsnNode(Opcodes.SWAP));
                    code.add(call(hook, "(Ljava/lang/Object;Ljava/lang/Object;)V"));
                } else if (hook.equals("dragArms")) {
                    code.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    code.add(new VarInsnNode(Opcodes.ALOAD, 7));
                    code.add(call(hook, "(Ljava/lang/Object;Ljava/lang/Object;)V"));
                } else if (hook.equals("inventoryOverlay")) {
                    code.add(new VarInsnNode(Opcodes.ALOAD, 3));
                    code.add(new VarInsnNode(Opcodes.ILOAD, 4));
                    code.add(new VarInsnNode(Opcodes.ILOAD, 5));
                    code.add(call(hook, "(Ljava/lang/Object;II)V"));
                } else {
                    code.add(new VarInsnNode(Opcodes.ALOAD, 2));
                    code.add(call(hook, "(Ljava/lang/Object;)V"));
                }
                method.instructions.insertBefore(at, code);
                changed = true;
            }
        }
        if (!changed) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        type.accept(writer);
        return writer.toByteArray();
    }

    private static void injectDamage(MethodNode method) {
        int raw = method.maxLocals++;
        InsnList init = new InsnList();
        init.add(new InsnNode(Opcodes.FCONST_0));
        init.add(new VarInsnNode(Opcodes.FSTORE, raw));
        method.instructions.insert(init);
        for (AbstractInsnNode at = method.instructions.getFirst(); at != null; at = at.getNext()) {
            if (at instanceof org.objectweb.asm.tree.FrameNode) {
                org.objectweb.asm.tree.FrameNode frame = (org.objectweb.asm.tree.FrameNode) at;
                frame.local = new java.util.ArrayList<>(frame.local);
                int slots = 0;
                for (Object local : frame.local)
                    slots += local.equals(Opcodes.LONG) || local.equals(Opcodes.DOUBLE) ? 2 : 1;
                while (slots++ < raw) frame.local.add(Opcodes.TOP);
                frame.local.add(Opcodes.FLOAT);
            }
            if (!(at instanceof MethodInsnNode)) continue;
            MethodInsnNode invoke = (MethodInsnNode) at;
            if (invoke.owner.equals("net/minecraftforge/common/ForgeHooks") && invoke.name.equals("onLivingHurt")) {
                InsnList code = new InsnList();
                code.add(new InsnNode(Opcodes.DUP));
                code.add(new VarInsnNode(Opcodes.FSTORE, raw));
                method.instructions.insert(at, code);
            }
            if ((invoke.name.equals("setHealth") || invoke.name.equals("func_70606_j")) && invoke.desc.equals("(F)V")) {
                InsnList code = new InsnList();
                code.add(new VarInsnNode(Opcodes.ALOAD, 0));
                code.add(new VarInsnNode(Opcodes.ALOAD, 1));
                code.add(new VarInsnNode(Opcodes.FLOAD, raw));
                code.add(new VarInsnNode(Opcodes.FLOAD, 2));
                code.add(call("ampDamage", "(Ljava/lang/Object;Ljava/lang/Object;FF)V"));
                code.add(new VarInsnNode(Opcodes.ALOAD, 1));
                method.instructions.insertBefore(at, code);
                AbstractInsnNode replacement = call("damagedHealth", "(Ljava/lang/Object;FLjava/lang/Object;)V");
                method.instructions.set(at, replacement);
                at = replacement;
            }
        }
    }

    private static boolean named(MethodNode method, String dev, String srg) {
        return method.name.equals(dev) || method.name.equals(srg);
    }

    private static boolean namedCall(MethodInsnNode method, String dev, String srg) {
        return method.name.equals(dev) || method.name.equals(srg);
    }

    private static boolean contains(MethodNode method, String hook) {
        for (AbstractInsnNode at = method.instructions.getFirst(); at != null; at = at.getNext())
            if (at instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) at;
                if (call.owner.equals(HOOKS) && call.name.equals(hook)) return true;
            }
        return false;
    }

    private static MethodInsnNode call(String name, String descriptor) {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, name, descriptor, false);
    }
}

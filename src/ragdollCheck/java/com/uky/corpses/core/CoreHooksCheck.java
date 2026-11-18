package com.uky.corpses.core;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;

/**
 * Verifies real 1.7.10 class bytes, stack frames and idempotence, without creating a Minecraft client or GL context.
 */
public final class CoreHooksCheck {

    public static void run() {
        String[] targets = { "net.minecraft.entity.player.EntityPlayer", "net.minecraft.client.model.ModelBiped",
            "net.minecraft.entity.Entity", "net.minecraft.item.ItemStack",
            "net.minecraft.client.renderer.entity.Render", "net.minecraft.client.renderer.entity.RenderItem",
            "net.minecraft.client.renderer.ItemRenderer", "net.minecraft.entity.EntityLivingBase",
            "net.minecraft.client.model.ModelRenderer", "net.minecraft.client.renderer.entity.RenderPlayer",
            "net.minecraft.entity.player.EntityPlayerMP", "net.minecraft.command.server.CommandTeleport" };
        CoreTransformer transformer = new CoreTransformer();
        for (String target : targets) {
            byte[] source = read(target.replace('.', '/') + ".class");
            byte[] result = transformer.transform(target, target, source);
            assert !Arrays.equals(source, result) : "missing hook in " + target;
            assert Arrays.equals(result, transformer.transform(target, target, result)) : "hook injected twice in "
                + target;
            ClassNode node = new ClassNode();
            new ClassReader(result).accept(node, 0);
            int hooks = 0;
            for (MethodNode method : node.methods) for (AbstractInsnNode at = method.instructions.getFirst(); at
                != null; at = at.getNext())
                if (at instanceof MethodInsnNode && ((MethodInsnNode) at).owner.equals("com/uky/corpses/core/Hooks")) {
                    assert !((MethodInsnNode) at).name.equals("eye") : "removed leaning eye hook remains";
                    hooks++;
                }
            assert hooks > 0;
            if (target.equals("net.minecraft.client.renderer.ItemRenderer")) {
                for (MethodNode method : node.methods) {
                    int held = 0;
                    for (AbstractInsnNode at = method.instructions.getFirst(); at != null; at = at.getNext())
                        if (at instanceof MethodInsnNode
                            && ((MethodInsnNode) at).owner.equals("com/uky/corpses/core/Hooks")
                            && ((MethodInsnNode) at).name.equals("heldOverlay")) held++;
                    if (held != 0) assert held == 1 && org.objectweb.asm.Type.getArgumentTypes(method.desc).length
                        == 4 : "weapon blood must run inside the actual Forge four-argument render path";
                }
            }
            StringWriter errors = new StringWriter();
            CheckClassAdapter.verify(new ClassReader(result), false, new PrintWriter(errors));
            assert errors.toString()
                .isEmpty() : target + ": " + errors;
            if (target.equals("net.minecraft.client.renderer.ItemRenderer")
                || target.equals("net.minecraft.client.model.ModelRenderer")) {
                new ClassLoader(CoreHooksCheck.class.getClassLoader()) {

                    void verify() {
                        defineClass(target, result, 0, result.length).getDeclaredMethods();
                    }
                }.verify();
            }
        }
        byte[] java11 = { 0, 1, 2, 3, 4, 5 };
        assert transformer
            .transform("com.github.stephengold.joltjni.Jolt", "com.github.stephengold.joltjni.Jolt", java11) == java11;
        assert transformer.transform("com.uky.corpses.core.Hooks", "com.uky.corpses.core.Hooks", java11) == java11;
        System.out
            .println("Core hooks: actual Minecraft bytecode, frames, duplicate protection and JNI exclusion passed");
    }

    private static byte[] read(String path) {
        try (InputStream in = CoreHooksCheck.class.getClassLoader()
            .getResourceAsStream(path)) {
            if (in == null) throw new AssertionError("Missing Minecraft class " + path);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int size;
            while ((size = in.read(buffer)) >= 0) bytes.write(buffer, 0, size);
            return bytes.toByteArray();
        } catch (java.io.IOException error) {
            throw new AssertionError(error);
        }
    }

    private CoreHooksCheck() {}
}

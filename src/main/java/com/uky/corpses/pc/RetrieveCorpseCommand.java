package com.uky.corpses.pc;

import java.util.List;
import java.util.UUID;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

/** /ukycorpses retrieve <UUID> [player], or <owner> lastdeath, as in Corpse storage. */
public final class RetrieveCorpseCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return com.uky.corpses.UkyCorpses.COMMAND;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/ukycorpses retrieve <corpseId> [player] | <owner> lastdeath";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 2 || args.length > 3 || !("retrieve".equals(args[0]) || "retrievecorpse".equals(args[0])))
            throw new WrongUsageException(getCommandUsage(sender));
        boolean success;
        String description;
        if (args.length == 3 && "lastdeath".equals(args[2])) {
            EntityPlayerMP owner = getPlayer(sender, args[1]);
            success = PcCorpses.retrieveLast(owner);
            description = owner.getCommandSenderName() + "'s last unlooted corpse";
        } else {
            UUID id;
            try {
                id = UUID.fromString(args[1]);
            } catch (IllegalArgumentException bad) {
                throw new WrongUsageException("Invalid corpse UUID: " + args[1]);
            }
            EntityPlayerMP target = args.length == 3 ? getPlayer(sender, args[2]) : getCommandSenderAsPlayer(sender);
            success = PcCorpses.retrieve(id, target);
            description = "corpse " + id + " to " + target.getCommandSenderName();
        }
        sender.addChatMessage(
            new ChatComponentText(success ? "Retrieved " + description : "No recoverable " + description + " found."));
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "retrieve");
        if (args.length == 2) {
            java.util.List<String> choices = new java.util.ArrayList<>();
            for (com.uky.corpses.EntityCorpse corpse : com.uky.corpses.Corpses.loaded()) choices.add(
                corpse.getUniqueID()
                    .toString());
            java.util.Collections.addAll(
                choices,
                net.minecraft.server.MinecraftServer.getServer()
                    .getAllUsernames());
            return getListOfStringsMatchingLastWord(args, choices.toArray(new String[0]));
        }
        if (args.length == 3) {
            java.util.List<String> choices = new java.util.ArrayList<>();
            java.util.Collections.addAll(
                choices,
                net.minecraft.server.MinecraftServer.getServer()
                    .getAllUsernames());
            choices.add("lastdeath");
            return getListOfStringsMatchingLastWord(args, choices.toArray(new String[0]));
        }
        return null;
    }
}

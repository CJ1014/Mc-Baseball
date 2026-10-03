package com.cj.mcbaseball.command;

import com.cj.mcbaseball.game.BaseballGame;
import com.cj.mcbaseball.game.GameManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class BaseballCommand {
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("baseball").requires(s -> s.hasPermission(2)))
                .then(((LiteralArgumentBuilder)Commands.literal("debug").executes(ctx -> {
                    if (GameManager.all().isEmpty()) {
                        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("[Baseball] No games running."), false);
                    }

                    for (BaseballGame g : GameManager.all()) {
                        String line = g.debugLine();
                        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("[Baseball] " + line), false);
                    }

                    return GameManager.all().size();
                })).then(Commands.literal("end").executes(ctx -> {
                    int n = GameManager.all().size();
                    GameManager.endAll();
                    ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("[Baseball] Ended " + n + " game(s)."), true);
                    return n;
                })))
        );
    }

    private BaseballCommand() {
    }
}

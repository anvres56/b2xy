package com.b2xy.commands;

import com.b2xy.modules.StashMover;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;

/**
 * Команда {@code .перенос} — задаёт зоны входа и выхода для StashMover.
 *
 * <p>Углы ставятся по блоку, на который игрок смотрит: смотришь на угол
 * и выполняешь {@code .перенос вход 1}, затем на второй угол —
 * {@code .перенос вход 2}. Так же задаётся зона выхода.
 */
public class MoverCommand extends Command {
    public MoverCommand() {
        super("перенос", "Зоны для переноса вещей между стешами.");
    }

    private StashMover mover() {
        return Modules.get().get(StashMover.class);
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            help();
            return SINGLE_SUCCESS;
        });

        for (String zone : new String[]{"вход", "выход"}) {
            LiteralArgumentBuilder<CommandSource> z = literal(zone);
            z.then(literal("1").executes(context -> corner(zone, 1)));
            z.then(literal("2").executes(context -> corner(zone, 2)));
            z.then(literal("очистить").executes(context -> clear()));
            builder.then(z);
        }

        builder.then(literal("старт").executes(context -> {
            StashMover m = mover();
            if (m == null) return notFound();
            m.toggle();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("помощь").executes(context -> {
            help();
            return SINGLE_SUCCESS;
        }));
    }

    private int corner(String zone, int n) {
        StashMover m = mover();
        if (m == null) return notFound();
        BlockPos pos = m.lookedAtBlock();
        if (pos == null) {
            info("Смотри на угол и повтори.");
            return SINGLE_SUCCESS;
        }
        boolean input = zone.equals("вход");
        m.setAreaCorner(input, n, pos);
        info((input ? "Вход" : "Выход") + ", угол " + n + ": §f" + pos.toShortString());
        return SINGLE_SUCCESS;
    }

    private int clear() {
        StashMover m = mover();
        if (m == null) return notFound();
        m.clearAreas();
        info("Зоны сброшены.");
        return SINGLE_SUCCESS;
    }

    private int notFound() {
        error("Модуль переноса не найден.");
        return SINGLE_SUCCESS;
    }

    private void help() {
        info(Formatting.GRAY + " .перенос вход 1 / вход 2 — углы зоны входа (смотри на блок)");
        info(" .перенос выход 1 / выход 2 — углы зоны выхода");
        info(" .перенос вход очистить — сбросить зоны");
        info(" .перенос старт — включить или выключить перенос");
    }
}

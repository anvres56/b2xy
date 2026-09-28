package com.b2xy.commands;

import com.b2xy.modules.StashSorter;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;
import net.minecraft.util.Formatting;

import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;

/**
 * Команда {@code .сорт} — всё, чем настраивается сортировщик.
 *
 * <p>Зона задаётся двумя кликами по блокам, которые игрок смотрит: первый
 * раз — угол, второй раз — второй угол. Метка вешается на сундук, на который
 * смотришь: она закрепляет за этим сундуком ту категорию, которой в нём больше.
 */
public class SorterCommand extends Command {
    public SorterCommand() {
        super("сорт", "Автосортировка стеша: зона, метки, запуск.");
    }

    private StashSorter sorter() {
        return Modules.get().get(StashSorter.class);
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            showHelp();
            return SINGLE_SUCCESS;
        });

        // .сорт зона [очистить]
        builder.then(literal("зона").executes(context -> {
            StashSorter sorter = sorter();
            if (sorter == null) return notFound();
            sorter.setSelectionCorner();
            return SINGLE_SUCCESS;
        }).then(literal("очистить").executes(context -> {
            StashSorter sorter = sorter();
            if (sorter == null) return notFound();
            int removed = sorter.clearZones();
            info(removed > 0 ? "Зон удалено: " + removed : "Зон и не было.");
            return SINGLE_SUCCESS;
        })));

        // .сорт метка [снять]
        builder.then(literal("метка").executes(context -> {
            StashSorter sorter = sorter();
            if (sorter == null) return notFound();
            sorter.pinLookedChest();
            return SINGLE_SUCCESS;
        }).then(literal("снять").executes(context -> {
            StashSorter sorter = sorter();
            if (sorter == null) return notFound();
            sorter.clearLookedPin();
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("старт").executes(context -> {
            StashSorter sorter = sorter();
            if (sorter == null) return notFound();
            sorter.startSorting();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("стоп").executes(context -> {
            StashSorter sorter = sorter();
            if (sorter == null) return notFound();
            sorter.stopSorting();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("пауза").executes(context -> {
            StashSorter sorter = sorter();
            if (sorter == null) return notFound();
            sorter.pauseSorting();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("продолжить").executes(context -> {
            StashSorter sorter = sorter();
            if (sorter == null) return notFound();
            sorter.resumeSorting();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("статус").executes(context -> {
            StashSorter sorter = sorter();
            if (sorter == null) return notFound();
            if (!sorter.isSorting()) {
                info("Сортировщик выключен. Задай зону: §7.сорт зона§f.");
            } else {
                info("Состояние: §f" + sorter.statusLine() + "§f, зон: §f" + sorter.zoneCount());
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("помощь").executes(context -> {
            showHelp();
            return SINGLE_SUCCESS;
        }));
    }

    private int notFound() {
        error("Модуль сортировщика не найден.");
        return SINGLE_SUCCESS;
    }

    private void showHelp() {
        info(Formatting.GRAY + " .сорт зона — назвать первый угол, повторить для второго");
        info(" .сорт зона очистить — снести зоны");
        info(" .сорт метка — отдать сундук под то, чего в нём больше");
        info(" .сорт метка снять — убрать метку");
        info(" .сорт старт / стоп / пауза / продолжить / статус");
    }
}

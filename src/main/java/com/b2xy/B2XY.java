package com.b2xy;

import com.b2xy.commands.ExampleCommand;
import com.b2xy.commands.SorterCommand;
import com.b2xy.hud.CrystalCpsHud;
import com.b2xy.hud.ExampleHud;
import com.b2xy.modules.AutoTnt;
import com.b2xy.modules.AutoWither;
import com.b2xy.modules.AutoXp;
import com.b2xy.modules.BepMine;
import com.b2xy.modules.BetterF5;
import com.b2xy.modules.ContainerIndex;
import com.b2xy.modules.ContainerTooltips;
import com.b2xy.modules.CrystalAuraTurbo;
import com.b2xy.modules.ElytraBounce;
import com.b2xy.modules.ElytraRecast;
import com.b2xy.modules.ElytraSwap;
import com.b2xy.modules.GrimAirPlace;
import com.b2xy.modules.GrimGlide;
import com.b2xy.modules.HandChams;
import com.b2xy.modules.HoleSnap;
import com.b2xy.modules.GrimVelocity;
import com.b2xy.modules.NewChunks;
import com.b2xy.modules.InvFix2b2t;
import com.b2xy.modules.NameTags;
import com.b2xy.modules.NoHurtCam;
import com.b2xy.modules.NoJumpDelay;
import com.b2xy.modules.NoWeb;
import com.b2xy.modules.Pitch40;
import com.b2xy.modules.Replenish;
import com.b2xy.modules.LitematicaPrinter;
import com.b2xy.modules.RocketBoost;
import com.b2xy.modules.StashSorter;
import com.b2xy.modules.VillagerRoller;
import com.b2xy.modules.ActivatedSpawnerDetector;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class B2XY extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("B2XY");
    public static final HudGroup HUD_GROUP = new HudGroup("B2XY");

    @Override
    public void onInitialize() {
        LOG.info("Initializing B2XY Addon");

        // Modules
        Modules.get().add(new AutoTnt());
        Modules.get().add(new NoJumpDelay());
        Modules.get().add(new GrimGlide());
        Modules.get().add(new GrimVelocity());
        Modules.get().add(new RocketBoost());
        Modules.get().add(new NewChunks());
        Modules.get().add(new AutoWither());
        Modules.get().add(new AutoXp());
        Modules.get().add(new NameTags());
        Modules.get().add(new BetterF5());
        Modules.get().add(new InvFix2b2t());
        Modules.get().add(new CrystalAuraTurbo());
        Modules.get().add(new ContainerIndex());
        Modules.get().add(new ContainerTooltips());
        Modules.get().add(new BepMine());
        Modules.get().add(new NoHurtCam());
        Modules.get().add(new ElytraBounce());
        Modules.get().add(new ElytraRecast());
        Modules.get().add(new Pitch40());
        Modules.get().add(new ElytraSwap());
        Modules.get().add(new LitematicaPrinter());
        Modules.get().add(new ActivatedSpawnerDetector());
        Modules.get().add(new VillagerRoller());
        Modules.get().add(new NoWeb());
        Modules.get().add(new HoleSnap());
        Modules.get().add(new Replenish());
        Modules.get().add(new GrimAirPlace());
        Modules.get().add(new StashSorter());
        Modules.get().add(new HandChams());

        // Commands
        Commands.add(new ExampleCommand());
        Commands.add(new SorterCommand());

        // HUD
        Hud.get().register(ExampleHud.INFO);
        Hud.get().register(CrystalCpsHud.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.b2xy";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("B2XY", "B2XY");
    }
}

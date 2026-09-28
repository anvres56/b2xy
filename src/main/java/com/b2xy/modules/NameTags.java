package com.b2xy.modules;

import com.b2xy.B2XY;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NameTags extends Module {
    public enum ScaleMode {
        HUD("HUD"),
        World("World");

        private final String name;

        ScaleMode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final SettingGroup sgTarget = settings.createGroup("Цели");
    private final SettingGroup sgPlayer = settings.createGroup("Игроки");
    private final SettingGroup sgRender = settings.createGroup("Отображение");

    private final Setting<Boolean> targetSelf = sgTarget.add(new BoolSetting.Builder()
        .name("self").description("Отображать собственную табличку (в третьем лице).").defaultValue(false).build());
    private final Setting<Boolean> targetMonsters = sgTarget.add(new BoolSetting.Builder()
        .name("monsters").description("Отображать таблички над враждебными мобами.").defaultValue(false).build());
    private final Setting<Boolean> targetAnimals = sgTarget.add(new BoolSetting.Builder()
        .name("animals").description("Отображать таблички над мирными мобами.").defaultValue(false).build());
    private final Setting<Boolean> targetPlayers = sgTarget.add(new BoolSetting.Builder()
        .name("players").description("Отображать таблички над игроками.").defaultValue(true).build());
    private final Setting<Boolean> targetItems = sgTarget.add(new BoolSetting.Builder()
        .name("items").description("Отображать таблички над выпавшими предметами.").defaultValue(false).build());

    private final Setting<Boolean> playerPing = sgPlayer.add(new BoolSetting.Builder()
        .name("show-ping").description("Отображать пинг.").defaultValue(true).build());
    private final Setting<Boolean> playerHealth = sgPlayer.add(new BoolSetting.Builder()
        .name("show-health").description("Отображать здоровье.").defaultValue(true).build());
    private final Setting<Boolean> playerArmor = sgPlayer.add(new BoolSetting.Builder()
        .name("show-armor").description("Отображать броню.").defaultValue(true).build());
    private final Setting<Boolean> playerItems = sgPlayer.add(new BoolSetting.Builder()
        .name("show-items").description("Отображать предметы в руках.").defaultValue(true).build());
    private final Setting<Boolean> playerSelf = sgPlayer.add(new BoolSetting.Builder()
        .name("show-self").description("Отображать себя (вместе с целью Self).").defaultValue(true).build());
    private final Setting<Boolean> playerInvisible = sgPlayer.add(new BoolSetting.Builder()
        .name("show-invisible").description("Отображать невидимых игроков.").defaultValue(false).build());
    private final Setting<Boolean> playerPrefix = sgPlayer.add(new BoolSetting.Builder()
        .name("show-prefix").description("Отображать привилегию из команды скора.").defaultValue(true).build());

    private final Setting<ScaleMode> scaleMode = sgRender.add(new EnumSetting.Builder<ScaleMode>()
        .name("scale-mode").description("HUD - масштаб с ограничением, World - во всём мире.")
        .defaultValue(ScaleMode.HUD).build());
    private final Setting<Boolean> friendColor = sgRender.add(new BoolSetting.Builder()
        .name("friend-color").description("Подсвечивать друзей зелёным цветом.").defaultValue(true).build());
    private final Setting<Boolean> hideItemsByDistance = sgRender.add(new BoolSetting.Builder()
        .name("hide-items-by-distance").description("Скрывать броню и предметы вдали.").defaultValue(true).build());
    private final Setting<Double> scale = sgRender.add(new DoubleSetting.Builder()
        .name("scale").description("Масштаб табличек.").defaultValue(1.0).min(0.3).max(2.0).build());
    private final Setting<Double> maxRange = sgRender.add(new DoubleSetting.Builder()
        .name("range").description("Максимальная дальность отрисовки.")
        .defaultValue(64.0).min(8.0).max(128.0)
        .visible(() -> scaleMode.get() == ScaleMode.HUD)
        .build());

    private final Map<UUID, Float> itemFade = new HashMap<>();

    private static final Color COLOR_SEP = new Color(255, 255, 255, 80);
    private static final Color COLOR_WHITE = new Color(227, 227, 227);
    private static final Color COLOR_COUNT = new Color(255, 255, 255, 184);
    private static final Color COLOR_ACCENT = new Color(74, 214, 160);
    private static final Color COLOR_FRIEND = new Color(66, 245, 149);
    private static final Color COLOR_FILL = new Color(19, 24, 22, 214);
    private static final Color COLOR_BORDER = new Color(227, 227, 227, 10);
    private static final Color COLOR_FILL_FRIEND = new Color(24, 46, 35, 214);
    private static final Color COLOR_BORDER_FRIEND = new Color(179, 232, 204, 10);

    private static final Map<Character, Character> DECOR_CHARS = new HashMap<>();
    private static final Map<Character, String> DECOR_RANKS = new HashMap<>();

    static {
        DECOR_CHARS.put('\u1D00', 'a'); // ᴀ
        DECOR_CHARS.put('\u0280', 'b'); // ʙ
        DECOR_CHARS.put('\u1D04', 'c'); // ᴄ
        DECOR_CHARS.put('\u1D05', 'd'); // ᴅ
        DECOR_CHARS.put('\u1D07', 'e'); // ᴇ
        DECOR_CHARS.put('\ua7B0', 'F'); // ꜰ
        DECOR_CHARS.put('\u0262', 'F'); // ғ
        DECOR_CHARS.put('\u0261', 'g'); // ɢ
        DECOR_CHARS.put('\u029C', 'h'); // ʜ
        DECOR_CHARS.put('\u026A', 'i'); // ɪ
        DECOR_CHARS.put('\u1D0A', 'j'); // ᴊ
        DECOR_CHARS.put('\u1D0B', 'k'); // ᴋ
        DECOR_CHARS.put('\u029F', 'l'); // ʟ
        DECOR_CHARS.put('\u1D0D', 'm'); // ᴍ
        DECOR_CHARS.put('\u0274', 'n'); // ɴ
        DECOR_CHARS.put('\u1D0F', 'o'); // ᴏ
        DECOR_CHARS.put('\u1D18', 'p'); // ᴘ
        DECOR_CHARS.put('\u01EB', 'q'); // ǫ
        DECOR_CHARS.put('\u0280', 'r'); // ʀ
        DECOR_CHARS.put('\uA7A1', 's'); // ꜱ
        DECOR_CHARS.put('\u1D1B', 't'); // ᴛ
        DECOR_CHARS.put('\u1D1C', 'u'); // ᴜ
        DECOR_CHARS.put('\u1D20', 'v'); // ᴠ
        DECOR_CHARS.put('\u1D21', 'w'); // ᴡ
        DECOR_CHARS.put('x', 'x');
        DECOR_CHARS.put('\u028F', 'y'); // ʏ
        DECOR_CHARS.put('\u1D22', 'z'); // ᴢ

        DECOR_RANKS.put('\u26A1', ""); // ⚡
        DECOR_RANKS.put('\uA500', "PLAYER"); // ꔀ
        DECOR_RANKS.put('\uA504', "HERO"); // ꔄ
        DECOR_RANKS.put('\uA508', "TITAN"); // ꔈ
        DECOR_RANKS.put('\uA512', "AVENGER"); // ꔒ
        DECOR_RANKS.put('\uA516', "OVERLORD"); // ꔖ
        DECOR_RANKS.put('\uA520', "MAGISTER"); // ꔠ
        DECOR_RANKS.put('\uA524', "IMPERATOR"); // ꔤ
        DECOR_RANKS.put('\uA528', "DRAGON"); // ꔨ
        DECOR_RANKS.put('\uA532', "BULL"); // ꔲ
        DECOR_RANKS.put('\uA536', "TIGER"); // ꔶ
        DECOR_RANKS.put('\uA544', "DRACULA"); // ꕄ
        DECOR_RANKS.put('\uA556', "BUNNY"); // ꕖ
        DECOR_RANKS.put('\uA548', "COBRA"); // ꕈ
        DECOR_RANKS.put('\uA540', "HYDRA"); // ꕀ
        DECOR_RANKS.put('\uA552', "RABBIT"); // ꕒ
        DECOR_RANKS.put('\uA541', "GOD"); // ꕁ
        DECOR_RANKS.put('\uA502', "GOD"); // ꔂ
        DECOR_RANKS.put('\uA518', "GOD"); // ꔸ
        DECOR_RANKS.put('\uA560', "D.HELPER"); // ꕠ
        DECOR_RANKS.put('\uA509', "HELPER"); // ꔉ
        DECOR_RANKS.put('\uA513', "ML.MODER"); // ꔓ
        DECOR_RANKS.put('\uA517', "MODER"); // ꔗ
        DECOR_RANKS.put('\uA521', "MODER+"); // ꔡ
        DECOR_RANKS.put('\uA525', "ST.MODER"); // ꔥ
        DECOR_RANKS.put('\uA529', "GL.MODER"); // ꔩ
        DECOR_RANKS.put('\uA533', "ML.ADMIN"); // ꔳ
        DECOR_RANKS.put('\uA537', "ADMIN"); // ꔷ
        DECOR_RANKS.put('\uA501', "MEDIA"); // ꔁ
        DECOR_RANKS.put('\uA557', "SPONSOR"); // ꕗ
        DECOR_RANKS.put('\uA545', "VAMPIRE"); // ꕅ
        DECOR_RANKS.put('\uA506', "VAMPIRE"); // ꔆ
        DECOR_RANKS.put('\uA549', "PEGAS"); // ꕉ
        DECOR_RANKS.put('\uA553', "GHOST"); // ꕓ
        DECOR_RANKS.put('\uA522', "D.ST.MODER"); // ꔢ
        DECOR_RANKS.put('\uA505', "YT"); // ꔅ
    }

    private static String decorate(String s) {
        if (s == null || s.isEmpty()) return s;

        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) sb.append(DECOR_CHARS.getOrDefault(c, c));
        String out = sb.toString();

        StringBuilder sb2 = new StringBuilder(out.length());
        for (char c : out.toCharArray()) {
            String rep = DECOR_RANKS.get(c);
            if (rep != null) sb2.append(rep);
            else sb2.append(c);
        }
        out = sb2.toString();

        out = out.replace('\u21AC', '>'); // ↬
        out = out.replace('\u21AB', '<'); // ↫
        out = out.replace('\u25C4', '<'); // ◄
        out = out.replace('\u25BA', '>'); // ►
        out = out.replace('\u25C0', '<'); // ◀
        out = out.replace('\u25B6', '>'); // ▶
        out = out.replace('\u25C2', '<'); // ◂
        out = out.replace('\u25B8', '>'); // ▸
        out = out.replace('\u25C1', '<'); // ◁
        out = out.replace('\u25B7', '>'); // ▷
        out = out.replace('\u2039', '<'); // ‹
        out = out.replace('\u203A', '>'); // ›
        out = out.replace("\u26A1", ""); // ⚡
        out = out.replace("\u2607", ""); // ☇
        out = out.replace("\u2313", ""); // ⌓
        out = out.replace("\u2728", ""); // ✨
        out = out.replace("\u2B50", ""); // ⭐
        out = out.replace("\uD83C\uDF1F", ""); // 🌟
        out = out.replace("\uD83D\uDCAB", ""); // 💫
        out = out.replace("\uD83D\uDD25", ""); // 🔥
        out = out.replace("\uD83D\uDC8E", ""); // 💎
        out = out.replace("\u2694", ""); // ⚔
        out = out.replace("\u2694\uFE0F", ""); // ⚔️
        out = out.replace("\uD83D\uDEE1", ""); // 🛡
        out = out.replace("\uD83D\uDEE1\uFE0F", ""); // 🛡️
        return out.replace("\u269C", ""); // ⚜
    }

    public NameTags() {
        super(B2XY.CATEGORY, "name-tags", "Расширенное отображение информации над сущностями");
    }

    @Override
    public void onDeactivate() {
        itemFade.clear();
        super.onDeactivate();
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.world == null || mc.player == null) return;

        boolean self = targetSelf.get() && playerSelf.get();
        boolean players = targetPlayers.get();
        boolean monsters = targetMonsters.get();
        boolean animals = targetAnimals.get();
        boolean items = targetItems.get();
        boolean showInvisible = playerInvisible.get();

        if (!self && !players && !monsters && !animals && !items) return;

        float tickDelta = event.tickDelta;
        boolean firstPerson = mc.options.getPerspective().isFirstPerson();

        List<Entity> list = new ArrayList<>();

        if (self || players) {
            for (PlayerEntity p : mc.world.getPlayers()) {
                boolean own = p == mc.player;
                boolean showOther = players && !p.isSpectator() && (showInvisible || !p.isInvisible());
                if (own ? (self && !firstPerson) : showOther) {
                    if (scaleMode.get() == ScaleMode.HUD) {
                        if (PlayerUtils.distanceToCamera(p) <= maxRange.get()) list.add(p);
                    } else {
                        list.add(p);
                    }
                }
            }
        }

        if (monsters || animals || items) {
            for (Entity e : mc.world.getEntities()) {
                if (e instanceof PlayerEntity) continue;
                if (e.isRemoved()) continue;
                boolean inRange = PlayerUtils.distanceToCamera(e) <= maxRange.get();
                if (scaleMode.get() == ScaleMode.HUD && !inRange) continue;

                if (e instanceof ItemEntity ie) {
                    if (items) list.add(e);
                } else if (e instanceof LivingEntity le && !le.isDead()) {
                    SpawnGroup group = e.getType().getSpawnGroup();
                    if (monsters && group == SpawnGroup.MONSTER) list.add(e);
                    else if (animals && isPassive(group)) list.add(e);
                }
            }
        }

        list.sort(Comparator.comparingDouble((Entity e) -> PlayerUtils.distanceToCamera(e)).reversed());

        for (Entity e : list) {
            Vector3d pos = getPos(e, tickDelta);
            double dist = Math.max(PlayerUtils.distanceToCamera(e), 0.5);

            double factor;
            if (scaleMode.get() == ScaleMode.HUD) {
                factor = Math.max(0.55, 5.0 / dist);
            } else {
                factor = 0.98 / (1.0 + dist * 0.015);
            }

            if (NametagUtils.to2D(pos, factor, false)) {
                NametagUtils.begin(pos, event.drawContext);

                if (e instanceof PlayerEntity p) renderPlayer(event, p, factor);
                else if (e instanceof ItemEntity ie) renderItem(event, ie, factor);
                else if (e instanceof LivingEntity le) renderMob(event, le, factor);

                NametagUtils.end(event.drawContext);
            }
        }
    }

    private void renderPlayer(Render2DEvent event, PlayerEntity player, double factor) {
        double s = scale.get();
        DrawContext drawContext = event.drawContext;
        TextRenderer text = TextRenderer.get();

        boolean showHealth = playerHealth.get();
        boolean showPing = playerPing.get();
        boolean showArmor = playerArmor.get();
        boolean showItems = playerItems.get();
        boolean showPrefix = playerPrefix.get();

        boolean friend = friendColor.get() && Friends.get().isFriend(player);
        double dist = Math.max(PlayerUtils.distanceToCamera(player), 0.5);

        String name = player.getName().getString();
        String prefix = "";
        Color prefixColor = COLOR_COUNT;
        if (showPrefix) {
            Team team = mc.world.getScoreboard().getScoreHolderTeam(name);
            if (team != null) {
                Text prefixText = team.getPrefix();
                if (prefixText != null) {
                    String raw = prefixText.getString();
                    if (raw != null) {
                        String cleaned = decorate(raw);
                        String trimmed = cleaned.replaceAll("§.", "").trim();
                        if (!trimmed.isEmpty()) {
                            prefix = trimmed;
                            Color pc = findTextColor(prefixText);
                            if (pc != null) prefixColor = pc;
                        }
                    }
                }
            }
        }

        String healthText = showHealth ? String.valueOf((int) (player.getHealth() + player.getAbsorptionAmount())) : "";
        String pingText = showPing ? EntityUtils.getPing(player) + "ms" : "";

        List<ItemStack> armor = showArmor ? getArmor(player) : new ArrayList<>();
        List<ItemStack> held = showItems ? getHeld(player) : new ArrayList<>();

        float targetFade = hideItemsByDistance.get() ? (dist < 32 ? 1f : 0f) : 1f;
        float fade = itemFade.getOrDefault(player.getUuid(), targetFade);
        fade += (targetFade - fade) * 0.08f;
        fade = MathHelper.clamp(fade, 0f, 1f);
        itemFade.put(player.getUuid(), fade);
        boolean showIcons = fade > 0.01f && (!armor.isEmpty() || !held.isEmpty());

        double bodyFs = s;
        double iconsFs = 1.15 * s;

        double nameW = text.getWidth(name, true) * bodyFs;
        double prefixW = prefix.isEmpty() ? 0 : text.getWidth(prefix, true) * bodyFs;
        double prefixGap = prefix.isEmpty() ? 0 : 3 * s;
        double healthW = healthText.isEmpty() ? 0 : text.getWidth(healthText, true) * bodyFs;
        double pingW = pingText.isEmpty() ? 0 : text.getWidth(pingText, true) * bodyFs;
        double bodyH = text.getHeight(true) * bodyFs;

        double iconSize = 12 * s;
        double smallIconW = 5 * s;
        double padX = 4 * s;
        double sepGap = 5 * s;
        double iconGap = 3 * s;
        double sepH = 7 * s;

        double armorW = itemRowWidth(armor, s);
        double heldW = itemRowWidth(held, s);
        double armorWf = armorW * fade;
        double heldWf = heldW * fade;

        double w = padX + iconSize + sepGap + 1 + sepGap + prefixW + prefixGap + nameW + padX;
        if (healthW > 0) w += sepGap + 1 + sepGap + smallIconW + iconGap + healthW;
        if (pingW > 0) w += sepGap + 1 + sepGap + smallIconW + iconGap + pingW;
        if (showIcons) {
            w += sepGap + 1 + sepGap;
            if (armorWf > 0) {
                w += armorWf;
                if (heldWf > 0) w += 3 * s;
            }
            if (heldWf > 0) w += heldWf;
        }

        double baseH = bodyH + fade * (iconSize - bodyH);
        double h = baseH + 7.5 * s;
        double up = 4.0 / factor;
        double top = -h - up;
        double left = -w / 2;
        double centerY = -h / 2 - up;
        double textY = centerY - bodyH / 2;

        Color fill = friend ? COLOR_FILL_FRIEND : COLOR_FILL;
        Color border = friend ? COLOR_BORDER_FRIEND : COLOR_BORDER;

        Renderer2D renderer = Renderer2D.COLOR;
        renderer.begin();
        drawRoundedRect(renderer, left, top, w, h, 6 * s, fill, border, 0.5);

        double x = left + padX;
        x += iconSize;
        x += sepGap;
        drawVSep(x, centerY, sepH, renderer);
        x += 1 + sepGap;

        if (!prefix.isEmpty()) {
            x = renderPrefix(text, prefix, x, textY, prefixColor, bodyFs);
            x += prefixGap;
        }
        x += text.render(name, x, textY, friend ? COLOR_FRIEND : COLOR_WHITE, true);

        if (healthW > 0) {
            x += sepGap;
            drawVSep(x, centerY, sepH, renderer);
            x += 1 + sepGap;
            drawHeart(x, centerY - 0.7 * s, iconsFs, friend ? COLOR_FRIEND : COLOR_ACCENT);
            x += smallIconW + iconGap;
        }

        if (pingW > 0) {
            x += sepGap;
            drawVSep(x, centerY, sepH, renderer);
            x += 1 + sepGap;
            drawBolt(x, centerY - 0.6 * s, iconsFs, friend ? COLOR_FRIEND : COLOR_ACCENT);
            x += smallIconW + iconGap;
        }

        if (showIcons) {
            x += sepGap;
            drawVSep(x, centerY, sepH, renderer);
        }

        // В 1.21.4 Renderer2D#render принимает DrawContext, но внутри блока
        // NametagUtils.begin/end матрица уже лежит в глобальном ModelViewStack,
        // поэтому DrawContext сюда не подкладываем (иначе трансформация применится
        // дважды) - ровно так же делает сам Meteor в Nametags#drawBg.
        renderer.render(null);

        double ax = left + padX;
        double ay = centerY - iconSize / 2;
        drawAvatar(drawContext, player, ax, ay, iconSize);

        Renderer2D cutRenderer = Renderer2D.COLOR;
        cutRenderer.begin();
        double arc = 3 * s;
        double cx = ax + arc;
        double cy = ay + arc;
        fillCornerCutout(cutRenderer, ax, ay, cx, cy, arc, Math.PI, Math.PI * 1.5, fill);
        fillCornerCutout(cutRenderer, ax + iconSize, ay, ax + iconSize - arc, cy, arc, Math.PI * 1.5, Math.PI * 2, fill);
        fillCornerCutout(cutRenderer, ax, ay + iconSize, cx, ay + iconSize - arc, arc, Math.PI * 0.5, Math.PI, fill);
        fillCornerCutout(cutRenderer, ax + iconSize, ay + iconSize, ax + iconSize - arc, ay + iconSize - arc, arc, 0, Math.PI * 0.5, fill);
        cutRenderer.render(null);

        x = left + padX + iconSize + sepGap + 1 + sepGap;

        text.begin(bodyFs, false, true);
        if (!prefix.isEmpty()) {
            x = renderPrefix(text, prefix, x, textY, prefixColor, bodyFs);
            x += prefixGap;
        }
        text.render(name, x, textY, friend ? COLOR_FRIEND : COLOR_WHITE, true);
        text.end();

        if (healthW > 0) {
            x = left + padX + iconSize + sepGap + 1 + sepGap + prefixW + prefixGap + nameW + sepGap + 1 + sepGap + smallIconW + iconGap;
            text.begin(bodyFs, false, true);
            text.render(healthText, x, textY, COLOR_WHITE, true);
            text.end();
        }

        if (pingW > 0) {
            x = left + padX + iconSize + sepGap + 1 + sepGap + prefixW + prefixGap + nameW
                + (healthW > 0 ? sepGap + 1 + sepGap + smallIconW + iconGap + healthW : 0)
                + sepGap + 1 + sepGap + smallIconW + iconGap;
            text.begin(bodyFs, false, true);
            text.render(pingText, x, textY, COLOR_WHITE, true);
            text.end();
        }

        if (showIcons) {
            x = left + padX + iconSize + sepGap + 1 + sepGap + prefixW + prefixGap + nameW
                + (healthW > 0 ? sepGap + 1 + sepGap + smallIconW + iconGap + healthW : 0)
                + (pingW > 0 ? sepGap + 1 + sepGap + smallIconW + iconGap + pingW : 0)
                + sepGap + 1 + sepGap;

            if (!armor.isEmpty()) {
                renderIconRow(drawContext, armor, x, centerY - iconSize / 2, iconSize, s);
                x += armorW;
                if (!held.isEmpty()) x += 3 * s;
            }
            if (!held.isEmpty()) renderIconRow(drawContext, held, x, centerY - iconSize / 2, iconSize, s);
        }
    }

    private void renderMob(Render2DEvent event, LivingEntity entity, double factor) {
        double s = scale.get();
        TextRenderer text = TextRenderer.get();

        String name = entity.getDisplayName().getString();
        String healthText = String.valueOf((int) (entity.getHealth() + entity.getAbsorptionAmount()));

        double bodyFs = s;
        double iconsFs = 1.15 * s;

        double nameW = text.getWidth(name, true) * bodyFs;
        double healthW = text.getWidth(healthText, true) * bodyFs;
        double bodyH = text.getHeight(true) * bodyFs;

        double smallIconW = 5 * s;
        double padX = 4 * s;
        double sepGap = 5 * s;
        double iconGap = 3 * s;
        double sepH = 7 * s;

        double w = padX + nameW + sepGap + 1 + sepGap + smallIconW + iconGap + healthW + padX;
        double h = bodyH + 7.5 * s;
        double up = 4.0 / factor;
        double top = -h - up;
        double left = -w / 2;
        double centerY = -h / 2 - up;
        double textY = centerY - bodyH / 2;

        Renderer2D renderer = Renderer2D.COLOR;
        renderer.begin();
        drawRoundedRect(renderer, left, top, w, h, 6 * s, COLOR_FILL, COLOR_BORDER, 0.5);
        double x = left + padX + nameW + sepGap;
        drawVSep(x, centerY, sepH, renderer);
        x += 1 + sepGap;
        drawHeart(x, centerY - 0.7 * s, iconsFs, COLOR_ACCENT);
        renderer.render(null);

        text.begin(bodyFs, false, true);
        text.render(name, left + padX, textY, COLOR_WHITE, true);
        text.end();

        text.begin(bodyFs, false, true);
        text.render(healthText, left + padX + nameW + sepGap + 1 + sepGap + smallIconW + iconGap, textY, COLOR_WHITE, true);
        text.end();
    }

    private void renderItem(Render2DEvent event, ItemEntity entity, double factor) {
        ItemStack stack = entity.getStack();
        if (stack.isEmpty()) return;

        double s = scale.get();
        DrawContext drawContext = event.drawContext;
        TextRenderer text = TextRenderer.get();

        String name = stack.getName().getString();
        int count = stack.getCount();
        String countText = count > 1 ? " x" + count : "";

        double bodyFs = s;
        double nameW = text.getWidth(name, true) * bodyFs;
        double countW = countText.isEmpty() ? 0 : text.getWidth(countText, true) * bodyFs;
        double bodyH = text.getHeight(true) * bodyFs;

        double iconSize = 12 * s;
        double padX = 4 * s;
        double iconGap = 3 * s;

        double w = padX + iconSize + iconGap + nameW + countW + padX;
        double h = Math.max(bodyH, iconSize) + 7.5 * s;
        double up = 4.0 / factor;
        double top = -h - up;
        double left = -w / 2;
        double centerY = -h / 2 - up;

        Renderer2D renderer = Renderer2D.COLOR;
        renderer.begin();
        drawRoundedRect(renderer, left, top, w, h, 6 * s, COLOR_FILL, COLOR_BORDER, 0.5);
        renderer.render(null);

        RenderUtils.drawItem(drawContext, stack, (int) (left + padX), (int) (centerY - iconSize / 2), (float) (iconSize / 16.0), true);

        text.begin(bodyFs, false, true);
        double x = left + padX + iconSize + iconGap;
        x += text.render(name, x, centerY - bodyH / 2, COLOR_WHITE, true);
        if (!countText.isEmpty()) x += text.render(countText, x, centerY - bodyH / 2, COLOR_COUNT, true);
        text.end();
    }

    private double renderPrefix(TextRenderer text, String prefix, double x, double y, Color baseColor, double fs) {
        double sx = x;
        int i = 0;
        while (i < prefix.length()) {
            RankStyle rs = matchRank(prefix, i);
            if (rs != null) {
                for (int j = 0; j < rs.len && i + j < prefix.length(); j++) {
                    sx += text.render(String.valueOf(prefix.charAt(i + j)), sx, y, rs.color(j, baseColor), true);
                }
                i += rs.len;
            } else {
                sx += text.render(String.valueOf(prefix.charAt(i)), sx, y, baseColor, true);
                i++;
            }
        }
        return sx;
    }

    private void renderIconRow(DrawContext drawContext, List<ItemStack> items, double x, double y, double size, double s) {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                RenderUtils.drawItem(drawContext, stack, (int) x, (int) y, (float) (size / 16.0), true);
            }
            x += size + 2 * s;
        }
    }

    private void drawAvatar(DrawContext drawContext, PlayerEntity player, double x, double y, double size) {
        if (!(player instanceof AbstractClientPlayerEntity ap)) return;
        // В 1.21.4 у AbstractClientPlayerEntity есть getSkinTextures() (не getSkin()),
        // а SkinTextures - плоская запись с полем texture() (не body().texturePath()).
        Identifier skin = ap.getSkinTextures().texture();
        // В 1.21.4 нет RenderPipelines: DrawContext#drawTexture принимает
        // Function<Identifier, RenderLayer>, и RenderLayer#getGuiTextured - это
        // ровно тот слой, который в 1.21.5+ стал пайплайном GUI_TEXTURED
        // (сверено: javap -c RenderLayer#getGuiTextured зовёт статическое
        // поле GUI_TEXTURED : Function).
        drawContext.drawTexture(RenderLayer::getGuiTextured, skin, (int) x, (int) y,
            8f, 8f, (int) size, (int) size, 8, 8, 64, 64);
    }

    private void drawVSep(double x, double cy, double height, Renderer2D renderer) {
        renderer.quad(x, cy - height / 2, x + 1, cy + height / 2, COLOR_SEP);
    }

    private void drawHeart(double cx, double cy, double fs, Color color) {
        Renderer2D renderer = Renderer2D.COLOR;
        double size = 6.0 * fs;
        double r = size * 0.28;
        double cxo = size * 0.26;
        double cyo = size * 0.06;
        double tipY = cy + size * 0.44;
        renderer.triangle(cx - cxo - r, cy - cyo + r * 0.9, cx + cxo + r, cy - cyo + r * 0.9, cx, tipY, color);
        fillCircle(renderer, cx - cxo, cy - cyo, r, color);
        fillCircle(renderer, cx + cxo, cy - cyo, r, color);
    }

    private void drawBolt(double cx, double cy, double fs, Color color) {
        Renderer2D renderer = Renderer2D.COLOR;
        double size = 6.0 * fs;
        double h = size * 0.5;
        double wsize = size * 0.5;
        double[][] p = {
            {cx + wsize * 0.30, cy - h},
            {cx - wsize * 0.18, cy - h * 0.10},
            {cx + wsize * 0.10, cy - h * 0.10},
            {cx - wsize * 0.40, cy + h},
            {cx - wsize * 0.10, cy + h * 0.12},
            {cx + wsize * 0.38, cy + h * 0.12},
        };
        for (int i = 0; i < p.length; i++) {
            int j = (i + 1) % p.length;
            renderer.triangle(cx, cy, p[i][0], p[i][1], p[j][0], p[j][1], color);
        }
    }

    private static void fillCircle(Renderer2D renderer, double cx, double cy, double r, Color color) {
        int seg = 12;
        for (int i = 0; i < seg; i++) {
            double a1 = Math.PI * 2 * i / seg;
            double a2 = Math.PI * 2 * (i + 1) / seg;
            renderer.triangle(cx, cy, cx + r * Math.cos(a1), cy + r * Math.sin(a1), cx + r * Math.cos(a2), cy + r * Math.sin(a2), color);
        }
    }

    private static void drawRoundedRect(Renderer2D renderer, double x, double y, double w, double h, double rad, Color fill, Color border, double borderWidth) {
        if (border != null && border.a > 0) {
            fillRoundedQuad(renderer, x - borderWidth, y - borderWidth, w + borderWidth * 2, h + borderWidth * 2, rad + borderWidth, border);
        }
        fillRoundedQuad(renderer, x, y, w, h, rad, fill);
    }

    private static void fillRoundedQuad(Renderer2D renderer, double x, double y, double w, double h, double rad, Color color) {
        if (rad <= 0.01) {
            renderer.quad(x, y, x + w, y + h, color);
            return;
        }
        renderer.quad(x, y + rad, x + w, y + h - rad, color);
        renderer.quad(x + rad, y, x + w - rad, y + rad, color);
        renderer.quad(x + rad, y + h - rad, x + w - rad, y + h, color);
        fillArc(renderer, x + rad, y + rad, rad, Math.PI, Math.PI * 1.5, color);
        fillArc(renderer, x + w - rad, y + rad, rad, Math.PI * 1.5, Math.PI * 2, color);
        fillArc(renderer, x + rad, y + h - rad, rad, Math.PI * 0.5, Math.PI, color);
        fillArc(renderer, x + w - rad, y + h - rad, rad, 0, Math.PI * 0.5, color);
    }

    private static void fillArc(Renderer2D renderer, double cx, double cy, double rad, double a1, double a2, Color color) {
        int seg = 6;
        double step = (a2 - a1) / seg;
        for (int i = 0; i < seg; i++) {
            double a = a1 + step * i;
            double b = a1 + step * (i + 1);
            renderer.triangle(cx, cy, cx + rad * Math.cos(a), cy + rad * Math.sin(a), cx + rad * Math.cos(b), cy + rad * Math.sin(b), color);
        }
    }

    private static void fillCornerCutout(Renderer2D renderer, double fx, double fy, double cx, double cy, double rad, double a1, double a2, Color color) {
        if (rad <= 0.01) return;
        int seg = 8;
        double step = (a2 - a1) / seg;
        for (int i = 0; i < seg; i++) {
            double a = a1 + step * i;
            double b = a1 + step * (i + 1);
            renderer.triangle(fx, fy,
                cx + rad * Math.cos(a), cy + rad * Math.sin(a),
                cx + rad * Math.cos(b), cy + rad * Math.sin(b), color);
        }
    }

    private static Color findTextColor(Text text) {
        TextColor color = text.getStyle().getColor();
        if (color != null) return new Color(color.getRgb());
        for (Text sibling : text.getSiblings()) {
            Color c = findTextColor(sibling);
            if (c != null) return c;
        }
        return null;
    }

    private static Vector3d getPos(Entity entity, float tickDelta) {
        // В 1.21.11 поле называлось lastX/lastY/lastZ, в 1.21.4 - prevX/prevY/prevZ,
        // и оно приватно для внешнего кода. Entity#getLerpedPos(float) делает ровно
        // то же самое: MathHelper.lerp(tickDelta, prevX, getX()) и т.д. (сверено
        // javap -c net.minecraft.entity.Entity#getLerpedPos).
        Vec3d lerped = entity.getLerpedPos(tickDelta);
        return new Vector3d(lerped.x, lerped.y + entity.getHeight() + 0.3, lerped.z);
    }

    private static List<ItemStack> getArmor(PlayerEntity player) {
        List<ItemStack> items = new ArrayList<>();
        ItemStack head = player.getEquippedStack(EquipmentSlot.HEAD);
        ItemStack chest = player.getEquippedStack(EquipmentSlot.CHEST);
        ItemStack legs = player.getEquippedStack(EquipmentSlot.LEGS);
        ItemStack feet = player.getEquippedStack(EquipmentSlot.FEET);
        if (!head.isEmpty()) items.add(head);
        if (!chest.isEmpty()) items.add(chest);
        if (!legs.isEmpty()) items.add(legs);
        if (!feet.isEmpty()) items.add(feet);
        return items;
    }

    private static List<ItemStack> getHeld(PlayerEntity player) {
        List<ItemStack> items = new ArrayList<>();
        ItemStack off = player.getOffHandStack();
        ItemStack main = player.getMainHandStack();
        if (!off.isEmpty()) items.add(off);
        if (!main.isEmpty()) items.add(main);
        return items;
    }

    private static double itemRowWidth(List<ItemStack> items, double s) {
        if (items.isEmpty()) return 0;
        return items.size() * (12 * s) + (items.size() - 1) * (2 * s);
    }

    private static boolean isPassive(SpawnGroup group) {
        return group == SpawnGroup.CREATURE
            || group == SpawnGroup.AMBIENT
            || group == SpawnGroup.AXOLOTLS
            || group == SpawnGroup.WATER_CREATURE
            || group == SpawnGroup.WATER_AMBIENT
            || group == SpawnGroup.UNDERGROUND_WATER_CREATURE;
    }

    private static RankStyle matchRank(String prefix, int index) {
        for (RankStyle rs : RANK_STYLES) {
            if (index + rs.len <= prefix.length() && prefix.regionMatches(true, index, rs.name, 0, rs.len)) {
                return rs;
            }
        }
        return null;
    }

    private record RankStyle(String name, int len, float r0, float g0, float b0, float r1, float g1, float b1, float gradLen) {
        Color color(int index, Color base) {
            float t = Math.min(1.0f, gradLen > 0.0f ? index / gradLen : 0.0f);
            int r = Math.max(0, Math.min(255, Math.round((r0 + (r1 - r0) * t) * 255)));
            int g = Math.max(0, Math.min(255, Math.round((g0 + (g1 - g0) * t) * 255)));
            int b = Math.max(0, Math.min(255, Math.round((b0 + (b1 - b0) * t) * 255)));
            return new Color(r, g, b, base.a);
        }
    }

    private static RankStyle rank(String name, float r, float g, float b, float len) {
        return new RankStyle(name, name.length(), r * 0.85f, g * 0.85f, b * 0.85f, r, g, b, len);
    }

    private static RankStyle rank(String name, float r0, float g0, float b0, float r1, float g1, float b1, float len) {
        return new RankStyle(name, name.length(), r0, g0, b0, r1, g1, b1, len);
    }

    private static final List<RankStyle> RANK_STYLES = new ArrayList<>(List.of(
        rank("D.ST.MODER", 0.17254902f, 0.22745098f, 0.8901961f, 6.0f),
        rank("IMPERATOR", 0.85f, 0.1f, 0.1f, 1.0f, 0.4f, 0.4f, 7.0f),
        rank("TITAN", 1.0f, 0.9f, 0.35f, 0.95f, 0.6f, 0.0f, 5.0f),
        rank("OVERLORD", 0.0f, 0.88f, 0.95f, 0.32f, 1.0f, 1.0f, 6.0f),
        rank("MAGISTER", 1.0f, 0.78f, 0.25f, 0.95f, 0.58f, 0.05f, 7.0f),
        rank("ML.ADMIN", 0.15f, 0.85f, 0.75f, 0.0f, 0.55f, 0.6f, 3.0f),
        rank("ML.MODER", 0.17254902f, 0.22745098f, 0.8901961f, 6.0f),
        rank("GL.MODER", 0.275f, 0.235f, 0.569f, 6.0f),
        rank("ST.MODER", 0.17254902f, 0.22745098f, 0.8901961f, 6.0f),
        rank("D.HELPER", 0.95f, 0.6f, 0.0f, 6.0f),
        rank("MODER+", 0.275f, 0.235f, 0.569f, 4.0f),
        rank("AVENGER", 0.15f, 1.0f, 0.15f, 5.0f),
        rank("SPONSOR", 0.9f, 0.7f, 0.0f, 5.0f),
        rank("DRACULA", 0.549f, 0.1f, 0.1f, 5.0f),
        rank("VAMPIRE", 0.549f, 0.1f, 0.1f, 5.0f),
        rank("PLAYER", 0.3f, 0.3f, 0.3f, 0.6f, 0.6f, 0.6f, 4.0f),
        rank("DRAGON", 0.6f, 0.2f, 0.9f, 0.9f, 0.55f, 1.0f, 4.0f),
        rank("RABBIT", 0.8f, 0.8f, 0.8f, 4.0f),
        rank("HELPER", 0.55f, 0.9f, 1.0f, 0.3f, 0.7f, 0.95f, 4.0f),
        rank("TIGER", 0.9f, 0.6f, 0.0f, 3.0f),
        rank("COBRA", 0.12f, 0.8f, 0.2f, 0.55f, 1.0f, 0.35f, 3.0f),
        rank("HYDRA", 0.156f, 0.365f, 0.012f, 3.0f),
        rank("BUNNY", 0.2f, 0.2f, 0.2f, 3.0f),
        rank("MODER", 0.45f, 0.72f, 0.95f, 0.2f, 0.5f, 0.85f, 4.0f),
        rank("ADMIN", 0.22f, 0.01f, 0.01f, 0.72f, 0.16f, 0.16f, 4.0f),
        rank("DEVELOPER", 0.32f, 0.03f, 0.03f, 0.78f, 0.12f, 0.12f, 8.0f),
        rank("MEDIA", 0.404f, 0.141f, 0.749f, 3.0f),
        rank("PEGAS", 0.8f, 0.45f, 0.0f, 1.0f, 0.75f, 0.15f, 3.0f),
        rank("GHOST", 0.6f, 0.6f, 0.6f, 3.0f),
        rank("HERO", 0.55f, 0.78f, 1.0f, 0.2f, 0.45f, 0.95f, 3.0f),
        rank("TIKTOK", 0.08f, 0.08f, 0.08f, 0.46f, 0.46f, 0.46f, 5.0f),
        rank("CUSTOM", 0.05f, 0.17f, 0.56f, 0.23f, 0.42f, 0.91f, 5.0f),
        rank("YOUTUBE+", 0.72f, 0.06f, 0.06f, 1.0f, 0.22f, 0.22f, 6.0f),
        rank("YOUTUBE", 0.7f, 0.05f, 0.05f, 0.95f, 0.18f, 0.18f, 5.0f),
        rank("SAKURA", 0.74f, 0.12f, 0.58f, 1.0f, 0.46f, 0.84f, 5.0f),
        rank("HALLOWEEN", 0.78f, 0.22f, 0.0f, 1.0f, 0.66f, 0.12f, 6.0f),
        rank("WINTER", 0.08f, 0.62f, 0.92f, 0.62f, 0.94f, 1.0f, 6.0f),
        rank("SUMMER", 0.9f, 0.66f, 0.08f, 1.0f, 0.92f, 0.35f, 6.0f),
        rank("PHANTOM", 0.75f, 0.08f, 0.08f, 1.0f, 0.3f, 0.3f, 5.0f),
        rank("KRATOS", 0.36f, 0.08f, 0.62f, 0.86f, 0.36f, 1.0f, 6.0f),
        rank("PHOENIX", 0.88f, 0.62f, 0.08f, 1.0f, 0.92f, 0.3f, 6.0f),
        rank("GUARDIAN", 0.0f, 0.62f, 0.22f, 0.26f, 0.95f, 0.48f, 6.0f),
        rank("PRINCE", 1.0f, 0.85f, 0.2f, 0.95f, 0.55f, 0.1f, 6.0f),
        rank("SPECTATOR", 0.45f, 0.45f, 0.45f, 0.78f, 0.78f, 0.78f, 6.0f),
        rank("INTERN", 0.08f, 0.48f, 0.82f, 0.45f, 0.86f, 1.0f, 6.0f),
        rank("CURATOR", 0.66f, 0.74f, 0.2f, 0.88f, 0.94f, 0.34f, 6.0f),
        rank("BULL", 0.7f, 0.15f, 0.7f, 2.0f),
        rank("GOD", 0.95f, 0.85f, 0.5f, 6.0f),
        rank("YT", 0.722f, 0.027f, 0.086f, 1.0f, 1.0f, 1.0f, 1.0f)
    ));

    static {
        RANK_STYLES.sort(Comparator.comparingInt((RankStyle r) -> r.len).reversed());
    }
}
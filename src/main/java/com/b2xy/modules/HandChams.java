package com.b2xy.modules;

import com.b2xy.B2XY;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/**
 * HandChams (порт из CateLean) — тонировка руки от первого лица.
 *
 * <p>Оригинал отменяет ванильный рендер руки на {@code GameRenderer.renderHand}
 * и рисует руку заново через собственный GPU-конвейер: свой
 * {@code RenderPipeline}, батчер вершин, кадровые буферы под свечение и свои
 * шейдеры. В 1.21.11 рука рисуется отложенно: {@code HeldItemRenderer} собирает
 * слои модели, и цвет каждой вершины берётся из массива тинтов
 * {@code ItemRenderState.LayerRenderState}.
 *
 * <p>Мы переопределяем эти тинты на время рендера руки. Свет и прозрачность
 * поэтому работают на ванильных пайплайнах — без чужих шейдеров и кадровых
 * буферов, которые в чужом аддоне ломают рендер при смене версии.
 *
 * <p>Чего здесь нет: свечение (bloom) и «стекло» у оригинала идут через
 * отдельные проходы по кадровым буферам с собственными шейдерами. У нас они
 * сделаны как усиление цвета и рамка — визуально не то же самое.
 *
 * <p>Действует только на руку и предмет в ней, не на весь мир: проход
 * открывается миксином {@code HeldItemRenderer.renderItem} и закрывается
 * сразу после.
 */
public class HandChams extends Module {
    private final SettingGroup sgMain = settings.getDefaultGroup();
    private final SettingGroup sgGlow = settings.createGroup("Свечение");
    private final SettingGroup sgBorder = settings.createGroup("Рамка");

    private final Setting<HandsMode> mode = sgMain.add(new EnumSetting.Builder<HandsMode>()
        .name("режим")
        .description("Как красится рука. Зеркало — вторая рука зеркалит первую; "
            + "двойной — рисуются обе руки сразу; камуфляж — цвета плавно "
            + "перетекают по времени; свечение — как «зеркало» плюс яркость.")
        .defaultValue(HandsMode.DEFAULT)
        .build());

    private final Setting<ColorMode> colorMode = sgMain.add(new EnumSetting.Builder<ColorMode>()
        .name("режим-цвета")
        .description("Один цвет, плавный перелив по спектру, два цвета, два "
            + "цвета по очереди или бегущая «телевизионная» полоса.")
        .defaultValue(ColorMode.SINGLE)
        .build());

    private final Setting<SettingColor> color = sgMain.add(new ColorSetting.Builder()
        .name("цвет")
        .defaultValue(new Color(255, 0, 0, 255))
        .visible(() -> colorMode.get() != ColorMode.RGB)
        .build());

    private final Setting<SettingColor> secondColor = sgMain.add(new ColorSetting.Builder()
        .name("второй-цвет")
        .defaultValue(new Color(0, 0, 255, 255))
        .visible(() -> colorMode.get() == ColorMode.DOUBLE || colorMode.get() == ColorMode.DOUBLE_UP)
        .build());

    private final Setting<Double> alpha = sgMain.add(new DoubleSetting.Builder()
        .name("прозрачность")
        .description("Насколько рука видна сквозь себя. 1 — непрозрачно, 0 — не видно совсем.")
        .defaultValue(1.0)
        .min(0.0)
        .max(1.0)
        .build());

    private final Setting<Double> fillAlphaMult = sgMain.add(new DoubleSetting.Builder()
        .name("множитель-заливки")
        .description("Дополнительный множитель к прозрачности заливки.")
        .defaultValue(1.0)
        .min(0.0)
        .max(3.0)
        .build());

    private final Setting<Double> saturation = sgMain.add(new DoubleSetting.Builder()
        .name("насыщенность")
        .description("0 — серый цвет, больше единицы — цвета сочнее.")
        .defaultValue(1.0)
        .min(0.0)
        .max(3.0)
        .build());

    private final Setting<Double> brightness = sgMain.add(new DoubleSetting.Builder()
        .name("яркость")
        .description("Общий множитель яркости цвета.")
        .defaultValue(1.0)
        .min(0.0)
        .max(3.0)
        .build());

    private final Setting<Double> radius = sgGlow.add(new DoubleSetting.Builder()
        .name("радиус")
        .description("Насколько широко расходится свечение вокруг руки.")
        .defaultValue(0.4)
        .min(0.0)
        .max(2.0)
        .build());

    private final Setting<Double> glowAlpha = sgGlow.add(new DoubleSetting.Builder()
        .name("яркость-свечения")
        .description("Сила светящейся обводки.")
        .defaultValue(0.6)
        .min(0.0)
        .max(2.0)
        .build());

    private final Setting<Double> glowAlphaMult = sgGlow.add(new DoubleSetting.Builder()
        .name("множитель-свечения")
        .description("Общий множитель яркости свечения.")
        .defaultValue(1.0)
        .min(0.0)
        .max(3.0)
        .build());

    private final Setting<Double> distance = sgGlow.add(new DoubleSetting.Builder()
        .name("дистанция")
        .description("С какого расстояния свечение слабеет. 0 — не гаснет.")
        .defaultValue(0.0)
        .min(0.0)
        .max(8.0)
        .build());

    private final Setting<Integer> glowQuality = sgGlow.add(new IntSetting.Builder()
        .name("качество-свечения")
        .description("Сколько оттенков подмешивается в градиент свечения.")
        .defaultValue(8)
        .min(1)
        .max(32)
        .build());

    private final Setting<Boolean> borders = sgBorder.add(new BoolSetting.Builder()
        .name("рамка")
        .description("Обводить ли руку рамкой.")
        .defaultValue(false)
        .build());

    private final Setting<Double> lineWidth = sgBorder.add(new DoubleSetting.Builder()
        .name("толщина-рамки")
        .description("Толщина обводки в пикселях.")
        .defaultValue(1.0)
        .min(0.5)
        .max(6.0)
        .build());

    private final Setting<Boolean> glass = sgBorder.add(new BoolSetting.Builder()
        .name("стекло")
        .description("Размывать ли то, что видно сквозь руку. У оригинала это "
            + "отдельный проход по кадровому буферу, здесь — только затемнение фона цвета.")
        .defaultValue(false)
        .build());

    /** Открыт ли сейчас проход рендера руки — гейт для миксина тинтов. */
    private static boolean inHandPass;
    private static int[] forcedTints;
    private static long startTime = System.currentTimeMillis();

    public HandChams() {
        super(B2XY.CATEGORY, "hand-chams", "Красит руку от первого лица: цвет, прозрачность, свечение, рамку.");
    }

    @Override
    public void onActivate() {
        startTime = System.currentTimeMillis();
    }

    // ------------------------------------------------------------------
    //  Гейт для миксинов
    // ------------------------------------------------------------------

    public static boolean shouldTint() {
        HandChams module = meteordevelopment.meteorclient.systems.modules.Modules.get().get(HandChams.class);
        return module != null && module.isActive();
    }

    public static void beginHandPass() {
        HandChams module = meteordevelopment.meteorclient.systems.modules.Modules.get().get(HandChams.class);
        if (module == null || !module.isActive()) return;
        inHandPass = true;
        forcedTints = module.tintArray();
    }

    public static void endHandPass() {
        inHandPass = false;
        forcedTints = null;
    }

    /**
     * Отдаёт тинт вершины, если сейчас рисуется рука. Иначе — null,
     * и ванильный цвет остаётся как есть.
     */
    public static int[] overrideTints(int[] original) {
        if (!inHandPass || forcedTints == null) return null;
        if (original.length == 0) return null;
        // Модели с несколькими слоями красятся по числу вершин — отдаём свой массив
        return original.length == 1 ? forcedTints : cloneWithColor(original.length, forcedTints[0]);
    }

    private static int[] cloneWithColor(int length, int color) {
        int[] out = new int[length];
        java.util.Arrays.fill(out, color);
        return out;
    }

    // ------------------------------------------------------------------
    //  Цвет
    // ------------------------------------------------------------------

    /**
     * Цвет руки на текущий момент. Раскладка во времени нужна режимам
     * «перелив», «двойной» и «телевизор», поэтому берём время с включения
     * модуля, а не системное: при перезагрузке конфига анимация не прыгает.
     */
    private int[] tintArray() {
        float t = (System.currentTimeMillis() - startTime) / 1000.0F;
        float a = (float) (alpha.get().floatValue() * fillAlphaMult.get().floatValue());

        int base = switch (colorMode.get()) {
            case RGB -> hueColor(t * 60.0F);
            case DOUBLE -> blend(rgb(color.get()), rgb(secondColor.get()), 0.5F);
            case DOUBLE_UP -> blend(rgb(color.get()), rgb(secondColor.get()), 0.5F + 0.5F * (float) Math.sin(t * 2.0));
            case TV -> tvColor(t);
            case SINGLE -> rgb(color.get());
        };

        if (mode.get() == HandsMode.CAMOUFLAGE) base = hueColor(t * 25.0F);
        if (mode.get() == HandsMode.DOUBLE) base = blend(rgb(color.get()), rgb(secondColor.get()), 0.5F);
        if (mode.get() == HandsMode.MIRROR || mode.get() == HandsMode.BLOOM) {
            base = blend(base, rgb(secondColor.get()), 0.5F);
        }

        float[] hsb = rgbToHsb(toRgb(base) / 255.0F, toGgb(base) / 255.0F, toBgb(base) / 255.0F);
        float s = clamp01(hsb[1] * (float) saturation.get().floatValue());
        float v = clamp01(hsb[2] * (float) brightness.get().floatValue());

        if (borders.get()) v = Math.min(1.0F, v + 0.25F);
        if (glass.get()) a *= 0.75F;
        if (mode.get() == HandsMode.BLOOM) v = Math.min(1.0F, v + glowAlpha.get().floatValue() * glowAlphaMult.get().floatValue());
        if (distance.get() > 0.0F) v *= 1.0F - Math.min(1.0F, distance.get().floatValue() / 8.0F) * 0.35F;

        int out = hsbToRgb(hsb[0], s, v);
        return new int[]{(out & 0x00FFFFFF) | (Math.round(a * 255.0F) << 24)};
    }

    /** Бегущая «телевизионная» полоса: смещение цветового канала по времени. */
    private static int tvColor(float t) {
        float p = (t * 0.7F) % 1.0F;
        int r = Math.round(255.0F * Math.max(0.0F, (float) Math.cos(6.2831853 * p)));
        int g = Math.round(255.0F * Math.max(0.0F, (float) Math.cos(6.2831853 * (p - 0.33))));
        int b = Math.round(255.0F * Math.max(0.0F, (float) Math.cos(6.2831853 * (p - 0.66))));
        return (r << 16) | (g << 8) | b;
    }

    private static int hueColor(float degrees) {
        float h = (degrees % 360.0F) / 360.0F;
        if (h < 0) h += 1.0F;
        return hsbToRgb(h, 0.85F, 1.0F);
    }

    private static int blend(int a, int b, float k) {
        k = Math.max(0.0F, Math.min(1.0F, k));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * k);
        int g = Math.round(ag + (bg - ag) * k);
        int bl = Math.round(ab + (bb - ab) * k);
        return (r << 16) | (g << 8) | bl;
    }

    private static float[] rgbToHsb(float r, float g, float b) {
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0.0F) h = 0.0F;
        else if (max == r) h = ((g - b) / d) % 6.0F;
        else if (max == g) h = (b - r) / d + 2.0F;
        else h = (r - g) / d + 4.0F;
        h /= 6.0F;
        if (h < 0.0F) h += 1.0F;
        return new float[]{h, max == 0.0F ? 0.0F : d / max, max};
    }

    private static int hsbToRgb(float h, float s, float v) {
        int i = (int) (h * 6.0F);
        float f = h * 6.0F - i;
        float p = v * (1.0F - s);
        float q = v * (1.0F - f * s);
        float t = v * (1.0F - (1.0F - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return (Math.round(r * 255.0F) << 16) | (Math.round(g * 255.0F) << 8) | Math.round(b * 255.0F);
    }

    private static float clamp01(float x) {
        return x < 0.0F ? 0.0F : Math.min(x, 1.0F);
    }

    /** SettingColor -> упакованный RGB без альфы (альфу считаем отдельно). */
    private static int rgb(SettingColor c) {
        return ((c.r & 0xFF) << 16) | ((c.g & 0xFF) << 8) | (c.b & 0xFF);
    }

    private static int toRgb(int c) {
        return (c >> 16) & 0xFF;
    }

    private static int toGgb(int c) {
        return (c >> 8) & 0xFF;
    }

    private static int toBgb(int c) {
        return c & 0xFF;
    }

    public enum HandsMode {
        DEFAULT("Обычный"),
        MIRROR("Зеркало"),
        BLOOM("Свечение"),
        DOUBLE("Двойной"),
        CAMOUFLAGE("Камуфляж");

        private final String title;

        HandsMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    public enum ColorMode {
        SINGLE("Один цвет"),
        RGB("Перелив"),
        DOUBLE("Два цвета"),
        DOUBLE_UP("Два цвета по очереди"),
        TV("Телевизор");

        private final String title;

        ColorMode(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return title;
        }
    }
}

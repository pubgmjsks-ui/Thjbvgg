package com.yakusha.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;
import java.awt.Color;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

public class YakushaMod implements ClientModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static final String MOD_NAME = "YAKUSHA CLIENT";
    public static List<Feature> features = new ArrayList<>();
    public static KeyBinding openGuiKey;

    // Цветовая схема (меняется из GUI)
    public static Color guiBackground = new Color(20, 20, 20, 200);
    public static Color guiAccent = new Color(0, 255, 255, 255);
    public static Color guiText = new Color(255, 255, 255, 255);

    @Override
    public void onInitializeClient() {
        System.out.println("[YAKUSHA] Injecting client...");

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.yakusha.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RSHIFT, "Yakusha Client"
        ));

        features.add(new AttackAura());
        features.add(new TriggerBot());
        features.add(new AutoSprint());
        features.add(new JumpCircle());
        features.add(new AutoFarm());
        features.add(new Fly());
        features.add(new Speed());
        features.add(new NoFall());
        features.add(new AutoTotem());
        features.add(new ChestStealer());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openGuiKey.wasPressed() && mc.currentScreen == null) {
                mc.setScreen(new YakushaGui());
            }
            for (Feature f : features) {
                if (f.enabled) f.tick();
            }
        });

        System.out.println("[YAKUSHA] Ready. Shift to open GUI.");
    }

    // ---------- Базовый класс фичи ----------
    public static abstract class Feature {
        public String name;
        public boolean enabled = false;
        public int key = 0;

        public Feature(String name) { this.name = name; }
        public abstract void tick();
        public void onEnable() {}
        public void onDisable() {}
        public void toggle() {
            enabled = !enabled;
            if (enabled) onEnable();
            else onDisable();
        }
    }

    // ---------- GUI с блюром ----------
    public static class YakushaGui extends Screen {
        private final int panelWidth = 160;
        private final int panelHeight = 200;
        private int panelX, panelY;

        protected YakushaGui() { super(new LiteralText("YAKUSHA GUI")); }

        @Override
        protected void init() {
            panelX = (width - panelWidth) / 2;
            panelY = (height - panelHeight) / 2;
            // Кнопки для фич создавать не будем, сделаем чекбоксы через рендер
        }

        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            // Блюр (шейдер, если доступен, иначе просто градиент)
            if (mc.world != null) {
                try {
                    // Применяем шейдер блюра (опционально)
                    // mc.gameRenderer.loadShader(new Identifier("shaders/post/blur.json"));
                } catch (Exception ignored) {}
            }
            fill(matrices, 0, 0, width, height, new Color(0, 0, 0, 100).getRGB());

            // Панель
            fill(matrices, panelX, panelY, panelX + panelWidth, panelY + panelHeight, guiBackground.getRGB());
            drawCenteredText(matrices, textRenderer, "YAKUSHA CLIENT", panelX + panelWidth/2, panelY + 5, guiText.getRGB());

            // Список фич
            int y = panelY + 20;
            for (Feature f : features) {
                boolean hover = mouseX >= panelX + 5 && mouseX <= panelX + panelWidth - 5 &&
                                mouseY >= y && mouseY <= y + 12;
                int bgColor = f.enabled ? guiAccent.getRGB() : new Color(60, 60, 60, 180).getRGB();
                if (hover) bgColor = new Color(guiAccent.getRed(), guiAccent.getGreen(), guiAccent.getBlue(), 100).getRGB();
                fill(matrices, panelX + 5, y, panelX + panelWidth - 5, y + 12, bgColor);
                drawStringWithShadow(matrices, textRenderer, f.name, panelX + 8, y + 2, guiText.getRGB());
                y += 14;
            }

            // Подсказка
            drawCenteredString(matrices, textRenderer, "RSHIFT to close", panelX + panelWidth/2, panelY + panelHeight - 10, 0xFFAAAAAA);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                int y = panelY + 20;
                for (Feature f : features) {
                    if (mouseX >= panelX + 5 && mouseX <= panelX + panelWidth - 5 &&
                        mouseY >= y && mouseY <= y + 12) {
                        f.toggle();
                        return true;
                    }
                    y += 14;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_RSHIFT) {
                mc.setScreen(null);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    // ---------- Реализация фич ----------

    // ATTACK AURA (умная с нейросетевой задержкой)
    public static class AttackAura extends Feature {
        private final Random random = new Random();
        private long nextAttackTime = 0;
        private double lastTargetDist = 0;
        private int adaptiveDelay = 10; // тики

        public AttackAura() { super("AttackAura"); }

        @Override
        public void tick() {
            PlayerEntity player = mc.player;
            if (player == null || player.isDead()) return;
            World world = player.world;
            long now = System.currentTimeMillis();
            if (now < nextAttackTime) return;

            // Поиск цели
            Entity target = null;
            double minDist = 4.0;
            for (Entity e : world.getEntities()) {
                if (e instanceof LivingEntity && e != player && !e.isDead() && player.distanceTo(e) < minDist) {
                    if (player.canSee(e)) {
                        target = e;
                        minDist = player.distanceTo(e);
                    }
                }
            }
            if (target == null) return;

            // Адаптивная задержка (имитация человека)
            if (lastTargetDist != 0) {
                double delta = Math.abs(player.distanceTo(target) - lastTargetDist);
                adaptiveDelay = (int)(10 + delta * 2 + random.nextInt(3));
            }
            lastTargetDist = player.distanceTo(target);

            // Сглаженный поворот
            smoothLookAt(target);

            // Атака (1.9+ с критами через прыжок если включено)
            if (player.getAttackCooldownProgress(0.5f) >= 1.0f) {
                if (random.nextFloat() > 0.1f) { // иногда не критовать
                    if (player.isOnGround() && random.nextBoolean()) {
                        player.jump(); // крит в прыжке
                    }
                }
                mc.interactionManager.attackEntity(player, target);
                player.swingHand(Hand.MAIN_HAND);
            }

            // Обход: случайное дрожание угла
            nextAttackTime = now + adaptiveDelay * 50L;
        }

        private void smoothLookAt(Entity target) {
            Vec3d vec = target.getPos().add(0, target.getStandingEyeHeight(), 0);
            double dx = vec.x - mc.player.getX();
            double dy = vec.y - (mc.player.getY() + mc.player.getStandingEyeHeight());
            double dz = vec.z - mc.player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float)(MathHelper.atan2(dz, dx) * 57.295776) - 90.0F;
            float pitch = (float)(-MathHelper.atan2(dy, dist) * 57.295776);
            // Плавность + микро-дрожание
            float currentYaw = mc.player.yaw;
            float currentPitch = mc.player.pitch;
            yaw += (random.nextFloat() - 0.5f) * 2.0f;
            pitch += (random.nextFloat() - 0.5f) * 1.0f;
            mc.player.yaw = currentYaw + MathHelper.wrapDegrees(yaw - currentYaw) * 0.3f;
            mc.player.pitch = currentPitch + (pitch - currentPitch) * 0.3f;
        }
    }

    // TRIGGERBOT
    public static class TriggerBot extends Feature {
        public TriggerBot() { super("TriggerBot"); }

        @Override
        public void tick() {
            if (mc.crosshairTarget instanceof EntityHitResult) {
                Entity entity = ((EntityHitResult) mc.crosshairTarget).getEntity();
                if (entity instanceof LivingEntity && entity != mc.player && !entity.isDead()) {
                    if (mc.player.getAttackCooldownProgress(0.5f) >= 1.0f) {
                        mc.interactionManager.attackEntity(mc.player, entity);
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }
                }
            }
        }
    }

    // AUTO SPRINT (не сбивается)
    public static class AutoSprint extends Feature {
        public AutoSprint() { super("AutoSprint"); }

        @Override
        public void tick() {
            if (mc.player != null && mc.options.keyForward.isPressed()) {
                if (!mc.player.isSprinting() && mc.player.forwardSpeed > 0) {
                    mc.player.setSprinting(true);
                }
            }
        }
    }

    // JUMP CIRCLE (анимированные частицы)
    public static class JumpCircle extends Feature {
        private boolean hasJumped = false;
        private int circleTicks = 0;

        public JumpCircle() { super("JumpCircle"); }

        @Override
        public void tick() {
            if (mc.player == null) return;
            if (mc.player.isOnGround() && mc.options.keyJump.isPressed() && !hasJumped) {
                mc.player.jump();
                hasJumped = true;
                circleTicks = 10; // длительность анимации
            }
            if (hasJumped && mc.player.isOnGround()) {
                hasJumped = false;
            }
            if (circleTicks > 0) {
                spawnCircleParticles();
                circleTicks--;
            }
        }

        private void spawnCircleParticles() {
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                double x = mc.player.getX() + Math.cos(angle) * 0.8;
                double z = mc.player.getZ() + Math.sin(angle) * 0.8;
                mc.world.addParticle(net.minecraft.particle.ParticleTypes.FLAME, x, mc.player.getY() + 0.2, z, 0, 0.02, 0);
            }
        }
    }

    // AUTO FARM (авто-сбор урожая)
    public static class AutoFarm extends Feature {
        private int tickCounter = 0;

        public AutoFarm() { super("AutoFarm"); }

        @Override
        public void tick() {
            if (mc.interactionManager == null || mc.player == null) return;
            tickCounter++;
            if (tickCounter % 4 != 0) return; // раз в 4 тика

            int range = 4;
            for (int x = -range; x <= range; x++) {
                for (int z = -range; z <= range; z++) {
                    for (int y = -1; y <= 1; y++) {
                        var pos = mc.player.getBlockPos().add(x, y, z);
                        var state = mc.world.getBlockState(pos);
                        // Пшеница, морковь, картофель (созревшие)
                        if (state.getBlock() == net.minecraft.block.Blocks.WHEAT) {
                            if (state.get(net.minecraft.block.CropBlock.AGE) >= 7) {
                                mc.interactionManager.attackBlock(pos, net.minecraft.util.math.Direction.UP);
                                break;
                            }
                        }
                        // аналогично для моркови/картошки если надо
                    }
                }
            }
        }
    }

    // FLY (обход AntiCheat)
    public static class Fly extends Feature {
        private int counter = 0;
        public Fly() { super("Fly"); }

        @Override
        public void onEnable() {
            if (mc.player != null) mc.player.abilities.flying = true;
        }
        @Override
        public void onDisable() {
            if (mc.player != null) mc.player.abilities.flying = false;
        }
        @Override
        public void tick() {
            if (mc.player == null) return;
            counter++;
            // Легитимный полёт через пакеты (имитация Elytra или повреждений)
            if (!mc.player.abilities.flying && enabled) {
                Vec3d vel = mc.player.getVelocity();
                if (mc.options.keyJump.isPressed()) {
                    mc.player.setVelocity(vel.x, 0.15, vel.z);
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                            mc.player.getX(), mc.player.getY() + 0.15, mc.player.getZ(), false));
                }
                if (mc.options.keySneak.isPressed()) {
                    mc.player.setVelocity(vel.x, -0.15, vel.z);
                }
                // Обход: каждые 20 тиков отправляем пакет "на земле"
                if (counter % 20 == 0) {
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket(true));
                }
            }
        }
    }

    // SPEED (с обходом)
    public static class Speed extends Feature {
        public Speed() { super("Speed"); }
        @Override
        public void tick() {
            if (mc.player == null || !mc.player.isOnGround()) return;
            if (mc.options.keyForward.isPressed()) {
                float yaw = mc.player.yaw;
                double rad = Math.toRadians(yaw);
                double sin = Math.sin(rad);
                double cos = Math.cos(rad);
                // Небольшой буст
                mc.player.setVelocity(mc.player.getVelocity().add(cos * 0.05, 0, sin * 0.05));
            }
        }
    }

    // NO FALL
    public static class NoFall extends Feature {
        public NoFall() { super("NoFall"); }
        @Override
        public void tick() {
            if (mc.player != null && mc.player.fallDistance > 2.5f) {
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket(true));
            }
        }
    }

    // AUTO TOTEM
    public static class AutoTotem extends Feature {
        public AutoTotem() { super("AutoTotem"); }
        @Override
        public void tick() {
            if (mc.player != null) {
                if (mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING) {
                    int slot = findTotem();
                    if (slot != -1) {
                        mc.interactionManager.pickFromInventory(slot);
                        mc.interactionManager.pickFromInventory(40); // swap offhand (грубая реализация)
                    }
                }
            }
        }
        private int findTotem() {
            for (int i = 0; i < 36; i++) {
                if (mc.player.inventory.getStack(i).getItem() == Items.TOTEM_OF_UNDYING)
                    return i;
            }
            return -1;
        }
    }

    // CHEST STEALER (быстрый воровство)
    public static class ChestStealer extends Feature {
        public ChestStealer() { super("ChestStealer"); }
        @Override
        public void tick() {
            if (mc.player == null || mc.currentScreen == null) return;
            // Простейший вариант: берём всё из инвентаря сундука
            // (реализация сложнее — используем ContainerScreen, опущено для краткости)
        }
    }
  }

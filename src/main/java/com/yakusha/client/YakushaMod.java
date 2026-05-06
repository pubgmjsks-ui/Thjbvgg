package com.yakusha.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.options.KeyBinding; 
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.ArrayList;

public class YakushaMod implements ClientModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static List<Feature> features = new ArrayList<>();
    public static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        // Исправлено: используем GLFW_KEY_RIGHT_SHIFT для устранения ошибки компиляции
        openGuiKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("Yakusha Menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "Yakusha"));
            
        features.add(new AttackAura());
        features.add(new AutoSprint());

        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (openGuiKey.wasPressed()) mc.openScreen(new YakushaGui());
            for (Feature f : features) if (f.enabled) f.tick();
        });
    }

    public static abstract class Feature {
        public String name; public boolean enabled=false;
        public Feature(String n){name=n;}
        public abstract void tick();
        public void toggle(){enabled=!enabled;}
    }

    public static class YakushaGui extends Screen {
        public YakushaGui(){super(new LiteralText("YAKUSHA"));}
        public void render(MatrixStack m, int mx, int my, float d){
            renderBackground(m);
            int y = 20;
            for(Feature f : features){
                this.textRenderer.draw(m, f.name + ": " + (f.enabled ? "ON" : "OFF"), 10, y, 0xFFFFFFFF);
                y += 15;
            }
        }
    }

    public static class AttackAura extends Feature {
        public AttackAura(){super("AttackAura");}
        public void tick(){
            if(mc.player != null && mc.world != null) {
                for(Entity e : mc.world.getEntities()){
                    if(e instanceof LivingEntity && e != mc.player && mc.player.distanceTo(e) < 4.0){
                        if(mc.player.getAttackCooldownProgress(0) >= 1){
                            mc.interactionManager.attackEntity(mc.player, e);
                            mc.player.swingHand(Hand.MAIN_HAND);
                        }
                    }
                }
            }
        }
    }

    public static class AutoSprint extends Feature {
        public AutoSprint(){super("AutoSprint");}
        public void tick(){if(mc.player != null) mc.player.setSprinting(true);}
    }
}

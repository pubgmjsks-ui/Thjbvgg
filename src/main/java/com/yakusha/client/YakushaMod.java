package com.yakusha.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.options.KeyBinding; // В 1.16.5 тут 'options' с буквой S
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.lwjgl.glfw.GLFW;
import java.awt.Color;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

public class YakushaMod implements ClientModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    public static List<Feature> features = new ArrayList<>();
    public static KeyBinding openGuiKey;
    public static Color guiBg = new Color(20,20,20,200);
    public static Color guiAc = new Color(0,255,255,255);

    @Override
    public void onInitializeClient() {
        openGuiKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("Yakusha Menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RSHIFT, "Yakusha"));
            
        features.add(new AttackAura());
        features.add(new AutoSprint());
        features.add(new Fly());

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
        int pw=160, ph=100, px, py;
        public YakushaGui(){super(new LiteralText("YAKUSHA"));}
        protected void init(){px=(width-pw)/2; py=(height-ph)/2;}
        
        public void render(MatrixStack m, int mx, int my, float d){
            fill(m, px, py, px+pw, py+ph, guiBg.getRGB());
            int y = py + 20;
            for(Feature f : features){
                int bg = f.enabled ? guiAc.getRGB() : 0xFF555555;
                fill(m, px+5, y, px+pw-5, y+12, bg);
                this.textRenderer.draw(m, f.name, px+8, y+2, 0xFFFFFFFF);
                y += 14;
            }
        }
        
        public boolean mouseClicked(double mx, double my, int b){
            if(b==0){
                int y = py + 20;
                for(Feature f : features){
                    if(mx >= px+5 && mx <= px+pw-5 && my >= y && my <= y+12){
                        f.toggle(); return true;
                    }
                    y += 14;
                }
            }
            return super.mouseClicked(mx, my, b);
        }
    }

    public static class AttackAura extends Feature {
        public AttackAura(){super("AttackAura");}
        public void tick(){
            if(mc.player == null) return;
            for(Entity e : mc.world.getEntities()){
                if(e instanceof LivingEntity && e != mc.player && mc.player.distanceTo(e) < 4.5){
                    if(mc.player.getAttackCooldownProgress(0) >= 1){
                        mc.interactionManager.attackEntity(mc.player, e);
                        mc.player.swingHand(Hand.MAIN_HAND);
                    }
                }
            }
        }
    }

    public static class AutoSprint extends Feature {
        public AutoSprint(){super("AutoSprint");}
        public void tick(){if(mc.player != null) mc.player.setSprinting(true);}
    }

    public static class Fly extends Feature {
        public Fly(){super("Fly");}
        public void tick(){
            if(mc.player != null && mc.options.keyJump.isPressed()){
                mc.player.setVelocity(0, 0.1, 0);
            }
        }
    }
}

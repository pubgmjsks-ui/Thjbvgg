package com.yakusha.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
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
    public static Color guiTx = new Color(255,255,255,255);

    @Override
    public void onInitializeClient() {
        System.out.println("[YAKUSHA] Loaded");
        openGuiKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("GUI", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RSHIFT, "Yakusha"));
            
        features.add(new AttackAura());
        features.add(new TriggerBot());
        features.add(new AutoSprint());
        features.add(new JumpCircle());
        features.add(new Fly());
        features.add(new Speed());
        features.add(new NoFall());
        features.add(new AutoTotem());

        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (openGuiKey.wasPressed()) mc.setScreen(new YakushaGui());
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
        int pw=160, ph=200, px, py;
        public YakushaGui(){super(new LiteralText("YAKUSHA"));}
        protected void init(){px=(width-pw)/2; py=(height-ph)/2;}
        
        public void render(MatrixStack m, int mx, int my, float d){
            fill(m, 0, 0, width, height, new Color(0,0,0,100).getRGB());
            fill(m, px, py, px+pw, py+ph, guiBg.getRGB());
            drawCenteredText(m, textRenderer, "YAKUSHA CLIENT", px+pw/2, py+5, guiTx.getRGB());
            int y = py + 20;
            for(Feature f : features){
                boolean h = mx >= px+5 && mx <= px+pw-5 && my >= y && my <= y+12;
                int bg = f.enabled ? guiAc.getRGB() : new Color(60,60,60,180).getRGB();
                if(h) bg = new Color(guiAc.getRed(), guiAc.getGreen(), guiAc.getBlue(), 100).getRGB();
                fill(m, px+5, y, px+pw-5, y+12, bg);
                drawStringWithShadow(m, textRenderer, f.name, px+8, y+2, guiTx.getRGB());
                y += 14;
            }
        }
        
        public boolean mouseClicked(double mx, double my, int b){
            if(b==0){
                int y = py + 20;
                for(Feature f : features){
                    if(mx >= px+5 && mx <= px+pw-5 && my >= y && my <= y+12){
                        f.toggle();
                        return true;
                    }
                    y += 14;
                }
            }
            return super.mouseClicked(mx, my, b);
        }
        
        public boolean keyPressed(int k, int s, int mod){
            if(k == GLFW.GLFW_KEY_RSHIFT){
                mc.setScreen(null);
                return true;
            }
            return super.keyPressed(k, s, mod);
        }
    }

    // --- ФУНКЦИИ ---
    public static class AttackAura extends Feature {
        Random r=new Random(); long na=0;
        public AttackAura(){super("AttackAura");}
        public void tick(){
            if(mc.player==null || mc.player.isDead()) return;
            long n=System.currentTimeMillis(); if(n<na) return;
            Entity t=null; double md=4.0;
            for(Entity e : mc.world.getEntities()) {
                if(e instanceof LivingEntity && e != mc.player && !e.isDead() && mc.player.distanceTo(e) < md && mc.player.canSee(e)){
                    t=e; md=mc.player.distanceTo(e);
                }
            }
            if(t==null) return;
            if(mc.player.getAttackCooldownProgress(0.5f)>=1.0f){
                mc.interactionManager.attackEntity(mc.player, t);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
            na=n+500+r.nextInt(100);
        }
    }

    public static class TriggerBot extends Feature {
        public TriggerBot(){super("TriggerBot");}
        public void tick(){
            if(mc.crosshairTarget instanceof EntityHitResult){
                Entity e=((EntityHitResult)mc.crosshairTarget).getEntity();
                if(e instanceof LivingEntity && e != mc.player && !e.isDead() && mc.player.getAttackCooldownProgress(0.5f)>=1.0f){
                    mc.interactionManager.attackEntity(mc.player, e);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            }
        }
    }

    public static class AutoSprint extends Feature {
        public AutoSprint(){super("AutoSprint");}
        public void tick(){if(mc.player!=null && mc.options.keyForward.isPressed()) mc.player.setSprinting(true);}
    }

    public static class JumpCircle extends Feature {
        boolean j=false; int ct=0;
        public JumpCircle(){super("JumpCircle");}
        public void tick(){
            if(mc.player==null) return;
            if(mc.player.isOnGround() && mc.options.keyJump.isPressed() && !j){mc.player.jump(); j=true; ct=10;}
            if(j && mc.player.isOnGround()) j=false;
            if(ct>0){
                for(int i=0; i<8; i++){
                    double a = i * Math.PI / 4;
                    mc.world.addParticle(net.minecraft.particle.ParticleTypes.FLAME, mc.player.getX()+Math.cos(a)*0.8, mc.player.getY()+0.2, mc.player.getZ()+Math.sin(a)*0.8, 0, 0.02, 0);
                }
                ct--;
            }
        }
    }

    public static class Fly extends Feature {
        public Fly(){super("Fly");}
        public void tick(){
            if(mc.player!=null && mc.options.keyJump.isPressed()){
                mc.player.setVelocity(mc.player.getVelocity().x, 0.15, mc.player.getVelocity().z);
                mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(mc.player.getX(), mc.player.getY()+0.15, mc.player.getZ(), false));
            }
        }
    }

    public static class Speed extends Feature {
        public Speed(){super("Speed");}
        public void tick(){
            if(mc.player!=null && mc.player.isOnGround() && mc.options.keyForward.isPressed()){
                float y=mc.player.yaw;
                mc.player.setVelocity(mc.player.getVelocity().add(Math.sin(Math.toRadians(-y))*0.05, 0, Math.cos(Math.toRadians(-y))*0.05));
            }
        }
    }

    public static class NoFall extends Feature {
        public NoFall(){super("NoFall");}
        public void tick(){if(mc.player!=null && mc.player.fallDistance > 2.5f) mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket(true));}
    }

    public static class AutoTotem extends Feature {
        public AutoTotem(){super("AutoTotem");}
        public void tick(){
            if(mc.player!=null && mc.player.getOffHandStack().getItem() != Items.TOTEM_OF_UNDYING){
                for(int i=0; i<36; i++){
                    if(mc.player.inventory.getStack(i).getItem() == Items.TOTEM_OF_UNDYING){
                        mc.interactionManager.pickFromInventory(i);
                        mc.interactionManager.pickFromInventory(40);
                        break;
                    }
                }
            }
        }
    }
}

package myau.module.modules;

import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.mixin.accessor.MinecraftAccessor;
import myau.module.Module;
import myau.util.TimerUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemSword;

import java.util.Random;

public class AutoClicker extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private final TimerUtil clickTimer = new TimerUtil();
    private final Random random = new Random();
    private long nextClickDelay = 0L;
    private boolean needReblock = false;

    public final FloatProperty minCPS = new FloatProperty("min-cps", 9.0F, 1.0F, 20.0F);
    public final FloatProperty maxCPS = new FloatProperty("max-cps", 13.0F, 1.0F, 20.0F);
    public final BooleanProperty blockhit = new BooleanProperty("blockhit", false);
    public final BooleanProperty leftClick = new BooleanProperty("left-click", true);

    public AutoClicker() {
        super("AutoClicker", false);
    }

    private boolean isHoldingSword() {
        return mc.thePlayer.getHeldItem() != null
                && mc.thePlayer.getHeldItem().getItem() instanceof ItemSword;
    }

    private long calculateDelay() {
        float min = Math.min(this.minCPS.getValue(), this.maxCPS.getValue());
        float max = Math.max(this.minCPS.getValue(), this.maxCPS.getValue());
        float cps = min + this.random.nextFloat() * (max - min);
        return (long) (1000.0F / cps);
    }

    public boolean isBlockhitting() {
        return this.isEnabled()
                && this.blockhit.getValue()
                && this.isHoldingSword()
                && mc.gameSettings.keyBindUseItem.isKeyDown();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.currentScreen != null) return;

        // Re-block after the attack landed last tick
        if (this.needReblock) {
            if (this.isHoldingSword() && mc.gameSettings.keyBindUseItem.isKeyDown()) {
                mc.playerController.sendUseItem(
                        mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem()
                );
            }
            this.needReblock = false;
        }

        if (!(Boolean) this.leftClick.getValue()) return;
        if (!mc.gameSettings.keyBindAttack.isKeyDown()) return;

        if (this.clickTimer.hasTimeElapsed(this.nextClickDelay)) {
            // If blockhit is on and currently blocking, unblock before attacking
            if (this.blockhit.getValue()
                    && mc.thePlayer.isUsingItem()
                    && this.isHoldingSword()) {
                mc.playerController.onStoppedUsingItem(mc.thePlayer);
                this.needReblock = true;
            }

            ((MinecraftAccessor) (Object) mc).invokeClickMouse();
            this.clickTimer.reset();
            this.nextClickDelay = this.calculateDelay();
        }
    }

    @Override
    public void onDisabled() {
        this.needReblock = false;
        this.nextClickDelay = 0L;
    }
}

package myau.module.modules;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.TickEvent;
import myau.module.Module;
import myau.util.ItemUtil;
import myau.util.KeyBindUtil;
import myau.property.properties.BooleanProperty;
import myau.property.properties.IntProperty;
import myau.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;

import java.util.List;

public class AutoTool extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int currentToolSlot = -1;
    private int previousSlot = -1;
    private int tickDelayCounter = 0;
    public final IntProperty switchDelay = new IntProperty("delay", 0, 0, 5);
    public final BooleanProperty switchBack = new BooleanProperty("switch-back", true);
    public final BooleanProperty sneakOnly = new BooleanProperty("sneak-only", true);
    public final BooleanProperty ignorePotions = new BooleanProperty("ignore-potions", true);

    public AutoTool() {
        super("AutoTool", false);
    }

    public boolean isKillAura() {
        KillAura killAura = (KillAura) Myau.moduleManager.modules.get(KillAura.class);
        if (killAura == null || !killAura.isEnabled()) return false;
        return TeamUtil.isEntityLoaded(killAura.getTarget()) && killAura.isAttackAllowed();
    }

    /**
     * Replacement for the old ManualBlock check.
     * Detects when the player is "block-hitting" with a sword:
     *   - holding a sword
     *   - use-item key (right-click) is down
     * This mirrors the same bypass behavior ManualBlock provided.
     */
    private boolean isBlockHitting() {
        if (mc.thePlayer == null) return false;
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack == null || !(stack.getItem() instanceof ItemSword)) return false;
        return mc.gameSettings.keyBindUseItem.isKeyDown();
    }

    private boolean isHoldingSword() {
        ItemStack stack = mc.thePlayer.getHeldItem();
        return stack != null && stack.getItem() instanceof ItemSword;
    }

    private boolean isHoldingBlockedPotion() {
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack == null || !(stack.getItem() instanceof ItemPotion)) {
            return false;
        }
        ItemPotion potion = (ItemPotion) stack.getItem();
        List<PotionEffect> effects = potion.getEffects(stack);
        if (effects == null || effects.isEmpty()) {
            return false;
        }
        for (PotionEffect effect : effects) {
            int id = effect.getPotionID();
            if (id == Potion.moveSpeed.getId()
                    || id == Potion.jump.getId()
                    || id == Potion.invisibility.getId()) {
                return true;
            }
        }
        return false;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (mc.thePlayer == null || mc.theWorld == null) return;

            if (this.currentToolSlot != -1 && this.currentToolSlot != mc.thePlayer.inventory.currentItem) {
                this.currentToolSlot = -1;
                this.previousSlot = -1;
            }

            // Don't swap if player is block-hitting with a sword (same bypass ManualBlock provided)
            if (this.isBlockHitting() && this.isHoldingSword()) {
                this.tickDelayCounter = 0;
                return;
            }

            // Don't swap if player is using item (blocking/eating/drinking)
            if (mc.thePlayer.isUsingItem()) {
                this.tickDelayCounter = 0;
                return;
            }

            if (mc.objectMouseOver != null
                    && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK
                    && mc.objectMouseOver.getBlockPos() != null
                    && mc.gameSettings.keyBindAttack.isKeyDown()
                    && !isKillAura()
                    && !(this.ignorePotions.getValue() && isHoldingBlockedPotion())) {

                if (this.tickDelayCounter >= this.switchDelay.getValue()
                        && (!(Boolean) this.sneakOnly.getValue()
                            || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()))) {
                    int slot = ItemUtil.findInventorySlot(
                            mc.thePlayer.inventory.currentItem,
                            mc.theWorld.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock()
                    );
                    if (slot != -1 && mc.thePlayer.inventory.currentItem != slot) {
                        if (this.previousSlot == -1) {
                            this.previousSlot = mc.thePlayer.inventory.currentItem;
                        }
                        mc.thePlayer.inventory.currentItem = this.currentToolSlot = slot;
                    }
                }
                this.tickDelayCounter++;
            } else {
                if (this.switchBack.getValue() && this.previousSlot != -1) {
                    mc.thePlayer.inventory.currentItem = this.previousSlot;
                }
                this.currentToolSlot = -1;
                this.previousSlot = -1;
                this.tickDelayCounter = 0;
            }
        }
    }

    @Override
    public void onDisabled() {
        this.currentToolSlot = -1;
        this.previousSlot = -1;
        this.tickDelayCounter = 0;
    }
}

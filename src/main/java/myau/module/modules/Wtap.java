package myau.module.modules;

  import myau.event.EventTarget;
  import myau.event.types.EventType;
  import myau.event.types.Priority;
  import myau.events.MoveInputEvent;
  import myau.events.PacketEvent;
  import myau.module.Module;
  import myau.util.TimerUtil;
  import myau.property.properties.FloatProperty;
  import net.minecraft.client.Minecraft;
  import net.minecraft.client.settings.KeyBinding;
  import net.minecraft.network.play.client.C02PacketUseEntity;
  import net.minecraft.network.play.client.C02PacketUseEntity.Action;
  import net.minecraft.potion.Potion;
  import org.lwjgl.input.Keyboard;

  public class Wtap extends Module {
      private static final Minecraft mc = Minecraft.getMinecraft();
      private final TimerUtil timer = new TimerUtil();

      private enum Phase { IDLE, DELAY, RELEASE, REPRESS }
      private Phase phase = Phase.IDLE;
      private long phaseTicks = 0L;

      public final FloatProperty delay = new FloatProperty("delay", 5.5F, 0.0F, 10.0F);
      public final FloatProperty duration = new FloatProperty("duration", 1.5F, 1.0F, 5.0F);
      public final FloatProperty repressTime = new FloatProperty("repress-time", 2.0F, 1.0F,
  5.0F);
      public final FloatProperty cooldown = new FloatProperty("cooldown", 500.0F, 100.0F,
  1000.0F);

      public Wtap() {
          super("WTap", false);
      }

      private boolean canTrigger() {
          return !(mc.thePlayer.movementInput.moveForward < 0.8F)
                  && !mc.thePlayer.isCollidedHorizontally
                  && (!((float) mc.thePlayer.getFoodStats().getFoodLevel() <= 6.0F) ||
  mc.thePlayer.capabilities.allowFlying)
                  && (mc.thePlayer.isSprinting()
                  || !mc.thePlayer.isUsingItem() &&
  !mc.thePlayer.isPotionActive(Potion.blindness) && mc.gameSettings.keyBindSprint.isKeyDown());
      }

      @EventTarget(Priority.LOWEST)
      public void onMoveInput(MoveInputEvent event) {
          if (!this.isEnabled()) {
              this.reset();
              return;
          }

          switch (this.phase) {
              case IDLE:
                  break;

              case DELAY:
                  if (!this.canTrigger()) {
                      this.reset();
                      return;
                  }
                  if (this.phaseTicks <= 0L) {
                      this.phase = Phase.RELEASE;
                      this.phaseTicks = (long) (50.0F * this.duration.getValue());
                  } else {
                      this.phaseTicks -= 50L;
                  }
                  break;

              case RELEASE:
                  // Actually release the W key at input level so the game sees it
                  KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(),
  false);
                  mc.thePlayer.movementInput.moveForward = 0.0F;
                  mc.thePlayer.setSprinting(false);
                  if (this.phaseTicks <= 0L) {
                      this.phase = Phase.REPRESS;
                      this.phaseTicks = (long) (50.0F * this.repressTime.getValue());
                  } else {
                      this.phaseTicks -= 50L;
                  }
                  break;

              case REPRESS:
                  // Actually press W key back down — sprint re-engages via held sprint key
                  KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), true);                  mc.thePlayer.movementInput.moveForward = 1.0F;
                  if (this.phaseTicks <= 0L) {
                      this.reset();
                  } else {
                      this.phaseTicks -= 50L;
                  }
                  break;
          }
      }

      @EventTarget
      public void onPacket(PacketEvent event) {
          if (this.isEnabled() && !event.isCancelled() && event.getType() == EventType.SEND) {
              if (event.getPacket() instanceof C02PacketUseEntity
                      && ((C02PacketUseEntity) event.getPacket()).getAction() == Action.ATTACK
                      && this.phase == Phase.IDLE
                      && this.timer.hasTimeElapsed((long) this.cooldown.getValue().floatValue())                      && mc.thePlayer.isSprinting()
                      && this.canTrigger()) {
                  this.timer.reset();
                  this.phase = Phase.DELAY;
                  this.phaseTicks = (long) (50.0F * this.delay.getValue());
              }
          }
      }

      private void reset() {
          this.phase = Phase.IDLE;
          this.phaseTicks = 0L;
          // Restore forward key to match the actual physical key state
          KeyBinding.setKeyBindState(
                  mc.gameSettings.keyBindForward.getKeyCode(),
                  Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())
          );
      }

      @Override
      public void onDisabled() {
          this.reset();
      }
  }

package me.jellysquid.mods.sodium.mixin.features.gui.hooks.settings;

import me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OptionsScreen.class)
public class OptionsScreenMixin extends Screen {
    protected OptionsScreenMixin(Text title) {
        super(title);
    }

    @Dynamic
    @Inject(method = "method_19828", at = @At("HEAD"), cancellable = true)
    private void open(CallbackInfoReturnable<Screen> ci) {
        ci.setReturnValue(new SodiumOptionsGUI(this));
    }
}

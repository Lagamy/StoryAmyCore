package net.amyrose.storyamycore.mixin.CreateWorld;

import net.amyrose.storyamycore.core.StoryEngine;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.tab.GridScreenTab;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/* Client only. Rule: the create world screen runs on the client and the integrated server is created after it, so the choice travels through
   the static StoryEngine.newWorldChapterGen (no networking); ServerDataManager reads it once when the world has no story_data.dat yet.
   MoreTab is a package-private inner class, hence the string target, and its constructor receives the outer screen as its only argument.
   The mixin extends GridScreenTab (the superclass of MoreTab) to reach the protected grid. */
@Mixin(targets = "net.minecraft.client.gui.screen.world.CreateWorldScreen$MoreTab")
public abstract class MoreTabMixin extends GridScreenTab {
    @Unique
    private boolean storyamycore$specialWorld = false;
    @Unique
    private boolean storyamycore$savedChoice = true;

    protected MoreTabMixin(Text title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void storyamycore$addChapterGenButton(CreateWorldScreen screen, CallbackInfo ci) {
        StoryEngine.newWorldChapterGen = true;
        CyclingButtonWidget<Boolean> button = CyclingButtonWidget.onOffBuilder(true)
                .build(0, 0, 210, 20, Text.translatable("createWorld.storyamycore.chapterGen"),
                        (b, value) -> StoryEngine.newWorldChapterGen = value);
        /* Row 3: below the game rules, experiments and data packs buttons (rows 0-2). */
        this.grid.add(button, 3, 0);

        /* Debug worlds have no use for chapters, so entering one turns the button off and leaving it brings back the previous choice.
           Only the transition is handled, so the player can still switch it on while a debug world is selected. The listener runs on every
           WorldCreator update, and once now for a screen that opens already on one. */
        screen.getWorldCreator().addListener(creator -> {
            boolean special = creator.isDebug();
            if (special == this.storyamycore$specialWorld) {
                return;
            }
            this.storyamycore$specialWorld = special;
            if (special) {
                this.storyamycore$savedChoice = button.getValue();
                button.setValue(false);
            } else {
                button.setValue(this.storyamycore$savedChoice);
            }
            StoryEngine.newWorldChapterGen = button.getValue();
        });
        screen.getWorldCreator().update();
    }
}

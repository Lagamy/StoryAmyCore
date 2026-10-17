package net.amyrose.storyamycore.item;

import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.Story;
import net.amyrose.storyamycore.core.StoryEngine;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.OpenWrittenBookS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.Map;

public class StoryBookItem extends Item {
    static final int MAX_LINES_PER_PAGE = 12;

    public StoryBookItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            /* OpenWrittenBookS2CPacket only carries the hand, and the client opens the book screen only if the stack in that hand
               is Items.WRITTEN_BOOK (ServerPlayerEntity.useBook checks the same), so a custom item never opens. A real written book
               with freshly built pages is swapped into the hand and synced, then the packet is sent, then the Story Book is put back.
               Packets are handled in order and the screen reads the pages when it opens, so the swap-back does not close it.
               Pages are built per viewer so coordinates are only visible in creative. */
            ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
            fillBook(book, player.isCreative());
            player.setStackInHand(hand, book);
            player.currentScreenHandler.sendContentUpdates();
            player.networkHandler.sendPacket(new OpenWrittenBookS2CPacket(hand));
            player.setStackInHand(hand, stack);
            player.currentScreenHandler.sendContentUpdates();
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    private static void fillBook(ItemStack stack, boolean showCoordinates) {
        NbtList pages = new NbtList();
        MutableText page = Text.empty();
        int lines = 0;

        for (Story story : StoryEngine.stories.values()) {
            int storyLines = 1;
            for (Chapter chapter : story.chapters.values()) {
                storyLines += chapterLines(chapter, showCoordinates);
            }

            /* A story starts on a fresh page when it does not fit in the rest of the current one. */
            if (lines > 0 && lines + storyLines > MAX_LINES_PER_PAGE) {
                pages.add(NbtString.of(Text.Serializer.toJson(page)));
                page = Text.empty();
                lines = 0;
            }

            page.append(Text.literal(story.name + "\n").formatted(Formatting.BOLD));
            lines++;

            for (Map.Entry<String, Chapter> entry : story.chapters.entrySet()) {
                int needed = chapterLines(entry.getValue(), showCoordinates);
                if (lines + needed > MAX_LINES_PER_PAGE) {
                    pages.add(NbtString.of(Text.Serializer.toJson(page)));
                    page = Text.empty();
                    lines = 0;
                }

                page.append(Text.literal(" " + entry.getKey() + "\n"));
                if (showCoordinates) {
                    page.append(Text.literal("  " + coordinatesOf(entry.getValue()) + "\n").formatted(Formatting.DARK_GRAY));
                }
                lines += needed;
            }
        }

        if (lines == 0 && pages.isEmpty()) {
            page.append(Text.literal("No stories registered."));
        }
        pages.add(NbtString.of(Text.Serializer.toJson(page)));

        stack.setSubNbt("title", NbtString.of("Story Book"));
        stack.setSubNbt("author", NbtString.of("Story Amy Core"));
        stack.setSubNbt("pages", pages);
        stack.getOrCreateNbt().putBoolean("resolved", true);
    }

    private static int chapterLines(Chapter chapter, boolean showCoordinates) {
        return showCoordinates ? 2 : 1;
    }

    private static String coordinatesOf(Chapter chapter) {
        if (!chapter.isPlaced()) {
            return "not placed yet";
        }
        return chapter.chapterArea.beginning.x + ", " + chapter.chapterArea.beginning.z
                + " to " + chapter.chapterArea.end.x + ", " + chapter.chapterArea.end.z;
    }
}

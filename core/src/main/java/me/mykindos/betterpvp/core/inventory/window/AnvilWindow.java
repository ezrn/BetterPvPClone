package me.mykindos.betterpvp.core.inventory.window;

import me.mykindos.betterpvp.core.inventory.gui.Gui;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * A {@link me.mykindos.betterpvp.core.inventory.window.Window} that uses an anvil inventory.
 */
public interface AnvilWindow extends me.mykindos.betterpvp.core.inventory.window.Window {
    
    /**
     * Creates a new {@link Builder.Single Window Builder} for a single {@link AnvilWindow}.
     *
     * @return The new {@link Builder.Single Window Builder}.
     */
    static @NotNull Builder.Single single() {
        return new me.mykindos.betterpvp.core.inventory.window.AnvilSingleWindowImpl.BuilderImpl();
    }
    
    /**
     * Creates a new single {@link AnvilWindow} after configuring a {@link Builder.Single Window Builder} using the given {@link Consumer}.
     *
     * @param consumer The {@link Consumer} to configure the {@link Builder.Single Window Builder}.
     * @return The created {@link AnvilWindow}.
     */
    static @NotNull AnvilWindow single(@NotNull Consumer<Builder.@NotNull Single> consumer) {
        Builder.Single builder = single();
        consumer.accept(builder);
        return builder.build();
    }
    
    /**
     * Creates a new {@link Builder.Split Window Builder} for a split {@link AnvilWindow}.
     *
     * @return The new {@link Builder.Split Window Builder}.
     */
    static @NotNull Builder.Split split() {
        return new me.mykindos.betterpvp.core.inventory.window.AnvilSplitWindowImpl.BuilderImpl();
    }
    
    /**
     * Creates a new split {@link AnvilWindow} after configuring a {@link Builder.Split Window Builder} using the given {@link Consumer}.
     *
     * @param consumer The {@link Consumer} to configure the {@link Builder.Split Window Builder}.
     * @return The created {@link AnvilWindow}.
     */
    static @NotNull AnvilWindow split(Consumer<Builder.@NotNull Split> consumer) {
        Builder.Split builder = split();
        consumer.accept(builder);
        return builder.build();
    }
    
    /**
     * Gets the current rename text.
     *
     * @return The current rename text.
     */
    @Nullable String getRenameText();
    
    /**
     * An {@link AnvilWindow} builder.
     *
     * @param <S> The builder type.
     * @see me.mykindos.betterpvp.core.inventory.window.Window.Builder.Normal
     * @see me.mykindos.betterpvp.core.inventory.window.CartographyWindow.Builder
     */
    interface Builder<S extends Builder<S>> extends me.mykindos.betterpvp.core.inventory.window.Window.Builder<AnvilWindow, S> {
        
        /**
         * Sets the rename handlers of the {@link AnvilWindow}.
         *
         * @param renameHandlers The new rename handlers.
         * @return The current builder.
         */
        @Contract("_ -> this")
        @NotNull S setRenameHandlers(@NotNull List<@NotNull Consumer<String>> renameHandlers);
        
        /**
         * Adds a rename handler to the {@link AnvilWindow}.
         *
         * @param renameHandler The rename handler to add.
         * @return The current builder.
         */
        @Contract("_ -> this")
        @NotNull S addRenameHandler(@NotNull Consumer<String> renameHandler);
        
        /**
         * A single {@link AnvilWindow} builder. Combines both {@link Builder} and {@link me.mykindos.betterpvp.core.inventory.window.Window.Builder.Single}
         * for an {@link AnvilWindow} with only one {@link Gui} that does not access the {@link Player Player's} inventory.
         *
         * @see me.mykindos.betterpvp.core.inventory.window.Window.Builder.Normal.Single
         * @see me.mykindos.betterpvp.core.inventory.window.CartographyWindow.Builder.Single
         */
        interface Single extends Builder<Single>, me.mykindos.betterpvp.core.inventory.window.Window.Builder.Single<AnvilWindow, Single> {}
        
        /**
         * A split {@link AnvilWindow} builder. Combines both {@link Builder} and {@link me.mykindos.betterpvp.core.inventory.window.Window.Builder.Double}
         * for an {@link AnvilWindow} with two {@link Gui Guis}, where the lower {@link Gui} is used to fill the
         * {@link Player Player's} inventory.
         *
         * @see me.mykindos.betterpvp.core.inventory.window.Window.Builder.Normal.Split
         * @see CartographyWindow.Builder.Split
         */
        interface Split extends Builder<Split>, Double<AnvilWindow, Split> {}
        
    }
    
}

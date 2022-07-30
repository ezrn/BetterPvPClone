package me.mykindos.betterpvp.core.command.commands.admin;

import com.google.inject.Inject;
import me.mykindos.betterpvp.core.Core;
import me.mykindos.betterpvp.core.client.Client;
import me.mykindos.betterpvp.core.client.Rank;
import me.mykindos.betterpvp.core.command.Command;
import me.mykindos.betterpvp.core.command.SubCommand;
import me.mykindos.betterpvp.core.command.loader.CoreCommandLoader;
import me.mykindos.betterpvp.core.framework.annotations.WithReflection;
import me.mykindos.betterpvp.core.listener.loader.CoreListenerLoader;
import org.bukkit.entity.Player;

public class CoreCommand extends Command {

    @WithReflection
    public CoreCommand() {
        subCommands.add(new ReloadCommand());
    }

    @Override
    public String getName() {
        return "core";
    }

    @Override
    public String getDescription() {
        return "Base core command";
    }

    @Override
    public void execute(Player player, Client client, String... args) {

    }

    @Override
    public Rank getRequiredRank() {
        return Rank.OWNER;
    }

    private static class ReloadCommand extends SubCommand {

        @Inject
        private Core core;

        @Inject
        private CoreCommandLoader commandLoader;

        @Inject
        private CoreListenerLoader listenerLoader;

        @Override
        public String getName() {
            return "reload";
        }

        @Override
        public String getDescription() {
            return "Reload the core plugin";
        }

        @Override
        public void execute(Player player, Client client, String... args) {
            core.reloadConfig();

            commandLoader.reload();
            listenerLoader.reload();
        }
    }
}

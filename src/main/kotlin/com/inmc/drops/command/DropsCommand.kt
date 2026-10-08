package com.inmc.drops.command

import com.inmc.drops.Drops
import com.inmc.drops.DropsPlugin
import com.inmc.drops.boost.Boost
import com.inmc.drops.gui.InfoMainMenu
import com.inmc.drops.gui.MainMenu
import com.inmc.drops.util.Ph
import com.inmc.drops.verify.Verifier
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/**
 * `/드랍` 한 트리. 인자 없이 치면 관리자는 관리 화면, 그 밖은 정보.
 */
class DropsCommand(private val drops: Drops, private val plugin: DropsPlugin) {

    fun register(owner: JavaPlugin) {
        owner.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            event.registrar().register(tree().build(), "INMC 커스텀 드랍", listOf("inmcdrops", "drops"))
        }
    }

    private fun sender(ctx: CommandContext<CommandSourceStack>): CommandSender = ctx.source.sender

    private fun player(ctx: CommandContext<CommandSourceStack>): Player? =
        (ctx.source.executor as? Player ?: ctx.source.sender as? Player) ?: null.also { drops.messages.send(sender(ctx), "player-only") }

    private fun isAdmin(source: CommandSourceStack): Boolean = source.sender.hasPermission(Drops.ADMIN)

    private fun tree(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("드랍")
            .executes { ctx ->
                val player = player(ctx) ?: return@executes 0
                if (player.hasPermission(Drops.ADMIN)) MainMenu(drops, player).show() else info(player)
                1
            }
            .then(Commands.literal("도움말").executes { ctx ->
                val sender = sender(ctx)
                drops.messages.send(sender, "help")
                // 관리자 줄은 권한이 있을 때만(2026-10-08).
                if (sender.hasPermission(Drops.ADMIN)) drops.messages.send(sender, "help-admin")
                1
            })
            .then(Commands.literal("정보").executes { ctx -> player(ctx)?.let(::info); 1 })
            .then(
                Commands.literal("배율").requires(::isAdmin)
                    .then(Commands.literal("끄기").executes { ctx ->
                        val stopped = drops.boost.stop()
                        drops.messages.send(sender(ctx), if (stopped) "boost-stopped" else "boost-none")
                        1
                    })
                    .then(
                        Commands.argument("배율", DoubleArgumentType.doubleArg(Boost.MIN, Boost.MAX))
                            .then(Commands.argument("분", LongArgumentType.longArg(1, Boost.MAX_MINUTES)).executes { ctx ->
                                drops.boost.startAnnounced(DoubleArgumentType.getDouble(ctx, "배율"), LongArgumentType.getLong(ctx, "분"))
                                1
                            }),
                    ),
            )
            .then(Commands.literal("리로드").requires(::isAdmin).executes { ctx ->
                val sender = sender(ctx)
                plugin.reload { drops.messages.send(sender, "reloaded", Ph.of().count(drops.tables.configuredCount())) }
                1
            })
            .then(Commands.literal("검증").requires(::isAdmin).executes { ctx ->
                player(ctx)?.let { Verifier(drops).run(it) }
                1
            })

    /** 몹 · 작물 · 블록으로 나눈 정보 화면. */
    private fun info(player: Player) {
        if (!player.hasPermission(Drops.INFO)) {
            drops.messages.send(player, "no-permission")
            return
        }
        InfoMainMenu(drops, player).show()
    }
}

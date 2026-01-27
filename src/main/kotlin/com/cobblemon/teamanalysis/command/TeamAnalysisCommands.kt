package com.cobblemon.teamanalysis.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.cobblemon.teamanalysis.analysis.TeamAnalyzer
import com.cobblemon.teamanalysis.data.PresetStorage
import com.cobblemon.teamanalysis.data.TeamPreset
import com.cobblemon.teamanalysis.network.ModNetworking
import com.cobblemon.teamanalysis.util.CobblemonHelper
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.level.ServerPlayer

/**
 * Command handler for /cta commands.
 */
object TeamAnalysisCommands {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val mainCmd = Commands.literal("teamanalysis")
            .executes(::openGui)
            .then(Commands.literal("save")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(::savePreset)))
            .then(Commands.literal("load")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(::loadPreset)))
            .then(Commands.literal("list")
                .executes(::listPresets))
            .then(Commands.literal("delete")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(::deletePreset)))
            .then(Commands.literal("share")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(::sharePreset)))
            .then(Commands.literal("import")
                .then(Commands.argument("code", StringArgumentType.greedyString())
                    .executes(::importPreset)))
            .then(Commands.literal("analyze")
                .executes(::analyzeParty)
                .then(Commands.argument("preset", StringArgumentType.string())
                    .executes(::analyzePreset)))
            .then(Commands.literal("browse")
                .executes(::browseAll)
                .then(Commands.argument("preset", StringArgumentType.string())
                    .executes(::browsePreset)))
            .then(Commands.literal("builder")
                .executes(::openBuilder))

        dispatcher.register(mainCmd)

        // Register /cta as full alias (redirect doesn't carry executes)
        val ctaCmd = Commands.literal("cta")
            .executes(::openGui)
            .then(Commands.literal("save")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(::savePreset)))
            .then(Commands.literal("load")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(::loadPreset)))
            .then(Commands.literal("list")
                .executes(::listPresets))
            .then(Commands.literal("delete")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(::deletePreset)))
            .then(Commands.literal("share")
                .then(Commands.argument("name", StringArgumentType.string())
                    .executes(::sharePreset)))
            .then(Commands.literal("import")
                .then(Commands.argument("code", StringArgumentType.greedyString())
                    .executes(::importPreset)))
            .then(Commands.literal("analyze")
                .executes(::analyzeParty)
                .then(Commands.argument("preset", StringArgumentType.string())
                    .executes(::analyzePreset)))
            .then(Commands.literal("browse")
                .executes(::browseAll)
                .then(Commands.argument("preset", StringArgumentType.string())
                    .executes(::browsePreset)))
            .then(Commands.literal("builder")
                .executes(::openBuilder))
        dispatcher.register(ctaCmd)
    }

    private fun openBuilder(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.player ?: run {
            ctx.source.sendFailure(Component.literal("This command must be run by a player."))
            return 0
        }

        // Send collection with forBuilder flag set
        ModNetworking.sendCollectionToPlayer(player, null, forBuilder = true)
        return 1
    }

    private fun openGui(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.player ?: run {
            ctx.source.sendFailure(Component.literal("This command must be run by a player."))
            return 0
        }
        ModNetworking.sendAnalysisToPlayer(player, null)
        return 1
    }

    private fun savePreset(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.player ?: run {
            ctx.source.sendFailure(Component.literal("This command must be run by a player."))
            return 0
        }

        val name = StringArgumentType.getString(ctx, "name")
        val preset = CobblemonHelper.createPresetFromParty(player, name)

        if (preset == null || preset.slots.isEmpty()) {
            ctx.source.sendFailure(Component.literal("Your party is empty!"))
            return 0
        }

        val saved = PresetStorage.get().savePreset(player.uuid, preset)
        if (saved) {
            ctx.source.sendSuccess({
                Component.literal("Saved team preset: ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(name).withStyle(ChatFormatting.WHITE))
            }, false)
            return 1
        } else {
            ctx.source.sendFailure(Component.literal("Failed to save. Name already exists or max presets reached (20)."))
            return 0
        }
    }

    private fun loadPreset(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.player ?: run {
            ctx.source.sendFailure(Component.literal("This command must be run by a player."))
            return 0
        }

        val name = StringArgumentType.getString(ctx, "name")
        val preset = PresetStorage.get().getPreset(player.uuid, name)

        if (preset == null) {
            ctx.source.sendFailure(Component.literal("Preset not found: $name"))
            return 0
        }

        val loaded = CobblemonHelper.loadPresetToParty(player, preset)
        if (loaded) {
            preset.markUsed()
            PresetStorage.get().updatePreset(player.uuid, preset)
            ctx.source.sendSuccess({
                Component.literal("Loaded team: ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(name).withStyle(ChatFormatting.WHITE))
            }, false)
            return 1
        } else {
            ctx.source.sendFailure(Component.literal("Failed to load preset. Some Pokemon may be missing."))
            return 0
        }
    }

    private fun listPresets(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.player ?: run {
            ctx.source.sendFailure(Component.literal("This command must be run by a player."))
            return 0
        }

        val presets = PresetStorage.get().getPresets(player.uuid)

        if (presets.isEmpty()) {
            ctx.source.sendSuccess({
                Component.literal("You have no saved team presets. Use ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("/cta save <name>").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" to save your current party."))
            }, false)
            return 1
        }

        ctx.source.sendSuccess({
            Component.literal("Your Team Presets (${presets.size}/20):")
                .withStyle(ChatFormatting.GOLD)
        }, false)

        for (preset in presets) {
            ctx.source.sendSuccess({
                Component.literal(" - ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(preset.name)
                        .withStyle(ChatFormatting.WHITE)
                        .withStyle { style ->
                            style.withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/cta load \"${preset.name}\""))
                                .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to load")))
                        })
                    .append(Component.literal(" (${preset.slots.size} Pokemon)").withStyle(ChatFormatting.DARK_GRAY))
            }, false)
        }

        return 1
    }

    private fun deletePreset(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.player ?: run {
            ctx.source.sendFailure(Component.literal("This command must be run by a player."))
            return 0
        }

        val name = StringArgumentType.getString(ctx, "name")
        val deleted = PresetStorage.get().deletePreset(player.uuid, name)

        if (deleted) {
            ctx.source.sendSuccess({
                Component.literal("Deleted preset: ")
                    .withStyle(ChatFormatting.RED)
                    .append(Component.literal(name).withStyle(ChatFormatting.WHITE))
            }, false)
            return 1
        } else {
            ctx.source.sendFailure(Component.literal("Preset not found: $name"))
            return 0
        }
    }

    private fun sharePreset(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.player ?: run {
            ctx.source.sendFailure(Component.literal("This command must be run by a player."))
            return 0
        }

        val name = StringArgumentType.getString(ctx, "name")
        val preset = PresetStorage.get().getPreset(player.uuid, name)

        if (preset == null) {
            ctx.source.sendFailure(Component.literal("Preset not found: $name"))
            return 0
        }

        val shareCode = preset.toShareCode()
        val message = Component.literal("")
            .append(Component.literal(player.name.string).withStyle(ChatFormatting.AQUA))
            .append(Component.literal(" shared a team: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(shareCode)
                .withStyle(ChatFormatting.GREEN)
                .withStyle { style ->
                    style.withClickEvent(ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/cta import $shareCode"))
                        .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to import")))
                })

        player.server?.playerList?.players?.forEach { p ->
            p.sendSystemMessage(message)
        }

        return 1
    }

    private fun importPreset(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.player ?: run {
            ctx.source.sendFailure(Component.literal("This command must be run by a player."))
            return 0
        }

        val code = StringArgumentType.getString(ctx, "code")
        val preset = TeamPreset.fromShareCode(code)

        if (preset == null) {
            ctx.source.sendFailure(Component.literal("Invalid share code format."))
            return 0
        }

        // Handle duplicate names
        var finalName = preset.name
        var suffix = 1
        while (PresetStorage.get().getPreset(player.uuid, finalName) != null) {
            finalName = "${preset.name} ($suffix)"
            suffix++
        }
        preset.name = finalName

        val saved = PresetStorage.get().savePreset(player.uuid, preset)
        if (saved) {
            ctx.source.sendSuccess({
                Component.literal("Imported team template: ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(preset.name).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" (${preset.slots.size} Pokemon)").withStyle(ChatFormatting.GRAY))
            }, false)
            return 1
        } else {
            ctx.source.sendFailure(Component.literal("Failed to import. Max presets reached (20)."))
            return 0
        }
    }

    private fun analyzeParty(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.player ?: run {
            ctx.source.sendFailure(Component.literal("This command must be run by a player."))
            return 0
        }

        // Open GUI with analysis
        ModNetworking.sendAnalysisToPlayer(player, null)
        return 1
    }

    private fun analyzePreset(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.player ?: run {
            ctx.source.sendFailure(Component.literal("This command must be run by a player."))
            return 0
        }

        val presetName = StringArgumentType.getString(ctx, "preset")
        ModNetworking.sendAnalysisToPlayer(player, presetName)
        return 1
    }

    private fun browseAll(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.player ?: run {
            ctx.source.sendFailure(Component.literal("This command must be run by a player."))
            return 0
        }

        ModNetworking.sendCollectionToPlayer(player, null)
        return 1
    }

    private fun browsePreset(ctx: CommandContext<CommandSourceStack>): Int {
        val player = ctx.source.player ?: run {
            ctx.source.sendFailure(Component.literal("This command must be run by a player."))
            return 0
        }

        val presetName = StringArgumentType.getString(ctx, "preset")
        val preset = PresetStorage.get().getPreset(player.uuid, presetName)

        if (preset == null) {
            ctx.source.sendFailure(Component.literal("Preset not found: $presetName"))
            return 0
        }

        ModNetworking.sendCollectionToPlayer(player, presetName)
        return 1
    }
}

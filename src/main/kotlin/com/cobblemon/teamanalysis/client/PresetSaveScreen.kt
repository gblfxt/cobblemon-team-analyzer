package com.cobblemon.teamanalysis.client

import com.cobblemon.teamanalysis.network.PresetActionPayload
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor

/**
 * Simple dialog for saving current party as a preset.
 */
class PresetSaveScreen(private val parentScreen: Screen) : Screen(Component.literal("Save Team Preset")) {

    private lateinit var nameField: EditBox
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button

    private val dialogWidth = 200
    private val dialogHeight = 80

    override fun init() {
        super.init()

        val dialogX = (width - dialogWidth) / 2
        val dialogY = (height - dialogHeight) / 2

        // Name input field
        nameField = EditBox(font, dialogX + 10, dialogY + 25, dialogWidth - 20, 18, Component.literal("Team Name"))
        nameField.setMaxLength(32)
        nameField.setHint(Component.literal("Enter team name...").withStyle(ChatFormatting.DARK_GRAY))
        nameField.setResponder { updateSaveButton() }
        addRenderableWidget(nameField)

        // Save button
        saveButton = Button.builder(Component.literal("Save")) { onSave() }
            .bounds(dialogX + 10, dialogY + 50, 85, 20)
            .build()
        saveButton.active = false
        addRenderableWidget(saveButton)

        // Cancel button
        cancelButton = Button.builder(Component.literal("Cancel")) { onClose() }
            .bounds(dialogX + 105, dialogY + 50, 85, 20)
            .build()
        addRenderableWidget(cancelButton)

        // Focus the text field
        setInitialFocus(nameField)
    }

    private fun updateSaveButton() {
        saveButton.active = nameField.value.isNotBlank()
    }

    private fun onSave() {
        val name = nameField.value.trim()
        if (name.isNotEmpty()) {
            // Send save request to server
            PacketDistributor.sendToServer(PresetActionPayload("save", name))
            onClose()
        }
    }

    override fun onClose() {
        minecraft?.setScreen(parentScreen)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Dim the background
        renderBackground(graphics, mouseX, mouseY, partialTick)

        val dialogX = (width - dialogWidth) / 2
        val dialogY = (height - dialogHeight) / 2

        // Dialog background
        graphics.fill(dialogX - 2, dialogY - 2, dialogX + dialogWidth + 2, dialogY + dialogHeight + 2, 0xFF333333.toInt())
        graphics.fill(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, 0xFF1a1a2e.toInt())

        // Title
        graphics.drawCenteredString(font, Component.literal("Save Team Preset").withStyle(ChatFormatting.GOLD),
            width / 2, dialogY + 8, 0xFFFFFF)

        super.render(graphics, mouseX, mouseY, partialTick)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // Enter to save
        if (keyCode == 257 && nameField.value.isNotBlank()) { // GLFW_KEY_ENTER
            onSave()
            return true
        }
        // Escape to cancel
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            onClose()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun isPauseScreen(): Boolean = false
}

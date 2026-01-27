package com.cobblemon.teamanalysis.client

import com.cobblemon.teamanalysis.network.PresetActionPayload
import com.cobblemon.teamanalysis.network.RequestPresetsPayload
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor

/**
 * Dialog for selecting and loading a preset.
 */
class PresetLoadScreen(private val parentScreen: Screen) : Screen(Component.literal("Load Team Preset")) {

    private val dialogWidth = 220
    private val dialogHeight = 180

    private var presetNames: List<String> = emptyList()
    private var selectedIndex: Int = -1
    private var scrollOffset: Int = 0
    private val visibleCount = 6

    private lateinit var loadButton: Button
    private lateinit var deleteButton: Button
    private lateinit var cancelButton: Button

    override fun init() {
        super.init()

        val dialogX = (width - dialogWidth) / 2
        val dialogY = (height - dialogHeight) / 2

        // Load button
        loadButton = Button.builder(Component.literal("Load")) { onLoad() }
            .bounds(dialogX + 10, dialogY + dialogHeight - 28, 60, 20)
            .build()
        loadButton.active = false
        addRenderableWidget(loadButton)

        // Delete button
        deleteButton = Button.builder(Component.literal("Delete")) { onDelete() }
            .bounds(dialogX + 80, dialogY + dialogHeight - 28, 60, 20)
            .build()
        deleteButton.active = false
        addRenderableWidget(deleteButton)

        // Cancel button
        cancelButton = Button.builder(Component.literal("Cancel")) { onClose() }
            .bounds(dialogX + 150, dialogY + dialogHeight - 28, 60, 20)
            .build()
        addRenderableWidget(cancelButton)

        // Request preset list from server
        PacketDistributor.sendToServer(RequestPresetsPayload)
    }

    /**
     * Called by client packet handler when preset list is received.
     */
    fun setPresetNames(names: List<String>) {
        this.presetNames = names
        this.selectedIndex = if (names.isNotEmpty()) 0 else -1
        updateButtons()
    }

    private fun updateButtons() {
        loadButton.active = selectedIndex >= 0 && selectedIndex < presetNames.size
        deleteButton.active = selectedIndex >= 0 && selectedIndex < presetNames.size
    }

    private fun onLoad() {
        if (selectedIndex >= 0 && selectedIndex < presetNames.size) {
            val name = presetNames[selectedIndex]
            PacketDistributor.sendToServer(PresetActionPayload("load", name))
            onClose()
        }
    }

    private fun onDelete() {
        if (selectedIndex >= 0 && selectedIndex < presetNames.size) {
            val name = presetNames[selectedIndex]
            PacketDistributor.sendToServer(PresetActionPayload("delete", name))
            // Remove from local list immediately for responsiveness
            presetNames = presetNames.filterIndexed { i, _ -> i != selectedIndex }
            if (selectedIndex >= presetNames.size) {
                selectedIndex = presetNames.size - 1
            }
            updateButtons()
        }
    }

    override fun onClose() {
        minecraft?.setScreen(parentScreen)
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(graphics, mouseX, mouseY, partialTick)

        val dialogX = (width - dialogWidth) / 2
        val dialogY = (height - dialogHeight) / 2

        // Dialog background
        graphics.fill(dialogX - 2, dialogY - 2, dialogX + dialogWidth + 2, dialogY + dialogHeight + 2, 0xFF333333.toInt())
        graphics.fill(dialogX, dialogY, dialogX + dialogWidth, dialogY + dialogHeight, 0xFF1a1a2e.toInt())

        // Title
        graphics.drawCenteredString(font, Component.literal("Load Team Preset").withStyle(ChatFormatting.GOLD),
            width / 2, dialogY + 8, 0xFFFFFF)

        // Preset list area
        val listX = dialogX + 10
        val listY = dialogY + 25
        val listWidth = dialogWidth - 20
        val listHeight = dialogHeight - 65
        val itemHeight = 18

        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, 0xFF0a0a0a.toInt())

        if (presetNames.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal("No saved presets").withStyle(ChatFormatting.GRAY),
                width / 2, listY + listHeight / 2 - 4, 0xFFFFFF)
        } else {
            // Render visible presets
            graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight)

            for (i in 0 until minOf(visibleCount, presetNames.size - scrollOffset)) {
                val index = i + scrollOffset
                val itemY = listY + 2 + (i * itemHeight)

                // Highlight selected
                if (index == selectedIndex) {
                    graphics.fill(listX + 2, itemY, listX + listWidth - 2, itemY + itemHeight - 2, 0xFF3366AA.toInt())
                }

                // Hover highlight
                if (mouseX in listX..(listX + listWidth) && mouseY in itemY..(itemY + itemHeight - 2)) {
                    if (index != selectedIndex) {
                        graphics.fill(listX + 2, itemY, listX + listWidth - 2, itemY + itemHeight - 2, 0xFF222244.toInt())
                    }
                }

                graphics.drawString(font, presetNames[index], listX + 6, itemY + 4, 0xFFFFFF)
            }

            graphics.disableScissor()

            // Scroll indicator
            if (presetNames.size > visibleCount) {
                val scrollBarHeight = listHeight - 4
                val thumbHeight = maxOf(20, scrollBarHeight * visibleCount / presetNames.size)
                val maxScroll = presetNames.size - visibleCount
                val thumbY = listY + 2 + ((scrollBarHeight - thumbHeight) * scrollOffset / maxScroll)

                graphics.fill(listX + listWidth - 6, listY + 2, listX + listWidth - 2, listY + listHeight - 2, 0xFF333333.toInt())
                graphics.fill(listX + listWidth - 5, thumbY, listX + listWidth - 3, thumbY + thumbHeight, 0xFF666666.toInt())
            }
        }

        super.render(graphics, mouseX, mouseY, partialTick)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val dialogX = (width - dialogWidth) / 2
        val dialogY = (height - dialogHeight) / 2
        val listX = dialogX + 10
        val listY = dialogY + 25
        val listWidth = dialogWidth - 20
        val listHeight = dialogHeight - 65
        val itemHeight = 18

        // Check if click is in list area
        if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
            val clickedIndex = ((mouseY - listY - 2) / itemHeight).toInt() + scrollOffset
            if (clickedIndex >= 0 && clickedIndex < presetNames.size) {
                // Double click to load
                if (clickedIndex == selectedIndex && button == 0) {
                    onLoad()
                    return true
                }
                selectedIndex = clickedIndex
                updateButtons()
                return true
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        val maxScroll = maxOf(0, presetNames.size - visibleCount)
        scrollOffset = (scrollOffset - scrollY.toInt()).coerceIn(0, maxScroll)
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        when (keyCode) {
            257 -> { // Enter
                if (selectedIndex >= 0) onLoad()
                return true
            }
            256 -> { // Escape
                onClose()
                return true
            }
            265 -> { // Up arrow
                if (selectedIndex > 0) {
                    selectedIndex--
                    if (selectedIndex < scrollOffset) scrollOffset = selectedIndex
                    updateButtons()
                }
                return true
            }
            264 -> { // Down arrow
                if (selectedIndex < presetNames.size - 1) {
                    selectedIndex++
                    if (selectedIndex >= scrollOffset + visibleCount) scrollOffset = selectedIndex - visibleCount + 1
                    updateButtons()
                }
                return true
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun isPauseScreen(): Boolean = false
}

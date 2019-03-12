/*
 * GPLv3 License
 *
 *  Copyright (c) WAI2K by waicool20
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.waicool20.wai2k.views.tabs.profile

import javafx.scene.control.CheckBox
import javafx.scene.layout.VBox
import tornadofx.*

class FactoryView : AbstractProfileView() {
    override val root: VBox by fxml("/views/tabs/profile/factory.fxml")
    private val enableEnhancementCheckBox: CheckBox by fxid()
    private val enableDisassemblyCheckBox: CheckBox by fxid()
    private val enableEquipEnhancementCheckBox: CheckBox by fxid()

    override fun setValues() = Unit

    override fun createBindings() {
        context.currentProfile.factory.apply {
            enableEnhancementCheckBox.bind(enhancement.enabledProperty)
            enableDisassemblyCheckBox.bind(disassembly.enabledProperty)
            enableEquipEnhancementCheckBox.bind(equipEnhancement.enabledProperty)
        }
    }
}
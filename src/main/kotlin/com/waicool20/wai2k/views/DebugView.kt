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

package com.waicool20.wai2k.views

import com.waicool20.wai2k.android.AndroidDevice
import com.waicool20.wai2k.config.Wai2KContext
import com.waicool20.wai2k.util.Ocr
import com.waicool20.wai2k.util.useCharFilter
import com.waicool20.waicoolutils.javafx.CoroutineScopeView
import com.waicool20.waicoolutils.javafx.addListener
import com.waicool20.waicoolutils.logging.loggerFor
import javafx.embed.swing.SwingFXUtils
import javafx.scene.control.*
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory
import javafx.scene.image.ImageView
import javafx.scene.layout.VBox
import javafx.stage.FileChooser
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import net.sourceforge.tess4j.ITesseract
import org.sikuli.script.ImagePath
import org.sikuli.script.Pattern
import tornadofx.*
import java.nio.file.Files
import java.nio.file.Paths

class DebugView : CoroutineScopeView() {
    override val root: VBox by fxml("/views/debug.fxml")
    private val openButton: Button by fxid()
    private val testButton: Button by fxid()
    private val pathField: TextField by fxid()
    private val assetOCRButton: Button by fxid()

    private val xSpinner: Spinner<Int> by fxid()
    private val ySpinner: Spinner<Int> by fxid()
    private val wSpinner: Spinner<Int> by fxid()
    private val hSpinner: Spinner<Int> by fxid()
    private val ocrImageView: ImageView by fxid()
    private val OCRButton: Button by fxid()
    private val resetOCRButton: Button by fxid()

    private val useLSTMCheckBox: CheckBox by fxid()
    private val filterCheckBox: CheckBox by fxid()
    private val filterOptions: ToggleGroup by fxid()
    private val filterOptionsVBox: VBox by fxid()
    private val digitsOnlyRadioButton: RadioButton by fxid()
    private val customRadioButton: RadioButton by fxid()
    private val allowedCharsTextField: TextField by fxid()

    private var lastAndroidDevice: AndroidDevice? = null

    private val wai2KContext: Wai2KContext by inject()

    private val logger = loggerFor<DebugView>()

    init {
        title = "WAI2K - Debugging tools"
    }

    override fun onDock() {
        super.onDock()
        uiSetup()
        openButton.setOnAction { openPath() }
        testButton.setOnAction { testPath() }
        assetOCRButton.setOnAction { doAssetOCR() }
        OCRButton.setOnAction { doOCR() }
        resetOCRButton.setOnAction { updateOCRUi() }
    }

    private fun uiSetup() {
        filterOptionsVBox.disableWhen { filterCheckBox.selectedProperty().not() }
        updateOCRUi()
        wai2KContext.wai2KConfig.lastDeviceSerialProperty
                .addListener("DebugViewDeviceListener") { _ -> updateOCRUi() }
    }

    fun updateOCRUi(serial: String = wai2KContext.wai2KConfig.lastDeviceSerial) {
        val device = async(Dispatchers.IO) {
            wai2KContext.adbServer.listDevices().find { it.adbSerial == serial }
                    .also { lastAndroidDevice = it }
        }
        launch(Dispatchers.IO) {
            fun updateImageView() = launch(Dispatchers.IO) {
                val image = device.await()!!.takeScreenshot().let {
                    if (wSpinner.value > 0 && hSpinner.value > 0) {
                        it.getSubimage(xSpinner.value, ySpinner.value, wSpinner.value, hSpinner.value)
                    } else it
                }
                withContext(Dispatchers.JavaFx) {
                    ocrImageView.image = SwingFXUtils.toFXImage(image, null)
                }
            }
            withContext(Dispatchers.JavaFx) {
                device.await()?.let {
                    val maxWidth = it.properties.displayWidth
                    val maxHeight = it.properties.displayHeight
                    xSpinner.valueFactory = IntegerSpinnerValueFactory(0, maxWidth, 0)
                    ySpinner.valueFactory = IntegerSpinnerValueFactory(0, maxHeight, 0)
                    wSpinner.valueFactory = IntegerSpinnerValueFactory(0, maxWidth, maxWidth)
                    hSpinner.valueFactory = IntegerSpinnerValueFactory(0, maxHeight, maxHeight)

                    xSpinner.valueProperty().addListener("DebugViewXSpinner") { newVal ->
                        if (newVal + wSpinner.value > maxWidth) {
                            wSpinner.valueFactory.value = maxWidth - newVal
                        }
                        updateImageView()
                    }
                    ySpinner.valueProperty().addListener("DebugViewYSpinner") { newVal ->
                        if (newVal + hSpinner.value > maxHeight) {
                            hSpinner.valueFactory.value = maxHeight - newVal
                        }
                        updateImageView()
                    }
                    wSpinner.valueProperty().addListener("DebugViewWSpinner") { newVal ->
                        if (newVal + xSpinner.value > maxWidth) {
                            wSpinner.valueFactory.value = maxWidth - xSpinner.value
                        }
                        updateImageView()
                    }
                    hSpinner.valueProperty().addListener("DebugViewHSpinner") { newVal ->
                        if (newVal + ySpinner.value > maxHeight) {
                            hSpinner.valueFactory.value = maxHeight - ySpinner.value
                        }
                        updateImageView()
                    }
                }
            }
            while (isActive) {
                updateImageView()
                delay(1000)
            }
        }
    }

    private fun openPath() {
        FileChooser().apply {
            title = "Open path to an asset..."
            initialDirectory = if (pathField.text.isNotBlank()) {
                Paths.get(pathField.text).parent.toFile()
            } else {
                wai2KContext.wai2KConfig.assetsDirectory.toFile()
            }
            extensionFilters.add(FileChooser.ExtensionFilter("PNG files (*.png)", "*.png"))
            showOpenDialog(null)?.let {
                pathField.text = it.path
            }
        }
    }

    private fun testPath() {
        launch(Dispatchers.IO) {
            wai2KContext.apply {
                val path = Paths.get(pathField.text)
                if (Files.exists(path)) {
                    logger.info("Finding $path")
                    ImagePath.add(path.parent.toString())
                    val device = wai2KContext.adbServer.listDevices(true).find { it.adbSerial == wai2KConfig.lastDeviceSerial }
                    if (device == null) {
                        logger.warn("Could not find device!")
                        return@launch
                    }
                    // Set similarity to 0.1f to make sikulix report the similarity value down to 0.6
                    device.screen.findAllOrEmpty(Pattern(path.fileName.toString()).similar(0.6f))
                            .takeIf { it.isNotEmpty() }
                            ?.forEach {
                                logger.info("Found ${path.fileName}: $it")
                            } ?: run { logger.warn("Could not find the asset anywhere") }
                    ImagePath.remove(path.parent.toString())
                } else {
                    logger.warn("That asset doesn't exist!")
                }
            }
        }
    }

    private fun doAssetOCR() {
        launch(Dispatchers.IO) {
            val path = Paths.get(pathField.text)
            if (Files.exists(path)) {
                logger.info("Result: \n${getOCR().doOCR(path.toFile())}\n----------")
            } else {
                logger.warn("That asset doesn't exist!")
            }
        }
    }

    private fun doOCR() {
        launch(Dispatchers.IO) {
            lastAndroidDevice?.let {
                val image = it.takeScreenshot().let { bi ->
                    if (wSpinner.value > 0 && hSpinner.value > 0) {
                        bi.getSubimage(xSpinner.value, ySpinner.value, wSpinner.value, hSpinner.value)
                    } else bi
                }
                logger.info("Result: \n${getOCR().doOCR(image)}\n----------")
            }

        }
    }

    private fun getOCR(): ITesseract {
        val ocr = Ocr.forConfig(
                config = wai2KContext.wai2KConfig,
                digitsOnly = filterCheckBox.isSelected && filterOptions.selectedToggle == digitsOnlyRadioButton,
                useLSTM = useLSTMCheckBox.isSelected
        )
        if (filterCheckBox.isSelected && filterOptions.selectedToggle == customRadioButton) {
            ocr.useCharFilter(allowedCharsTextField.text)
        }
        return ocr
    }
}
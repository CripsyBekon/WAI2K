/*
 * GPLv3 License
 *
 *  Copyright (c) WAI2K by joo
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

package com.waicool20.wai2k.script.modules

import com.waicool20.wai2k.android.AndroidRegion
import com.waicool20.wai2k.config.Wai2KConfig
import com.waicool20.wai2k.config.Wai2KProfile
import com.waicool20.wai2k.game.LocationId
import com.waicool20.wai2k.script.Navigator
import com.waicool20.wai2k.script.ScriptRunner
import com.waicool20.wai2k.util.Ocr
import com.waicool20.wai2k.util.cancelAndYield
import com.waicool20.wai2k.util.doOCRAndTrim
import com.waicool20.waicoolutils.logging.loggerFor
import kotlinx.coroutines.*
import org.sikuli.script.Image
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class ResearchModule(
        scriptRunner: ScriptRunner,
        region: AndroidRegion,
        config: Wai2KConfig,
        profile: Wai2KProfile,
        navigator: Navigator
) : ScriptModule(scriptRunner, region, config, profile, navigator) {
    private val logger = loggerFor<ResearchModule>()

    override suspend fun execute() {
        checkEquipOverflow()
    }

    private suspend fun checkEquipOverflow() {
        if (!gameState.equipOverflow) return
        if (profile.factory.equipEnhancement.enabled) enhanceEquip()
    }


    /**
     * Keeping enhancing equipsments using 2-4* equipment fodders
     */
    private suspend fun enhanceEquip() {
        logger.info("Equip limit reached, will try to enhance")
        navigator.navigateTo(LocationId.EQUIP_ENHANCEMENT)

        var oldEquipCount: List<String>? = null
        val equipsUsedForEnhancement = AtomicInteger(0)
        val statUpdateJobs = mutableListOf<Job>()

        while (isActive) {
            val selectEquipButton = region.subRegion(479, 432, 240, 353)
            // Click select equip
            selectEquipButton.clickRandomly(); delay(500)

            // Find the old equip count
            statUpdateJobs += updateEquip(region.subRegion(1750, 810, 290, 70).takeScreenshot()) { count ->
                val c = count[0].toInt()
                oldEquipCount?.get(0)?.toIntOrNull()?.let {
                    equipsUsedForEnhancement.getAndAdd(it - c)
                }
                oldEquipCount = count
                c >= count[1].toInt()
            }

            logger.info("Selecting first available equip for enhancement")
            // Randomly select a equip on the screen for enhancement
            while (isActive) {
                val equip = region.findAllOrEmpty("research/5star.png")
                        .also { logger.info("Found ${it.size} equips on screen available for enhancement") }
                        // Map lock region to doll region
                        .map { region.subRegion(it.x - 104, it.y, 244, 432) }
                        // Prioritize higher level dolls
                        .sortedBy { it.y * 10 + it.x }
                        .firstOrNull()
                if (equip == null) {
                    logger.info("No equipments that can be enhanced found")
                    // Click cancel
                    region.subRegion(120, 0, 205, 144).clickRandomly()
                    return
                } else {
                    equip.clickRandomly()
                    break
                }
            }

            delay(400)

            // Click "Select Equip" button
            logger.info("Selecting equipment used for enhancement")
            region.subRegion(791, 219, 1125, 441).find("research/select.png").clickRandomly()
            delay(200)

            val twoStars = region.subRegion(123, 155, 261, 454).findOrNull("research/2star.png")

            // Click smart select button
            logger.info("Using smart select")
            region.subRegion(1770, 900, 240, 150).clickRandomly(); yield()

            // Confirm equip fodder selection
            val okButton = region.findOrNull("research/ok.png")
            if (twoStars == null) {
                // Click cancel if no equipment could be used for enhancement
                region.subRegion(120, 0, 205, 144).clickRandomly()
                logger.info("out of 2 star equipments")
                break
            } else if (okButton != null) {
                okButton.clickRandomly()
                scriptStats.equipEnhancementsDone += 1
            }

            delay(200)
            // Click ok button
            region.subRegion(1723, 913, 214, 82).clickRandomly(); delay(2600)
        }


        // Click "Select Equip" button
        logger.info("Selecting equipment used for enhancement")
        region.subRegion(791, 219, 1125, 441).find("research/select.png").clickRandomly()
        delay(200)

        logger.info("Enhancing using 3 star equipment")
        logger.info("Applying filters")
        applyEquipFilters(3)
        delay(750)

        while (isActive) {
            statUpdateJobs += updateEquip(region.subRegion(1750, 515, 290, 70).takeScreenshot()) { count ->
                val c = count[0].toInt()
                oldEquipCount?.get(0)?.toIntOrNull()?.let {
                    equipsUsedForEnhancement.getAndAdd(it - c)
                }
                oldEquipCount = count
                c >= count[1].toInt()
            }

            val equip = region.findAllOrEmpty("research/3star.png")
                    .also { logger.info("Found ${it.size} that can be enhanced") }
                    .map { region.subRegion(it.x - 104, it.y, 244, 432) }
            if (equip.isEmpty()) {
                // Click cancel if no equip could be used for enhancement
                region.subRegion(120, 0, 205, 144).clickRandomly()
                break
            }
            // Select all the equips
            region.mouseDelay(0.0) {
                equip.sortedBy { it.y * 10 + it.x }.forEach { it.clickRandomly() }
            }
            // Click ok
            region.subRegion(1767, 880, 252, 185).find("research/ok.png").clickRandomly(); yield()
            // Click ok button
            region.subRegion(1723, 913, 214, 82).clickRandomly(); delay(2600)
            // Update stats
            // Can break if disassembled count is less than 12
            if (equip.size < 12) {
                scriptStats.equipEnhancementsDone += 1
                break
            }
            // Wait for menu to settle
            region.subRegion(791, 219, 1125, 441)
                    .waitSuspending("research/select.png", 10)?.let {
                        it.clickRandomly()
                        delay(750)
                    }
        }

        // Click "Select Equip" button
        logger.info("Selecting equipment used for enhancement")
        region.subRegion(791, 219, 1125, 441).find("research/select.png").clickRandomly()
        delay(200)

        logger.info("Enhancing using 4 star equipment")
        logger.info("Applying filters")
        applyEquipFilters(4)
        delay(750)

        while (isActive) {
            statUpdateJobs += updateEquip(region.subRegion(1750, 515, 290, 70).takeScreenshot()) { count ->
                val c = count[0].toInt()
                oldEquipCount?.get(0)?.toIntOrNull()?.let {
                    equipsUsedForEnhancement.getAndAdd(it - c)
                }
                oldEquipCount = count
                c >= count[1].toInt()
            }

            val equip = region.findAllOrEmpty("research/4star.png")
                    .also { logger.info("Found ${it.size} that can be enhanced") }
                    .map { region.subRegion(it.x - 104, it.y, 244, 432) }
            if (equip.isEmpty()) {
                // Click cancel if no equip could be used for enhancement
                region.subRegion(120, 0, 205, 144).clickRandomly()
                break
            }
            // Select all the equips
            region.mouseDelay(0.0) {
                equip.sortedBy { it.y * 10 + it.x }.forEach { it.clickRandomly() }
            }
            // Click ok
            region.subRegion(1767, 880, 252, 185).find("research/ok.png").clickRandomly(); yield()
            // Click ok button
            region.subRegion(1723, 913, 214, 82).clickRandomly(); delay(2600)
            // Click confirm
            region.subRegion(1100, 688, 324, 161).find("confirm.png").clickRandomly(); delay(200)
            // Update stats
            // Can break if disassembled count is less than 12
            if (equip.size < 12) {
                scriptStats.equipEnhancementsDone += 1
                break
            }
            // Wait for menu to settle
            region.subRegion(791, 219, 1125, 441)
                    .waitSuspending("research/select.png", 10)?.let {
                        it.clickRandomly()
                        delay(750)
                    }
        }

        // Update stats after all the update jobs are complete
        launch {
            statUpdateJobs.forEach { it.join() }
            scriptStats.equipsUsedForEnhancement += equipsUsedForEnhancement.get()
            if (!gameState.equipOverflow) logger.info("The base now has space for new equipment")
        }
    }

    private fun updateEquip(screenshot: BufferedImage, action: (List<String>) -> Boolean): Job {
        return launch {
            Ocr.forConfig(config).doOCRAndTrim(screenshot)
                    .also { logger.info("Detected equip count: $it") }
                    .split(Regex("\\D"))
                    .let { currentEquipCount ->
                        gameState.equipOverflow = try {
                            action(currentEquipCount)
                        } catch (e: Exception) {
                            false
                        }
                    }
        }
    }
}


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

package com.waicool20.wai2k.game

import com.waicool20.wai2k.android.AndroidRegion

class EquipFilterRegions(region: AndroidRegion) {
    /**
     * 'Filter By' button
     */
    val filter = region.subRegion(1791, 192, 193, 121)

    /**
     * Map of 1-5 star rating and their regions
     */
    val starRegions = mapOf(
            5 to region.subRegion(917, 215, 256, 117),
            4 to region.subRegion(1188, 215, 256, 117),
            3 to region.subRegion(1459, 215, 256, 117),
            2 to region.subRegion(917, 349, 256, 117)

    )

    /**
     * Reset button
     */
    val reset = region.subRegion(902, 980, 413, 83)
    /**
     * Confirm button
     */
    val confirm = region.subRegion(1318, 980, 413, 83)
}
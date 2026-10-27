/**
 *  Copyright (C) 2002-2024   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.client.gui;

import java.awt.image.BufferedImage;
import java.util.Set;

import net.sf.freecol.common.io.FreeColDataFile;
import net.sf.freecol.common.io.FreeColDirectories;
import net.sf.freecol.common.io.FreeColTcFile;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.resources.ImageResource;
import net.sf.freecol.common.resources.ResourceManager;
import net.sf.freecol.util.test.FreeColTestCase;


/**
 * The nation soldier pictures (mod): every unit type with a soldier
 * picture has one per nation, and it loads.
 */
public class NationArtTest extends FreeColTestCase {

    private static final String[] NATIONS = { "dutch", "french", "english" };

    /** Soldiers with a picture of their own, the same for every nation. */
    private static final Set<String> OWN_PICTURE = Set.of(
        "model.unit.indianConvert", "model.unit.colonialRegular",
        "model.unit.revenger", "model.unit.undead");


    @Override
    public void setUp() {
        try {
            ResourceManager.setBaseData(new FreeColDataFile(
                FreeColDirectories.getBaseDirectory()));
            // The unit pictures are in the default data, as in the game.
            ResourceManager.setTcData(FreeColTcFile.getFreeColTcFile("default"));
        } catch (Exception e) {
            fail("Could not load base data: " + e.getMessage());
        }
        ResourceManager.prepare();
    }

    public void testEverySoldierHasNationPictures() {
        int checked = 0;
        for (UnitType type : spec().getUnitTypeList()) {
            final String key = "image.unit." + type.getId() + ".soldier";
            if (ResourceManager.getImageResource(key, false) == null
                || OWN_PICTURE.contains(type.getId())) {
                continue;
            }
            for (String n : NATIONS) {
                final ImageResource r
                    = ResourceManager.getImageResource(key + "." + n, false);
                assertNotNull(key + "." + n, r);
                final BufferedImage im = r.getImage();
                assertNotNull(key + "." + n, im);
                assertEquals(33, im.getWidth());
            }
            checked++;
        }
        // The colonist, the experts, the veteran...
        assertTrue("checked " + checked, checked >= 20);
    }

    public void testVeteranLooksDifferent() {
        for (String n : NATIONS) {
            final BufferedImage colonist = ResourceManager.getImageResource(
                "image.unit.model.unit.freeColonist.soldier." + n, false)
                .getImage();
            final BufferedImage veteran = ResourceManager.getImageResource(
                "image.unit.model.unit.veteranSoldier.soldier." + n, false)
                .getImage();
            int differ = 0;
            for (int y = 0; y < colonist.getHeight(); y++) {
                for (int x = 0; x < colonist.getWidth(); x++) {
                    if (colonist.getRGB(x, y) != veteran.getRGB(x, y)) differ++;
                }
            }
            assertTrue(n + " differs in " + differ, differ > 300);
        }
    }
}

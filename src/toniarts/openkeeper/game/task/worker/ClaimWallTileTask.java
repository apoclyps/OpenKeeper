/*
 * Copyright (C) 2014-2016 OpenKeeper
 *
 * OpenKeeper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenKeeper is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenKeeper.  If not, see <http://www.gnu.org/licenses/>.
 */
package toniarts.openkeeper.game.task.worker;

import toniarts.openkeeper.game.controller.IGameWorldController;
import toniarts.openkeeper.game.controller.IMapController;
import toniarts.openkeeper.game.controller.creature.ICreatureController;
import toniarts.openkeeper.game.map.MapTile;
import toniarts.openkeeper.tools.convert.map.ArtResource;

/**
 * Claim a wall task
 *
 * @author Toni Helenius <helenius.toni@gmail.com>
 */
public class ClaimWallTileTask extends DigTileTask {

    public ClaimWallTileTask(final IGameWorldController gameWorldController, final IMapController mapController, int x, int y, short playerId) {
        super(gameWorldController, mapController, x, y, playerId);
    }

    @Override
    public boolean isValid(ICreatureController creature) {
        MapTile tile = mapController.getMapData().getTile(getTaskLocation());
        return mapController.isClaimableWall(getTaskLocation().x, getTaskLocation().y, playerId) && !tile.isSelected(playerId);
    }

    @Override
    public int getMaxAllowedNumberOfAsignees() {
        // TODO: I think it is 1 per accessible side
        return 1;
    }

    @Override
    public int getPriority() {
        return 176;
    }

    @Override
    public String toString() {
        return "Claim wall at " + getTaskLocation();
    }

    @Override
    protected String getStringId() {
        return "2603";
    }

    @Override
    public void executeTask(ICreatureController creature, float executionDuration) {

        // TODO: is this a general case or even smart to do this like this...?
        if (executionDuration - getExecutionDuration(creature) >= 1.0f) {
            setExecutionDuration(creature, executionDuration - getExecutionDuration(creature));

            mapController.applyClaimTile(getTaskLocation(), playerId);
        }
    }

    @Override
    public ArtResource getTaskAnimation(ICreatureController creature) {
        //return creature.getCreature().getAnimSleepResource();
        return null;
    }

    @Override
    public String getTaskIcon() {
        return "Textures/GUI/moods/SJ-Reinforce.png";
    }

}
